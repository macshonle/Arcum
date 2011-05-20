package edu.ucsd.arcum.natures;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;

import edu.ucsd.arcum.ArcumPlugin;

public class ArcumNature implements IProjectNature
{
    private IProject project;

    // Add nature-specific information for the project. Adds a builder
    // to the project's build spec. This builder is placed at the end of
    // the command list.
    public void configure() throws CoreException {
        System.out.printf("ArcumNature.configure() called%n");
        
        IProjectDescription projectDescription = project.getDescription();

        ICommand[] cmds = projectDescription.getBuildSpec();
        if (!contains(cmds, ArcumPlugin.BUILDER_ID)) {
            ICommand command = projectDescription.newCommand();
            command.setBuilderName(ArcumPlugin.BUILDER_ID);
            
            cmds = insert_back(cmds, command);
        }
        projectDescription.setBuildSpec(cmds);
        project.setDescription(projectDescription, null);
    }

    // COPIED from AJDT -- with modifications
    // Remove the nature-specific information
    public void deconfigure() throws CoreException {
        System.out.printf("ArcumNature.deconfigure() called%n");
        IProjectDescription description = project.getDescription();

        ICommand[] cmds = description.getBuildSpec();
        if (contains(cmds, ArcumPlugin.BUILDER_ID)) {
            cmds = remove(cmds, ArcumPlugin.BUILDER_ID);
        }
        description.setBuildSpec(cmds);
        project.setDescription(description, null);
    }

    public IProject getProject() {
        return project;
    }

    public void setProject(IProject value) {
        this.project = value;
    }
    
    private static ICommand[] insert_back(ICommand[] cmds, ICommand command) {
        ICommand[] result = new ICommand[cmds.length + 1];
        System.arraycopy(cmds, 0, result, 0, cmds.length);
        result[cmds.length] = command;
        return result;
    }

    /// BEGIN code copied from AJDT ///
    /**
     * Check if the given build command list contains a given command
     */
    private static boolean contains(ICommand[] commands, String builderId) {
        boolean found = false;
        for (int i = 0; i < commands.length; i++) {
            if (commands[i].getBuilderName().equals(builderId)) {
                found = true;
                break;
            }
        }
        return found;
    }

    /**
     * In a list of build commands, swap all occurences of one entry for another
     */
    private static ICommand[] swap(ICommand[] sourceCommands,
            String oldBuilderId, ICommand newCommand) {
        ICommand[] newCommands = new ICommand[sourceCommands.length];
        for (int i = 0; i < sourceCommands.length; i++) {
            if (sourceCommands[i].getBuilderName().equals(oldBuilderId)) {
                newCommands[i] = newCommand;
            } else {
                newCommands[i] = sourceCommands[i];
            }
        }
        return newCommands;
    }

    /**
     * Insert a new build command at the front of an existing list
     */
    private static ICommand[] insert(ICommand[] sourceCommands, ICommand command) {
        ICommand[] newCommands = new ICommand[sourceCommands.length + 1];
        newCommands[0] = command;
        for (int i = 0; i < sourceCommands.length; i++) {
            newCommands[i + 1] = sourceCommands[i];
        }
        return newCommands;
    }

    /**
     * Remove a build command from a list
     */
    private static ICommand[] remove(ICommand[] sourceCommands, String builderId) {
        ICommand[] newCommands = new ICommand[sourceCommands.length - 1];
        int newCommandIndex = 0;
        for (int i = 0; i < sourceCommands.length; i++) {
            if (!sourceCommands[i].getBuilderName().equals(builderId)) {
                newCommands[newCommandIndex++] = sourceCommands[i];
            }
        }
        return newCommands;
    }
/// END code copied from AJDT ///
}