package dolmenplugin.base;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

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

}
