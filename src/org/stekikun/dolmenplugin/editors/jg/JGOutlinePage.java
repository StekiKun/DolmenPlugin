package org.stekikun.dolmenplugin.editors.jg;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;
import org.stekikun.dolmen.syntax.PGrammar;
import org.stekikun.dolmenplugin.base.Images;
import org.stekikun.dolmenplugin.editors.OutlineFilterAction;
import org.stekikun.dolmenplugin.editors.OutlineNode;

/**
 * Implementation of the <i>outline view</i> for 
 * {@link JGEditor}. It simply extends {@link ContentOutlinePage}
 * and takes care of updating the input (a grammar or an exception)
 * and handling selection.
 * <p>
 * The structured contents are described by {@link JGOutlineNode}.
 * 
 * @author Stéphane Lescuyer
 */
public class JGOutlinePage extends ContentOutlinePage {

	private final JGEditor editor;
	private Object input;
	
	public JGOutlinePage(JGEditor editor) {
		super();
		this.editor = editor;
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);

		TreeViewer viewer = getTreeViewer();
		JGContentProvider provider = this.new JGContentProvider();
		viewer.setContentProvider(provider);
		viewer.setLabelProvider(new DelegatingStyledCellLabelProvider(provider));
		viewer.addSelectionChangedListener(this);

		IToolBarManager manager = getSite().getActionBars().getToolBarManager();
		addFilterActions(manager, viewer);
		
		if (input != null)
			viewer.setInput(input);
	}
	
	private void addFilterActions(IToolBarManager manager, TreeViewer viewer) {
		manager.add(
				new OutlineFilterAction(viewer, NoTokensFilter::new,
					"Hide token declarations", Images.NO_TOKEN_DECL));
		manager.add(
				new OutlineFilterAction(viewer, ValuedTokensFilter::new,
					"Hide non-valued tokens", Images.VALUED_TOKEN_DECL));
		manager.add(
			new OutlineFilterAction(viewer, PublicRulesFilter::new,
				"Hide non-public rules", Images.PUBLIC_RULE));
	}	
	
	void setInput(Object input) {
		if (!(acceptable(input))) return;
		this.input = input;
	    TreeViewer viewer = getTreeViewer();
	    if (viewer != null)
	    {
	        Control control = viewer.getControl();
	        if (control != null && !control.isDisposed())
	        {
	            control.setRedraw(false);
	            viewer.setInput(input);
	            viewer.expandToLevel(1);
	            control.setRedraw(true);
	        }
	    }
	}
	
	private boolean acceptable(Object input) {
		if (input instanceof PGrammar) return true;
		if (input instanceof Exception) return true;
		return false;
	}
	
	@Override
	public void selectionChanged(SelectionChangedEvent event)
	{
	    super.selectionChanged(event);

	    // If the selection is empty, simply un-highlight everything
	    ISelection selection = event.getSelection();
	    if (selection.isEmpty()) {
	        editor.resetHighlightRange();
	        return;
	    }
	    
	    // Otherwise it must be a structured selection, fetch the root
	    IStructuredSelection sel = (IStructuredSelection) selection;
	    Object element = sel.getFirstElement();
	    
	    // If it is an outline node, proceed, otherwise ignore
	    if (element instanceof OutlineNode<?>) {
	    	OutlineNode<?> node = (OutlineNode<?>) element;
	    	final int start = node.getOffset();
	    	final int length = node.getLength();
	    	if (start >= 0 && length >= 0) { 
	    		try {
	    			editor.setHighlightRange(start, length, true);
	    			editor.selectAndReveal(start, length);
	    		}
	    		catch (IllegalArgumentException x) {
	    			editor.resetHighlightRange();
	    		}
	    		return;
	    	}
	    }
	    editor.resetHighlightRange();
	    return;	    
	}
	
	/**
	 * Implementation of {@link ITreeContentProvider} and 
	 * {@link IStyledLabelProvider} which delegates operations
	 * to {@link JGOutlineNode}s
	 * 
	 * @author Stéphane Lescuyer
	 */
	public final class JGContentProvider
		extends ListenerList<ILabelProviderListener>
		implements ITreeContentProvider, IStyledLabelProvider {
		
		@Override
		public Image getImage(Object element) {
			if (element instanceof OutlineNode<?>)
				return ((OutlineNode<?>) element).getImage();
			return null;
		}

		@Override
		public StyledString getStyledText(Object element) {
			if (element instanceof OutlineNode<?>)
				return ((OutlineNode<?>) element).getText(editor.getDocument());
			return null;
		}

		@Override
		public Object[] getElements(Object inputElement) {
			return JGOutlineNode.roots(inputElement).toArray();
		}

		private final Object[] NO_CHILDREN = new Object[0];
		@Override
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof OutlineNode<?>)
				return ((OutlineNode<?>) parentElement).getChildren();
			return NO_CHILDREN;
		}

		@Override
		public Object getParent(Object element) {
			// Shall I built something doubly-linked?
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			if (element instanceof OutlineNode<?>)
				return ((OutlineNode<?>) element).hasChildren();
			return false;
		}

		@Override
		public boolean isLabelProperty(Object element, String property) {
			return false;
		}
		
		@Override
		public void addListener(ILabelProviderListener listener) {
			add(listener);
		}


		@Override
		public void removeListener(ILabelProviderListener listener) {
			remove(listener);
		}

		@Override
		public void dispose() {
			clear();
		}
	}
	
	private static final class PublicRulesFilter extends ViewerFilter {
		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			if (!(element instanceof JGOutlineNode.Rule)) return true;
			JGOutlineNode.Rule rule = (JGOutlineNode.Rule) element;
			return rule.rule.visibility;
		}
	}
	
	private static final class NoTokensFilter extends ViewerFilter {
		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			return (!(element instanceof JGOutlineNode.Token));
		}
	}
	
	private static final class ValuedTokensFilter extends ViewerFilter {
		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			if (!(element instanceof JGOutlineNode.Token)) return true;
			JGOutlineNode.Token token = (JGOutlineNode.Token) element;
			return token.decl.isValued();
		}
	}
}
