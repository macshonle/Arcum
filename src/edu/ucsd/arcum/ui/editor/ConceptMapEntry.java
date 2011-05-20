package edu.ucsd.arcum.ui.editor;

import static edu.ucsd.arcum.util.StringUtil.stripWhitespace;

import org.eclipse.jface.text.IDocument;

public class ConceptMapEntry implements TopLevelSourceEntry
{
    private IDocument document;
    private String optionName;
    private String fullText;

    public ConceptMapEntry(IDocument document, String optionName, String fullText) {
        this.document = document;
        this.optionName = optionName;
        this.fullText = fullText;
        System.out.printf("Created ConceptMapEntry(.., %s, %s)%n", optionName, fullText);
    }
    
    @Override
    public String toString() {
        return stripWhitespace(fullText);
    }

    public String getDisplayName() {
        return toString();
    }

    public String getOptionName() {
        return optionName;
    }

    public String getFullText() {
        return fullText;
    }
    
    public String alternativeOptionText(String alternativeOption) {
        String result = fullText.replaceFirst(optionName, alternativeOption);
        return result;
    }

    public IDocument getDocument() {
        return document;
    }
}