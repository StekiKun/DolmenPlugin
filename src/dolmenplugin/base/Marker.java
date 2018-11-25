package dolmenplugin.base;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import codegen.SourceMapping;
import syntax.IReport;

/**
 * Utility class for Dolmen problem markers
 * 
 * @author Stéphane Lescuyer
 */
public final class Marker {

	/**
	 * ID of Dolmen problem marker (keep it sync with plugin.xml)
	 */
	public static final String ID = "dolmenplugin.base.marker";
	
	/**
	 * Marker source value for markers created from Dolmen reports
	 */
	private static final String DOLMEN_SOURCE_ID = "Dolmen";
	
	/**
	 * Marker source value for markers created from JDT markers
	 */
	private static final String JDT_SOURCE_ID = "JDT";

	/**
	 * Whether source mappings should be turned into markers
	 */
	private static boolean markSourceMappings = false;
	
	private Marker() {
		// Static utility only
	}

	/**
	 * Deletes all Dolmen markers associated with the given resource
	 * 
	 * @param res
	 */
	public static void deleteAll(IResource res) {
		try {
			res.deleteMarkers(Marker.ID, true, IResource.DEPTH_ZERO);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Adds a Dolmen marker to the given resource {@code res}, with
	 * the provided attributes
	 * 
	 * @param res
	 * @param message	the message of the marker
	 * @param severity  the severity of the marker
	 * @param line		the line number
	 * @param start		the start offset
	 * @param end		the end offset
	 */
	public static void add(IResource res, String message, int severity,
			int line, int start, int end) {
		try {
			IMarker report = res.createMarker(Marker.ID);
			report.setAttribute(IMarker.SOURCE_ID, DOLMEN_SOURCE_ID);
			report.setAttribute(IMarker.MESSAGE, message);
			report.setAttribute(IMarker.SEVERITY, severity);
			report.setAttribute(IMarker.LINE_NUMBER, line);
			report.setAttribute(IMarker.CHAR_START, start);
			report.setAttribute(IMarker.CHAR_END, end);
		} catch (CoreException e1) {
			e1.printStackTrace();
		}
	}
		
	/**
	 * Same as {@link #add(IResource, String, int, int, int, int)} but
	 * uses the severity {@link IMarker#SEVERITY_ERROR}
	 * @param res
	 * @param message
	 * @param line
	 * @param start
	 * @param end
	 */
	public static void addError(IResource res, String message,
		int line, int start, int end) {
		add(res, message, IMarker.SEVERITY_ERROR, line, start, end);
	}

	/**
	 * Same as {@link #add(IResource, String, int, int, int, int)} but
	 * uses the severity {@link IMarker#SEVERITY_WARNING}
	 * @param res
	 * @param message
	 * @param line
	 * @param start
	 * @param end
	 */
	public static void addWarning(IResource res, String message,
		int line, int start, int end) {
		add(res, message, IMarker.SEVERITY_WARNING, line, start, end);
	}
	
	/**
	 * Adds a Dolmen marker to the given resource {@code res} based
	 * on the given {@code report}
	 * @param res
	 * @param report
	 */
	public static void add(IResource res, IReport report) {
		int sev = IMarker.SEVERITY_ERROR;
		switch (report.getSeverity()) {
		case ERROR: sev = IMarker.SEVERITY_ERROR; break;
		case WARNING: sev = IMarker.SEVERITY_WARNING; break;
		case LOG: sev = IMarker.SEVERITY_INFO; break;
		}
		
		add(res, report.getMessage(), sev,
			report.getLine(), report.getOffset(), report.getOffset() + report.getLength());
	}
	
	/**
	 * Adds a Dolmen marker to the given resource {@code res} for
	 * each of the given {@code reports}
	 * @param res
	 * @param reports
	 */
	public static void addAll(IResource res, Iterable<? extends IReport> reports) {
		for (IReport report : reports)
			add(res, report);
	}

	private static void addMapping(IFile res, SourceMapping.Mapping mapping) {
		try {
			IMarker report = res.createMarker(Marker.ID);
			report.setAttribute(IMarker.MESSAGE, mapping.toString());
			report.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
			report.setAttribute(IMarker.CHAR_START, mapping.offset);
			report.setAttribute(IMarker.CHAR_END, mapping.offset + mapping.length);
			report.setAttribute(IMarker.TRANSIENT, true);
		} catch (CoreException e1) {
			e1.printStackTrace();
		}
	}
	
	public static void addMappings(IFile res, SourceMapping smap) {
		if (markSourceMappings)
			smap.forEach(c -> addMapping(res, c));
	}

	/**
	 * Adds a Dolmen marker mimicking a JDT problem marker to the given 
	 * resource {@code res}, with the same attributes as the given
	 * {@code jdtMarker} and the positional attributes overriden
	 * by the given parameters
	 * 
	 * @param res
	 * @param jdtMarker	the marker to copy
	 * @param origin	the origin of the marker's region in {@code res}
	 */
	public static IMarker copyFromJDT(IResource res, IMarker jdtMarker,
			SourceMapping.Origin origin) {
		try {
			IMarker jdtProblem = res.createMarker(Marker.ID);
			// TODO: better display of replacements in message or other attribute
			// TODO: avoid adding different instantiations of the same problem
			//		to the exact same source region
			jdtMarker.getAttributes().forEach((s, o) -> {
				try {
					// Tweak message to show its true origin
					if (IMarker.MESSAGE.equals(s)) {
						StringBuilder msg = new StringBuilder();
						msg.append("[Java Problem] ").append(o);
						if (!origin.replacements.isEmpty()) {
							msg.append("\nWith instances:\n");
							origin.replacements.forEach((hole, rep) -> {
								msg.append(" - ").append(hole)
									.append(" -> ").append(rep);
							});
						}
						jdtProblem.setAttribute(s, msg.toString());
					}
					else 
						jdtProblem.setAttribute(s, o);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			});
			// Override the positional and source attributes
			jdtProblem.setAttribute(IMarker.SOURCE_ID, JDT_SOURCE_ID);
			// jdtProblem.setAttribute(IMarker.LINE_NUMBER, line);
			jdtProblem.setAttribute(IMarker.CHAR_START, origin.offset);
			jdtProblem.setAttribute(IMarker.CHAR_END, origin.offset + origin.length);
			return jdtProblem;
		} catch (CoreException e1) {
			e1.printStackTrace();
			return null;
		}
	}
	
}
