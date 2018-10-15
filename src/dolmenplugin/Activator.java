package dolmenplugin;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "DolmenPlugin"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;
	
	/**
	 * The constructor
	 */
	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
	
	/**
	 * <i>This uses the plug-in's own {@link ImageRegistry image registry}
	 *  to share instances of images across the plug-in and automatically
	 *  perform resource management when the plug-in is closed. 
	 * </i>
	 * 
	 * @param path
	 * @return the image at the given plug-in relative path
	 */
	public static Image getImage(String path) {
		ImageRegistry registry = getDefault().getImageRegistry();
		Image image = registry.get(path);
		if (image == null) {
			image = getImageDescriptor(path).createImage();
			registry.put(path, image);
		}		
		return image;
	}

	/**
	 * 
	 * @param base	the plug-in relative path to the base image
	 * @param quadrant	any of {@link IDecoration#TOP_LEFT}, {@link IDecoration#TOP_RIGHT},
	 * 		{@link IDecoration#BOTTOM_LEFT}, {@link IDecoration#BOTTOM_RIGHT} and
	 * 		{@link IDecoration#UNDERLAY}
	 * @param overlay	the plug-in relative path to the image to use as an overlay
	 * @return image composed of {@code base} and {@code overlay} at the position
	 * 	described by {@link quadrant}
	 */
	public static Image getImage(String base, int quadrant, String overlay) {
		String key = base + ":" + quadrant + ":" + overlay;
		ImageRegistry registry = getDefault().getImageRegistry();
		Image image = registry.get(key);
		if (image == null) {
			ImageDescriptor descr = new DecorationOverlayIcon(
				getImageDescriptor(base), getImageDescriptor(overlay), quadrant);
			image = descr.createImage();
			registry.put(key, image);
		}
		return image;
	}
	
}