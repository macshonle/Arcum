package edu.ucsd.arcum.interpreter.ast;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.dom.ITypeBinding;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.JavaFragmentCompilationProblem;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.transformation.ResolvedConceptMapEntry;
import edu.ucsd.arcum.util.MultiDictionary;
import edu.ucsd.arcum.util.StringUtil;

public class RequireMap extends TopLevelConstruct
{
    private static int serialCounter = 0;

    private MultiDictionary<String, List<MapNameValueBinding>> optionsUsed;
    private IdentityHashMap<List<MapNameValueBinding>, String> argsPresent;
    private IdentityHashMap<List<MapNameValueBinding>, SourceLocation> locations;
    private List<ResolvedConceptMapEntry> resolvedBindings;
    private IProject project;
    private IResource resource;

    public static RequireMap newArcumMap(final String imports, final IProject project,
        ArcumDeclarationTable table, final IResource resource)
    {
        final String name = "AnonymousMap" + (serialCounter++);
        return table.conditionalCreate(name, new ConstructorThunk<RequireMap>() {
            public RequireMap create() {
                return new RequireMap(name, imports, project, resource);
            }
        });
    }

    private RequireMap(String name, String imports, IProject project, IResource resource)
    {
        super(name, imports);
        this.optionsUsed = MultiDictionary.newInstance();
        this.argsPresent = new IdentityHashMap<List<MapNameValueBinding>, String>();
        this.locations = new IdentityHashMap<List<MapNameValueBinding>, SourceLocation>();
        this.resolvedBindings = null;
        this.project = project;
        this.resource = resource;
    }

    public void addBindings(String optionUsed, List<MapNameValueBinding> bindings,
        String optionClauseText, SourceLocation location)
    {
        optionsUsed.addDefinition(optionUsed, bindings);
        argsPresent.put(bindings, optionClauseText);
        locations.put(bindings, location);
        for (MapNameValueBinding binding : bindings) {
            binding.setOptionClauseText(optionClauseText);
        }
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("require\n{");
        for (Map.Entry<String, List<List<MapNameValueBinding>>> entry : optionsUsed) {
            buff.append("\n  ");
            String key = entry.getKey();
            List<List<MapNameValueBinding>> bindingsList = entry.getValue();
            for (List<MapNameValueBinding> bindings : bindingsList) {
                buff.append(key);
                buff.append("(");
                StringUtil.separate(buff, bindings, ", ");
                buff.append(")");
                buff.append(";\n");
            }
        }
        buff.append("}\n");
        return buff.toString();
    }

    @Override public void doTypeCheck(ArcumDeclarationTable table) {
        this.resolvedBindings = new ArrayList<ResolvedConceptMapEntry>();
        for (Map.Entry<String, List<List<MapNameValueBinding>>> entry : optionsUsed) {
            String optionName = entry.getKey();
            List<List<MapNameValueBinding>> bindingsList = entry.getValue();
            for (List<MapNameValueBinding> bindings : bindingsList) {
                checkAndDisambiguateBinding(optionName, bindings, table);
            }
        }
    }

