package dolmenplugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.editors.text.TextEditor;

import dolmenplugin.editors.DolmenEditor;
import dolmenplugin.handlers.HandlerUtils.SelectedWord;
import syntax.Located;

/**
 * This handler implements the comment <i>Toggle Comment</i>,
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
public class OpenDeclarationHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// Find the editor where the command was executed, it must be
		// one of ours, or we simply ignore the command
	    final DolmenEditor<?> editor = HandlerUtils.findActiveDolmenEditor(event);
	    if (editor == null) return null;

	    // Find the selection to which the command should be applied
	    final String selectedWord = findSelectedWord(editor);
	    if (selectedWord == null) return null;

	    // Look for a declaration matching the selection in the editor's model
	    Located<?> declaration = editor.findDeclarationFor(selectedWord);
	    if (declaration == null) return null;
	    
	    editor.selectAndReveal(declaration.start.offset, declaration.length());
	    return null;
	}
	
	/**
	 * If there is an active selection, it must exactly match a word
	 * for it to be selected. Otherwise, if the cursor is in the middle
	 * of some word, it is returned.
	 * 
	 * @param editor
	 * @return the word selected in {@code editor}, or {@code null} if
	 * 	no word is selected
	 */
	private String findSelectedWord(TextEditor editor) {
		// The selection must be a text selection for the command to work
	    final ISelection selection = editor.getSelectionProvider().getSelection();
	    if (!(selection instanceof ITextSelection))
	      return null;
	    
	    // There should be a document but don't break havoc if none
	    IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
	    if (doc == null) return null;
	   
	    SelectedWord sword = HandlerUtils.selectWord(doc, (ITextSelection) selection);
	    return sword == null ? null : sword.word;
	}
	
}
