package dolmenplugin.editors.jl;

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
	
	/** 
	 * See {@link #getLexer()}
	 */
	private Lexer lexer;
	/**
	 * Is {@code true} if and only if {@code lexer} is up-to-date with
	 * the resource's state (i.e. not the document's contents necessarily,
	 * but the contents that were saved last).
	 */
	private boolean uptodate;
	
	public JLEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JLConfiguration(colorManager, this));
		// setDocumentProvider(new TextFileDocumentProvider());
		lexer = null;
		uptodate = true;
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
			uptodate = false;
			return;
		}
		 
		Lexer newLexer = null;
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
			
			final JLLexerGenerated jlLexer =
				new JLLexerGenerated(inputName, reader);
			JLParser jlParser = new JLParser(jlLexer, JLLexerGenerated::main);
			newLexer = jlParser.parseLexer();
		}
		catch (LexicalError | ParsingException | Lexer.IllFormedException e) {
			// Use the exception as input for the outline
			// Or should we simply use the last OK version of the lexer?
			if (contentOutlinePage != null)
				contentOutlinePage.setInput(e);
		}
		if (newLexer != null) {
			lexer = newLexer;
			uptodate = true;
			contentOutlinePage.setInput(lexer);
		}
		else
			uptodate = false;
	}

	@Override
	protected void editorSaved() {
		super.editorSaved();
		parseLexer();
	}	

	/**
	 * The lexer description parsed from this editor's contents.
	 * It represents the last time the editor was saved and its contents
	 * were correctly parsed into a {@link Lexer lexer description}.
	 * <p>
	 * Can be {@code null} if the lexer was never successfully parsed.
	 * 
	 *  @param stale	if non-null, will contain {@code true} or {@code false}
	 *  				on return depending on whether the returned lexer
	 *  				is stale wrt the file's contents, or up-to-date with
	 *  				the last time the editor was saved
	 */
	Lexer getLexer(ByRef<Boolean> stale) {
		if (stale == null) return lexer;
		synchronized (this) {
			stale.set(!uptodate);
			return lexer;
		}
	}
	
	/**
	 * Same as {@link #getLexer(null)}
	 * @return
	 */
	Lexer getLexer() {
		return getLexer(null);
	}
}
