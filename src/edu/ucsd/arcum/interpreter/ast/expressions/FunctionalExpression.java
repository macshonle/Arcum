package edu.ucsd.arcum.interpreter.ast.expressions;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.satisfier.BindingsSet;
import edu.ucsd.arcum.interpreter.satisfier.Satisfier;
import edu.ucsd.arcum.util.ListUtil;
import edu.ucsd.arcum.util.StringUtil;
import edu.ucsd.arcum.util.SystemUtil;

// An expression written in a functional style: for example, predicate
// expressions or accessor expressions.
// E.g.:
//   hasField(fromType, edge)
// and:
//   toType == TypeOf(edge)
// In the last case, the TypeOf(edge) is an accessor, and not a predicate, and
// will have a bound variable that is non-null.
public class FunctionalExpression extends ConstraintExpression
{
    private IFunction function;
    private List<ConstraintExpression> args;

    // valid after type checking
    private List<EntityType> parameterTypes;

    public FunctionalExpression(SourceLocation location, IFunction function,
        List<ConstraintExpression> args)
    {
        super(location);
        this.function = function;
        this.args = args;
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append(function.toString());
        buff.append("(");
        StringUtil.separate(buff, args, ", ");
        buff.append(")");
        return buff.toString();
    }

    @Override public Set<String> getArcumVariableReferences() {
        Set<String> result = Sets.newHashSet();
        for (ConstraintExpression arg : args) {
            result.addAll(arg.getArcumVariableReferences());
        }
        return result;
    }

    public BindingsSet evaluate(BindingMap in, IEntityLookup lookup, Satisfier satisfier,
        boolean matchingMode)
    {
        if (DEBUG) {
            SystemUtil.getErrStream().printf("function=%s%nargs=%s%nparameterTypes=%s%n",
                function, args, parameterTypes);
        }
        if (args.size() != parameterTypes.size()) {
            ArcumError.fatalError("Internal error: need to check arguments first");
        }

        List<List<Object>> allArgCombinations = Lists.newArrayList();
        for (int i = 0; i < args.size(); ++i) {
            ConstraintExpression arg = args.get(i);
            EntityType type = parameterTypes.get(i);
            BindingsSet set = satisfier.immediateMatchingSat(arg, type, in);
            List<Object> ithArgPossibilities = Lists.newArrayList();
            for (BindingMap theta : set) {
                Object entity = theta.getResult();
                entity = Entity.canonicalizeRepresentation(entity);
                ithArgPossibilities.add(entity);
            }
            allArgCombinations.add(ithArgPossibilities);
        }

        BindingsSet result = BindingsSet.newEmptySet();
        List<List<Object>> realAgsList = ListUtil.crossProduct(allArgCombinations);
        for (List<Object> realArgs : realAgsList) {
            BindingsSet set = function.evaluate(realArgs, lookup, in, matchingMode, getPosition());
            // "false" results are the empty set, so they will not add to the
            // result here
            for (BindingMap theta : set) {
                result.addEntry(theta);
            }
        }
        return result;
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        this.parameterTypes = function.checkArgs(getPosition(), tupleSets, args.size());
        // EXAMPLE: This was a bug in the software! The for-loop below was forgotten about!
        for (ConstraintExpression arg : args) {
            arg.doCheckUserDefinedPredicates(tupleSets, varsInScope);
        }
    }

    @Override public Set<String> findAllTraitDependencies() {
        Set<String> result = flattenFindAllTraitDependencies(args);
        result.add(function.getName());
        return result;
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return Sets.newHashSet();
    }
}