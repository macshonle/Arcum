package edu.ucsd.arcum.interpreter.ast;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.expressions.ConstraintExpression;
import edu.ucsd.arcum.interpreter.query.EntityDataBase;
import edu.ucsd.arcum.interpreter.query.OptionMatchTable;
import edu.ucsd.arcum.util.StringUtil;

public class MapTraitArgument extends MapNameValueBinding
{
    private RequireMap map;
    private ConstraintExpression patternExpr;
    private List<FormalParameter> formals;

    // TODO: paramNames should be allowed to have the types explicit, just like
    // any other realize statement
    public MapTraitArgument(SourceLocation location, RequireMap map, String traitName,
        List<FormalParameter> formals, ConstraintExpression patternExpr)
    {
        super(location, traitName);
        this.map = map;
        this.patternExpr = patternExpr;
        this.formals = formals;
    }

    public void initializeValue(EntityDataBase edb, Option option, OptionMatchTable table)
        throws CoreException
    {
        StaticRealizationStatement pseudoStmt;
        OptionInterface optionIntf = option.getOptionInterface();
        List<FormalParameter> allParams = optionIntf.getSingletonParameters();
        List<FormalParameter> formals = null;
        for (FormalParameter param : allParams) {
            if (param.getIdentifier().equals(getName())) {
                formals = param.getTraitArguments();
                break;
            }
        }
        if (formals == null) {
            ArcumError.fatalUserError(getLocation(), "Couldn't find %s", getName());
        }
        pseudoStmt = StaticRealizationStatement.makeNested(map, getName(), patternExpr,
            formals, this.getLocation());
        pseudoStmt.typeCheckAndValidate(optionIntf);
        List<StaticRealizationStatement> stmts = Lists.newArrayList(pseudoStmt);
        try {
            EntityDataBase.pushCurrentDataBase(edb);
            RealizationStatement.collectivelyRealizeStatements(stmts, edb, table);
        }
        finally {
            EntityDataBase.popMostRecentDataBase();
        }
    }

    @Override public Object getValue() {
        return this;
    }

    @Override public String toString() {
        return String.format("%s(%s): %s", getName(), StringUtil.separate(formals),
            patternExpr.toString());
    }

    public void checkUserDefinedPredicates(List<TraitSignature> tupleSets) {
        Set<String> names = Sets.newHashSet();
        names.addAll(Lists.transform(formals, FormalParameter.getIdentifier));
        patternExpr.checkUserDefinedPredicates(tupleSets, names);
    }
}