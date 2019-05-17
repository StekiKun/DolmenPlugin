package org.stekikun.dolmenplugin.handlers;

import java.util.Iterator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.viewers.ISelection;
import org.stekikun.dolmenplugin.editors.DolmenEditor;

/**
 * This handler implements the command <i>Next Annotation</i>,
 * which is available in the <i>Source</i> top-level menu on
 * Dolmen editors and via the Ctrl+. shortcut.
 * <p>
 * It applies to the active editor and jumps to the next
 * annotation in the editor from the current position in
 * the editor.
 * 
 * @author Stéphane Lescuyer
 */
public class NextAnnotationHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// Find the editor where the command was executed, it must be
		// one of ours, or we simply ignore the command
	    final DolmenEditor<?> editor = HandlerUtils.findActiveDolmenEditor(event);
	    if (editor == null) return null;

	    // Find the position in the editor after which we should look for
	    // an annotation. selection to which the command should be applied
	    ISelection selection = editor.getSelectionProvider().getSelection();
	    if (!(selection instanceof ITextSelection)) return null;
	    int position = ((ITextSelection) selection).getOffset();
	    if (position < 0) return null;
	    
	    // Go through the annotations on the editor's document, trying the find
	    // the closest one after the selection
	    IAnnotationModel annotModel =
	    	editor.getDocumentProvider().getAnnotationModel(editor.getEditorInput());
	    Annotation nextAnnot = null;
	    int nextPos = Integer.MAX_VALUE;
	    int nextLength = 0;
		for (Iterator<Annotation> it = annotModel.getAnnotationIterator();
				it.hasNext(); ) {
			Annotation a = it.next();
			if (a.isMarkedDeleted()) continue;
			
			Position p = annotModel.getPosition(a);
			if (p == null) continue;
			int pos = p.getOffset();
			// skipping if pos = position ensures several successive calls
			// will keep rotating through markers
			if (pos <= position) continue;
			if (pos < nextPos) {
				nextAnnot = a;
				nextPos = pos;
				nextLength = p.getLength();
			}
		}

		// Finally, if we found an annotation, let's move the selection to it
		if (nextAnnot != null)
			editor.selectAndReveal(nextPos, nextLength);

	    return null;
	}	
}