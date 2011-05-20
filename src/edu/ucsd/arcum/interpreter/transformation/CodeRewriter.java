package edu.ucsd.arcum.interpreter.transformation;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;
import static edu.ucsd.arcum.util.StringUtil.debugDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.ast.ASTUtil;
import edu.ucsd.arcum.interpreter.ast.ASTUtil.ASTPath;
import edu.ucsd.arcum.interpreter.fragments.ResolvedType;
import edu.ucsd.arcum.interpreter.parser.ASTVisitorAdaptor;
import edu.ucsd.arcum.interpreter.query.ASTTraverseTable;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.EntityTuple;

public class CodeRewriter
{
    private static final String TRACKING_ID = "edu.ucsd.arcum.interpreter.transformation.CodeRewriter.TRACKING_ID";
    final private List<Change> additionalChanges;
    final private Map<String, ASTRewrite> rewriteLookup;
    final private Map<String, ImportRewrite> importRewriteLookup;
    final private Map<String, CompilationUnit> unitLookup;
    final private TextEditGroup group;

    // to keep track of the first doomed node per each type declaration, so that
    // any new nodes inserted can go to the same place (that way, if the user has
    // a preference for locations this should be the least surprising)
    final private Map<TypeDeclaration, ASTNode> typeDeclarationMap;
    
    final private Set<ASTNode> deletedNodes;
    private List<ASTNode> trackedNodes;

    final private static ASTTraverseTable traverser;
    static {
        traverser = new ASTTraverseTable();
    }
    
    public CodeRewriter(List<Change> additionalChanges, String editMessage) {
        this.additionalChanges = additionalChanges;
        this.rewriteLookup = Maps.newHashMap();
        this.importRewriteLookup = Maps.newHashMap();
        this.unitLookup = Maps.newHashMap();
        this.group = new TextEditGroup(editMessage);
        this.typeDeclarationMap = Maps.newIdentityHashMap();
        this.deletedNodes = Sets.newHashSet();
    }

    public Change[] getChanges() throws IllegalArgumentException, CoreException {
        List<Change> result = new ArrayList<Change>(additionalChanges);
        for (String unit : rewriteLookup.keySet()) {
            ASTRewrite rewrite = rewriteLookup.get(unit);
            ImportRewrite importRewrite = importRewriteLookup.get(unit);
            CompilationUnit cu = unitLookup.get(unit);

            MultiTextEdit edits = new MultiTextEdit();
            TextEdit astEdits = rewrite.rewriteAST();
            edits.addChild(astEdits);
            if (importRewrite != null) {
                TextEdit importEdits = importRewrite.rewriteImports(null);
                edits.addChild(importEdits);
            }

            IFile file = (IFile)cu.getJavaElement().getResource();
            TextFileChange change = new TextFileChange(unit, file);
            change.setTextType("java");
            change.setEdit(edits);

            result.add(change);
        }
        return result.toArray(new Change[0]);
    }

