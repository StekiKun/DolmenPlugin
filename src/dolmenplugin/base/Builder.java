package dolmenplugin.base;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.stekikun.dolmen.codegen.SourceMapping;
import org.stekikun.dolmen.common.Iterables;
import org.stekikun.dolmen.common.Nulls;

import dolmenplugin.builders.JGCompile;
import dolmenplugin.builders.JLCompile;

/**
 * ...
 * 
 * @author Stéphane Lescuyer
 */
public final class Builder extends IncrementalProjectBuilder 
	implements IResourceChangeListener {
	
	/**
	 * ID of Dolmen builder (keep it sync with plugin.xml)
	 */
	public static final String ID = "dolmenplugin.base.Builder";

	/**
	 * The builder should only be used in a project with the 
	 * Java nature. The Java project interface is fetched during
	 * the builder initialization.
	 */
	private IJavaProject javaProject;
	
	/**
	 * The builder will keep track of the various resources which
	 * are generated, associated to the Dolmen resource that they
	 * have been generated from and their known source mapping
	 */
	private final Map<IFile, Map<IFile, SourceMapping>> generatedMap;
	
	/**
	 * The builder will listen to all marker changes from the JDT
	 * in resources generated by Dolmen and copy them back into the
	 * Dolmen source file if it belongs to a source mapping. We keep
	 * the map from JDT markers to the computed origin in the source
	 * mapping.
	 */
	private final Map<IMarker, Forwarding> forwardedMarkers;
	
	/**
	 * For every origin corresponding to a forwarded marker from a 
	 * generated JDT resource, we keep a single Dolmen marker for
	 * all the origins that represent the same region. These are
	 * given by {@link #markersByOrigin}. The set of different
	 * rule names for which some origin has been reported is
	 * recorded in {@link #ruleNamesByOrigin}, with the multiplicity
	 * with which any single rule name appears. These two maps rely
	 * on the fact that rule names are ignored when comparing origins.
	 */
	private final Map<Forwarding,
		Map<@Nullable String, Integer>> ruleNamesByOrigin;
	private final Map<Forwarding, IMarker> markersByOrigin;
	
	public Builder() {
		this.generatedMap = new HashMap<>();
		this.forwardedMarkers = new HashMap<>();
		this.ruleNamesByOrigin = new HashMap<>();
		this.markersByOrigin = new HashMap<>();
		this.javaProject = null;
	}

	/**
	 * Register that {@code generated} has been generated
	 * for the Dolmen file {@code dolmen} with the associated
	 * source mappings
	 * 
	 * @param dolmen
	 * @param generated
	 * @param smap
	 */
	private void add(IFile dolmen, IFile generated, SourceMapping smap) {
		Map<IFile, SourceMapping> gens = generatedMap.get(dolmen);
		if (gens == null) {
			gens = new HashMap<>();
			generatedMap.put(dolmen, gens);
		}
		gens.put(generated, smap);
	}
	
	/**
	 * Registers that the resource {@code ifile} has been
	 * removed
	 * 
	 * @param ifile
	 */
	private void remove(IFile ifile) {
		generatedMap.remove(ifile);
		for (Map<IFile, SourceMapping> gens : generatedMap.values())
			gens.remove(ifile);
	}
	
	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data)
			throws CoreException {
		super.setInitializationData(config, propertyName, data);
	}

	@Override
	protected void startupOnInitialize() {
		super.startupOnInitialize();
		
		IProject project = getProject();
		javaProject = JavaCore.create(project);
		ResourcesPlugin.getWorkspace()
			.addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
	}
	
	protected void dispose() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		IMarkerDelta[] deltas = 
			event.findMarkerDeltas(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true);
		
		ISchedulingRule schedulingRule = null;
		ArrayList<MarkerJob> addedMarkerJobs = new ArrayList<>();
		ArrayList<MarkerJob> removedMarkerJobs = new ArrayList<>();
		
		for (IMarkerDelta delta : deltas) {
			// Find out if delta is associated to resource
			// generated by Dolmen
			IResource res = delta.getResource();
			IFile dolmenRes = null;
			SourceMapping smap = null;
			for (Map.Entry<IFile, Map<IFile, SourceMapping>> entry 
					: generatedMap.entrySet()) {
				smap = entry.getValue().get(res);
				if (smap != null) {
					dolmenRes = entry.getKey();
					break;
				}
			}
			if (smap == null) continue;
			
			// Handle the marker
			IMarker jdtMarker = delta.getMarker();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED: {
				log("ADDED marker " + delta.getId() + " on " + delta.getResource());
				addedMarkerJobs.add(addMarker(jdtMarker, dolmenRes, smap));
				break;
			}
			case IResourceDelta.CHANGED: {
				log("CHANGED marker " + delta.getId() + " on " + delta.getResource());
				addedMarkerJobs.add(addMarker(jdtMarker, dolmenRes, smap));
				break;
			}
			case IResourceDelta.REMOVED: {
				log("REMOVED marker " + delta.getId() + " on " + delta.getResource());
				removedMarkerJobs.add(removeMarker(jdtMarker));
				break;
			}
			}
			
			// Add the resource to the scheduling rule
			schedulingRule = MultiRule.combine(schedulingRule, res);
		}
		
		if (addedMarkerJobs.isEmpty() && removedMarkerJobs.isEmpty()) return;
		
		WorkspaceJob job = new WorkspaceJob("Copying JDT markers to Dolmen") {			
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
				// Do the deletion first, and the changes/additions next
				for (MarkerJob job : Iterables.concat(removedMarkerJobs, addedMarkerJobs))
					job.run(monitor);
				forwardingsSanityCheck();
				return Status.OK_STATUS;
			}
		}; 
		job.setRule(schedulingRule);
		job.schedule();
	}
	
	@Override
	public ISchedulingRule getRule(int kind, Map<String, String> args) {
		return super.getRule(kind, args);
	}

	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor)
			throws CoreException {
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
			logErr("Unknown build kind (ignoring): " + kind);
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
		log("FULL BUILD");
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
		displayDelta(null, delta);
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
		log("DolmenBuilder.clean");
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
		case FOLDER: {
			// Try and avoid visiting the output folder
			// because the JDT builder will keep copying our lexer
			// and grammar descriptions to the Java output folder and
			// if we don't pay attention, we will generate Java files
			// in there as well.
			// NB: Other specific output folders may be specified for
			//  in the project's configuration, so avoiding the default
			//  output folder is not enough in general.
			IPath outputFolder = javaProject.getOutputLocation();
			if (res.getProjectRelativePath().equals(outputFolder))
				return false;
			return true;
		}
		case FILE: {
			final IFile ifile = (IFile) res;
			String extension = ifile.getFileExtension();
			// Avoid checking classpaths for .java files, of which 
			// there may be many and which we always want to ignore
			if (!"jl".equals(extension) && !"jg".equals(extension)) 
				return false;
			// Users should not explicitly try to exclude .jl/.jg files
			// from the classpath to prevent JDT from copying them, because
			// we deal with it in the builder.
			// This should also take care of all files in all output folders,
			// unless output folders and classpath entries are not exclusive?
			if (!javaProject.isOnClasspath(res)) return false;
			
			if ("jl".equals(extension)) {
				Map<IFile, SourceMapping> generated =
					new JLCompile(getLoggingStream(), monitor)
						.compile(getProject(), ifile);
				generated.forEach((gen, smap) -> add(ifile, gen, smap));
			} else if ("jg".equals(extension)) {
				Map<IFile, SourceMapping> generated =
					new JGCompile(getLoggingStream(), monitor)
						.compile(getProject(), ifile);
				generated.forEach((gen, smap) -> add(ifile, gen, smap));
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
		Map<IFile, SourceMapping> gens =
			generatedMap.getOrDefault(ifile, Collections.emptyMap());
		for (IFile gen : gens.keySet())
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
	
	/**
	 * Class to pretty-print a resource delta
	 * 
	 * @author Stéphane Lescuyer
	 */
	private static class DeltaDisplayer {
		private final PrintStream out;
		private int indent = 0;
		
		DeltaDisplayer(PrintStream out) {
			this.out = out;
		}
		
		private void println(String line) {
			for (int i = 0; i < indent; i++)
				out.append(' ');
			out.append(line);
			out.append("\n");
		}
		
		private void open() {
			indent += 2;
		}
		
		private void close() {
			indent -= 2;
		}
		
		private String kindToString(int k) {
			switch (k) {
			case IResourceDelta.ADDED:
				return " [ADDED] ";
			case IResourceDelta.CHANGED:
				return " [CHANGED] ";
			case IResourceDelta.REMOVED:
				return " [REMOVED] ";
			default:
				return " [?" + k + "?] ";
			}
		}
		
		void display(IResourceDelta delta) {
			IResourceDelta[] children = delta.getAffectedChildren();
			if (children.length == 0) {
				println(delta.getResource() + kindToString(delta.getKind()));
				return;
			}
			println(delta.getResource() + kindToString(delta.getKind())
					+ "{");
			open();
			for (IResourceDelta child : children)
				display(child);
			close();
			println("}");
		}
	}
	
	/**
	 * Pretty-prints the given resource delta to the given stream
	 * @param out
	 * @param delta
	 */
	private void displayDelta(PrintStream out, IResourceDelta delta) {
		if (out != null) 
			new DeltaDisplayer(out).display(delta);
	}
	
	private static boolean debug = false;
	
	/**
	 * Logs some message to standard output, if {@link #debug} is {@code true}
	 * @param s
	 */
	private void log(String s) {
		if (!debug) return;
		System.out.println(s);
	}

	/**
	 * Logs some message to standard error output, if {@link #debug} is {@code true}
	 * @param s
	 */
	private void logErr(String s) {
		if (!debug) return;
		System.err.println(s);
	}
	
	/**
	 * Container class packing together an origin for a forwarded
	 * marker and the associated original JDT message. It also
	 * specifies the resource in which the marker is forwarded in
	 * case there are several Dolmen resources with similar markers
	 * at the same positions.
	 * 
	 * @author Stéphane Lescuyer
	 */
	private static final class Forwarding { // implements Comparable<Forwarding> {
		final IFile dolmenResource;
		final SourceMapping.Origin origin;
		final String message;
		
		Forwarding(IFile dolmenResource, SourceMapping.Origin origin, String message) {
			this.dolmenResource = dolmenResource;
			this.origin = origin;
			this.message = message;
		}

		@Override
		public int hashCode() {
			int result = dolmenResource.hashCode();
			result = 31 * result + origin.hashCode();
			result = 31 * result + message.hashCode();
			return result;
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Forwarding)) return false;
			Forwarding fwd = (Forwarding) o;
			if (!(dolmenResource.equals(fwd.dolmenResource))) return false;
			if (!(message.equals(fwd.message))) return false;
			if (!(origin.equals(fwd.origin))) return false;
			return true;
		}
		
		@Override
		public String toString() {
			return "[origin=" + origin +", msg=" + message + "]";
		}
	}
	
	/**
	 * Common class for all atomic jobs scheduled when
	 * listening to JDT problem markers
	 * 
	 * @author Stéphane Lescuyer
	 */
	private abstract class MarkerJob implements IJobFunction {
		@Override
		public abstract IStatus run(IProgressMonitor monitor);
	}

	/**
	 * @param jdtMarker
	 * @return a marker job for when the given JDT marker is
	 * 	removed, which removes the corresponding copied marker
	 * 	in Dolmen if any
	 */
	MarkerJob removeMarker(IMarker jdtMarker) {
		return new MarkerJob() {
			@Override
			public IStatus run(IProgressMonitor monitor) {
				@Nullable Forwarding fwd = forwardedMarkers.get(jdtMarker);
				if (fwd == null) return Status.OK_STATUS;
				
				log("[==> Remove marker " + jdtMarker.getId() + "]");
				// Remove this origin's rule and if it was
				// the last one, remove the forwarded marker as well
				SourceMapping.Origin origin = fwd.origin;
				Map<@Nullable String, Integer> rules = Nulls.ok(ruleNamesByOrigin.get(fwd));
				Integer count = Nulls.ok(rules.get(origin.ruleName));
				if (count == 1)
					rules.remove(origin.ruleName);
				else
					rules.replace(origin.ruleName, count - 1);
				if (rules.isEmpty()) {
					ruleNamesByOrigin.remove(fwd);
					IMarker dolmenMarker = Nulls.ok(markersByOrigin.remove(fwd));
					try {
						dolmenMarker.delete();
					} catch (CoreException e) {
						e.printStackTrace();
					}
					log("Deleted Dolmen marker " + dolmenMarker.getId() + " at " + fwd);
				}
				forwardedMarkers.remove(jdtMarker);
				log("[<== Remove marker " + jdtMarker.getId() + "]");
				
				return Status.OK_STATUS;
			}
		};
	}
	
	/**
	 * @param jdtMarker
	 * @param dolmenRes
	 * @param smap
	 * @return a marker job which reacts to the change or creation of
	 * 	the given JDT problem marker by copying it on {@code dolmenRes}
	 * 	if the marker's position can be mapped via the given source mapping
	 */
	MarkerJob addMarker(IMarker jdtMarker, IFile dolmenRes, SourceMapping smap) {
		return new MarkerJob() {
			@Override
			public IStatus run(IProgressMonitor monitor) {
				int start = jdtMarker.getAttribute(IMarker.CHAR_START, -1);
				int end = jdtMarker.getAttribute(IMarker.CHAR_END, -1);
				int length = end - start;
				SourceMapping.@Nullable Origin origin = smap.map(start, length);
				if (origin == null) return Status.OK_STATUS;

				log("[==> Add marker " + jdtMarker + "]");
				// Look for an existing marker at this origin
				final String jdtMessage = 
					jdtMarker.getAttribute(IMarker.MESSAGE, "<no message>");
				final Forwarding fwd = new Forwarding(dolmenRes, origin, jdtMessage);
				forwardedMarkers.put(jdtMarker, fwd);
				@Nullable IMarker dolmenMarker = markersByOrigin.get(fwd);
				// It may be that the marker has been deleted by the builder directly
				// in which case we consider it as if it needed to be created
				if (dolmenMarker != null && !dolmenMarker.exists()) {
					dolmenMarker = null;
					// we could remove(fwd) from ruleNamesByOrigin but this is going
					// to be done below with a forced replacement
				}
				final Map<@Nullable String, Integer> rules;
				if (dolmenMarker == null) {
					// Forward the marker, record the origin
					dolmenMarker = Marker.copyFromJDT(dolmenRes, jdtMarker, origin);
					log("Created Dolmen marker " + dolmenMarker.getId() + " at " + fwd);
					rules = new HashMap<>();
					rules.put(origin.ruleName, 1);
					ruleNamesByOrigin.put(fwd, rules);
					markersByOrigin.put(fwd, dolmenMarker);
				}
				else {
					// Extend the existing marker with the new origin
					rules = Nulls.ok(ruleNamesByOrigin.get(fwd));
					@Nullable Integer count = rules.get(origin.ruleName);
					rules.put(origin.ruleName, count == null ? 1 : count + 1);
				}
				Marker.updateMessage(dolmenMarker, jdtMessage, rules);
				log("[<== Add marker " + jdtMarker + "]");

				return Status.OK_STATUS;
			}
		};
	}
	
	private void forwardingsSanityCheck() {
		for (Map.Entry<IMarker, Forwarding> entry : forwardedMarkers.entrySet()) {
			IMarker jdtMarker = entry.getKey();
			Forwarding fwd = entry.getValue();
			if (!jdtMarker.exists())
				logErr("JDT marker " + jdtMarker + " is registered to " + fwd + " but does not exist anymore");

			@Nullable  Map<@Nullable String, Integer> rules = ruleNamesByOrigin.get(fwd);
			@Nullable IMarker dolmenMarker = markersByOrigin.get(fwd);
			if (rules == null)
				logErr("Rules for " + fwd + " (associated to " + jdtMarker + ") are missing");
			else {
				rules.forEach((rule, count) -> {
					if (count <= 0)
						logErr("Count for rule " + Objects.toString(rule) + " is invalid: " + count);
				});
			}
			if (dolmenMarker == null)
				logErr("Dolmen marker " + fwd + " (associated to " + jdtMarker + ") is missing");
			else {
				if (!dolmenMarker.exists())
					logErr("Dolmen marker " + fwd + " (associated to " + jdtMarker + ") does not exist anymore");
			}
		}
	}
}
