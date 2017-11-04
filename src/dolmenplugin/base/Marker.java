package dolmenplugin.base;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

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
	public static void delete(IResource res) {
		try {
			res.deleteMarkers(Marker.ID, true, IResource.DEPTH_ZERO);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
}
