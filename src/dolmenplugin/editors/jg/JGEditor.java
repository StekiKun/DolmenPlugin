package dolmenplugin.editors.jg;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import codegen.BaseParser.ParsingException;
import codegen.LexBuffer.LexicalError;
import common.Lists;
import dolmenplugin.editors.ColorManager;
import dolmenplugin.editors.DolmenEditor;
import dolmenplugin.handlers.HandlerUtils;
import dolmenplugin.handlers.HandlerUtils.SelectedWord;
import jg.JGLexer;
import jg.JGParserGenerated;
import jge.JGELexer;
import jge.JGEParser;
import syntax.Grammar;
import syntax.Grammar.TokenDecl;
import syntax.GrammarRule;
import syntax.Located;
import syntax.Production;

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
			// Try and use a relevant input name, so that Extents
			// can be resolved in the resulting lexer
			String inputName;
			IEditorInput input = this.getEditorInput();
			if (input instanceof FileEditorInput) {
				inputName = ((FileEditorInput) input).getPath().toFile().getPath();
			}
			else {
				inputName = this.getContentDescription();
			}

			final JGELexer jgLexer = new JGELexer(inputName, reader);
			JGEParser jgParser = new JGEParser(jgLexer, JGELexer::main);
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
	public @Nullable Located<String> findDeclarationFor(String name) {
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

	/**
	 * Only supports {@link GrammarRule.class} and {@link TokenDecl.class}.
	 * 
	 * @param name
	 * @param clazz
	 * @return the references in the model for the entity with the given	
	 * 	{@code name} and {@code clazz}, or {@code null} if the model is
	 * 	not available or {@code clazz} is not supported
	 */
	private List<Located<?>> findReferencesFor(String name, Class<?> clazz) {
		if (model == null) return null;
		if (clazz == TokenDecl.class) {
			// Reference to tokens are found in production items
			return model.rules.values().stream()
				.flatMap(rule -> rule.productions.stream())
				.flatMap(prod -> prod.items.stream())
				.filter(item -> item instanceof Production.Actual
						&& ((Production.Actual) item).item.val.equals(name))
				.map(item -> ((Production.Actual) item).item)
				.collect(Collectors.toList());
		}
		else if (clazz == GrammarRule.class) {
			// References to non-terminals are found in production items,
			// including in continuations for the current rule
			List<Located<?>> res = new ArrayList<>();
			for (GrammarRule rule : model.rules.values()) {
				rule.productions.stream()
					.flatMap(prod -> prod.items.stream())
					.forEach(item -> {
						switch (item.getKind()) {
						case ACTION:
							break;
						case ACTUAL:
							Production.Actual actual = (Production.Actual) item;
							if (actual.item.val.equals(name))
								res.add(actual.item);
							break;
						case CONTINUE:
							if (rule.name.val.equals(name))
								res.add(((Production.Continue) item).cont);
							break;
						}
					});
			}
			return res.isEmpty() ? Lists.empty() : res;
		}
		return null;
	}
	
	@Override
	protected @Nullable Occurrences findOccurrencesFor(ITextSelection selection) {
		if (model == null) return null;
		
		@Nullable Located<String> decl = findDeclarationFor(selection);
		if (decl == null) return null;
		
		Class<?> clazz = Character.isUpperCase(decl.val.charAt(0)) ?
				TokenDecl.class : GrammarRule.class;
		return new Occurrences(decl, findReferencesFor(decl.val, clazz));
	}
	
	private @Nullable Located<String> findDeclarationFor(ITextSelection selection) {
		// If the selection happens to point to a declaration, use it (it's
		// faster and a bit more robust)
		@Nullable Located<String> decl = findSelectedDeclaration(selection);
		if (decl != null) return decl;

		// Otherwise, do a textual match. This allows finding occurrences from
		// a 'reference' in a semantic action or in a regexp but it's a slightly
		// fragile approach of course.
		@Nullable SelectedWord sword = HandlerUtils.selectWord(getDocument(), selection); 
		if (sword == null) return null;
		return findDeclarationFor(sword.word);
	}
	
	/**
	 * If the selection matches the location of an entity's declaration,
	 * or if the selection is empty and the caret is over the entity's declaration,
	 * this returns the corresponding location. 
	 * 
	 * @param selection
	 * @return the declaration described by the {@code selection}
	 */
	private @Nullable Located<String> findSelectedDeclaration(ITextSelection selection) {
		if (model == null) return null;
		for (TokenDecl token : model.tokenDecls) {
			if (enclosedInLocation(token.name, selection)) return token.name;
		}
		for (GrammarRule rule : model.rules.values()) {
			if (enclosedInLocation(rule.name, selection))
				return rule.name;
		}
		return null;
	}
}
