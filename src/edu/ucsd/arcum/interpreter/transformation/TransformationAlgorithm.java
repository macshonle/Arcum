package edu.ucsd.arcum.interpreter.transformation;

import static com.google.common.base.ReferenceType.WEAK;

import java.util.*;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.text.edits.MalformedTreeException;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.ReferenceMap;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.EclipseUtil;
import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.UserCompilationProblem;
import edu.ucsd.arcum.interpreter.ast.ASTUtil;
import edu.ucsd.arcum.interpreter.ast.Option;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.*;
import edu.ucsd.arcum.interpreter.satisfier.NodesWithLocations;
import edu.ucsd.arcum.ui.UIUtil;
import edu.ucsd.arcum.ui.wizards.ArcumRefactoringWizard;
import edu.ucsd.arcum.util.Graph;

public class TransformationAlgorithm
{
    private final ArcumDeclarationTable arcumDeclarationTable;
    private final OptionMatchTable oldEntities;
    private final OptionMatchTable newEntities;
    private final List<Change> externalChanges;
    private final String transformationMesg;
    private final String srcText;

    // Constructs a new transformation from the originalOption to the
    // alternativeOption, given that both are declared in the top-level symbol table
    public TransformationAlgorithm(ArcumDeclarationTable arcumDeclarationTable,
        Option originalOption, Option alternativeOption, String srcText)
        throws CoreException
    {
        this.arcumDeclarationTable = arcumDeclarationTable;
        this.oldEntities = arcumDeclarationTable.constructEntityTable(srcText);
        this.newEntities = new OptionMatchTable(arcumDeclarationTable, originalOption,
            alternativeOption, oldEntities);
        this.externalChanges = new ArrayList<Change>();
        this.transformationMesg = String.format("Transform %s to %s", originalOption
            .getName(), alternativeOption.getName());
        this.srcText = srcText;
    }

    // add an additional change to the Java source code refactoring, call before
    // calling transform
    public void addChange(Change change) {
        externalChanges.add(change);
    }

    // Returns true if the user clicked OK; false on error or cancel
    public boolean transform()
        throws IllegalArgumentException, MalformedTreeException, BadLocationException,
        CoreException, InterruptedException
    {
        try {
            EntityDataBase.pushCurrentDataBase(arcumDeclarationTable.getEntityDataBase());

            CodeRewriter rewriter;
            rewriter = new CodeRewriter(externalChanges, "Transform Implementation");

            // Transformation algorithm:
            // * remove the existing singleton and locals
            Collection<ASTNode> doomedNodes = oldEntities.getRemovableLocalNodes();
            removeNodes(rewriter, doomedNodes);

            // * create the new singletons and other locals
            NodesWithLocations newSingletons;
            newSingletons = newEntities.generateLocalEntities();
            insertSingletons(rewriter, newSingletons);

            // * transform all traits
            Collection<TraitValue> traits;
            traits = oldEntities.getNonSingletons();
            transformTraits(rewriter, traits);

            // MACNEIL (!!!): Also need to filter the built-in traits asserted to
            // be true: Need to take the new root and set that as the binding. Could
            // be tricky and may require special recomputation of the predicate

            // MACNEIL (!!!): Right here would be the location to add the locals that
            // use "before" or "after" constructs that refer to an interface-level trait
            /*... e.g., ... = newEntities.generateTraitDependentLocalEntities(); */

            // Then, write the changes to the buffers
            boolean completed = queryUserAndRefactor(transformationMesg, rewriter);

            if (completed) {
                arcumDeclarationTable.disposeEntityTable(srcText);
                return true;
            }
            else {
                return false;
            }
        }
        catch (UserCompilationProblem e) {
            UIUtil.error(String.format(
                "Cannot transform, please check your"
                    + " code and try again. You may need to select"
                    + " \"Project->Clean...\" and then click on \"Refresh.\""
                    + " Cause: %s.", e.getMessage()), "Check Failed");
            ArcumError.fatalUserError(e.getPosition(), "%s", e.getMessage());
            return false;
        }
        finally {
            EntityDataBase.popMostRecentDataBase();
        }
    }

    private void removeNodes(CodeRewriter rewriter, Collection<ASTNode> doomedNodes) {
        // If we're deleting the whole parent, no need to separately delete the child
        List<ASTNode> doomedRoots = ASTUtil.findTrees(doomedNodes);
        for (ASTNode doomed : doomedRoots) {
            rewriter.removeNode(doomed);
        }
    }

    private void insertSingletons(CodeRewriter rewriter, NodesWithLocations newSingletons)
    {
        EntityDataBase edb = arcumDeclarationTable.getEntityDataBase();
        List<TraitValue> locations = newSingletons.getLocations();
        for (TraitValue location : locations) {
            List<EntityTuple> entities = location.getEntities();
            for (EntityTuple tuple : entities) {
                Object entityToAdd = tuple.lookupEntity(EntityDataBase.CHILD_VAR_REF);
                Object parent = tuple.lookupEntity(EntityDataBase.PARENT_VAR_REF);
                if (parent instanceof ITypeBinding) {
                    parent = edb.lookupTypeDeclaration((ITypeBinding)parent);
                }
                ASTNode parentNode = (ASTNode)parent;
                ASTNode entityNode = (ASTNode)entityToAdd;
                rewriter.insertNode(parentNode, entityNode);
            }
        }
        
//        List<EntityTuple> nodes = newSingletons.getNodes();
//        nodes = filterOutNonRoots(nodes);
//        for (EntityTuple node : nodes) {
//            ASTNode rootNode = node.getRootNode();
//            ASTNode ownerNode = newSingletons.findParentLocation(rootNode, dataBase);
//
//            if (DEBUG) {
//                System.out.printf("I'm creating: [%s]%n -and adding it to %s.%n",
//                    rootNode.toString().trim(), firstLine(ownerNode.toString()));
//            }
//
//            rewriter.insertNode(ownerNode, rootNode);
//        }
    }

