package dolmenplugin.base;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

/**
 * The class providing the Dolmen nature to
 * Eclipse projects.
 * 
 * @author Stéphane Lescuyer
 */
public final class Nature implements IProjectNature {

	/**
	 * ID of Dolmen nature (keep it sync with plugin.xml, it's
	 * the bundle symbolic name + the ID defined in plugin.xml)
	 */
	public static final String ID = "DolmenPlugin.DolmenNature";
	
	private IProject project = null;
	
	@Override
	public void configure() throws CoreException {
		if (project == null) {
			System.err.println("Null project in Nature instance");
			return;
		}
			
		final IProjectDescription desc = project.getDescription();
		// Look for already installed command with Dolmen builder ID
		final ICommand[] cmds = desc.getBuildSpec();
		for (ICommand cmd : cmds)
			if (cmd.getBuilderName().equals(Builder.ID))
				return;
		
		// If none found, install our own
		ICommand command = desc.newCommand();
		command.setBuilderName(Builder.ID);
		ICommand[] newCmds = new ICommand[cmds.length + 1];
		newCmds[0] = command;
		System.arraycopy(cmds, 0, newCmds, 1, cmds.length);
		desc.setBuildSpec(newCmds);
		
		project.setDescription(desc, null);
	}

	@Override
	public void deconfigure() throws CoreException {
		final IProjectDescription desc = project.getDescription();
		final ICommand[] cmds = desc.getBuildSpec();
		
		// Look for already installed Dolmen builder
		int i = 0;
		for (; i < cmds.length; ++i) {
			if (cmds[i].getBuilderName().equals(Builder.ID))
				break;
		}
		if (i >= cmds.length) return;
		// If one found, remove it
		ICommand[] newCmds = new ICommand[cmds.length - 1];
		System.arraycopy(cmds, 0, newCmds, 0, i);
		System.arraycopy(cmds, i+1, newCmds, i, cmds.length - i - 1);
		desc.setBuildSpec(newCmds);
		
		project.setDescription(desc, null);
	}

	@Override
	public IProject getProject() {
		return project;
	}

	@Override
	public void setProject(IProject project) {
		this.project = project;
	}
	
	/**
	 * @param project
	 * @return whether the given project has the Dolmen nature
	 * 	(returns false when the project is not opened, or some
	 * 	 exception occurs when fetching the project's description)
	 */
	public static boolean hasNature(final IProject project) {
		if (project == null)
			return false;
		try {
			IProjectDescription desc = project.getDescription();
			for (String nat : desc.getNatureIds())
				if (nat.equals(Nature.ID))
					return true;	
		} catch (CoreException ce) {
			System.err.println(ce.getMessage());
			return false;
		}
		return false;
	}

}
