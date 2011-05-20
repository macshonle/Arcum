package edu.ucsd.arcum.ui.views;

import static edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable.lookupSymbolTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.part.ViewPart;

import edu.ucsd.arcum.ArcumPlugin;
import edu.ucsd.arcum.EclipseUtil;
import edu.ucsd.arcum.builders.ParseArcumCodeOperation;
import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.UserCompilationProblem;
import edu.ucsd.arcum.interpreter.ast.Option;
import edu.ucsd.arcum.interpreter.ast.RequireMap;
import edu.ucsd.arcum.interpreter.ast.TopLevelConstruct;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.interpreter.query.OptionMatchTable;
import edu.ucsd.arcum.interpreter.transformation.ResolvedConceptMapEntry;
import edu.ucsd.arcum.interpreter.transformation.TransformationAlgorithm;
import edu.ucsd.arcum.ui.UIUtil;
import edu.ucsd.arcum.util.FileUtil;
import edu.ucsd.arcum.util.SystemUtil;

public class FragmentsView extends ViewPart
{
    private TreeViewer entitiesTreeViewer;
    private Button refreshButton;
    private Button transformButton;
    private Combo entrySelect;
    private Combo transformSelect;
    private EntityTableContentProvider tableContentProvider;

    private RefreshAction refreshAction;
    private EntrySelectionListener entrySelectionListener;
    private TransformSelectionListener transformSelectionListener;
    
    @Override
    public void createPartControl(Composite parent) {
        layoutControls(parent);
        hookActionListeners();
    }

    private void hookActionListeners() {
        this.entrySelectionListener = new EntrySelectionListener();
        entrySelect.addSelectionListener(entrySelectionListener);
        
        this.refreshAction = new RefreshAction();
        refreshButton.addSelectionListener(refreshAction);
        
        this.transformSelectionListener = new TransformSelectionListener();
        transformSelect.addSelectionListener(transformSelectionListener);
        
        transformButton.addSelectionListener(new TransformButtonAction());
    }

    private void layoutControls(Composite parent) {
        GridLayout layout = new GridLayout(1, true);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        parent.setLayout(layout);
        parent.setLayoutData(fillGreedy());
        
        Composite topControls = new Composite(parent, SWT.NONE);
        makeControls(topControls);
        GridData data = new GridData();
        data.grabExcessHorizontalSpace = true;
        data.grabExcessVerticalSpace = false;
        topControls.setLayoutData(data);
        
        Composite bottomTable = new Composite(parent, SWT.NONE);
        makeTable(bottomTable);        
        bottomTable.setLayoutData(fillGreedy());
    }

    private GridData fillGreedy() {
        GridData data = new GridData();
        data.grabExcessHorizontalSpace = true;
        data.grabExcessVerticalSpace = true;
        data.horizontalAlignment = SWT.FILL;
        data.verticalAlignment = SWT.FILL;
        return data;
    }

    private void makeControls(Composite parent) {
        GridLayout layout = new GridLayout();
        layout.numColumns = 7;
        layout.makeColumnsEqualWidth = false;
        parent.setLayout(layout);
        
        Label focusOn = new Label(parent, SWT.LEFT);
        focusOn.setText("Focus on map entry:");

        this.entrySelect = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        GridData data = new GridData();
        data.horizontalAlignment = SWT.FILL;
        data.grabExcessHorizontalSpace = true;
        entrySelect.setLayoutData(data);
        entrySelect.setEnabled(false);
        entrySelect.setVisible(true);

        this.refreshButton = new Button(parent, SWT.PUSH);
        refreshButton.setText("Refresh");

        Label spacer = new Label(parent, SWT.LEFT);
        spacer.setText("       ");

        Label transformTo = new Label(parent, SWT.LEFT);
        transformTo.setText("Transform to:");
        
        this.transformSelect = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        transformSelect.setVisible(true);
        transformSelect.setEnabled(false);

        this.transformButton = new Button(parent, SWT.PUSH);
        transformButton.setText("Transform");
    }