    public void insertNode(ASTNode parentNode, ASTNode newChildNode) {
        ASTRewrite rewrite = getRewriter(ASTUtil.findCompilationUnit(parentNode));
        ImportRewrite imports = getImportRewriter(ASTUtil.findCompilationUnit(parentNode));

        if (parentNode instanceof TypeDeclaration) {
            TypeDeclaration declaration = (TypeDeclaration)parentNode;
            ListRewrite listRewrite;
            listRewrite = rewrite.getListRewrite(parentNode,
                TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
            ASTNode firstRemoved = typeDeclarationMap.get(declaration);

            newChildNode = ensugarNode(newChildNode, imports);

            if (firstRemoved != null) {
                listRewrite.insertAfter(newChildNode, firstRemoved, group);
            }
            else {
                listRewrite.insertLast(newChildNode, group);
            }
        }

    }

    public void removeNode(ASTNode doomed) {
        deletedNodes.add(doomed);

        if (true||DEBUG) {
            System.out.printf("%nI'm gonna delete: [%s]%n", doomed.toString().trim());
        }

        ASTNode parent = doomed.getParent();
        ASTRewrite rewrite = getRewriter(ASTUtil.findCompilationUnit(parent));
        rewrite.remove(doomed, group);

        if (parent instanceof TypeDeclaration) {
            TypeDeclaration declaration = (TypeDeclaration)parent;
            if (typeDeclarationMap.get(declaration) == null) {
                typeDeclarationMap.put(declaration, doomed);
            }
        }
    }

    // Assumed to be called in a top-down order. That is, nodes higher up in the tree
    // get replaced first. Also assumes all original nodes being replaced are being
    // tracked.
    public void replaceNode(ASTNode original, EntityTuple replacementTuple) {
        final CompilationUnit cu = ASTUtil.findCompilationUnit(original);
        final ASTRewrite rewriter = getRewriter(cu);
        final ImportRewrite imports = getImportRewriter(cu);

        final int trackingID = (Integer)original.getProperty(TRACKING_ID);
        
        ASTNode replacement = replacementTuple.getReplacementNode(original);
        replacement = ensugarNode(replacement, imports);

        original = trackedNodes.get(trackingID);
        if (original == null){
            ArcumError.fatalError("Cannot replace node that wasn't tracked!");
        }
        if (true || DEBUG) {
            System.out.printf("Replacing %s (%d)%n with: %s%n", debugDisplay(original),
                trackingID, replacement);
        }
        // GETDONE: WAS HERE LAST -- If original is a sugared guy, we need to replace
        // the list (e.g., of field declarations) using the list rewritter
        rewriter.replace(original, replacement, group);
        List<ASTNode> updatedChildren = findAllTrackedNodes(replacement);
        for (ASTNode updatedChild : updatedChildren) {
            int id = (Integer)getTrackingID(updatedChild);
            trackedNodes.set(id, updatedChild);
        }
    }

    private ASTNode searchForTrackingIdentifier(ASTNode node, final int trackingID) {
        final ASTNode[] result = new ASTNode[1];
        traverser.traverseAST(node, new ASTVisitorAdaptor() {
            @Override
            public boolean visitASTNode(ASTNode node, StructuralPropertyDescriptor edge) {
                if (node == null) {
                    return false;
                }
                Integer foundID = (Integer)node.getProperty(TRACKING_ID);
                if (foundID != null && foundID == trackingID) {
                    result[0] = node;
                    return false;
                }
                return true;
            }
        });
        return result[0];
    }

    private ASTNode removeObsoleteNodes(final ASTNode original) {
        final ASTNode result = Entity.copySubtree(original.getAST(), original);
        traverser.traverseAST(original, new ASTVisitorAdaptor() {
            @Override
            public boolean visitASTNode(ASTNode node, StructuralPropertyDescriptor edge) {
                if (node == null) {
                    return false;
                }
                ASTPath path = ASTUtil.getPathToRoot(node, original);
                ASTNode element = path.getASTNodeFrom(result);
                ASTUtil.recordUpdatedNode(node, element);
                if (deletedNodes.contains(node)) {
                    ASTUtil.removeNodeFromParent(element);
                    return false;
                }
                else {
                    return true;
                }
            }
        });
        return result;
    }

    // Remove qualifiers from the given node, if possible. E.g., fully qualified
    // type names become simple type names when an import can be done.
    private ASTNode ensugarNode(ASTNode node, ImportRewrite imports) {
        ASTNode newNode = doEnsugarNode(node, imports, null);
        if (newNode != null) {
            if (true || DEBUG) {
                System.out.printf("Ensugaring %s into %s%n", debugDisplay(node),
                    debugDisplay(newNode));
            }
            return newNode;
        }
        else {
            return node;
        }
    }

    // track changes based on if we return null or not
    private ASTNode doEnsugarNode(ASTNode node, ImportRewrite imports,
        StructuralPropertyDescriptor parentEdge)
    {
        if (node instanceof QualifiedName) {
            return ensugarQualifiedName((QualifiedName)node, imports, parentEdge);
        }
        else {
            StructuralPropertyDescriptor[] spds = ASTTraverseTable.getProperties(node);
            boolean madeChanges = false;
            edges: for (StructuralPropertyDescriptor spd : spds) {
                Object property = node.getStructuralProperty(spd);
                if (property == null) {
                    continue edges;
                }
                if (spd.isChildProperty()) {
                    ASTNode child = (ASTNode)property;
                    // Don't ensugar tracked nodes: They will be replaced anyway
                    if (!isTracked(child)) {
                        ASTNode n = doEnsugarNode(child, imports, spd);
                        if (n != null) {
                            node.setStructuralProperty(spd, n);
                            madeChanges = true;
                        }
                    }
                }
                else if (spd.isChildListProperty()) {
                    List children = (List)property;
                    for (int i = 0; i < children.size(); ++i) {
                        ASTNode child = (ASTNode)children.get(i);
                        // Ditto
                        if (!isTracked(child)) {
                            ASTNode n = doEnsugarNode(child, imports, spd);
                            if (n != null) {
                                children.set(i, n);
                                madeChanges = true;
                            }
                        }
                    }
                }
            }
            if (madeChanges) {
                ASTNode newNode = Entity.copySubtree(node.getAST(), node);
                return newNode;
            }
            else {
                return null;
            }
        }
    }

    // if we can't change it, return null
    private ASTNode ensugarQualifiedName(QualifiedName name, ImportRewrite imports,
        StructuralPropertyDescriptor parentEdge)
    {
        if (representsAType(name, parentEdge)) {
            String fullyQualifiedName = name.getFullyQualifiedName();
            String toUse = imports.addImport(fullyQualifiedName);
            if (toUse.contains(".")) {
                return null;
            }
            else {
                if (true || DEBUG) {
                    System.out.printf("The name [%s] can be [%s].%n", name, toUse);
                }
                AST ast = name.getAST();
                Name newName = ast.newName(toUse);
                return newName;
            }
        }
        return null;
    }

    private boolean representsAType(QualifiedName name, StructuralPropertyDescriptor parentEdge) {
        if (parentEdge == SimpleType.NAME_PROPERTY) {
            return true;
        }
        String fullyQualifiedName = name.getFullyQualifiedName();
        if (ResolvedType.isKnownType(fullyQualifiedName)) {
            return true;
        }
        return false;
    }

    private ASTRewrite getRewriter(CompilationUnit rootNode) {
        String cuName = rootNode.getJavaElement().getPath().toString();

        ASTRewrite rewrite = rewriteLookup.get(cuName);
        if (rewrite == null) {
            AST ast = rootNode.getAST();
            rewrite = ASTRewrite.create(ast);
            rewriteLookup.put(cuName, rewrite);
            if (!unitLookup.containsKey(cuName)) {
                unitLookup.put(cuName, rootNode);
            }
        }
        return rewrite;
    }

    private ImportRewrite getImportRewriter(CompilationUnit rootNode) {
        String cuName = rootNode.getJavaElement().getPath().toString();

        ImportRewrite importRewrite = importRewriteLookup.get(cuName);
        if (importRewrite == null) {
            importRewrite = ImportRewrite.create(rootNode, true);
            importRewriteLookup.put(cuName, importRewrite);
            if (!unitLookup.containsKey(cuName)) {
                unitLookup.put(cuName, rootNode);
            }
        }
        return importRewrite;
    }

    public void trackNodes(List<ASTNode> nodesToTrack) {
        int trackingID = 0;
        for (ASTNode node : nodesToTrack) {
            node.setProperty(TRACKING_ID, trackingID++);
        }
        this.trackedNodes = Lists.newArrayList(nodesToTrack);
    }

    public void resetTracking() {
        for (ASTNode node : trackedNodes) {
            node.setProperty(TRACKING_ID, null);
        }
        this.trackedNodes = null;
    }

    public static boolean isTracked(ASTNode node) {
        return node.getProperty(TRACKING_ID) != null;
    }

    public static Object getTrackingID(ASTNode node) {
        return node.getProperty(TRACKING_ID);
    }

    public static void setTrackingID(ASTNode node, Object id) {
        node.setProperty(TRACKING_ID, id);
    }

    public static List<ASTNode> findAllTrackedNodes(ASTNode node) {
        final List<ASTNode> result = Lists.newArrayList();
        traverser.traverseAST(node, new ASTVisitorAdaptor() {
            @Override
            public boolean visitASTNode(ASTNode node, StructuralPropertyDescriptor edge) {
                if (node == null) {
                    return false;
                }
                if (isTracked(node)) {
                    System.out.printf("Tracking %s (%d)%n", debugDisplay(node), getTrackingID(node));
                    result.add(node);
                }
                return true;
            }
        });
        return result;
    }

}