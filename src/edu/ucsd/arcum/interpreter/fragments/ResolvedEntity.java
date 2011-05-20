package edu.ucsd.arcum.interpreter.fragments;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleName;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.util.StringUtil;

// URGENT (!!!) -- Handle matching for the signature case; but will need to think
// of a better place to handle the signature coercion
public class ResolvedEntity extends ProgramFragment
{
    private @Union("Entity") final Object entity;

    public ResolvedEntity(ASTNode astNode) {
        this.entity = astNode;
    }

    public ResolvedEntity(ModifierElement modifierElement) {
        this.entity = modifierElement;
    }

    public ResolvedEntity(SignatureEntity signatureEntity) {
        this.entity = signatureEntity;
    }

    public ResolvedEntity(String string) {
        this.entity = string;
    }

    // Does the program fragment represent the given modifier element?
    public static boolean represents(ProgramFragment frag, ModifierElement mod1) {
        if (frag instanceof ResolvedEntity) {
            ResolvedEntity resolvedEntity = (ResolvedEntity)frag;
            if (resolvedEntity.entity instanceof ModifierElement) {
                ModifierElement mod2 = (ModifierElement)resolvedEntity.entity;
                if (mod1.equals(mod2)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Does the program fragment represent a modifier element that's an access specifier?
    public static boolean representsAccessSpecifier(ProgramFragment frag) {
        if (frag instanceof ResolvedEntity) {
            ResolvedEntity resolvedEntity = (ResolvedEntity)frag;
            if (resolvedEntity.entity instanceof ModifierElement) {
                ModifierElement mod = (ModifierElement)resolvedEntity.entity;
                if (mod.isAccessSpecifier()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void buildString(StringBuilder buff) {
        buff.append(getIndenter());
        buff.append("(ResolvedEntity ");
        buff.append(Entity.getDisplayString(entity));
        buff.append(")");
    }

    @Override
    public BindingMap generateNode(IEntityLookup lookup, AST ast) {
        if (entity instanceof ASTNode) {
            String id = lookup.lookupEntitiesID(entity);
            return bindNamedRoot(copy(ast, ASTNode.class, (ASTNode)entity), id, EntityType.PUNT);
        }
        else {
            return bindRoot(entity);
        }
    }

    public Object getValue() {
        return entity;
    }

    @Override
    protected BindingMap matchesASTNode(ASTNode node) {
        // MONDAY: Should this be compareTo or compareToWithLocations? And if it
        // should be compareToWithLocations then the unification search can be
        // greatly reduced when the fragment matched contains an instance of
        // ResolvedEntity
        if (Entity.compareToWithLocations(entity, node) == 0) {
            return bindRoot(node);
        }
        else {
            return null;
        }
    }

    @Override
    protected BindingMap matchesModifierElement(ModifierElement modifier) {
        if (Entity.compareTo(entity, modifier) == 0) {
            return bindRoot(modifier);
        }
        else {
            return null;
        }
    }

    @Override
    protected BindingMap matchesSignatureEntity(SignatureEntity signature) {
        ArcumError.fatalError("Unsupported case");
        return null;
    }

    @Override
    final protected BindingMap matchesSimpleProperty(Object node) {
        if (node instanceof String) {
            String text;
            if (entity instanceof SimpleName) {
                SimpleName name = (SimpleName)entity;
                text = name.getIdentifier();
            }
            else if (entity instanceof String) {
                text = (String)entity;
            }
            else {
                text = null;
                ArcumError.fatalError("Cannot handle string case with: %s", StringUtil
                    .debugDisplay(entity));
            }
            if (text.equals(node)) {
                return bindRoot(node);
            }
        }
        return null;
    }

    public static ProgramFragment newInstance(Object entity) {
        ProgramFragment result;
        if (entity instanceof ASTNode) {
            result = new ResolvedEntity((ASTNode)entity);
        }
        else if (entity instanceof ModifierElement) {
            result = new ResolvedEntity((ModifierElement)entity);
        }
        else if (entity instanceof SignatureEntity) {
            result = new ResolvedEntity((SignatureEntity)entity);
        }
        else if (entity instanceof String) {
            result = new ResolvedEntity((String)entity);
        }
        else if (entity instanceof ITypeBinding) {
            result = new ResolvedType((ITypeBinding)entity);
        }
        else if (entity instanceof SubtreeList) {
            result = (SubtreeList)entity;
        }
        else {
            ArcumError.fatalError("Unhandled case: %s", entity);
            return null;
        }
        return result;
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    @Override
    public BindingMap matchResolvedEntity() {
        BindingMap result = bindRoot(entity);
        return result;
    }
}