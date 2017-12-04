package dolmenplugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdapterManager;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import dolmenplugin.base.Nature;

/**
 * Handles the command whose ID is "dolmenplugin.ConvertDolmenNatureCommand" and
 * which helps add the Dolmen nature to a project
 * 
 * @author Stéphane Lescuyer
 */
public final class ConvertDolmenNatureHandler extends AbstractHandler {

	private IStatus convert(Object selected) throws ExecutionException {
		// Get an IResource as an adapter from the current selection
		IAdapterManager adapterManager = Platform.getAdapterManager();
		IResource resourceAdapter = adapterManager.getAdapter(selected, IResource.class);

		if (resourceAdapter != null) {
			IResource resource = resourceAdapter;
			IProject project = resource.getProject();
			try {
				IProjectDescription description = project.getDescription();
				String[] natures = description.getNatureIds();
				String[] newNatures = new String[natures.length + 1];
				System.arraycopy(natures, 0, newNatures, 1, natures.length);

				// add our Dolmen nature id
				newNatures[0] = Nature.ID;

				// validate the natures
				IWorkspace workspace = ResourcesPlugin.getWorkspace();
				IStatus status = workspace.validateNatureSet(newNatures);

				// only apply new nature, if the status is ok
				if (status.getCode() == IStatus.OK) {
					description.setNatureIds(newNatures);
					project.setDescription(description, null);
				}

//				System.out.println("Changed nature of project " + project);
//				System.out.println(" - natures now " + Arrays.toString(project.getDescription().getNatureIds()));
//				System.out.println(" - has nature: " + project.hasNature(Nature.ID));
				
				return status;
			} catch (CoreException e) {
				throw new ExecutionException(e.getMessage(), e);
			}
		}
		return Status.OK_STATUS;
	}
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection currentSelection = HandlerUtil.getCurrentSelection(event);
		if (currentSelection instanceof IStructuredSelection) {
			IStructuredSelection structuredSel = (IStructuredSelection) currentSelection;
			for (Object selected : structuredSel.toList()) {
				IStatus status = convert(selected);
				if (status.getCode() != IStatus.OK) {
					return status;
				}
			}
		}
		return Status.OK_STATUS;
	}
}