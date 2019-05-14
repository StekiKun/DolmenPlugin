package org.stekikun.dolmenplugin.editors.jg;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.stekikun.dolmen.codegen.BaseParser.ParsingException;
import org.stekikun.dolmen.codegen.LexBuffer.LexicalError;
import org.stekikun.dolmen.codegen.LexBuffer.Position;
import org.stekikun.dolmen.common.Lists;
import org.stekikun.dolmen.jg.JGLexer;
import org.stekikun.dolmen.jg.JGParserGenerated;
import org.stekikun.dolmen.jge.JGELexer;
import org.stekikun.dolmen.jge.JGEParser;
import org.stekikun.dolmen.syntax.Located;
import org.stekikun.dolmen.syntax.PExtent;
import org.stekikun.dolmen.syntax.PGrammar;
import org.stekikun.dolmen.syntax.PGrammarRule;
import org.stekikun.dolmen.syntax.PGrammars;
import org.stekikun.dolmen.syntax.PGrammars.Sort;
import org.stekikun.dolmen.syntax.PProduction;
import org.stekikun.dolmen.syntax.PProduction.ActualExpr;
import org.stekikun.dolmenplugin.editors.ColorManager;
import org.stekikun.dolmenplugin.editors.DolmenEditor;
import org.stekikun.dolmenplugin.handlers.HandlerUtils;
import org.stekikun.dolmenplugin.handlers.HandlerUtils.SelectedWord;
import org.stekikun.dolmen.syntax.Reporter;
import org.stekikun.dolmen.syntax.TokenDecl;

/**
 * Custom editor for Dolmen grammar descriptions (.jg)
 * <p>
 * The model description for the contents of this editor
 * is a syntactic Dolmen parametric grammar {@link syntax.PGrammar}.
 * 
 * @author Stéphane Lescuyer
 */
public class JGEditor extends DolmenEditor<PGrammar> {

	private ColorManager colorManager;
	private JGOutlinePage contentOutlinePage;

	// The sorts inferred for each rule of the current model
	private @Nullable Map<String, List<Sort>> formalSorts;
	
	public JGEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JGConfiguration(colorManager, this));
