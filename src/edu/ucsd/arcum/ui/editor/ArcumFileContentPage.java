package edu.ucsd.arcum.ui.editor;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.*;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import edu.ucsd.arcum.interpreter.ast.TopLevelConstruct;
import edu.ucsd.arcum.interpreter.ast.Option;
import edu.ucsd.arcum.interpreter.parser.ArcumSourceFileParser;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.ui.UIUtil;
import edu.ucsd.arcum.ui.actions.DisabledAction;
import edu.ucsd.arcum.ui.actions.TransformAction;

public class ArcumFileContentPage extends ContentOutlinePage implements IDocumentListener
{
    private static final String MENU_ID = "edu.ucsd.arcum.arcumFileContentPage";
    private IDocumentProvider documentProvider;
    private IEditorInput editorInput;
    private List<TopLevelSourceEntry> entries;
    private long previousEdit;
    
    // initialized after createControl is called
    private IDocument document;
//
//    Next to do is to add in the darn menu items: but only if we can find the
//    associated project with the document: then, it's refactor time!
//    
    public ArcumFileContentPage(ArcumEditorPart editor,
            IDocumentProvider documentProvider, IEditorInput input)
    {
        this.documentProvider = documentProvider;
        this.editorInput = input;
        this.entries = new ArrayList<TopLevelSourceEntry>();
        this.previousEdit = -1;
    }

    @Override
    public void createControl(Composite parent) {
        super.createControl(parent);

        this.document = documentProvider.getDocument(editorInput);
        document.addDocumentListener(this);
        
        TreeViewer viewer = getTreeViewer();
        viewer.setContentProvider(new ProgramConstructs());
        viewer.setLabelProvider(new LabelProvider());
        viewer.addSelectionChangedListener(this);
        viewer.setInput(editorInput);
        
        createContextMenu(viewer);
    }
    
    private void createContextMenu(final TreeViewer viewer) {
        MenuManager menuMgr = new MenuManager("#PopupMenu");
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager manager) {
                IStructuredSelection selection;
                selection = (IStructuredSelection)viewer.getSelection();
                if (!selection.isEmpty()) {
                    Object object = selection.getFirstElement();
                    if (object instanceof ConceptMapEntry) {
                        ConceptMapEntry entry = (ConceptMapEntry)object;
                        addOptionActions(manager, entry);
                    }
                }
                // Other plug-ins can contribute their actions here
                manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
            }

            private void addOptionActions(IMenuManager manager,
                    final ConceptMapEntry entry)
            {
                if (UIUtil.existsUnsavedEditor()) {
                    DisabledAction message = new DisabledAction(
                            "You must save all editors first");
                    manager.add(message);
                    return;                    
                }
                
                if (editorInput instanceof FileEditorInput) {
                    FileEditorInput fileEditorInput;
                    IFile file;
                    final IProject proj;
                    
                    fileEditorInput = (FileEditorInput)editorInput;
                    file = fileEditorInput.getFile();
                    proj = file.getProject();
                    ArcumDeclarationTable declTable;
                    Option option;
                    TopLevelConstruct optionInterface;
                    List<Option> options;
                    
                    declTable = ArcumDeclarationTable.lookupSymbolTable(proj);                        
                    option = declTable.lookup(entry.getOptionName(), Option.class);
                    
                    if (option == null) {
                        DisabledAction message = new DisabledAction(
                                String.format("The option \'%s\' has not been defined",
                                        entry.getOptionName()));
                        manager.add(message);
                        return;
                    }
                    
                    optionInterface = option.getOptionInterface();
                    options = declTable.getImplementingOptions(optionInterface);
                    
                    options.remove(option);
                    for (Option alternative: options) {
                        TransformAction action;
                        action = new TransformAction(declTable, option,
                                entry, alternative, documentProvider);
                        manager.add(action);
                    }
                    
                    if (options.size() == 0) {
                        DisabledAction message = new DisabledAction(
                                String.format("The option \'%s\' has no alternatives",
                                        option.getName()));
                        manager.add(message);
                    }
                }
                else {
                    String msg = "No project is associated with this document";
                    Action action = new DisabledAction(msg);
                    manager.add(action);
                }
            }
        });
        Menu menu = menuMgr.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
        getSite().registerContextMenu(MENU_ID, menuMgr, viewer);
    }

    @Override
    public void selectionChanged(SelectionChangedEvent event) {
        super.selectionChanged(event);
    }

    private final class ProgramConstructs implements ITreeContentProvider
    {
        public Object[] getChildren(Object parentElement) {
            return new Object[0];
        }
    
        public Object getParent(Object element) {
            return null;
        }
    
        public boolean hasChildren(Object element) {
            return false;
        }
    
        public Object[] getElements(Object inputElement) {
            return entries.toArray();
        }
    
        public void dispose() {
        }
    
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            quickParseDocument();
        }
    }

    public void documentAboutToBeChanged(DocumentEvent event) {
        // intentionally left blank
    }

    public void documentChanged(DocumentEvent event) {
////        quickParseDocument();
//        if (bigEvent(event)) {
//            quickParseDocument();
//        }
    }

    // MACNEIL: this is certainly not ideal: also should look at where the edits
    // are made and whatever other tricks JDT uses
    private boolean bigEvent(DocumentEvent event) {
        long currentTime = System.currentTimeMillis();
        if (previousEdit == -1) {
            previousEdit = currentTime;
            return true;
        }
        final long ONE_SECOND = 1 * 1000;
        if (currentTime - previousEdit > ONE_SECOND) {
            previousEdit = currentTime;
            return true;
        }
        return false;
    }

    int numParsed = 0;
    private synchronized void quickParseDocument() {
        entries.clear();
        entries.addAll(ArcumSourceFileParser.quickParse(document));
        
        if (DEBUG) System.out.printf("I've been parsed %s times%n", numParsed++);

        getTreeViewer().refresh();
    }
}