    private List<EntityTuple> filterOutNonRoots(List<EntityTuple> entityTuples) {
        ReferenceMap<ASTNode, EntityTuple> map;
        map = new ReferenceMap<ASTNode, EntityTuple>(WEAK, WEAK);
        Set<ASTNode> astNodes = Sets.newHashSet();
        for (EntityTuple entityTuple : entityTuples) {
            ASTNode astNode = entityTuple.getRootNode();
            if (astNode != null) {
                map.put(astNode, entityTuple);
                astNodes.add(astNode);
            }
        }
        Iterator<ASTNode> it = astNodes.iterator();
        astNode: while (it.hasNext()) {
            ASTNode astNode = it.next();
            for (;;) {
                ASTNode parent = astNode.getParent();
                if (parent == null)
                    break;
                if (astNodes.contains(parent)) {
                    it.remove();
                    continue astNode;
                }
                astNode = parent;
            }
        }
        List<EntityTuple> result = Lists.newArrayList();
        for (EntityTuple entityTuple : entityTuples) {
            ASTNode astNode = entityTuple.getRootNode();
            if (astNodes.contains(astNode)) {
                result.add(entityTuple);
            }
        }
        return result;
    }

    // URGENT: Need to check that any option realizes only the traits in the interface,
    // except for extra singletons and static traits (the check would not go here,
    // however, but is a pre-condition for the getRealizationStatement call below to
    // not get a null pointer error)
    //
    // MACNEIL: Note that it's possible for the destination option to have even
    // more matches from code that was around before (meaning that if it were to
    // be translated back to the original option, it would have more matches). Thus,
    // we will likely need to rematch everything: Building the table again in this
    // manner (by indirectly calling newEntities.addTrait(...)) is not complete.
    private void transformTraits(CodeRewriter rewriter, Collection<TraitValue> traits)
        throws CoreException
    {
        Map<ASTNode, EntityTuple> nodesMap = extractNonStaticNodes(traits);
        List<EntityTuple> originalEntities = topDownOrdering(nodesMap);

        try {
            List<ASTNode> originalNodes;
            originalNodes = Lists.transform(originalEntities, EntityTuple.getRootNode);
            rewriter.trackNodes(originalNodes);
            
            for (EntityTuple original : originalEntities) {
                EntityTuple replacement;
                replacement = newEntities.generateEntityReplacement(original);
                rewriter.replaceNode(original.getRootNode(), replacement);
            }
        }
        finally {
            rewriter.resetTracking();
        }
    }

    // Extracts from the given collection all ASTNodes that are non-static traits.
    // At the same time, the "newEntities" table it updated to have entries for
    // these traits
    private Map<ASTNode, EntityTuple> extractNonStaticNodes(Collection<TraitValue> traits)
    {
        Map<ASTNode, EntityTuple> allNodes = Maps.newIdentityHashMap();
        traitIteration: for (TraitValue trait : traits) {
            if (trait.isStatic()) {
                continue traitIteration;
            }
            TraitSignature traitType = trait.getTraitType();
            newEntities.addTrait(traitType, trait.isNested(), trait.isStatic());
            for (EntityTuple entity : trait.getEntities()) {
                ASTNode rootNode = entity.getRootNode();
                allNodes.put(rootNode, entity);
            }
        }
        return allNodes;
    }

    // Forms a graph that abstract the given "nodes" containment relationships and
    // returns a top-down ordering of their associated entity tuples
    private List<EntityTuple> topDownOrdering(Map<ASTNode, EntityTuple> nodes) {
        Graph<EntityTuple> hasDescendantGraph = Graph.newGraph();
        Set<ASTNode> justASTNodes = nodes.keySet();
        eachNode: for (ASTNode node : justASTNodes) {
            EntityTuple child = nodes.get(node);
            hasDescendantGraph.addNode(child);
            ASTNode ancestorNode = node.getParent();
            while (ancestorNode != null) {
                if (nodes.containsKey(ancestorNode)) {
                    EntityTuple ancestor = nodes.get(ancestorNode);
                    hasDescendantGraph.addEdge(ancestor, child);
                    continue eachNode;
                }
                ancestorNode = ancestorNode.getParent();
            }
        }

        List<EntityTuple> tops = hasDescendantGraph.getTrees();
        System.out.printf("%d change(s), with %d root(s):%n", nodes.size(), tops.size());
        for (EntityTuple top : tops) {
            System.out.printf(" -- %s%n", top);
        }
        System.out.printf("%n");

        List<EntityTuple> result = Lists.newArrayList();
        for (EntityTuple top : tops) {
            result.addAll(hasDescendantGraph.breadthFirstSearch(top));
        }
        return result;
    }

    // returns true if the change actually happened, false if the user canceled
    private boolean queryUserAndRefactor(String transformationMesg, CodeRewriter rewriter)
        throws InterruptedException
    {
        ArcumRefactoring refactoring;
        refactoring = new ArcumRefactoring(transformationMesg, rewriter);

        RefactoringWizard wizard = new ArcumRefactoringWizard(refactoring);

        RefactoringWizardOpenOperation operation;
        operation = new RefactoringWizardOpenOperation(wizard);
        int button = operation.run(EclipseUtil.getShell(), refactoring.getName());
        return (button == IDialogConstants.OK_ID);
    }
}