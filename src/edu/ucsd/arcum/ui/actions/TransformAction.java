package edu.ucsd.arcum.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.ast.Option;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.interpreter.transformation.TransformationAlgorithm;
import edu.ucsd.arcum.ui.editor.ArcumEditorPart;
import edu.ucsd.arcum.ui.editor.ConceptMapEntry;
import edu.ucsd.arcum.util.SystemUtil;

// Too much code copied, hence the deprecation
//@Deprecated -- MACNEIL commented out to avoid clutter
public class TransformAction extends Action
{
    private ArcumDeclarationTable symbTab;
    private Option option;
    private ConceptMapEntry entry;
    private Option alternative;

    // this action has been move either temporarily or permanently somewhere
    // else-- at least don't make any more uses of it in the mean time
    //@Deprecated -- MACNEIL commented out to avoid clutter
    public TransformAction(ArcumDeclarationTable symbTab,
            Option option, ConceptMapEntry entry,
            Option alternative, IDocumentProvider documentProvider)
    {
        super(String.format("Transform to %s implementation",
                alternative.getName()));
        this.symbTab = symbTab;
        this.option = option;
        this.entry = entry;
        this.alternative = alternative;
    }

    @Override
    public void run() {
        try {
            String srcText;
            TransformationAlgorithm xform;
            Change change;

            srcText = entry.getFullText();
            xform = new TransformationAlgorithm(symbTab, option, alternative, srcText);
            change = makeMapEntryEdit(srcText);

            xform.addChange(change);
            boolean completed = xform.transform();
            if (completed) {
                saveAllArcumSources();
                symbTab.rematchAllCode();
            }
        }
        catch (ArcumError e) {
            e.printStackTrace(System.err);
            e.printStackTrace(SystemUtil.getErrStream());
            throw e;
        }
        catch (RuntimeException e) {
            e.printStackTrace(System.err);
            e.printStackTrace(SystemUtil.getErrStream());
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void saveAllArcumSources() {
        IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
        for (IWorkbenchWindow window: windows) {
            for (IWorkbenchPage page: window.getPages()) {
                IEditorPart activeEditor = page.getActiveEditor();
                if (activeEditor instanceof ArcumEditorPart) {
                    page.saveAllEditors(false);
                }
            }
        }
    }

    private Change makeMapEntryEdit(String srcText) {
        String newText;
        String changeMessage;
        IDocument document;
        DocumentChange change;
        String entireMap;
        TextEdit edit;

        newText = entry.alternativeOptionText(alternative.getName());
        changeMessage = String.format("Change concept map to use %s",
                                option.getName(), alternative.getName());
        document = entry.getDocument();
        change = new DocumentChange(changeMessage, document);
        entireMap = document.get();
        edit = new ReplaceEdit(entireMap.indexOf(srcText), srcText.length(), newText);
        change.setEdit(edit);
        return change;
    }
}