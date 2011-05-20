package edu.ucsd.arcum.interpreter.query;

import static edu.ucsd.arcum.interpreter.ast.ASTUtil.IDENTITY_ACCESSOR;
import static edu.ucsd.arcum.interpreter.ast.ASTUtil.checkNames;
import static edu.ucsd.arcum.interpreter.ast.ASTUtil.extractNames;
import static edu.ucsd.arcum.util.Subset.subset;
import static java.util.Collections.synchronizedMap;

import java.io.PrintStream;
import java.util.*;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import edu.ucsd.arcum.ArcumPlugin;
import edu.ucsd.arcum.builders.ParseArcumCodeOperation;
import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.ast.*;
import edu.ucsd.arcum.interpreter.ast.ASTUtil.NameAccessor;
import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.transformation.ResolvedConceptMapEntry;
import edu.ucsd.arcum.util.Subset;

// A symbol table for all Arcum code constructs: Options, Option Interfaces,
// and Maps. A map declaration (that is, one specific instantiation of an Option
// with parameters) can be looked up to find its equivalent OptionMatchTable.
public class ArcumDeclarationTable
{
    private static Map<IProject, ArcumDeclarationTable> arcumDeclarationTables;
    static {
        arcumDeclarationTables = synchronizedMap(new WeakHashMap<IProject, ArcumDeclarationTable>());
    }

    public static ArcumDeclarationTable lookupSymbolTable(final IProject project) {
        ArcumDeclarationTable declarationTable = arcumDeclarationTables.get(project);
        if (declarationTable == null) {
            ParseArcumCodeOperation.parseArcumCodeWithDialog(project);
        }
        // this counts specifically on the ArcumBuilder calling newSymbolTable
        declarationTable = arcumDeclarationTables.get(project);
        return declarationTable;
    }

    // Creates a new symbol table and associates it with the given project
    public static ArcumDeclarationTable newSymbolTable(IProject project) {
        ArcumDeclarationTable symbTab = new ArcumDeclarationTable(project);
        arcumDeclarationTables.put(project, symbTab);
        return symbTab;
    }

    // The "_" is a special variable that matches anything, a "don't care"; this
    // can only be used in patterns for pure matching: cannot be used in patterns
    // for code generation
    public static final String SPECIAL_ANY_VARIABLE = "_";

    private Map<String, TopLevelConstruct> allDeclarations;
    private IProject project;

    // valid after makeEntityTables is called
    private Map<String, OptionMatchTable> entityTableLookup;
    private EntityDataBase entityDataBase;

    private ArcumDeclarationTable(IProject project) {
        this.project = project;
        this.allDeclarations = new LinkedHashMap<String, TopLevelConstruct>();
        this.entityTableLookup = new HashMap<String, OptionMatchTable>();
        this.entityDataBase = null;

//        parseBuiltinConcepts();
    }

    public <T extends TopLevelConstruct> T conditionalCreate(String name,
        ConstructorThunk<T> ctor)
    {
        if (allDeclarations.containsKey(name)) {
            ArcumError.fatalError("A declaration named " + name + " already exists%n");
        }
        T result = ctor.create();
        allDeclarations.put(name, result);
        return result;
    }

    public void typeCheck() {
        Collection<TopLevelConstruct> decls;
        decls = allDeclarations.values();

        List<String> allGlobalNames = new ArrayList<String>();
        extractNames(allGlobalNames, decls, new NameAccessor<TopLevelConstruct>() {
            public String getName(TopLevelConstruct decl) {
                return decl.getName();
            }
        });
        checkNames(allGlobalNames, IDENTITY_ACCESSOR);

        for (OptionInterface optionInterface : subset(decls, OptionInterface.class)) {
            optionInterface.doTypeCheck(this);
        }
        for (Option option : subset(decls, Option.class)) {
            option.doTypeCheck(this);
        }
        Subset<RequireMap> requireMaps = subset(decls, RequireMap.class);
        for (RequireMap requireMap : requireMaps) {
            requireMap.doTypeCheck(this);
        }

        if (false && ArcumPlugin.DEBUG) {
            printDeclarations(System.out);
        }
    }

