package edu.ucsd.arcum.interpreter.parser;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.JavaFragmentCompilationProblem;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.EntityDataBase;
import edu.ucsd.arcum.util.StringUtil;

public class FragmentParser
{
    private static int serialCounter = 0;

    private String imports;
    private IJavaProject project;
    private boolean matchingMode;

    public FragmentParser() {
        this("", null);
    }

    public FragmentParser(String imports, IProject project) {
        this.imports = imports;
        this.project = JavaCore.create(project);
        this.matchingMode = true;
    }

    public static Type getType(String simpleOrQualifiedName) {
        String className = newTempName();
        String generatedSource = String.format("class %s { %s o; }", className,
            simpleOrQualifiedName);
        AbstractTypeDeclaration decl = parseTypeDeclaration(generatedSource);
        BodyDeclaration bodyDecl = (BodyDeclaration)decl.bodyDeclarations().get(0);
        FieldDeclaration field = (FieldDeclaration)bodyDecl;
        return field.getType();
    }

    public AbstractTypeDeclaration getTypeDeclaration(String typeString) {
        String generatedSource = String.format("%s%n%s", imports, typeString);
        return parseTypeDeclaration(generatedSource);
    }

    // XXX: may want to deprecate this one and use the AST node version instead
    public ITypeBinding getTypeBinding(String typeString)
        throws JavaFragmentCompilationProblem
    {
        SingleVariableDeclaration var = declareParameterOfType(typeString);
        IVariableBinding binding = (IVariableBinding)EntityDataBase
            .resolveBindingNullOK(var);
        if (binding == null)
            return null;
        ITypeBinding type = binding.getType();
        return type;
    }

    public static class TypeBindingWithModifiers {
        public ITypeBinding typeBinding;
        public List<IExtendedModifier> modifiers;
    }
    
    public TypeBindingWithModifiers getTypeBindingWithModifiers(String typeString) throws JavaFragmentCompilationProblem {
        SingleVariableDeclaration var = declareParameterOfType(typeString);
        TypeBindingWithModifiers result = new TypeBindingWithModifiers();
        result.typeBinding = (ITypeBinding)EntityDataBase.resolveBinding(var.getType());
        result.modifiers = var.modifiers();
        return result;
    }

    public MethodDeclaration getMethod(String methodString)
        throws JavaFragmentCompilationProblem
    {
        String name = newTempName();
        String src = String.format("%s%nclass %s {%n %s}", imports, name, methodString);

        AbstractTypeDeclaration decl = parseTypeDeclaration(name, src);
        BodyDeclaration bodyDecl = (BodyDeclaration)decl.bodyDeclarations().get(0);
        MethodDeclaration method = (MethodDeclaration)bodyDecl;
        return method;
    }

    // TODO: All other methods like this one in FragmentParser should also pass in
    // a location, so that error messages can be seen. Usually these errors would be
    // due to a typo or a missing import.
    // TODO: All other methods like this one should return a copy of the subtree, so
    // that it doesn't have the fake parent associated with it (the fake parent is
    // the dummy compilation unit that we make)
    public Annotation getAnnotation(String anntString, SourceLocation location)
        throws JavaFragmentCompilationProblem
    {
        String name = newTempName();
        String src = String.format("%s%n %s class %s {}", imports, anntString, name);

        AbstractTypeDeclaration decl = parseTypeDeclaration(name, src);
        List modifiers = decl.modifiers();
        if (modifiers.size() != 1 && !(modifiers.get(0) instanceof Annotation)) {
            ArcumError.fatalError("Internal parsing error in getAnnotation");
        }
        Annotation annotation = (Annotation)modifiers.get(0);
        IAnnotationBinding binding = annotation.resolveAnnotationBinding();
        if (binding == null) {
            ArcumError.fatalUserError(location, "%s cannot be resolved to an annotation",
                annotation.toString());
        }
        return Entity.copySubtree(annotation.getAST(), annotation);
    }

    public Expression getExpression(String exprString, Collection<String> locals)
        throws JavaFragmentCompilationProblem
    {
        String methodString = String.format("void f(%s) { Object o = %s; }", StringUtil
            .separate(locals, ", "), exprString);
        MethodDeclaration method = getMethod(methodString);
        Block body = method.getBody();
        VariableDeclarationStatement statement = (VariableDeclarationStatement)body
            .statements().get(0);
        VariableDeclarationFragment varFrag = (VariableDeclarationFragment)statement
            .fragments().get(0);
        Expression expression = varFrag.getInitializer();
        return expression;
    }

    public Statement getStatement(String stmtString)
        throws JavaFragmentCompilationProblem
    {
        String methodString = String.format("void f() { %s }", stmtString);
        MethodDeclaration method = getMethod(methodString);
        Block body = method.getBody();
        return (Statement)body.statements().get(0);
    }

    // WEDNESDAY: Consider having this be a return type instead, so that the void
    // case can be used legally too.
    private SingleVariableDeclaration declareParameterOfType(String typeString)
        throws JavaFragmentCompilationProblem
    {
        String name = newTempName();

        StringBuilder buff = new StringBuilder();
        buff.append(imports);
        buff.append(String.format("%ninterface %s {%n", name));
        buff.append(String.format("  void f(%s param); }", typeString));
        String generatedSource = buff.toString();

        AbstractTypeDeclaration decl = parseTypeDeclaration(name, generatedSource);
        BodyDeclaration bodyDecl = (BodyDeclaration)decl.bodyDeclarations().get(0);
        MethodDeclaration signature = (MethodDeclaration)bodyDecl;
        SingleVariableDeclaration var = (SingleVariableDeclaration)signature.parameters()
            .get(0);
        return var;
    }

    private static String newTempName() {
        return String.format("F%d", serialCounter++);
    }

    private AbstractTypeDeclaration parseTypeDeclaration(String name,
        String generatedSource) throws JavaFragmentCompilationProblem
    {
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(generatedSource.toCharArray());
        parser.setCompilerOptions(new CompilerOptions().getMap());
        parser.setProject(project);
        parser.setUnitName(name);
        parser.setResolveBindings(true);

        CompilationUnit cu = (CompilationUnit)parser.createAST(null);
        AbstractTypeDeclaration decl = (AbstractTypeDeclaration)cu.types().get(0);
        Message[] messages = cu.getMessages();
        boolean nonResolutionError = false;
        for (Message message : messages) {
            if (!message.getMessage().endsWith("cannot be resolved")) {
                nonResolutionError = true;
                break;
            }
        }
        if (nonResolutionError && messages.length != 0 && matchingMode) {
            // URGENT !!!!! -- No Typo Checking Available When Commented Out! 
//            throw new JavaFragmentCompilationProblem(messages);
        }
        return decl;
    }

    // parse without resolution
    private static AbstractTypeDeclaration parseTypeDeclaration(String generatedSource) {
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(generatedSource.toCharArray());
        CompilationUnit result = (CompilationUnit)parser.createAST(null);
        AbstractTypeDeclaration decl = (AbstractTypeDeclaration)result.types().get(0);
        return decl;
    }

    public void setMatchingMode(boolean matchingMode) {
        this.matchingMode = matchingMode;
    }
}