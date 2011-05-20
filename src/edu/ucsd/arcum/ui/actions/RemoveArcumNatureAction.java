package edu.ucsd.arcum.ui.actions;

import static java.util.Arrays.asList;

import java.util.ArrayList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;

import edu.ucsd.arcum.ArcumPlugin;
import edu.ucsd.arcum.EclipseUtil;

public class RemoveArcumNatureAction extends ProjectAction
{
    public RemoveArcumNatureAction() {
        super(true);
    }

    @Override
    protected void doAction(IProject project) throws CoreException {
        IProjectDescription description = project.getDescription();

        ArrayList<String> newIds = new ArrayList<String>();
        newIds.addAll(asList(description.getNatureIds()));
        int index = newIds.indexOf(ArcumPlugin.NATURE_ID);
        if (index != -1) {
            newIds.remove(index);
        }
        description.setNatureIds(newIds.toArray(new String[0]));
        project.setDescription(description, null);
        EclipseUtil.refreshPackageExplorer();
    }
}