    public void printDeclarations(PrintStream out) {
        for (TopLevelConstruct decl : allDeclarations.values()) {
            out.printf("%s%n", decl);
        }
    }

    public <T> T lookup(String name, Class<T> clazz) {
        TopLevelConstruct decl = allDeclarations.get(name);
        if (decl != null && clazz.isAssignableFrom(decl.getClass())) {
            return clazz.cast(decl);
        }
        else {
            return null;
        }
    }

    public List<OptionMatchTable> makeEntityTables() throws CoreException {
        this.entityTableLookup.clear();

        List<OptionMatchTable> result = new ArrayList<OptionMatchTable>();
        for (ResolvedConceptMapEntry binding : getAllResolvedBindings()) {
            OptionMatchTable entities = makeEntityTable(binding);
            result.add(entities);
        }
        return result;
    }

    // parses built-in concepts, done once per project
//    private void parseBuiltinConcepts() {
//        if (ArcumPlugin.DEBUG) {
//            System.out.printf("Reading Arcum built-in files...%n");
//        }
//        for (String filePath : BUILT_IN_FILES) {
//            String source = FileUtil.readBundledFile(filePath);
//            String[] strings = filePath.split("/");
//            String name = strings[strings.length - 1];
//            ArcumSourceFileParser parser;
//            parser = new ArcumSourceFileParser(source, null, name, project);
//            parser.parseArcumSource(this);
//        }
//    }

    public List<Option> getImplementingOptions(TopLevelConstruct optionInterface) {
        List<Option> result = new ArrayList<Option>();
        for (TopLevelConstruct decl : allDeclarations.values()) {
            if (decl instanceof Option) {
                Option option = (Option)decl;
                if (option.getOptionInterface() == optionInterface) {
                    result.add(option);
                }
            }
        }
        return result;
    }

    public List<RequireMap> getAllMaps() {
        List<RequireMap> result = new ArrayList<RequireMap>();
        for (TopLevelConstruct decl : allDeclarations.values()) {
            if (decl instanceof RequireMap) {
                result.add((RequireMap)decl);
            }
        }
        return result;
    }

    public OptionMatchTable constructEntityTable(String optionArgs) throws CoreException {
        OptionMatchTable result = entityTableLookup.get(optionArgs);
        if (result == null) {
            for (ResolvedConceptMapEntry binding : getAllResolvedBindings()) {
                String argumentSourceText = binding.getArgumentSourceText();
                if (optionArgs.equals(argumentSourceText)) {
                    OptionMatchTable entities = makeEntityTable(binding);
                    return entities;
                }
            }
        }
        return result;
    }

    private List<ResolvedConceptMapEntry> getAllResolvedBindings() {
        List<ResolvedConceptMapEntry> result = new ArrayList<ResolvedConceptMapEntry>();
        for (RequireMap map : subset(allDeclarations.values(), RequireMap.class)) {
            for (ResolvedConceptMapEntry binding : map.getResolvedBindings()) {
                result.add(binding);
            }
        }
        return result;
    }

    private OptionMatchTable makeEntityTable(ResolvedConceptMapEntry binding)
        throws CoreException
    {
        if (entityDataBase == null) {
            entityDataBase = new EntityDataBase(project);
            entityDataBase.populate();
        }
        
        OptionMatchTable entities = new OptionMatchTable(this, binding);
        entities.matchAllEntities(entityDataBase);
        boolean passed = entities.checkExtraDefinitionConditions(entityDataBase);
        if (passed) {
            String argumentSourceText = binding.getArgumentSourceText();
            entityTableLookup.put(argumentSourceText, entities);
        }
        else {
            ArcumError.stop();
        }

        return entities;
    }

    public void disposeEntityTable(String optionArgs) {
        entityTableLookup.remove(optionArgs);
    }

    public FragmentParser newParser(TopLevelConstruct context) {
        return new FragmentParser(context.getImports(), project);
    }

    public void rematchAllCode() {
        ParseArcumCodeOperation.rematchAllCodeWithDialog(project);
    }

    public EntityDataBase getEntityDataBase() {
        return entityDataBase;
    }
}