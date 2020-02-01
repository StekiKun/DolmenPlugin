package org.stekikun.dolmenplugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.stekikun.dolmenplugin.base.Utils;
import org.stekikun.dolmenplugin.editors.DolmenEditor;

/**
 * Utility functions for implementing various handlers
 * 
 * @author Stéphane Lescuyer
 */
public abstract class HandlerUtils {

	private HandlerUtils() {
		//  Static utility only
	}
	
	/**
	 * @param event
	 * @return the text editor where the command originated, if it
	 * 	is either one of the custom Dolmen editors, or {@code null}
	 *  otherwise
	 */
	public static DolmenEditor<?> findActiveDolmenEditor(ExecutionEvent event) {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (editor == null) return null;
		
		if (!(editor instanceof DolmenEditor<?>))
			return null;
		return (DolmenEditor<?>) editor;
	}
	
	/**
	 * Describes a word found at selection point in an editor
	 * 
	 * @see HandlerUtils#selectWord(IDocument, int)
	 * 
	 * @author Stéphane Lescuyer
	 */
	public static final class SelectedWord {
		/** The selected word */
		public final String word;
		/** The offset in the document of the word */
		public final int startOffset;
		/** The length of the word */
		public final int length;
		
		private SelectedWord(String word, int startOffset, int length) {
			this.word = word;
			this.startOffset = startOffset;
			this.length = length;
		}
	}
	
	/**
	 * Finds the word at offset {@code offset} in the document {@code doc}.
	 * <p>
	 * The word can start at {@code offset} or end right before {@code offset}.
	 * 
	 * @param doc
	 * @param offset
	 * @return the selected word, or {@code null} if no word could be found
	 */
	public static @Nullable SelectedWord selectWord(IDocument doc, int offset) {
		int start = offset - 1;
		while (start >= 0) {
			char ch;
			try {
				ch = doc.getChar(start);
			} catch (BadLocationException e) {
				// ignore
				return null;
			}
			if (!Utils.isDolmenWordPart(ch)) break;
			--start;
		}
		final int length = doc.getLength();
		int last = offset;
		while (last < length) {
			char ch;
			try {
				ch = doc.getChar(last);
			} catch (BadLocationException e) {
				// ignore
				return null;
			}
			if (!Utils.isDolmenWordPart(ch)) break;
			++last;
		}
		++start;
		if (last <= start) return null;
		String word;
		try {
			word = doc.get(start, last - start);
		} catch (BadLocationException e) {
			// this one would be really weird
			e.printStackTrace();
			return null;
		}
		return new SelectedWord(word, start, last - start);
	}

	/**
	 * Finds the word corresponding to {@code selection} in the document {@code doc}.
	 * <p>
	 * If the selection is of length zero, this behaves as {@link #selectWord(IDocument, int)}
	 * i.e. will find the word at (or ending at) the selection offset. Otherwise, this
	 * returns exactly the selected text if and only if it is a word.
	 * 
	 * @param doc
	 * @param offset
	 * @return the selected word, or {@code null} if no word could be found
	 */
	public static @Nullable SelectedWord selectWord(IDocument doc, ITextSelection selection) {
		if (selection.getLength() != 0) {
			String word = selection.getText();
			if (!Utils.isDolmenWordStart(word.charAt(0))) return null;
			for (int i = 1; i < word.length(); ++i) {
				if (!Utils.isDolmenWordPart(word.charAt(i))) return null;
			}
			return new SelectedWord(word, selection.getOffset(), selection.getLength());
		} else
			return HandlerUtils.selectWord(doc, selection.getOffset());
	}
}