    // See if the binding is a valid option and that the correct arguments
    // were passed. Also tries to disambiguate any ambiguous arguments,
    // replacing them with a correct value
    private void checkAndDisambiguateBinding(String optionName,
        List<MapNameValueBinding> args, ArcumDeclarationTable table)
    {
        Option option = table.lookup(optionName, Option.class);
        SourceLocation location = locations.get(args);
        if (option == null) {
            ArcumError.fatalUserError(location, "The name %s is not a known option",
                optionName);
        }
        List<FormalParameter> params = option.getSingletonParameters(table);
        // for each argument, there is a parameter
        List<MapNameValueBinding> disambiguated = new ArrayList<MapNameValueBinding>();
        for (MapNameValueBinding arg : args) {
            String argName = arg.getName();
            FormalParameter formal = null;
            for (FormalParameter param : params) {
                if (param.getIdentifier().equals(argName)) {
                    formal = param;
                    break;
                }
            }
            if (formal == null) {
                ArcumError.fatalUserError(location,
                    "The argument named %s does not match any valid parameter names",
                    argName);
            }
            disambiguated
                .add(checkAndDisambiguateArgument(arg, formal, option, location));
        }
        // for each parameter, there is an argument
        for (FormalParameter param : params) {
            String paramName = param.getIdentifier();
            boolean found = false;
            for (MapNameValueBinding arg : args) {
                String argName = arg.getName();
                if (paramName.equals(argName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ArcumError.fatalUserError(location, "Expected a parameter named \"%s\"",
                    paramName);
            }
        }
        ResolvedConceptMapEntry resolvedEntry;
        resolvedEntry = new ResolvedConceptMapEntry(option, disambiguated, argsPresent
            .get(args), location, (IFile)resource);
        this.resolvedBindings.add(resolvedEntry);
    }

    private MapNameValueBinding checkAndDisambiguateArgument(MapNameValueBinding binding,
        FormalParameter formal, Option option, SourceLocation location)
    {
        String argumentName = binding.getName();
        EntityType type = formal.getType();
        if (binding instanceof MapAmbiguousArgument) {
            MapAmbiguousArgument ambiguous = (MapAmbiguousArgument)binding;
            switch (type) {
            case TYPE:
            case DECLARATION_ELEMENT:
                String body = ambiguous.getBody();
                FragmentParser parser = new FragmentParser(getImports(), project);
                ITypeBinding resolved;
                try {
                    resolved = parser.getTypeBinding(body);
                }
                catch (JavaFragmentCompilationProblem jfcp) {
                    String message = StringUtil.enumerate(jfcp.getMessages());
                    ArcumError.fatalUserError(location, "%s", message);
                    return null;
                }
                if (resolved == null) {
                    ArcumError.fatalUserError(location, "Cannot resolve \"%s\"", body);
                }
                MapTypeArgument mapTypeArg = new MapTypeArgument(location, ambiguous
                    .getName(), resolved);
                return mapTypeArg;
            case ANNOTATION:
            case STATEMENT:
            case STRING:
            case BOOLEAN:
            case EXPR:
            case ACCESS_SPECIFIER:
            case FIELD:
            case METHOD:
                // URGENT: for field and method we want to grab what's before the
                // last dot and treat it as a type, and then what's after the dot
                // treat it as a name -- for method we might also look at a formal
                // parameter list, in order to cope with overloading
            case MODIFIERS:
            case ERROR:
                ArcumError.fatalError("Cannot disambiguate a " + type);
                break;
            }
            return null;
        }
        else if (binding instanceof MapBooleanArgument) {
            checkTypes(argumentName, EntityType.BOOLEAN, type, location);
        }
        else if (binding instanceof MapStringArgument) {
            checkTypes(argumentName, EntityType.STRING, type, location);
        }
        else if (binding instanceof MapTraitArgument) {
            // DOCUMENTATION: There is a bootstrapping issue here of sorts. We could
            // allow for the map trait argument to refer to static traits defined
            // in the option interface, but those definitions might depend on the
            // parameters (i.e. maybe even the map trait argument itself) and would
            // need to be circularly solved. We could allow the same for Option
            // defined static traits too, so the tupleSets defined in the option
            // instead of the optionInterface should be passed. However, it is done
            // this way currently so that the built-in trait tupleSets are passed,
            // which do not have the circular issues.
            OptionInterface optionInterface = option.getOptionInterface();
            List<TraitSignature> tupleSets = optionInterface.getTraitSignatures();
            checkTypeOfTraitArgument((MapTraitArgument)binding, formal, tupleSets,
                location);
        }

        return binding;
    }

    private void checkTypes(String name, EntityType actualType, EntityType expectedType,
        SourceLocation location)
    {
        if (actualType != expectedType) {
            ArcumError.fatalUserError(location, "The argument named %s must be"
                + " of type \'%s\', but a \'%s\' was passed instead", name, expectedType,
                actualType);
        }
    }

    private void checkTypeOfTraitArgument(MapTraitArgument mapTraitArgument,
        FormalParameter formal, List<TraitSignature> tupleSets, SourceLocation location)
    {
        if (!formal.isSubTrait() || !EntityType.TRAIT.equals(formal.getType())) {
            ArcumError.fatalUserError(location,
                "Expected %s to be a predicate expression", mapTraitArgument);
        }
        tupleSets = Lists.newArrayList(tupleSets);
        tupleSets.add(formal.getSubTraitType());
        mapTraitArgument.checkUserDefinedPredicates(tupleSets);
    }

    // valid only after a type check
    public List<ResolvedConceptMapEntry> getResolvedBindings() {
        return resolvedBindings;
    }
}