package edu.ucsd.arcum.ui.editor;

public class OptionOrConceptDeclaration implements TopLevelSourceEntry
{
    private String declName;

    public OptionOrConceptDeclaration(String declName) {
        this.declName = declName;
    }
    
    public String getDisplayName() {
        return declName;
    }

}
