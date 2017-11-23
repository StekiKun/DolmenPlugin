package dolmenplugin.editors.jg;

import java.io.StringReader;

import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import codegen.BaseParser.ParsingException;
import codegen.LexBuffer.LexicalError;
import dolmenplugin.editors.ColorManager;
import jg.JGLexer;
import jg.JGParserGenerated;
import syntax.Grammar;

/**
 * Custom editor for Dolmen grammar descriptions (.jg)
 * 
 * @author Stéphane Lescuyer
 */
public class JGEditor extends TextEditor {

	private ColorManager colorManager;
	private JGOutlinePage contentOutlinePage;
	private Grammar grammar;
	
	public JGEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JGConfiguration(colorManager));
//		setDocumentProvider(new JGDocumentProvider());
		this.grammar = null;
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
				contentOutlinePage = new JGOutlinePage(this);
				parseGrammar();
			}
			return (T) contentOutlinePage;
		}
		return super.getAdapter(required);
	}
	
	IDocument getDocument() {
		return this.getDocumentProvider().getDocument(this.getEditorInput());
	}
	
	private void parseGrammar() {
		IDocument doc = getDocument();
		if (doc == null) {
			grammar = null;
			return;
		}
		 
		Grammar newGrammar = null;
		try (StringReader reader = new StringReader(doc.get())) {
			final JGLexer jgLexer =
				new JGLexer(this.getContentDescription(), reader);
			JGParserGenerated jgParser = new JGParserGenerated(jgLexer, JGLexer::main);
			newGrammar = jgParser.start();
		}
		catch (LexicalError | ParsingException | Grammar.IllFormedException e) {
			newGrammar = null;
			// Use the exception as input for the outline
			if (contentOutlinePage != null)
				contentOutlinePage.setInput(e);
		}
		grammar = newGrammar;
		if (grammar != null)
			contentOutlinePage.setInput(grammar);
	}

	@Override
	protected void editorSaved() {
		super.editorSaved();
		parseGrammar();
	}
	
}
