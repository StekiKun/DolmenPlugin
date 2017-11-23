package dolmenplugin.editors.jl;

import java.io.StringReader;

import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import codegen.BaseParser.ParsingException;
import codegen.LexBuffer.LexicalError;
import dolmenplugin.editors.ColorManager;
import jl.JLLexerGenerated;
import jl.JLParser;
import syntax.Lexer;

/**
 * Custom editor for Dolmen lexer descriptions (.jl)
 * 
 * @author Stéphane Lescuyer
 */
public class JLEditor extends TextEditor {

	private ColorManager colorManager;
	private JLOutlinePage contentOutlinePage;
	private Lexer lexer;
	
	public JLEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JLConfiguration(colorManager));
		// setDocumentProvider(new TextFileDocumentProvider());
		lexer = null;
	}
	
	public void dispose() {
		colorManager.dispose();
		super.dispose();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> required) {
		if (IContentOutlinePage.class.equals(required)) {
			if (contentOutlinePage == null) {
				contentOutlinePage = new JLOutlinePage(this);
				parseLexer();
			}
			return (T) contentOutlinePage;
		}
		return super.getAdapter(required);
	}
	
	IDocument getDocument() {
		return this.getDocumentProvider().getDocument(this.getEditorInput());
	}
	
	private void parseLexer() {
		IDocument doc = getDocument();
		if (doc == null) {
			lexer = null;
			return;
		}
		 
		Lexer newLexer = null;
		try (StringReader reader = new StringReader(doc.get())) {
			final JLLexerGenerated jlLexer =
				new JLLexerGenerated(this.getContentDescription(), reader);
			JLParser jlParser = new JLParser(jlLexer, JLLexerGenerated::main);
			newLexer = jlParser.parseLexer();
		}
		catch (LexicalError | ParsingException | Lexer.IllFormedException e) {
			newLexer = null;
			// Use the exception as input for the outline
			if (contentOutlinePage != null)
				contentOutlinePage.setInput(e);
		}
		lexer = newLexer;
		if (lexer != null)
			contentOutlinePage.setInput(lexer);
	}

	@Override
	protected void editorSaved() {
		super.editorSaved();
		parseLexer();
	}	
	
}
