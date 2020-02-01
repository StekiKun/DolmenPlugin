package org.stekikun.dolmenplugin.editors;

import java.util.function.Supplier;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.stekikun.dolmenplugin.Activator;

/**
 * Common implementation for all actions which enable/disable
 * a filter in Dolmen's outline views
 * 
 * @author Stéphane Lescuyer
 */
public final class OutlineFilterAction extends Action {

	// Need to check that the action gets disposed along with
	// the outline view, otherwise this may keep the viewer in the heap
	private final TreeViewer viewer;
	private final Supplier<ViewerFilter> filterSupplier;
	private ViewerFilter filter;
	
	private final String tooltipText;
	private final ImageDescriptor imageDescriptor;
	
	public OutlineFilterAction(
			TreeViewer viewer, 	Supplier<ViewerFilter> filterSupplier, 
			String tooltipText, String imagePath) {
		this.viewer = viewer;
		this.filterSupplier = filterSupplier;
		this.filter = null;
		this.tooltipText = tooltipText;
		this.imageDescriptor = Activator.getImageDescriptor(imagePath);
	}
	
	@Override
	public ImageDescriptor getDisabledImageDescriptor() {
		return imageDescriptor;
	}

	@Override
	public String getId() {
		return null;	// Should I add an ID?
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return imageDescriptor;
	}

	@Override
	public int getStyle() {
		return AS_CHECK_BOX;
	}

	@Override
	public String getText() {
		return tooltipText;
	}

	@Override
	public String getToolTipText() {
		return tooltipText;
	}

	@Override
	public synchronized void run() {
		if (isChecked()) {
			if (filter == null)
				filter = filterSupplier.get();
			viewer.addFilter(filter);
		}
		else {
			viewer.removeFilter(filter);
		}
	}
	
}