    private void makeTable(Composite parent) {
        GridLayout layout = new GridLayout(1, true);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        parent.setLayout(layout);
        parent.setLayoutData(fillGreedy());

        this.entitiesTreeViewer = new TreeViewer(parent, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
        Tree control = entitiesTreeViewer.getTree();

        GridData data = fillGreedy();
        control.setLayoutData(data);

        control.setHeaderVisible(true);
        control.setLinesVisible(true);

        TreeColumn column = new TreeColumn(control, SWT.LEFT, 0);
        column.setText("Concept");
        column.setWidth(200);
        column.setAlignment(SWT.LEFT);
        
        column = new TreeColumn(control, SWT.LEFT, 1);
        column.setText("Program Fragment");
        column.setWidth(250);
        column.setAlignment(SWT.LEFT);
        
        column = new TreeColumn(control, SWT.LEFT, 2);
        column.setText("Resource");
        column.setWidth(150);
        column.setAlignment(SWT.LEFT);
        
        column = new TreeColumn(control, SWT.LEFT, 3);
        column.setText("Path");
        column.setWidth(200);
        column.setAlignment(SWT.LEFT);

        column = new TreeColumn(control, SWT.LEFT, 4);
        column.setText("Line");
        column.setWidth(100);
        column.setAlignment(SWT.LEFT);

        this.tableContentProvider = new EntityTableContentProvider();
        entitiesTreeViewer.setContentProvider(tableContentProvider);
        entitiesTreeViewer.setLabelProvider(new EntityTableLabelProvider());
        entitiesTreeViewer.addDoubleClickListener(new EntityTableDoubleClickListener(entitiesTreeViewer));
    }

    @Override
    public void setFocus() {
    }

    private boolean checkForUnsavedEditors() {
        if (UIUtil.existsUnsavedEditor()) {
            UIUtil.notify("You must save all modified editors first",
                    "Code Unsaved");
            return true;
        }
        return false;
    }

    private class RefreshAction extends SelectionAdapter
    {
		public void widgetSelected(SelectionEvent e) {
            if (checkForUnsavedEditors()) {
                return;
            }
            entrySelectionListener.clearEntries();
            List<String> items = new ArrayList<String>();
            List<IProject> projects = EclipseUtil.getOpenProjects();
            for (IProject project: projects) {
            	IJavaProject javaProject = JavaCore.create(project);
            	if (!javaProject.exists())
            		continue;
            	
            	try {
                    if (!project.hasNature(ArcumPlugin.NATURE_ID))
                        continue;
                }
                catch (CoreException ce) {
                    SystemUtil.getErrStream().printf("Impossible");
                    ce.printStackTrace(SystemUtil.getErrStream());
                }
            	
            	if (UIUtil.hasErrors(project)) {
                    return;
                }
            	
                ParseArcumCodeOperation.rematchAllCodeWithDialog(project);
                ArcumDeclarationTable declTable = lookupSymbolTable(project);
                List<RequireMap> maps = declTable.getAllMaps();
                for (RequireMap map: maps) {
                    List<ResolvedConceptMapEntry> bindings = map.getResolvedBindings();
                    for (ResolvedConceptMapEntry binding: bindings) {
                        items.add(makeEntrySelectMenuItem(binding, project));
                    }
                }
            }
            int curSelection = entrySelect.getSelectionIndex();
            if (curSelection == -1) {
                curSelection = 0;
            }
            entrySelect.setItems(items.toArray(new String[0]));
            if (curSelection >= items.size()) {
                curSelection = 0;
            }
            entrySelect.select(curSelection);
            entrySelect.setEnabled(true);
            entrySelectionListener.widgetSelected(null);
        }

        private String makeEntrySelectMenuItem(ResolvedConceptMapEntry binding, IProject project) {
            String entryText = String.format("%s - %s",
                    binding.getDisplayString(),
                                project.getName());
            entrySelectionListener.bindEntry(entryText, binding, project);
            return entryText;
        }
    }
    
    private class EntrySelectionListener extends SelectionAdapter
    {
        private Map<String, IProject> projectLookup;
        private Map<String, ResolvedConceptMapEntry> entryLookup;

        public void widgetSelected(SelectionEvent e) {
            int index = entrySelect.getSelectionIndex();
            String entryText = entrySelect.getItem(index);
            
            IProject project = projectLookup.get(entryText);
            ArcumDeclarationTable declTable = lookupSymbolTable(project);
            ResolvedConceptMapEntry entry = entryLookup.get(entryText);
            String optionArgs = entry.getArgumentSourceText();
            OptionMatchTable optionMatchTable;
            try {
                optionMatchTable = declTable.constructEntityTable(optionArgs);
            }
            catch (UserCompilationProblem ucp) {
                ucp.printStackTrace(SystemUtil.getErrStream());
                ArcumError.fatalUserError(ucp.getPosition(), "%s", ucp.getMessage());
                return;
            }
            catch (CoreException ce) {
                ce.printStackTrace(SystemUtil.getErrStream());
                throw new RuntimeException(ce);
            }
            catch (ArcumError ae) {
                ae.printStackTrace(SystemUtil.getErrStream());
                throw ae;
            }
            catch (RuntimeException re) {
                re.printStackTrace(SystemUtil.getErrStream());
                ArcumError.fatalUserError(null, "%s: %s", re.getClass().getCanonicalName(),
                        re.getMessage());
                throw re;
            }
            refreshTable(optionMatchTable);
            refreshTransformSelect(declTable, project, entry.getOption(),
                    entry.getSourceFile(), optionArgs);
        }

        private void refreshTable(OptionMatchTable optionMatchTable) {
            tableContentProvider.setEntityTable(optionMatchTable);
            entitiesTreeViewer.setInput(optionMatchTable);
            entitiesTreeViewer.refresh();
        }
        
        private void refreshTransformSelect(ArcumDeclarationTable declTable,
                IProject project, Option option, IFile file, String optionArgs)
        {
            TopLevelConstruct optionInterface = option.getOptionInterface();
            List<Option> options = declTable.getImplementingOptions(optionInterface);
            
            options.remove(option);
            
            transformSelectionListener.clearEntries();
            List<String> items = new ArrayList<String>();
            for (Option alternative: options) {
                String itemName = alternative.getName();
                items.add(itemName);
                transformSelectionListener.bindEntry(declTable, itemName,
                        option, alternative, file, optionArgs);
            }
            transformSelect.setItems(items.toArray(new String[0]));
            transformSelect.select(0);
            transformSelect.setEnabled(true);
        }

        public void bindEntry(String entryText, ResolvedConceptMapEntry entry, IProject project) {
            projectLookup.put(entryText, project);
            entryLookup.put(entryText, entry);
        }

        public void clearEntries() {
            this.projectLookup = new HashMap<String, IProject>();
            this.entryLookup = new HashMap<String, ResolvedConceptMapEntry>();
        }
    }

    private class TransformSelectionListener extends SelectionAdapter
    {
        private Map<String, Transformer> transformerLookup;

        public void bindEntry(ArcumDeclarationTable symbTab, String itemName,
                Option option, Option alternative, IFile file, String optionArgs)
        {
            Transformer transformer = new Transformer(symbTab, option, alternative,
                    optionArgs, file);
            transformerLookup.put(itemName, transformer);
        }

        public void clearEntries() {
            this.transformerLookup = new HashMap<String, Transformer>();
        }

        public Transformer lookup(String item) {
            return transformerLookup.get(item);
        }
    }
    
    private class TransformButtonAction extends SelectionAdapter
    {
        public void widgetSelected(SelectionEvent se) {
            try {
                if (checkForUnsavedEditors()) {
                    return;
                }
                List<IProject> projects = EclipseUtil.getOpenProjects();
                for (IProject project: projects) {
                    IJavaProject javaProject = JavaCore.create(project);
                    if (!javaProject.exists())
                        continue;
                    
                    if (UIUtil.hasErrors(project)) {
                        return;
                    }
                }
                int index = transformSelect.getSelectionIndex();
                if (index < 0) {
                    String message = "Transformation cannot be performed because"
                        + " there are no alternative options available.";
                    UIUtil.notify(message, "Nothing to Transform to");
                }
                else {
                    String item = transformSelect.getItem(index);
                    Transformer transformer = transformSelectionListener.lookup(item);
                    transformer.doTransform();
                }
            }
            catch (ArcumError e) {
                e.printStackTrace(System.err);
                throw e;
            }
            catch (RuntimeException e) {
                e.printStackTrace(System.err);
                throw e;
            }
            catch (Exception e) {
                e.printStackTrace(System.err);
                throw new RuntimeException(e);
            }
        }
    }
    
    private class Transformer {
        private ArcumDeclarationTable symbTab;
        private Option option;
        private Option alternative;
        private String srcText;
        private IFile file;
        
        public Transformer(ArcumDeclarationTable symbTab, Option option,
                Option alternative, String srcText, IFile file)
        {
            this.symbTab = symbTab;
            this.option = option;
            this.alternative = alternative;
            this.srcText = srcText;
            this.file = file;
        }
        
        public void doTransform() throws Exception {
            TransformationAlgorithm xform;
            Change change;
            
            xform = new TransformationAlgorithm(symbTab, option, alternative, srcText);
            change = makeMapEntryEdit(srcText);
            xform.addChange(change);
            
            boolean completed = xform.transform();
            if (completed) {
//              saveAllArcumSources();
//              ParseArcumCodeOperation.rematchAllCodeWithDialog(symbTab.getProject());
                refreshAction.widgetSelected(null);
            }
        }

        private Change makeMapEntryEdit(String srcText) throws CoreException, IOException {
            String changeMessage = String.format("Change concept map to use %s",
                    option.getName(), alternative.getName());
            TextFileChange change = new TextFileChange(changeMessage, file);
            
            String newText = srcText.replaceFirst(option.getName(), alternative.getName());
            String map = FileUtil.readFileWithThrow(file);
            ReplaceEdit replaceEdit = new ReplaceEdit(map.indexOf(srcText), srcText.length(), newText);
            change.setEdit(replaceEdit);
            
            return change;
        }
    }
}