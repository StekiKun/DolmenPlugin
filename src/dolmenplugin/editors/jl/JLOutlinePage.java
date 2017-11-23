package dolmenplugin.editors.jl;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import syntax.Lexer;

/**
 * WIP
 * TODO: document
 * 
 * @author Stéphane Lescuyer
 */
public class JLOutlinePage extends ContentOutlinePage {

	private final JLEditor editor;
	private Object input;
	
	public JLOutlinePage(JLEditor editor) {
		super();
		this.editor = editor;
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);

		TreeViewer viewer = getTreeViewer();
		JLContentProvider provider = this.new JLContentProvider();
		viewer.setContentProvider(provider);
		viewer.setLabelProvider(provider);
		viewer.addSelectionChangedListener(this);

		if (input != null)
			viewer.setInput(input);
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
	            viewer.expandAll();
	            control.setRedraw(true);
	        }
	    }
	}
	
	private boolean acceptable(Object input) {
		if (input instanceof Lexer) return true;
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
	    if (element instanceof JLOutlineNode) {
	    	JLOutlineNode node = (JLOutlineNode) element;
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
	
	public final class JLContentProvider
		extends LabelProvider implements ITreeContentProvider {
		
		@Override
		public Image getImage(Object element) {
			if (element instanceof JLOutlineNode)
				return ((JLOutlineNode) element).getImage();
			return null;
		}

		@Override
		public String getText(Object element) {
			if (element instanceof JLOutlineNode)
				return ((JLOutlineNode) element).getText(editor.getDocument());
			return null;
		}

		@Override
		public Object[] getElements(Object inputElement) {
			return JLOutlineNode.roots(inputElement).toArray();
		}

		private final Object[] NO_CHILDREN = new Object[0];
		@Override
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof JLOutlineNode)
				return ((JLOutlineNode) parentElement).getChildren();
			return NO_CHILDREN;
		}

		@Override
		public Object getParent(Object element) {
			// Shall I built something doubly-linked?
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			if (element instanceof JLOutlineNode)
				return ((JLOutlineNode) element).hasChildren();
			return false;
		}

	}
}