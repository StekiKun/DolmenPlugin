package dolmenplugin.editors.jl;

import java.io.StringReader;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import codegen.BaseParser.ParsingException;
import codegen.LexBuffer.LexicalError;
import dolmenplugin.editors.ColorManager;
import dolmenplugin.editors.DolmenEditor;
import jl.JLLexerGenerated;
import jl.JLParser;
import syntax.Lexer;

/**
 * Custom editor for Dolmen lexer descriptions (.jl)
 * <p>
 * The model description for the contents of this editor
 * is a syntactic Dolmen lexer {@link syntax.Lexer}.
 * 
 * @author Stéphane Lescuyer
 */
public class JLEditor extends DolmenEditor<Lexer> {

	private ColorManager colorManager;
	private JLOutlinePage contentOutlinePage;
	
	public JLEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JLConfiguration(colorManager, this));
		// setDocumentProvider(new TextFileDocumentProvider());
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
				updateModel();
			}
			return (T) contentOutlinePage;
		}
		return super.getAdapter(required);
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * Parses the lexer description using {@link JLLexerGenerated} and {@link JLParser}
	 */
	@Override
	protected final @Nullable Lexer parseModel() {
		IDocument doc = getDocument();
		if (doc == null) return null;
		 
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
			return jlParser.parseLexer();
		}
		catch (LexicalError | ParsingException | Lexer.IllFormedException e) {
			// Use the exception as input for the outline
			// Or should we simply use the last OK version of the lexer?
			if (contentOutlinePage != null)
				contentOutlinePage.setInput(e);
			return null;
		}
	}
	
	@Override
	protected void modelChanged(Lexer lexer) {
		if (contentOutlinePage != null)
			contentOutlinePage.setInput(lexer);
	}

}
