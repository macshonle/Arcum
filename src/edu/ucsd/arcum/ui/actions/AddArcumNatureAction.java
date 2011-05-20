package edu.ucsd.arcum.ui.actions;

import static java.util.Arrays.asList;

import java.util.ArrayList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;

import edu.ucsd.arcum.ArcumPlugin;
import edu.ucsd.arcum.EclipseUtil;

public class AddArcumNatureAction extends ProjectAction
{
    public AddArcumNatureAction() {
        super(true);
    }
    
    @Override
    protected void doAction(IProject project) throws CoreException {
        IProjectDescription description = project.getDescription();
        
        ArrayList<String> newIds = new ArrayList<String>();
        newIds.addAll(asList(description.getNatureIds()));
        int index = newIds.indexOf(ArcumPlugin.NATURE_ID);
        if (index == -1) {
            /* We add to the front so that our icon is drawn instead of JDT's */
            newIds.add(0, ArcumPlugin.NATURE_ID);
        }
        description.setNatureIds(newIds.toArray(new String[0]));
        project.setDescription(description, null);
        EclipseUtil.refreshPackageExplorer();
    }
}
