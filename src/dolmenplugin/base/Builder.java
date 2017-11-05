package dolmenplugin.base;

import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

import dolmenplugin.builders.JGCompile;
import dolmenplugin.builders.JLCompile;

/**
 * ...
 * 
 * @author Stéphane Lescuyer
 */
public final class Builder extends IncrementalProjectBuilder {
	
	/**
	 * ID of Dolmen builder (keep it sync with plugin.xml)
	 */
	public static final String ID = "dolmenplugin.base.Builder";

	/**
	 * The builder will keep track of the various resources which
	 * are generated, associated to the Dolmen resource that they
	 * have been generated from
	 */
	private final Map<IFile, Set<IFile>> generatedMap;
	
	public Builder() {
		this.generatedMap = new HashMap<>();
	}

	/**
	 * Register that {@code generated} has been generated
	 * for the Dolmen file {@code dolmen}
	 * 
	 * @param dolmen
	 * @param generated
	 */
	private void add(IFile dolmen, IFile generated) {
		Set<IFile> gens = generatedMap.get(dolmen);
		if (gens == null) {
			gens = new HashSet<>();
			generatedMap.put(dolmen, gens);
		}
		gens.add(generated);
	}
	
	/**
	 * Registers that the resource {@code ifile} has been
	 * removed
	 * 
	 * @param ifile
	 */
	private void remove(IFile ifile) {
		generatedMap.remove(ifile);
		for (Set<IFile> gens : generatedMap.values())
			gens.remove(ifile);
	}
	
	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data)
			throws CoreException {
		super.setInitializationData(config, propertyName, data);
		System.out.println("DolmenBuilder.setInitializationData");
	}

	@Override
	protected void startupOnInitialize() {
		super.startupOnInitialize();
		System.out.println("DolmenBuilder.startupOnInitialize");
		// TODO Retrieve project-wide preferences or something like that?
	}

	@Override
	public ISchedulingRule getRule(int kind, Map<String, String> args) {
		return super.getRule(kind, args);
	}

	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor)
			throws CoreException {
		System.out.println("Dolmen builder being called!");
		switch (kind) {
		case FULL_BUILD:
			fullBuild(monitor);
			break;
		case INCREMENTAL_BUILD:
		case AUTO_BUILD:
			IResourceDelta delta = getDelta(getProject());
			if (delta == null)
				fullBuild(monitor);
			else
				incrementalBuild(delta, monitor);
			break;
		case CLEAN_BUILD:
			clean(monitor);
			break;
		default:
			System.err.println("Unknown build kind (ignoring): " + kind);
		}
		// Refresh the whole project
		getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
		// No project dependencies for the Dolmen builder
		return null;
	}

	/**
	 * Performs a full build on this project
	 * 
	 * @param monitor
	 * @throws CoreException
	 */
	private void fullBuild(final IProgressMonitor monitor) throws CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor);
		System.out.println("FULL BUILD");
		getProject().accept(this.new ResourceVisitor(subMonitor));
	}
	
	private /* non-static */ class ResourceVisitor implements IResourceVisitor {
		private final SubMonitor monitor;
		
		ResourceVisitor(SubMonitor monitor) {
			this.monitor = monitor;
		}
		
		@Override
		public boolean visit(IResource resource) throws CoreException {
			return Builder.this.visit(resource, monitor);
		}
	}
	
	/**
	 * Performs an incremental build, described by the given {@code delta}
	 * 
	 * @param delta
	 * @param monitor
	 * @throws CoreException
	 */
	private void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor)
			throws CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor);
		System.out.println("INCREMENTAL BUILD");
		System.out.println(delta.toString());
		delta.accept(new DeltaVisitor(subMonitor));
	}
	
	private /* non-static */ class DeltaVisitor implements IResourceDeltaVisitor {
		private final SubMonitor monitor;
		
		DeltaVisitor(SubMonitor monitor) {
			this.monitor = monitor;
		}
		
		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
			case IResourceDelta.CHANGED:
				// Added or changed resource are handled in a similar way
				return Builder.this.visit(delta.getResource(), monitor);
			case IResourceDelta.REMOVED: {
				// We are only really interested in removed _files_ and
				// when a folder is removed, the files are also in the
				// builder's delta so we can safely ignore folders and
				// handle files only
				if (delta.getResource().getType() == IResource.FILE)
					removedResource((IFile) (delta.getResource()), monitor);
				return true;
			}
			default:
				return false;
			}			
		}
	}
	
	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		System.out.println("DolmenBuilder.clean");
		super.clean(monitor);
		IResource[] members = getProject().members();
		SubMonitor subMonitor = SubMonitor.convert(monitor, members.length);
		for (IResource resource : getProject().members())
			clean(resource, subMonitor.newChild(1));
	}

	/**
	 * Applies the builder to the given resource (either as part of a
	 * full build, or if the resource has been added or changed in case
	 * of an incremental build)
	 * 
	 * @param res
	 * @param monitor
	 * @return {@code true} if the children of the given resource must
	 * 	be visited as well
	 * @throws CoreException
	 */
	private boolean visit(IResource res, SubMonitor monitor)
			throws CoreException {
		switch (Utils.IResourceKind.of(res)) {
		case ROOT:
		case PROJECT:
			return true;
		case FOLDER:
			return true;
		case FILE: {
			final IFile ifile = (IFile) res;
			String extension = ifile.getFileExtension();
			if ("jl".equals(extension)) {
				List<IFile> generated =
					new JLCompile(getLoggingStream(), monitor)
						.compile(getProject(), ifile);
				for (IFile gen : generated)
					add(ifile, gen);
			} else if ("jg".equals(extension)) {
				List<IFile> generated =
					new JGCompile(getLoggingStream(), monitor)
						.compile(getProject(), ifile);
				for (IFile gen : generated)
					add(ifile, gen);		
			}
			return true;
		}
		}
		throw new IllegalStateException();
	}
	
	/**
	 * Handles the fact that the given file resource has been removed,
	 * potentially deleting associated derived resources
	 * 
	 * @param ifile
	 * @param monitor
	 * @throws CoreException
	 */
	private void removedResource(IFile ifile, SubMonitor monitor) 
			throws CoreException {
		// Delete the resources derived from ifile, if any
		Set<IFile> gens = generatedMap.getOrDefault(ifile, Collections.emptySet());
		for (IFile gen : gens)
			gen.delete(IResource.KEEP_HISTORY, monitor);
		// Track the fact the resource has been removed
		remove(ifile);
	}
	
	/**
	 * Cleans the given resource, i.e. removes it if the file
	 * has been generated by Dolmen and removes all potential
	 * Dolmen problem markers
	 * 
	 * @param resource	should be a folder or a file
	 * @param monitor
	 * @throws CoreException
	 */
	private void clean(IResource resource, SubMonitor monitor)
			throws CoreException {
		switch (Utils.IResourceKind.of(resource)) {
		case ROOT:
		case PROJECT:
			return;
		case FOLDER: {
			final IFolder ifolder = (IFolder) resource;
			for (IResource res : ifolder.members())
				clean(res, monitor);
			return;
		}
		case FILE: {
			final IFile ifile = (IFile) resource;
			// If the file has been generated by Dolmen, it must be deleted
			if (Utils.isDolmenGenerated(ifile)) {
				remove(ifile);
				ifile.delete(IResource.KEEP_HISTORY, monitor);
			}
			// Otherwise, if the file could hold Dolmen problems, they
			// must be deleted
			if (Utils.isDolmenResource(ifile))
				Marker.deleteAll(ifile);
			return;
		}
		}
	}
	
	/**
	 * @return a stream where the builders can log their progression
	 * 	(typically, a console, or the standard output)
	 */
	private PrintStream getLoggingStream() {
		return new PrintStream(Console.findDolmenConsole().newMessageStream());
	}
	
}
