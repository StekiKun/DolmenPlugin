package dolmenplugin.editors.jg;

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
import jg.JGLexer;
import jg.JGParserGenerated;
import syntax.Grammar;
import syntax.Grammar.TokenDecl;
import syntax.GrammarRule;
import syntax.Located;

/**
 * Custom editor for Dolmen grammar descriptions (.jg)
 * <p>
 * The model description for the contents of this editor
 * is a syntactic Dolmen lexer {@link syntax.Lexer}.
 * 
 * @author Stéphane Lescuyer
 */
public class JGEditor extends DolmenEditor<Grammar> {

	private ColorManager colorManager;
	private JGOutlinePage contentOutlinePage;

	public JGEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JGConfiguration(colorManager, this));
//		setDocumentProvider(new JGDocumentProvider());
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
				updateModel();
			}
			return (T) contentOutlinePage;
		}
		return super.getAdapter(required);
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * Parses the grammar description using {@link JGLexer} and {@link JGParserGenerated}
	 */
	@Override
	protected @Nullable Grammar parseModel() {
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

			final JGLexer jgLexer = new JGLexer(inputName, reader);
			JGParserGenerated jgParser = new JGParserGenerated(jgLexer, JGLexer::main);
			return jgParser.start();
		}
		catch (LexicalError | ParsingException | Grammar.IllFormedException e) {
			// Use the exception as input for the outline
			if (contentOutlinePage != null)
				contentOutlinePage.setInput(e);
			return null;
		}
	}

	@Override
	protected void modelChanged(Grammar model) {
		if (contentOutlinePage != null)
			contentOutlinePage.setInput(model);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * Can look for token declarations and grammar rules
	 */
	@Override
	public @Nullable Located<?> findDeclarationFor(String name) {
		if (model == null) return null;
		for (TokenDecl token : model.tokenDecls) {
			if (token.name.val.equals(name)) return token.name;
		}
		for (GrammarRule rule : model.rules.values()) {
			if (rule.name.val.equals(name)) return rule.name;
		}
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * Only supports {@link GrammarRule.class} and {@link TokenDecl.class}
	 */
	@Override
	public <Decl> @Nullable Decl 
		findDeclarationFor(String name, Class<Decl> clazz) {
		if (clazz == TokenDecl.class) {
			for (TokenDecl token : model.tokenDecls) {
				if (token.name.val.equals(name)) return clazz.cast(token);
			}
			return null;
		}
		if (clazz == GrammarRule.class) {
			for (GrammarRule rule : model.rules.values()) {
				if (rule.name.val.equals(name)) return clazz.cast(rule);
			}
			return null;
		}
		return null;
	}
	
}
