package edu.ucsd.arcum.interpreter.ast.expressions;

import static com.google.common.collect.Lists.transform;
import static edu.ucsd.arcum.ArcumPlugin.DEBUG;
import static edu.ucsd.arcum.util.StringUtil.separate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.*;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.fragments.SignatureEntity;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.query.VariablePlaceholder;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.satisfier.BindingsSet;
import edu.ucsd.arcum.interpreter.satisfier.Satisfier;

// A BuildInFunction cannot be used for binding elements: they can only check
// properties of already bound elements. This limitation is to avoid, for
// example, the use of isPrivate(a) returning every "a" that is private. If
// there is a use-case for this in the future this could be revisited, but the
// current implementation means it would consume a lot of memory.
//
// MACNEIL: Could include a type checking phase for these functions, to be
// sure that their args are the right type, instead of waiting until evaluation
public enum BuiltInFunction implements IFunction
{
    HAS_SIGNATURE("hasSignature", EntityType.TRAIT, EntityType.SIGNATURE) {
        public boolean _evaluate(Object typeWrapped, Object signatureWrapped) {
            ITypeBinding type = unwrap(ITypeBinding.class, typeWrapped);
            SignatureEntity signature = unwrap(SignatureEntity.class, signatureWrapped);
            IMethodBinding[] methods = type.getDeclaredMethods();
            for (IMethodBinding method : methods) {
                if (signature.hasSameSignatureAs(method)) {
                    return true;
                }
            }
            return false;
        }
    },
    // EXAMPLE: Ensure that a _evaluate method is defined for each type. This implies
    // there is a need for Arcum code to be embedded in Java code (perhaps), because
    // the check is local only to the class an likely does not need to be generalized,
    // although a generalized form would fit the notion of soft interfaces
    WITHIN("within", EntityType.ANY, EntityType.ANY) {
        // DOCUMENTATION: Currently only supports ASTNodes, although types are a
        // clear choice for the second parameter as well
        public boolean _evaluate(Object element, Object container) {
            ASTNode node = unwrap(ASTNode.class, element);
            ASTNode potentialParent = unwrap(ASTNode.class, container);
            for (;;) {
                ASTNode parent = node.getParent();
                if (parent == null)
                    break;
                if (parent == potentialParent)
                    return true;
                node = parent;
            }
            return false;
        }
    },
    SAME_TYPE("sameType", EntityType.TYPE, EntityType.TYPE) {
        public boolean _evaluate(Object e1, Object e2) {
            return Entity.compareTo(e1, e2) == 0;
        }
    },
    IS_A("isA", EntityType.ANY /*EXPR or TYPE*/, EntityType.TYPE) {
        public boolean _evaluate(Object typeLHSWrapped, Object typeRHSWrapped) {
//            GETDONE Was here last... in transformation mode, we should make this one pass?
//                OR, we should store away what was true and keep track of the bindings
//                under the generate and test scheme, we could do the wrong thing!
            ITypeBinding lhsType = coerce(ITypeBinding.class, typeLHSWrapped);
            ITypeBinding rhsType = unwrap(ITypeBinding.class, typeRHSWrapped);

            // POSSIBLE_ECLIPSE_BUG: It would return "false" for
            // two different bindings of java.lang.String, yet the isEqualTo works,
            // so we need to call our own isAssignableFrom (below) in the mean time
            boolean equal = isAssignableFrom(rhsType, lhsType);
            boolean canAssign = lhsType.isAssignmentCompatible(rhsType);

            boolean result = equal || canAssign;
            return result;
        }

        private boolean isAssignableFrom(ITypeBinding variableType, ITypeBinding exprType)
        {
            if (exprType == null) {
                return false;
            }
            else if (exprType.isEqualTo(variableType)) {
                return true;
            }
            else {
                if (variableType.isInterface()) {
                    ITypeBinding[] interfaces = exprType.getInterfaces();
                    for (ITypeBinding superinterface : interfaces) {
                        if (isAssignableFrom(variableType, superinterface))
                            return true;
                    }
                    return false;
                }
                else {
                    ITypeBinding superclass = exprType.getSuperclass();
                    return isAssignableFrom(variableType, superclass);
                }
            }
        }
    },
    IS_JAVA_IDENTIFIER("isJavaIdentifier", EntityType.STRING) {
        public boolean _evaluate(Object strWrapped) {
            String str = unwrap(String.class, strWrapped);
            if (str.length() < 1) {
                return false;
            }
            if (!Character.isJavaIdentifierStart(str.charAt(0))) {
                return false;
            }
            for (int i = 1; i < str.length(); ++i) {
                if (!Character.isJavaIdentifierPart(str.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    },
    IS_REFERENCE_TYPE("isReferenceType", EntityType.TYPE) {
        public boolean _evaluate(Object value) {
            if (value instanceof ITypeBinding) {
                ITypeBinding type = unwrap(ITypeBinding.class, value);
                return !type.isPrimitive();
            }
            else {
                return (value instanceof AbstractTypeDeclaration);
            }
        }
    },
    IS_EXPRESSION_STATEMENT("isExpressionStatement", EntityType.EXPR) {
        public boolean _evaluate(Object exprWrapped) {
            Expression expr = unwrap(Expression.class, exprWrapped);
            ASTNode parent = expr.getParent();
            return (parent instanceof ExpressionStatement);
        }
    },
    IS_CLASS("isClass", EntityType.TYPE) {
        public boolean _evaluate(Object typeWrapped) {
            ITypeBinding type = unwrap(ITypeBinding.class, typeWrapped);
            return type.isClass();
        }
    },
    IS_INTERFACE("isInterface", EntityType.TYPE) {
        public boolean _evaluate(Object typeWrapped) {
            ITypeBinding type = unwrap(ITypeBinding.class, typeWrapped);
            return type.isInterface();
        }
    },
    IS_ENUM("isEnum", EntityType.TYPE) {
        public boolean _evaluate(Object typeWrapped) {
            ITypeBinding type = unwrap(ITypeBinding.class, typeWrapped);
            return type.isEnum();
        }
    },
    IS_ANNOTATION_TYPE("isAnnotationType", EntityType.TYPE) {
        public boolean _evaluate(Object typeWrapped) {
            ITypeBinding type = unwrap(ITypeBinding.class, typeWrapped);
            return type.isAnnotation();
        }
    },
    IS_SIMPLE_ASSIGNMENT("isSimpleAssignment", EntityType.EXPR) {
        public boolean _evaluate(Object exprWrapped) {
            Expression expr = unwrap(Expression.class, exprWrapped);
            if (expr instanceof Assignment) {
                Assignment assignment = (Assignment)expr;
                return assignment.getOperator() == Assignment.Operator.ASSIGN;
            }
            return false;
        }
    },
    IS_ABSTRACT("isAbstract", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isAbstract();
                }
            });
        }
    },
    IS_FINAL("isFinal", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isFinal();
                }
            });
        }
    },
    IS_NATIVE("isNative", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isNative();
                }
            });
        }
    },
    IS_PRIVATE("isPrivate", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isPrivate();
                }
            });
        }
    },
    IS_PROTECTED("isProtected", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isProtected();
                }
            });
        }
    },
    IS_PUBLIC("isPublic", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isPublic();
                }
            });
        }
    },
    IS_STATIC("isStatic", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isStatic();
                }
            });
        }
    },
    IS_STRICTFP("isStrictfp", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isStrictfp();
                }
            });
        }
    },
    IS_SYNCHRONIZED("isSynchronized", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isSynchronized();
                }
            });
        }
    },
    IS_TRANSIENT("isTransient", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isTransient();
                }
            });
        }
    },
    IS_VOLATILE("isVolatile", EntityType.FIELD) {
        public boolean _evaluate(Object fieldWrapped) {
            FieldDeclaration field = unwrap(FieldDeclaration.class, fieldWrapped);
            return testForModifier(field, new ModifierChecker() {
                public boolean check(Modifier modifier) {
                    return modifier.isVolatile();
                }
            });
        }
    },
    STATIC_TRAIT("<STATIC TRAIT>") {
        @Override public BindingsSet evaluate(List<Object> args,
            IEntityLookup entityLookup, BindingMap theta, boolean matchingMode, SourceLocation location)
        {
            throw new RuntimeException("Internal error: should be in static trait%n");
        }
    },
    ;

    protected String functionName;
    private EntityType[] params;
    private Method reflectiveMethod = null;
    private IEntityLookup entityLookup;

    private static HashMap<String, BuiltInFunction> lookup;
    static {
        lookup = new HashMap<String, BuiltInFunction>();
        for (BuiltInFunction function : values()) {
            lookup.put(function.functionName, function);
        }
    }

    public static Set<String> getBuiltInNames() {
        return lookup.keySet();
    }

    @Override public synchronized BindingsSet evaluate(List<Object> args,
        IEntityLookup lookup, BindingMap theta, boolean dummy2, SourceLocation location)
    {
        IEntityLookup prevEntityLookup = this.entityLookup;
        try {
            this.entityLookup = lookup;
            if (reflectiveMethod == null) {
                Class[] reflectTypes = new Class[params.length];
                for (int i = 0; i < reflectTypes.length; ++i) {
                    reflectTypes[i] = Object.class;
                }
                Class<? extends BuiltInFunction> clazz = this.getClass();
                this.reflectiveMethod = clazz.getMethod("_evaluate", reflectTypes);
            }

            Object[] reflectArgs = new Object[args.size()];
            for (int i = 0; i < reflectArgs.length; ++i) {
                Object arg = args.get(i);
                if (arg instanceof VariablePlaceholder) {
                    VariablePlaceholder placeholder = (VariablePlaceholder)arg;
                    String placeholderName = placeholder.getName();
                    arg = lookup.lookupEntity(placeholderName);
                    if (arg == null) {
                        ArcumError.fatalUserError(location, "Built-in functions expect"
                            + " arguments that are already bound, but variable %s has no"
                            + " binding", placeholderName);
                    }
                }
                reflectArgs[i] = arg;
            }

            if (reflectArgs.length != params.length) {
                ArcumError.fatalUserError(location, "Mismatched number of arguments for \"%s\":"
                    + " expected %d, but got %d%n", functionName, params.length,
                    reflectArgs.length);
            }

            Boolean answer;
            try {
                SourceLocation.pushLocation(location);
                answer = (Boolean)reflectiveMethod.invoke(this, reflectArgs);
            }
            finally {
                SourceLocation.popLocation();
            }
            if (DEBUG) {
                System.err.printf("Invoked %s(%s):%n ==>%b%n", this.functionName,
                    separate(transform(args, new Function<Object, String>() {
                        @Override public String apply(Object from) {
                            return Entity.getDisplayString(from);
                        }
                    })), answer);
            }
            if (answer) {
                return Satisfier.trueResult(theta);
            }
            else {
                return Satisfier.falseResult(theta);
            }
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        finally {
            this.entityLookup = prevEntityLookup;
        }
    }

    private BuiltInFunction(String functionName, EntityType... params) {
        this.functionName = functionName;
        this.params = params;
    }

    public static BuiltInFunction lookup(String name) {
        if (lookup.containsKey(name)) {
            return lookup.get(name);
        }
        else {
            return STATIC_TRAIT;
        }
    }

    // EXAMPLE: The casting and the unwrap calls wouldn't be needed if there were
    // extra checking
    protected <T> T unwrap(Class<T> clazz, Object entity) {
        return clazz.cast(entity);
    }

    protected <T> T coerce(Class<T> clazz, Object entity) {
        if (clazz.equals(ITypeBinding.class) && entity instanceof ASTNode) {
            return (T)entityLookup.lookupTypeBinding((ASTNode)entity);
        }
        return unwrap(clazz, entity);
    }

    private interface ModifierChecker
    {
        boolean check(Modifier modifier);
    }

    private static boolean testForModifier(FieldDeclaration field, ModifierChecker checker)
    {
        for (Object obj : field.modifiers()) {
            IExtendedModifier modifierOrAnnotation = (IExtendedModifier)obj;
            if (modifierOrAnnotation.isModifier()) {
                Modifier modifier = (Modifier)modifierOrAnnotation;
                if (checker.check(modifier))
                    return true;
            }
        }
        return false;
    }

    @Override public List<EntityType> checkArgs(SourceLocation position,
        List<TraitSignature> tupleSets, int numGiven)
    {
        int numExpectedArgs = params.length;
        if (numExpectedArgs != numGiven) {
            ArcumError.fatalUserError(position,
                "The built-in function \"%s\" expects %d arguments, instead found %d",
                functionName, numExpectedArgs, numGiven);
        }
        return Lists.newArrayList(params);
    }

    @Override public String toString() {
        return functionName;
    }

    @Override public String getName() {
        return functionName;
    }
}