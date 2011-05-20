package edu.ucsd.arcum.interpreter.fragments;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.util.DynamicScope;
import edu.ucsd.arcum.util.Indenter;

public abstract class ProgramFragment
{
    private static DynamicScope<Indenter> indenter = DynamicScope.newInstance();

    // returns bindings matched on the specific entity, or null if there is no match
    public final BindingMap matches(Object entity) {
        if (entity == null) {
            ArcumError.fatalError("Might need to be wrapped in empty entity");
        }

        if (entity instanceof ASTNode) {
            return matchesASTNode((ASTNode)entity);
        }
        else if (entity instanceof ITypeBinding) {
            return matchesTypeBinding((ITypeBinding)entity);
        }
        else if (entity instanceof EntityList) {
            return matchesEntityList((EntityList)entity);
        }
        else if (entity instanceof ModifierElement) {
            return matchesModifierElement((ModifierElement)entity);
        }
        else if (entity instanceof SignatureEntity) {
            return matchesSignatureEntity((SignatureEntity)entity);
        }
        else if (entity instanceof EmptyEntityInfo) {
            return matchesEmpty((EmptyEntityInfo)entity);
        }
        else {
            return matchesSimpleProperty(entity);
        }
    }

    protected BindingMap matchesASTNode(ASTNode astNode) {
        return null;
    }

    protected BindingMap matchesTypeBinding(ITypeBinding typeBinding) {
        return null;
    }

    protected BindingMap matchesEntityList(EntityList list) {
        return null;
    }

    // Some SubtreeNodes, like ModifiersList, can represent lists of things
    // that may be empty. This is to check for those situations.
    protected BindingMap matchesEmpty(EmptyEntityInfo emptyEntityInfo) {
        return null;
    }

    protected BindingMap matchesModifierElement(ModifierElement modifier) {
        return null;
    }

    protected BindingMap matchesSignatureEntity(SignatureEntity signature) {
        return null;
    }

    protected BindingMap matchesSimpleProperty(Object simple) {
        return null;
    }

    @Override public final int hashCode() {
        return super.hashCode();
    }

    @Override public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    // each top-level call to toString starts a new indenter; the below-levels
    // exist at the "buildString" level and should only make other calls to
    // "buildString" unless the item is unknown and a typical toString is called
    // for (in which case, it must be on one line to work)
    public final String toString() {
        try {
            indenter.push(new Indenter());
            StringBuilder buff = new StringBuilder();
            this.buildString(buff);
            return buff.toString();
        }
        finally {
            indenter.pop();
        }
    }

    // Creates a binding with the special result binding
    protected final BindingMap bindRoot(@Union("Entity") Object entity) {
        BindingMap bindingMap = new BindingMap(entity);
        return bindingMap;
    }

    // Creates a binding with the special result binding, in additional to naming it
    // the given name (if the passed id is null, then this call will be equivalent
    // to bindRoot)
    protected BindingMap bindNamedRoot(Object entity, String id, EntityType type) {
        BindingMap result = bindRoot(entity);
        if (id != null) {
            result.bind(id, entity, type);
        }
        return result;
    }
    
    // Quick short-hand notation for making an unparented copy of an existing ASTNode,
    // which will belong to the target ast.
    protected static <T extends ASTNode> T copy(AST ast, Class<T> clazz, ASTNode astNode) {
        return clazz.cast(Entity.copySubtree(ast, astNode));
    }

    // The logic behind a buildString method is: Assume that a new line has
    // been created for you. Insert the correct amount of indentation. Each time
    // you create a new line of your own, insert the correct amount of
    // indentation, unless it's your final newline. Leave the indenter at the
    // same level with which you found it.
    protected abstract void buildString(StringBuilder buff);

    protected Indenter getIndenter() {
        return indenter.peek();
    }

    public abstract BindingMap generateNode(IEntityLookup lookup, AST ast);

    // Does this fragment represent a literal value? If so, that value can be
    // accessed via matchResolvedEntity
    public boolean isResolved() {
        return false;
    }

    public BindingMap matchResolvedEntity() {
        ArcumError.fatalError("Internal programming error: should only be called"
            + "when the fragment is resolved to a literal value with a single match");
        return null/*unreachable*/;
    }
}