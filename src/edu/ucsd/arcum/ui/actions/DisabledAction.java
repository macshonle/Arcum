package edu.ucsd.arcum.ui.actions;

import org.eclipse.jface.action.Action;

public class DisabledAction extends Action
{
    public DisabledAction(String text) {
        super(text);
        setEnabled(false);
    }
}