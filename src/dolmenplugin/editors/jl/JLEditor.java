package dolmenplugin.editors.jl;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

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
import jl.JLLexerGenerated;
import jl.JLParser;
import jle.JLELexer;
import jle.JLEParser;
import syntax.Lexer;
import syntax.Located;
import syntax.Regular;

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
			
			final JLELexer jlLexer =
				new JLELexer(inputName, reader);
			JLEParser jlParser = new JLEParser(jlLexer, JLELexer::main);
			return jlParser.lexer();
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

	/**
	 * {@inheritDoc}
	 * 
	 * Can look for regexp definitions and lexer entries
	 */
	@Override
	public @Nullable Located<String> findDeclarationFor(String name) {
		if (model == null) return null;
		for (Located<String> s : model.regulars.keySet()) {
			if (s.val.equals(name)) return s;
		}
		for (Lexer.Entry entry : model.entryPoints) {
			if (entry.name.val.equals(name)) return entry.name;
		}
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * Only supports {@link Lexer.Entry.class} and {@link Regular.class}.
	 */
	@Override
	public <Decl> @Nullable Decl 
		findDeclarationFor(String name, Class<Decl> clazz) {
		if (clazz == Lexer.Entry.class) {
			for (Lexer.Entry entry : model.entryPoints) {
				if (entry.name.val.equals(name)) 
					return clazz.cast(entry);
			}
			return null;
		}
		else if (clazz == Regular.class) {
			for (Map.Entry<Located<String>, Regular> def : model.regulars.entrySet()) {
				if (def.getKey().val.equals(name))
					return clazz.cast(def.getValue());
			}
			return null;
		}
		return null;
	}

	/**
	 * Only supports {@link Lexer.Entry.class} and {@link Regular.class}.
	 * 
	 * @param name
	 * @param clazz
	 * @return the references in the model for the entity with the given	
	 * 	{@code name} and {@code clazz}, or {@code null} if the model is
	 * 	not available or {@code clazz} is not supported
	 */
	private @Nullable List<Located<?>> findReferencesFor(String name, Class<?> clazz) {
		if (model == null) return null;
		if (clazz == Lexer.Entry.class) {
			// Unfortunately, references to rules are in semantic actions
			// and are not linked.
			// Shall we return all strings that seem to match? It is maybe
			// a good compromise in practice... for now, return [].
			return Lists.empty();
		}
		else if (clazz == Regular.class) {
			// References to defined regular expressions can be found in
			// other regular expressions, and in clauses. Unfortunately
			// the occurrences are simply inlined during the
			// parsing of lexer entries so they are not apparent in the
			// model... for now, return [].
			return Lists.empty();
		}
		return null;
	}
	
	@Override
	protected @Nullable Occurrences findOccurrencesFor(ITextSelection selection) {
		if (model == null) return null;
		
		@Nullable Located<String> decl = findDeclarationFor(selection);
		if (decl == null) return null;
		
		Class<?> clazz = model.regulars.containsKey(decl) ?
			Regular.class : Lexer.Entry.class;
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
		for (Located<String> s : model.regulars.keySet()) {
			if (enclosedInLocation(s, selection)) return s;
		}
		for (Lexer.Entry entry : model.entryPoints) {
			if (enclosedInLocation(entry.name, selection))
				return entry.name;
		}
		return null;
	}
}
