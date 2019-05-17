package org.stekikun.dolmenplugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.editors.text.TextEditor;
import org.stekikun.dolmenplugin.editors.DolmenEditor;

/**
 * This handler implements the command <i>Toggle Comment</i>,
 * which is available in the <i>Source</i> top-level menu on
 * Dolmen editors and via the Ctrl+/ shortcut.
 * <p>
 * It applies to the active editor's selection and comments it out
 * using single-line ('//') comments, unless the selection is already
 * completely commented using single-line comments in which case it
 * is uncommented.
 * 
 * @author Stéphane Lescuyer
 */
public class ToggleCommentHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// Find the editor where the command was executed, it must be
		// one of ours, or we simply ignore the command
	    final DolmenEditor<?> editor = HandlerUtils.findActiveDolmenEditor(event);
	    if (editor == null) return null;

	    // Find the selection to which the command should be applied
	    final SelectedBlock selection = findSelectedBlock(editor);
	    if (selection == null) return null;

	    // We will add comment markers for all lines, unless the whole selection
	    // is made of commented lines, in which case we remove the comment markers
	    final int[] insertionPoints;
	    final boolean addComment;
	    try {
	    	insertionPoints = selection.getInsertionPoints();
			addComment = insertionPoints[0] >= 0;
		} catch (BadLocationException e) {
			return null;
		}
	    
	    
	    // Perform all changes, line by line, but try to group them in a 
	    // single change for the undo/redo stack
	    IRewriteTarget rwTarget = editor.getAdapter(IRewriteTarget.class);
	    final IDocument document =
	    	rwTarget == null ? selection.document : rwTarget.getDocument();
	    if (rwTarget != null) rwTarget.beginCompoundChange();
	    final int replaceLength = addComment ? 0 : COMMENT_LENGTH;
	    final String newContent = addComment ? COMMENT_PREFIX : "";
	    for (int l = selection.firstLine; l <= selection.lastLine; ++l) {
	    	final int replaceOffset;
	    	if (addComment)
	    		replaceOffset = insertionPoints[l - selection.firstLine];
	    	else
	    		replaceOffset = -insertionPoints[l - selection.firstLine]-1;
	    	try {
				document.replace(replaceOffset, replaceLength, newContent);
			} catch (BadLocationException e) {
				return null;
			}
	    }
	    if (rwTarget != null) rwTarget.endCompoundChange();
	    
	    // Finally, try to reproduce the original selection
	    final int shiftPerLine = COMMENT_LENGTH * (addComment ? 1 : -1); 
	    int selOffset = selection.rawSelection.getOffset() + shiftPerLine;
	    int selLength = selection.rawSelection.getLength() +
	    		(selection.lastLine - selection.firstLine) * shiftPerLine;
	    editor.selectAndReveal(selOffset, selLength);

	    return null;
	}
	
	private final static String COMMENT_PREFIX = "//";
	private final static int COMMENT_LENGTH = COMMENT_PREFIX.length();
	
	/**
	 * Describes the text selection on which Toggle Comment should be
	 * applied, along with its extension to line boundaries
	 * 
	 * @author Stéphane Lescuyer
	 */
	private static final class SelectedBlock {
		final IDocument document;
		final ITextSelection rawSelection;
		final int firstLine;
		final int lastLine;
		final int firstOffsetX;	// Start of firstLine
		final int lastOffsetX;	// End of lastLine, including delimiter
		
		SelectedBlock(IDocument document, ITextSelection rawSelection, 
				int firstLine, int lastLine, int firstOffsetX, int lastOffsetX) {
			this.document = document;
			this.rawSelection = rawSelection;
			this.firstLine = firstLine;
			this.lastLine = lastLine;
			this.firstOffsetX = firstOffsetX;
			this.lastOffsetX = lastOffsetX;
		}
		
		@SuppressWarnings("unused")
		ITextSelection toTextSelection() {
			return new TextSelection(document, firstOffsetX, lastOffsetX - firstOffsetX + 1);
		}
		
		/**
		 * <i>Insertion points are returned as is, deletion points {@code n}
		 *  are returned as {@code -n-1} so that non-negative numbers denote
		 *  insertions and negative ones deletions.
		 * </i>
		 * <p>
		 * <b>The returned offsets are suitable for incremental updates to
		 * 	the document, i.e. they already take into account the potential
		 * 	offset shifts that would result from changing the previous lines.
		 * </b>
		 * 
		 * @return the offsets in the document where comment prefixes should be
		 * 	returned, or the offsets where comment prefixes should be <i>removed</i>,
		 *  depending on whether the whole selected block is initially commented or not
		 * @throws BadLocationException
		 */
		int[] getInsertionPoints() throws BadLocationException {
			int[] marks = new int[lastLine - firstLine + 1];
			int[] starts = new int[lastLine - firstLine + 1];
			boolean allCommented = true;
			for (int l = firstLine; l <= lastLine; ++l) {
				int delta = l - firstLine;
				IRegion r = document.getLineInformation(l);
				starts[delta] = r.getOffset() + delta * COMMENT_LENGTH;
				String line = document.get(r.getOffset(), r.getLength());
				if (!line.trim().startsWith(COMMENT_PREFIX))
					allCommented = false;
				else {
					int mark = r.getOffset() + line.indexOf(COMMENT_PREFIX);
					mark -= delta * COMMENT_LENGTH;
					marks[delta] = -mark -1;
				}
			}
			return allCommented ? marks : starts;
		}
	}
	
	/**
	 * @param editor
	 * @return the {@link SelectedBlock selection block} in the editor
	 * 	where this command originated, or {@code null} if no such selection
	 *  can be retrieved
	 */
	private SelectedBlock findSelectedBlock(TextEditor editor) {
		// The selection must be a text selection for the command to work
	    final ISelection selection = editor.getSelectionProvider().getSelection();
	    if (!(selection instanceof ITextSelection))
	      return null;
	    
	    // There should be a document but don't break havoc if none
	    IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
	    if (doc == null) return null;
	   
	    ITextSelection textSelection = (ITextSelection) selection;
	    int firstLine = textSelection.getStartLine();
	    if (firstLine < 0) return null;
	    int lastLine = textSelection.getEndLine();
	    if (lastLine < 0) return null;

	    // At that point, we could insert COMMENT_PREFIX for each line
	    // between [firstLine] and [lastLine] but that would trigger as many
	    // DocumentEvents for any listener so maybe it's better to just change
	    // all lines at once? To do that though, we have to extend the selection
	    // to account for full lines
	    try {
	    	final IRegion firstLineInfo = doc.getLineInformation(firstLine);    
	    	final IRegion lastLineInfo = doc.getLineInformation(lastLine);
	    	int firstOffsetX = firstLineInfo.getOffset();
	    	int lastOffsetX = lastLineInfo.getOffset() + doc.getLineLength(lastLine);
	    	return new SelectedBlock(doc, textSelection, firstLine, lastLine, firstOffsetX, lastOffsetX);
		} catch (BadLocationException e) {
			return null;
		}
	}
	
}
