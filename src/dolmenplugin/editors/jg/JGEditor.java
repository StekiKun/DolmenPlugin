package dolmenplugin.editors.jg;

import java.io.StringReader;

import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import codegen.BaseParser.ParsingException;
import codegen.LexBuffer.LexicalError;
import dolmenplugin.editors.ColorManager;
import dolmenplugin.lib.ByRef;
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

	/** 
	 * See {@link #getGrammar()}
	 */
	private Grammar grammar;
	/**
	 * Is {@code true} if and only if {@code grammar} is up-to-date with
	 * the resource's state (i.e. not the document's contents necessarily,
	 * but the contents that were saved last).
	 */
	private boolean uptodate;
	
	public JGEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JGConfiguration(colorManager, this));
//		setDocumentProvider(new JGDocumentProvider());
		this.grammar = null;
		this.uptodate = true;
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
			uptodate = false;
			return;
		}
		 
		Grammar newGrammar = null;
		try (StringReader reader = new StringReader(doc.get())) {
			// Try and use a relevannt input name, so that Extents
			// can be resolved in the resulting lexer
			String inputName;
			IEditorInput input = this.getEditorInput();
			if (input instanceof FileEditorInput) {
				inputName = ((FileEditorInput) input).getPath().toFile().getPath();
			}
			else {
				inputName = this.getContentDescription();
			}

			final JGLexer jgLexer = new JGLexer(inputName, reader);
			JGParserGenerated jgParser = new JGParserGenerated(jgLexer, JGLexer::main);
			newGrammar = jgParser.start();
		}
		catch (LexicalError | ParsingException | Grammar.IllFormedException e) {
			newGrammar = null;
			// Use the exception as input for the outline
			if (contentOutlinePage != null)
				contentOutlinePage.setInput(e);
		}
		
		if (newGrammar != null) {
			grammar = newGrammar;
			uptodate = true;
			contentOutlinePage.setInput(grammar);
		}
		else
			uptodate = false;
	}

	@Override
	protected void editorSaved() {
		super.editorSaved();
		parseGrammar();
	}

	/**
	 * The grammar description parsed from this editor's contents.
	 * It represents the last time the editor was saved and its contents
	 * were correctly parsed into a {@link Grammar grammar description}.
	 * <p>
	 * Can be {@code null} if the grammar was never successfully parsed.
	 * 
	 *  @param stale	if non-null, will contain {@code true} or {@code false}
	 *  				on return depending on whether the returned grammar
	 *  				is stale wrt the file's contents, or up-to-date with
	 *  				the last time the editor was saved
	 */
	Grammar getGrammar(ByRef<Boolean> stale) {
		if (stale == null) return grammar;
		synchronized (this) {
			stale.set(!uptodate);
			return grammar;
		}
	}
	
	/**
	 * Same as {@link #getGrammar(null)}
	 * @return
	 */
	Grammar getGrammar() {
		return getGrammar(null);
	}
}
