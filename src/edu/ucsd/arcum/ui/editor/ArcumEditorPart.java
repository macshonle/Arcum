package edu.ucsd.arcum.ui.editor;

import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
public class ArcumEditorPart extends CompilationUnitEditor
//import org.eclipse.ui.editors.text.TextEditor;
//public class ArcumEditorPart extends TextEditor
{
    private ArcumFileContentPage outlinePage;

    @Override
    public Object getAdapter(Class adapter) {
        if (IContentOutlinePage.class.equals(adapter)) {
//            return _createOutlinePage(); MACNEIL: TEMP or not... could be a while
        }
        return super.getAdapter(adapter);
    }

    private ArcumFileContentPage _createOutlinePage() {
        if (outlinePage == null) {
            this.outlinePage = new ArcumFileContentPage(this,
                    getDocumentProvider(), getEditorInput());
        }
        return outlinePage;
    }
}