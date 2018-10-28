package dolmenplugin.editors;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension7;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import dolmenplugin.Activator;
import dolmenplugin.base.Images;
import syntax.Grammar.TokenDecl;
import syntax.GrammarRule;
import syntax.Lexer;

/**
 * Common implementation of {@link ICompletionProposal} for completions
 * proposed by content-assistants in .jl and .jg editors.
 * <p>
 * It delegates most of its job to an instance of {@link CompletionProposal}
 * but defines a few specific sub-classes for the various typical kinds
 * of completions proposed by the Dolmen plugin.
 * 
 * @see #keyword(Category, String, int)
 * @see #regexp(String, syntax.Regular, int)
 * @see #lexerEntry(syntax.Lexer.Entry, int)
 * @see #token(TokenDecl, int, int)
 * @see #rule(GrammarRule, int, int)
 * @see #method(Category, String, String, int, int)
 * @see #field(Category, String, String, int, int)
 * 
 * @author Stéphane Lescuyer
 */
public abstract class DolmenCompletionProposal
		implements ICompletionProposal, ICompletionProposalExtension7, 
				Comparable<DolmenCompletionProposal> {
	
	/**
	 * The different categories of completion proposals generated
	 * by Dolmen. Each category is given an {@link #ordinal} number;
	 * proposals are ordered by their category ordinal in the UI popup.
	 * 
	 * @author Stéphane Lescuyer
	 */
	public static enum Category {
		JAVA_KEYWORD(3),
		LEXER_KEYWORD(3),
		GRAMMAR_KEYWORD(3),
		
		OPTION_KEY(0),
		
		REGEXP(0),
		LEXER_ENTRY(1),
		LEXER_METHOD(2),
		
		TOKEN(0),
		GRAMMAR_RULE(1),
		PARSER_METHOD(2),
		PARSER_FIELD(2);
		
		public final int ordinal;
		
		private Category(int ordinal) {
			this.ordinal = ordinal;
		}
	}
	
	/** The category to which this category belongs */
	public final Category category;
	
	private final CompletionProposal delegate;
	/** The styled display string used to present the proposal */
	private final StyledString displayString;

	private DolmenCompletionProposal(Category category,
			CompletionProposal delegate, StyledString displayString) {
		this.category = category;
		this.delegate = delegate;
		this.displayString = displayString;
	}
	
	private DolmenCompletionProposal(Category category,
		String replacement, int replacementOffset, int replacementLength, int cursorPosition,
		@Nullable String imagePath, StyledString displayString) {
		this(category, 
			new CompletionProposal(replacement, replacementOffset, replacementLength, cursorPosition,
					imagePath == null ? null : Activator.getImage(imagePath), null, null, null),
			displayString);
	}

	@Override
	public void apply(IDocument document) {
		delegate.apply(document);
	}

	@Override
	public Point getSelection(IDocument document) {
		return delegate.getSelection(document);
	}

	@Override
	public String getAdditionalProposalInfo() {
		return delegate.getAdditionalProposalInfo();
	}

	@Override
	public String getDisplayString() {
		return displayString.getString();
	}

	@Override
	public StyledString getStyledDisplayString(IDocument document, 
			int offset, BoldStylerProvider boldStylerProvider) {
		return displayString;
	}

	@Override
	public Image getImage() {
		return delegate.getImage();
	}

	@Override
	public IContextInformation getContextInformation() {
		return delegate.getContextInformation();
	}

	@Override
	public final int compareTo(DolmenCompletionProposal o) {
		int c = category.ordinal - o.category.ordinal;
		if (c != 0)	return c;
		return getDisplayString().compareToIgnoreCase(o.getDisplayString());
	}
	
	// Keyword, regexp definition, lexer entry, token decl, grammar rule, java method, java field
	
	private static final class Keyword extends DolmenCompletionProposal {
		@SuppressWarnings("unused")
		final String keyword;
		
		private Keyword(Category category, String keyword, int offset, int length) {
			super(category, keyword, offset, length, 
					keyword.length(), null, new StyledString(keyword));
			this.keyword = keyword;
		}
	}
	public static DolmenCompletionProposal 
		keyword(Category category, String keyword, int offset, int length) {
		return new Keyword(category, keyword, offset, length);
	}
	
	private static final class Regexp extends DolmenCompletionProposal {
		@SuppressWarnings("unused")
		final String name;
		@SuppressWarnings("unused")
		final syntax.Regular regexp;
		
		private Regexp(String name, syntax.Regular regexp, int offset, int length) {
			super(Category.REGEXP, name, offset, length,
					name.length(), Images.REGEXP_DEF, new StyledString(name));
			this.name = name;
			this.regexp = regexp;
		}
	}
	public static DolmenCompletionProposal
		regexp(String name, syntax.Regular regexp, int offset, int length) {
		return new Regexp(name, regexp, offset, length);
	}
	
	private static final class LexerEntry extends DolmenCompletionProposal {
		@SuppressWarnings("unused")
		final Lexer.Entry entry;
		
		private LexerEntry(Lexer.Entry entry, int offset, int length) {
			super(Category.LEXER_ENTRY, entry.name.val + "()", offset, length,
					cursor(entry),
					Images.LEXER_ENTRY(entry.visibility), 
					display(entry));
			this.entry = entry;
		}
		
		private static int cursor(Lexer.Entry entry) {
			int c = entry.name.val.length() + 2;
			if (entry.args != null) c--;
			return c;
		}
		private static StyledString display(Lexer.Entry entry) {
			StyledString display = new StyledString(
				entry.name.val + "(" + (entry.args == null ? "" : entry.args.find()) + ")");
			display.append(			
				" : " + entry.returnType.find().trim(),
				StyledString.DECORATIONS_STYLER);
			return display;
		}
	}
	public static DolmenCompletionProposal
		lexerEntry(Lexer.Entry entry, int offset, int length) {
		return new LexerEntry(entry, offset, length);
	}
	
	private static final class Token extends DolmenCompletionProposal {
		@SuppressWarnings("unused")
		final TokenDecl tokenDecl;
		
		private Token(TokenDecl tokenDecl, int offset, int length) {
			super(Category.TOKEN, tokenDecl.name.val, offset, length,
					tokenDecl.name.val.length(),
					Images.TOKEN_DECL(tokenDecl.isValued()), 
					display(tokenDecl));
			this.tokenDecl = tokenDecl;
		}
		
		private static StyledString display(TokenDecl decl) {
			StyledString display = new StyledString(decl.name.val);
			if (decl.valueType != null) 
				display.append(" : " + decl.valueType.find().trim(), 
						StyledString.DECORATIONS_STYLER);
			return display;
		}
	}
	public static DolmenCompletionProposal
		token(TokenDecl tokenDecl, int offset, int length) {
		return new Token(tokenDecl, offset, length);
	}

	private static final class Rule extends DolmenCompletionProposal {
		private Rule(GrammarRule rule, int offset, int length) {
			super(Category.GRAMMAR_RULE, rule.name.val + "()", offset, length,
					cursor(rule),
					Images.RULE(rule.visibility), 
					display(rule));
		}
		
		private static int cursor(GrammarRule rule) {
			int c = rule.name.val.length() + 2;
			if (rule.args != null) c--;
			return c;
		}
		private static StyledString display(GrammarRule rule) {
			StyledString display =
				new StyledString(rule.name.val + "(" + 
					(rule.args == null ? "" : rule.args.find()) + ")");
			display.append(" : " + rule.returnType.find().trim(),
					StyledString.DECORATIONS_STYLER);
			return display;
		}
	}
	public static DolmenCompletionProposal
		rule(GrammarRule rule, int offset, int length) {
		return new Rule(rule, offset, length);
	}
	
	private static final class Method extends DolmenCompletionProposal {
		private Method(Category category, String template, String prototype, 
				int offset, int length) {
			super(category, template, offset, length,
					cursor(template), Images.PROTECTED_METHOD, 
					display(prototype));
		}
		
		private static int cursor(String template) {
			if (template.endsWith("()")) return template.length();
			return template.indexOf('(') + 1;
		}
		private static StyledString display(String prototype) {
			int s = prototype.indexOf('-');
			if (s < 0) return new StyledString(prototype);
			StyledString res = new StyledString(prototype.substring(0, s));
			res.append(prototype.substring(s), StyledString.QUALIFIER_STYLER);
			return res;
		}
	}
	public static DolmenCompletionProposal
		method(Category category, String template, String prototype, int offset, int length) {
		return new Method(category, template, prototype, offset, length);
	}

	private static final class Field extends DolmenCompletionProposal {
		private Field(Category category, String name, String type, int offset, int length) {
			super(category, name, offset, length, 
					name.length(), Images.PROTECTED_FIELD, 
					display(name, type));
		}
		
		private static StyledString display(String name, String type) {
			StyledString res = new StyledString(name);
			res.append(" : " + type, StyledString.DECORATIONS_STYLER);
			return res;
		}
	}
	public static DolmenCompletionProposal
		field(Category category, String name, String type, int offset, int length) {
		return new Field(category, name, type, offset, length);
	}
}