//		setDocumentProvider(new JGDocumentProvider());
		this.formalSorts = null;
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
	protected @Nullable PGrammar parseModel() {
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
		catch (LexicalError | ParsingException | PGrammar.IllFormedException e) {
			// Use the exception as input for the outline
			if (contentOutlinePage != null)
				contentOutlinePage.setInput(e);
			return null;
		}
	}

	@Override
	protected void modelChanged(PGrammar model) {
		if (contentOutlinePage != null)
			contentOutlinePage.setInput(model);
		Reporter reporter = new Reporter();
		formalSorts = PGrammars.analyseGrammar(model,
			PGrammars.dependencies(model.rules), reporter);
	}
	
	/**
	 * @param ruleName
	 * @return the sorts inferred for the formal parameters
	 * 	of rule {@code ruleName}, in order of declaration,
	 *  or {@code null} if no model is available or {@code ruleName}
	 *  is not a non-terminal of the model
	 */
	public @Nullable List<Sort> getFormalSorts(String ruleName) {
		if (model == null || formalSorts == null) 
			return null;
		return formalSorts.get(ruleName);
	}
	
	/**
	 * A descriptor for a formal parameter declaration, characterized
	 * by the {@link #rule} where the parameter is declared, and
	 * its {@linkplain #param name}. It is used to denote formal
	 * parameters when marking occurrences or finding declaration sites.
	 * 
	 * @author Stéphane Lescuyer
	 */
	public static final class FormalDecl {
		/**
		 * The rule where the formal is declared
		 */
		public final PGrammarRule rule;
		/**
		 * The formal parameter's name at declaration site
		 */
		public final Located<String> param;
		
		public FormalDecl(PGrammarRule rule, Located<String> param) {
			this.rule = rule;
			this.param = param;
			for (Located<String> p : rule.params) {
				if (p == param) return;
			}
			throw new IllegalArgumentException("" + param.val + " is not a formal parameter of " + rule);
		}
	}
	
	/**
	 * @param rule
	 * @param selection
	 * @return whether the range of {@code selection} is enclosed in 
	 * 	the definition of the rule {@code rule}
	 */
	private static boolean enclosedInRule(PGrammarRule rule, ITextSelection selection) {
		// Approximate the range of the rule in the source
		int first = rule.returnType.startPos;
		int last = ruleEndOffset(rule);
		// Check whether selection is included in the rule's range
		if (selection.getOffset() < first) return false;
		if (selection.getOffset() + selection.getLength() > last) return false;
		return true;
	}
	
	/**
	 * This is an approximation as it returns the position of the end
	 * of the last item of the last production. In particular, the closing
	 * semicolon is past this position.
	 * 
	 * @param rule
	 * @return the position of the end of the parametric {@code rule}
	 */
	private static int ruleEndOffset(PGrammarRule rule) {
		PProduction lastProd = rule.productions.get(rule.productions.size() - 1);
		if (lastProd.items.isEmpty()) return -1;
		PProduction.Item lastItem = lastProd.items.get(lastProd.items.size() - 1);
		int last = -1;
		switch (lastItem.getKind()) {
		case ACTION:
			last = ((PProduction.ActionItem) lastItem).extent.endPos;
			break;
		case ACTUAL:
			PProduction.Actual actual = (PProduction.Actual) lastItem;
			if (actual.args != null) {
				last = actual.args.endPos;
				break;
			}
			ActualExpr aexpr = ((PProduction.Actual) lastItem).item;
			while (!aexpr.params.isEmpty())
				aexpr = aexpr.params.get(aexpr.params.size() - 1);
			last = aexpr.symb.end.offset;
			break;
		case CONTINUE:
			last = ((PProduction.Continue) lastItem).cont.end.offset;
			break;
		}
		return last;
	}
	
	/**
	 * @param offset
	 * @return the rule at the given offset in this editor's model,
	 * 	or {@code null} if there is no such rule or if the model
	 *  is not available
	 */
	public @Nullable PGrammarRule findRuleAtOffset(int offset) {
		if (model == null) return null;
		for (PGrammarRule rule : model.rules.values()) {
			// Approximate the range of the rule in the source
			int first = rule.returnType.startPos;
			int last = ruleEndOffset(rule);
			// Check whether selection is included in the rule's range
			if (offset < first) continue;
			if (offset > last) continue;
			return rule;
		}
		// No suitable rule found
		return null;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * Can look for token declarations, grammar rules and formal parameters
	 */
	@Override
	public @Nullable Located<String> findDeclarationFor(String name, ITextSelection selection) {
		@Nullable SelectedDeclaration decl = findSelectedDeclarationFor(name, selection);
		return decl == null ? null : decl.declarationName;
	}
	
	/**
	 * @param name
	 * @param selection
	 * @return the declaration corresponding to the given {@code name}
	 * 	as referred in the range described by {@code selection}, or
	 *  {@code null} if no model is available or no such declaration
	 *  can be found
	 */
	public @Nullable SelectedDeclaration 
		findSelectedDeclarationFor(String name, ITextSelection selection) {
		if (model == null) return null;
		for (TokenDecl token : model.tokenDecls) {
			if (token.name.val.equals(name)) 
				return new SelectedDeclaration(token.name, TokenDecl.class);
		}
		// Look for a formal parameter first, and then for a non-terminal
		for (PGrammarRule rule : model.rules.values()) {
			for (Located<String> param : rule.params) {
				if (param.val.equals(name) && enclosedInRule(rule, selection))
					return new SelectedDeclaration(param, FormalDecl.class);
			}
		}
		try {
			return new SelectedDeclaration(model.rule(name).name, PGrammarRule.class);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * Only supports {@link PGrammarRule.class}, {@link FormalDecl.class} 
	 * and {@link TokenDecl.class}
	 */
	@Override
	public <Decl> @Nullable Decl 
		findDeclarationFor(String name, ITextSelection selection, Class<Decl> clazz) {
		if (model == null) return null;
		if (clazz == TokenDecl.class) {
			for (TokenDecl token : model.tokenDecls) {
				if (token.name.val.equals(name)) return clazz.cast(token);
			}
			return null;
		}
		else if (clazz == PGrammarRule.class) {
			for (PGrammarRule rule : model.rules.values()) {
				if (rule.name.val.equals(name)) return clazz.cast(rule);
			}
			return null;
		}
		else if (clazz == FormalDecl.class) {
			for (PGrammarRule rule : model.rules.values()) {
				for (Located<String> param : rule.params) {
					if (param.val.equals(name) && enclosedInRule(rule, selection))
						return clazz.cast(new FormalDecl(rule, param));
				}
			}
		}
		return null;
	}

	/**
	 * Only supports {@link PGrammarRule.class}, {@link FormalDecl.class}
	 * and {@link TokenDecl.class}.
	 * 
	 * @param name
	 * @param selection		where {@code name} was referenced
	 * @param clazz
	 * @return the references in the model for the entity with the given	
	 * 	{@code name} and {@code clazz}, or {@code null} if the model is
	 * 	not available or {@code clazz} is not supported
	 */
	private List<Located<?>> findReferencesFor(String name, 
			ITextSelection selection, Class<?> clazz) {
		if (model == null) return null;
		if (clazz == TokenDecl.class) {
			// Reference to tokens are found in production items
			List<Located<?>> res = new ArrayList<>();
			model.rules.values().stream()
				.flatMap(rule -> rule.productions.stream())
				.flatMap(prod -> prod.items.stream())
				.forEach(item -> {
					if (item instanceof PProduction.Actual)
						addReferencesFor(res, name, ((PProduction.Actual) item).item);
				});
			return res.isEmpty() ? Lists.empty() : res;
		}
		else if (clazz == PGrammarRule.class) {
			// References to non-terminals are found in production items,
			// including in continuations for the current rule
			List<Located<?>> res = new ArrayList<>();
			for (PGrammarRule rule : model.rules.values()) {
				rule.productions.stream()
					.flatMap(prod -> prod.items.stream())
					.forEach(item -> {
						switch (item.getKind()) {
						case ACTION:
							break;
						case ACTUAL:
							PProduction.Actual actual = (PProduction.Actual) item;
							addReferencesFor(res, name, actual.item);
							break;
						case CONTINUE:
							if (rule.name.val.equals(name))
								res.add(((PProduction.Continue) item).cont);
							break;
						}
					});
			}
			return res.isEmpty() ? Lists.empty() : res;
		}
		else if (clazz == FormalDecl.class) {
			// References to formal parameters are found in the production items
			// of the rule where they are declared, as well as in holes of the
			// return type, arguments and action items
			List<Located<?>> res = new ArrayList<>();
			for (PGrammarRule rule : model.rules.values()) {
				// We look for the actual rule and formal parameter
				Optional<Located<String>> param = rule.params.stream()
						.filter(p -> p.val.equals(name)).findFirst();
				if (!param.isPresent()) continue;
				if (!enclosedInRule(rule, selection)) continue;
				// We have found the rule, let's find all occurrences now
				addHoleReferences(res, name, rule.returnType);
				addHoleReferences(res, name, rule.args);
				rule.productions.stream()
					.flatMap(prod -> prod.items.stream())
					.forEach(item -> {
						switch (item.getKind()) {
						case ACTION:
							addHoleReferences(res, name, ((PProduction.ActionItem) item).extent);
							break;
						case ACTUAL:
							PProduction.Actual actual = (PProduction.Actual) item;
							addReferencesFor(res, name, actual.item);
							addHoleReferences(res, name, actual.args);
							break;
						case CONTINUE:
							break;
						}						
					});
			}
			return res.isEmpty() ? Lists.empty() : res;
		}
		return null;
	}

	/**
	 * Add located references to the symbol {@code name} in
	 * the actual expression {@code aexpr} to the reference list {@code refs}
	 * 
	 * @param refs
	 * @param name
	 * @param aexpr
	 */
	private void addReferencesFor(List<Located<?>> refs, 
			String name, ActualExpr aexpr) {
		if (aexpr.symb.val.equals(name))
			refs.add(aexpr.symb);
		for (ActualExpr sexpr : aexpr.params)
			addReferencesFor(refs, name, sexpr);
	}
	
	/**
	 * Add located references to the formal parameter {@code name}
	 * in the holes of the parameterized extent {@link extent}. 
	 * References are added to the reference list {@code refs}.
	 * 
	 * @param refs
	 * @param name
	 * @param extent
	 */
	private void addHoleReferences(List<Located<?>> refs,
			String name, @Nullable PExtent extent) {
		if (extent == null) return;
		for (PExtent.Hole hole : extent.holes) {
			if (hole.name.equals(name)) {
				int base = extent.startPos;
				refs.add(Located.of(hole.name,
					new Position(extent.filename, base + hole.offset, 
							hole.startLine, hole.startCol),
					new Position(extent.filename, base + hole.endOffset() + 1, 
							hole.startLine, hole.startCol + hole.length())));
			}
		}
	}
	
	/**
	 * Container class which packs together a declaration site and
	 * a {@link Class} object representing the kind of declared entity
	 * 
	 * @author Stéphane Lescuyer
	 */
	public final static class SelectedDeclaration {
		/** The name and site of the declaration */
		public final Located<String> declarationName;
		/** The kind of entity being declared */
		public final Class<?> declarationClass;
		
		SelectedDeclaration(Located<String> declarationName, Class<?> declarationClass) {
			this.declarationName = declarationName;
			this.declarationClass = declarationClass;
		}
	}
	
	@Override
	protected @Nullable Occurrences findOccurrencesFor(ITextSelection selection) {
		if (model == null) return null;
		
		@Nullable SelectedDeclaration decl = findDeclarationFor(selection);
		if (decl == null) return null;
				
		return new Occurrences(decl.declarationName,
			findReferencesFor(decl.declarationName.val, selection, decl.declarationClass));
	}
	
	private @Nullable SelectedDeclaration findDeclarationFor(ITextSelection selection) {
		// If the selection happens to point to a declaration, use it (it's
		// faster and a bit more robust)
		@Nullable SelectedDeclaration decl = findSelectedDeclaration(selection);
		if (decl != null) return decl;

		// Otherwise, do a textual match. This allows finding occurrences from
		// a 'reference' in a semantic action or in a regexp but it's a slightly
		// fragile approach of course.
		@Nullable SelectedWord sword = HandlerUtils.selectWord(getDocument(), selection); 
		if (sword == null) return null;
		return findSelectedDeclarationFor(sword.word, selection);
	}
	
	/**
	 * If the selection matches the location of an entity's declaration,
	 * or if the selection is empty and the caret is over the entity's declaration,
	 * this returns the corresponding location. 
	 * 
	 * @param selection
	 * @return the declaration described by the {@code selection}
	 */
	private @Nullable SelectedDeclaration findSelectedDeclaration(ITextSelection selection) {
		if (model == null) return null;
		for (TokenDecl token : model.tokenDecls) {
			if (enclosedInLocation(token.name, selection)) 
				return new SelectedDeclaration(token.name, TokenDecl.class);
		}
		for (PGrammarRule rule : model.rules.values()) {
			if (enclosedInLocation(rule.name, selection))
				return new SelectedDeclaration(rule.name, PGrammarRule.class);
			for (Located<String> param : rule.params) {
				if (enclosedInLocation(param, selection))
					return new SelectedDeclaration(param, FormalDecl.class);
			}
		}
		return null;
	}
}
