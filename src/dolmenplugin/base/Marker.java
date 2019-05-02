package dolmenplugin.base;

import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;
import org.stekikun.dolmen.codegen.SourceMapping;
import org.stekikun.dolmen.syntax.IReport;

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
	 * Marker attribute name for HTML messages in Dolmen markers
	 */
	public static final String DOLMEN_MARKER_HTML_MESSAGE = "dolmenplugin.base.marker.htmlMessage";
	
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
			jdtMarker.getAttributes().forEach((s, o) -> {
				try {
					jdtProblem.setAttribute(s, o);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			});
			// Override the positional and source attributes
			jdtProblem.setAttribute(IMarker.SOURCE_ID, JDT_SOURCE_ID);
			jdtProblem.setAttribute(IMarker.CHAR_START, origin.offset);
			jdtProblem.setAttribute(IMarker.CHAR_END, origin.offset + origin.length);
			return jdtProblem;
		} catch (CoreException e1) {
			e1.printStackTrace();
			return null;
		}
	}

	/**
	 * Updates the {@linkplain IMarker#MESSAGE message} attribute in
	 * {@code dolmenMarker} based on the set of rule names {@code rules}
	 * that it has been forwarded from
	 * 
	 * @param dolmenMarker
	 * @param jdtMarkerMessage 	the original problem message in the JDT
	 * @param rules
	 */
	public static void updateMessage(IMarker dolmenMarker, 
			String jdtMarkerMessage, Map<@Nullable String, Integer> rules) {
		StringBuilder html = new StringBuilder();
		StringBuilder msg = new StringBuilder();
		msg.append("[Java Problem] ").append(jdtMarkerMessage);
		html.append("<b>[Java Problem]</b> ").append(escapeHtml(jdtMarkerMessage));
		
		if (rules.size() == 1) {
			Map.Entry<@Nullable String, Integer> only = rules.entrySet().iterator().next();
			// When no instantiation we don't add anything
			if (only.getKey() != null) {
				msg.append(" (in rule ").append(only.getKey()).append(")");
				html.append(" <i>(in rule ")
					.append(escapeHtml(only.getKey()))
					.append(")</i>");
			}
		}
		else {
			int sz = rules.size();
			// Find the most frequent rule among those reporting the problem
			// To break ties, use the shortest rule name
			Map.Entry<@Nullable String, Integer> mostFrequent = 
				rules.entrySet().iterator().next();
			for (Map.Entry<@Nullable String, Integer> entry : rules.entrySet()) {
				if (entry.getValue() > mostFrequent.getValue())
					mostFrequent = entry;
				else if (entry.getValue() == mostFrequent.getValue()
					&& entry.getKey().length() < mostFrequent.getKey().length()) {
					mostFrequent = entry;
				}
			}
			msg.append("  (in rule ").append(mostFrequent.getKey());
			msg.append(" and ").append(sz - 1)
				.append(sz > 2 ? " others)" : " other)");
			html.append("  <i>(in rule ")
				.append(escapeHtml(mostFrequent.getKey()))
				.append(" and ").append(sz - 1)
				.append(sz > 2 ? " others)" : " other)").append("</i>");
		}
		
		// Update the message attribute
		try {
			dolmenMarker.setAttribute(IMarker.MESSAGE, msg.toString());
			dolmenMarker.setAttribute(DOLMEN_MARKER_HTML_MESSAGE, html.toString());
		} catch (CoreException e) {
			e.printStackTrace();
			return;
		}
	}

	/**
	 * @param s
	 * @return {@code s} where characters reserved in HTML have been escaped
	 */
	public static String escapeHtml(String s) {
		StringBuilder buf = new StringBuilder(s.length() + 5);
		s.chars().forEach(c -> {
			switch (c) {
			case '&':	buf.append("&amp"); break;
			case '"':   buf.append("&quot;"); break;
			case '<':	buf.append("&lt;"); break;
			case '>':   buf.append("&gt;"); break;
			default:
				buf.append((char)c);
			}
		});
		return buf.toString();
	}
	
}
