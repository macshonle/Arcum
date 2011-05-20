package edu.ucsd.arcum.interpreter.ast;

import static edu.ucsd.arcum.interpreter.ast.ASTUtil.PARAMETER_NAME;
import static edu.ucsd.arcum.interpreter.ast.ASTUtil.checkNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;

public abstract class TopLevelConstruct
{
    private String name;
    private String importsString;
    private List<RealizationStatement> realizationStatements;

    // check for redefinition of names
    abstract public void doTypeCheck(ArcumDeclarationTable table);

    public static void checkFormals(List<FormalParameter> params) {
        checkNames(params, PARAMETER_NAME);
    }

    protected TopLevelConstruct(String name, String importsString) {
        this.name = name;
        this.importsString = importsString;
        this.realizationStatements = new ArrayList<RealizationStatement>();
    }

    public String getName() {
        return name;
    }

    public String getImports() {
        return importsString;
    }

    // singletons can be realized only with other singletons
    protected void checkRealizationConsistency(OptionInterface optionInterface)
    {
        for (RealizationStatement stmt : getRealizationStatements()) {
            stmt.typeCheckAndValidate(optionInterface);
        }
    }

    protected void checkRequireClausePredicates(List<TraitSignature> tupleSets)
    {
        Set<String> varsInScope = TraitSignature.getGlobals(tupleSets);
        for (TraitSignature tupleSet : tupleSets) {
            // EXAMPLE: This is really the visitor pattern! And a bug that kept on
            // happening again and again.
            tupleSet.checkUserDefinedPredicates(tupleSets, varsInScope);
        }
    }
    
    protected void checkExpressionPredicates(RealizationStatement stmt, List<TraitSignature> tupleSets)
    {
        stmt.checkUserDefinedPredicates(tupleSets);
    }

    // on interpretation these will all have to be type-checked and matched
    public void addRealizationStatement(RealizationStatement statement) {
        realizationStatements.add(statement);
    }

    public List<RealizationStatement> getRealizationStatements() {
        return realizationStatements;
    }
}
