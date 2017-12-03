package dolmenplugin.editors.jg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.ICompletionProposalSorter;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import dolmenplugin.Activator;
import syntax.Grammar;
import syntax.GrammarRule;
import syntax.Grammar.TokenDecl;

/**
 * WIP
 * 
 * Implements content-assist proposals for {@link JGEditor}
 * 
 * @author Stéphane Lescuyer
 */
public final class JGContentAssistProcessor implements IContentAssistProcessor {

	/**
	 * Content types for which the content-assist process can be configured
	 * 
	 * @author Stéphane Lescuyer
	 */
	public static enum ContentType {
		DEFAULT, JAVA
	}
	
	private final JGEditor editor;
	private final ContentType contentType;
	private String lastErrorMessage = null;
	
	/**
	 * Creates a new content-assist processor for the given {@link JGEditor}
	 * and the partitions of the given {@code content_type}
	 * @param editor		must not be {@code null}
	 * @param contentType	
	 */
	public JGContentAssistProcessor(JGEditor editor, ContentType contentType) {
		if (editor == null)
			throw new IllegalArgumentException(
				"Cannot create content-assist processor with null editor");
		this.editor = editor;
		this.contentType = contentType;
	}
	
	private ICompletionProposal[] fail(String message) {
		lastErrorMessage = message;
		return null;
	}
	
	@SuppressWarnings("unused")
	private ICompletionProposal[] failf(String format, Object ...objects) {
		return fail(String.format(format, objects));
	}
	
	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		if (viewer == null) return null;
		lastErrorMessage = null; // in case of success
		final IDocument doc = viewer.getDocument();
		if (!doc.equals(editor.getDocument())) {
			return fail("Given viewer is not attached to the configured grammar editor");
		}
		
		ProposalCollector collector = new ProposalCollector(doc, offset);
		String prefix = findPrefixAtOffset(doc, offset);
		// Try keywords corresponding to the content type
		switch (contentType) {
		case DEFAULT:
			addPrefixCompletions(collector, prefix, CAT_KEYWORD, JG_KEYWORDS);
			break;
		case JAVA:
			addPrefixCompletions(collector, prefix, CAT_KEYWORD, JAVA_KEYWORDS);
			break;
		}
		final Grammar grammar = editor.getGrammar();
		if (grammar != null) {
			switch (contentType) {
			case DEFAULT:
				// Try rules and tokens in grammar rules
				addFieldCompletions(collector, prefix, CAT_TOKEN,
					grammar.tokenDecls.stream().filter(e -> !e.isValued())
						.map(JGContentAssistProcessor::completionDescrOfToken)
						::iterator);
				addFieldCompletions(collector, prefix, CAT_TOKEN_VALUED,
						grammar.tokenDecls.stream().filter(e -> e.isValued())
							.map(JGContentAssistProcessor::completionDescrOfToken)
							::iterator);
				addMethodCompletions(collector, prefix, CAT_PUBLIC_RULE,
						grammar.rules.values().stream().filter(r -> r.visibility)
							.map(JGContentAssistProcessor::completionDescrOfRule)
							::iterator);
				addMethodCompletions(collector, prefix, CAT_PRIVATE_RULE,
						grammar.rules.values().stream().filter(r -> !r.visibility)
							.map(JGContentAssistProcessor::completionDescrOfRule)
							::iterator);
				break;
			case JAVA:
				// Try methods and fields from BaseParser
				addMethodCompletions(collector, prefix, CAT_PARSER_METHOD,
					BASEPARSER_METHODS);
				addFieldCompletions(collector, prefix, CAT_PARSER_FIELD,
						BASEPARSER_FIELDS);
				break;
			}
		}
		return collector.collect();
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		// No context information for now
		return null;
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		// Activate completion on Ctrl+space
		return new char[] {' '};
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		// No context information for now
		return null;
	}

	@Override
	public String getErrorMessage() {
		return lastErrorMessage;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		// No context information for now
		return null;
	}
	
	private static String findPrefixAtOffset(IDocument document, int offset) {
		int soffset = offset - 1;
		while (soffset >= 0) {
			char ch;
			try {
				ch = document.getChar(soffset);
			} catch (BadLocationException e) {
				e.printStackTrace();
				return "";
			}
			if (!isJGWordPart(ch)) break;
			--soffset;
		}
		++soffset;
		if (soffset >= offset) return "";
		try {
			String prefix = document.get(soffset, offset - soffset);
			// System.out.println("[JL Completion] Prefix found: " + prefix);
			return prefix;
		} catch (BadLocationException e) {
			e.printStackTrace();
			return "";
		}
	}

	private static boolean isJGWordPart(char ch) {
		if (ch == '_') return true;
		if (ch >= 'a' && ch <= 'z') return true;
		if (ch >= 'A' && ch <= 'Z') return true;
		if (ch >= '0' && ch <= '9') return true;
		return false;
	}

	private static String[] completionDescrOfToken(TokenDecl decl) {
		String display = decl.name.val +
			(decl.valueType == null ? "" : " : " + decl.valueType.find());
		String replacement = decl.name.val;
		return new String[] { replacement, display };
	}

	private static String[] completionDescrOfRule(GrammarRule rule) {
		String display = rule.name.val + "(" + 
			(rule.args == null ? "" : rule.args.find()) + ")" +
			" : " + rule.returnType.find();
		String replacement = rule.name.val + 
			(rule.args == null ? "()" : "(?)");
		return new String[] { replacement, display };
	}
	
	private static final List<String> JG_KEYWORDS =
		Arrays.asList(
			"rule", "token", "public", "private", "import", "static"
		);
	private static final List<String> JAVA_KEYWORDS =
		Arrays.asList(
			"abstract", "continue", "for", "new", "switch",
			"assert", "default", "if", "package", "synchronized",
			"boolean", "do",         "goto",         "private",    "this",
			"break",      "double",     "implements",   "protected",   "throw",
			"byte",       "else",       "import",       "public",     "throws",
			"case",       "enum",      "instanceof",   "return",      "transient",
			"catch",      "extends",    "int",          "short",       "try",
			"char",     "final",      "interface",    "static",      "void",
			"class",      "finally",    "long",         "strictfp",    "volatile",
			"const",      "float",      "native",       "super",       "while",
			"true", "false", "null"
		);
	private static final List<String[]> BASEPARSER_METHODS =
		Arrays.asList(
			new String[] { "parsingError(msg)", "parsingError(String) : ParsingException" },
			new String[] { "tokenError(found, expected...)", "tokenError(Object, Object...) : ParsingException" }
			// TODO: add methods from BaseParser.WithPositions depending on config?
		);
	private static final List<String[]> BASEPARSER_FIELDS =
			Arrays.asList(
				new String[] { "_jl_lexbuf", "_jl_lexbuf : LexBuffer" },
				new String[] { "_jl_lastTokenStart", "_jl_lastTokenStart : LexBuffer.Position" },
				new String[] { "_jl_lastTokenEnd", "_jl_lastTokenEnd : LexBuffer.Position" }
			);
	
	private static final String CAT_KEYWORD = "3_keyword";
	private static final String CAT_TOKEN = "0_token";
	private static final String CAT_TOKEN_VALUED = "0_token_valued";
	private static final String CAT_PRIVATE_RULE = "1_pubrule";
	private static final String CAT_PUBLIC_RULE = "1_prirule";
	private static final String CAT_PARSER_METHOD = "2_method";
	private static final String CAT_PARSER_FIELD = "2_field";
	
	private static final Map<String, String> CAT_IMAGES;
	static {
		CAT_IMAGES = new HashMap<>();
		CAT_IMAGES.put(CAT_KEYWORD, null);
		CAT_IMAGES.put(CAT_TOKEN, "icons/token_decl.gif");
		CAT_IMAGES.put(CAT_TOKEN_VALUED, "icons/token_decl_valued.gif");
		CAT_IMAGES.put(CAT_PRIVATE_RULE, "icons/rule_pri.gif");
		CAT_IMAGES.put(CAT_PUBLIC_RULE, "icons/rule_pub.gif");
		CAT_IMAGES.put(CAT_PARSER_METHOD, "icons/protected_method.gif");
		CAT_IMAGES.put(CAT_PARSER_FIELD, "icons/protected_field.gif");
	}
	
	private void addPrefixCompletions(ProposalCollector collector, 
		String prefix, String category, Iterable<String> candidates) {
		for (String candidate : candidates) {
			if (candidate.startsWith(prefix)) {
				int pl = prefix.length();
				// Check that the candidate is not already there
				if (collector.startsWith(candidate.substring(pl))) continue;
				JGCompletionProposal proposal = JGCompletionProposal.of(
					category, 
					candidate.substring(pl), collector.offset, 0,
					candidate.length() - pl, candidate);
				collector.add(proposal);
			}
		}
	}

	private void addFieldCompletions(ProposalCollector collector, 
			String prefix, String category, Iterable<String[]> candidates) {
		for (String[] candidate : candidates) {
			String replacement = candidate[0];
			String display = candidate[1];
			if (replacement.startsWith(prefix)) {
				int pl = prefix.length();
				// Check that the candidate is not already there
				if (collector.startsWith(replacement.substring(pl))) continue;
				JGCompletionProposal proposal = JGCompletionProposal.of(
					category, 
					replacement.substring(pl), collector.offset, 0,
					replacement.length() - pl, display);
				collector.add(proposal);
			}
		}
	}

	private void addMethodCompletions(ProposalCollector collector, 
		String prefix, String category, Iterable<String[]> candidates) {
		for (String[] candidate : candidates) {
			String replacement = candidate[0];
			String display = candidate[1];
			String match = replacement.substring(0, replacement.indexOf('('));
			if (match.startsWith(prefix)) {
				int pl = prefix.length();
				// Check that the candidate is not already there
				if (collector.startsWith(match.substring(pl))) continue;
				// cursor before first parameter if any, after closing
				// parentheses otherwise, trim '?' if any
				int cursor = match.length() - pl +
					(replacement.length() == 2 + match.length() ? 2 : 1);
				if (replacement.charAt(match.length() + 1) == '?') {
					replacement = match + "(" + replacement.substring(match.length() + 2);
				}
				JGCompletionProposal proposal = JGCompletionProposal.of(
					category,
					replacement.substring(pl), collector.offset, 0,
					cursor, display);
				collector.add(proposal);
			}
		}
	}

	private /* non-static */ class ProposalCollector {
		private List<JGCompletionProposal> proposals;
		final IDocument doc;
		final int offset;
		
		ProposalCollector(IDocument doc, int offset) {
			this.doc = doc;
			this.offset = offset;
			this.proposals = null;
		}
		
		boolean startsWith(String s) {
			try {
				String p = doc.get(offset, s.length());
				return (p.equals(s));
			} catch (BadLocationException e) {
				return false;
			}
		}
		
		void add(JGCompletionProposal prop) {
			if (proposals == null) {
				proposals = new ArrayList<>();
			}
			proposals.add(prop);
		}
		
		JGCompletionProposal[] collect() {
			if (proposals == null) return null;
			return proposals.toArray(new JGCompletionProposal[proposals.size()]);
		}
	}
	
	private static class JGCompletionProposal 
		implements ICompletionProposal, ICompletionProposalExtension6,
				   Comparable<JGCompletionProposal> {
		final String category;
		final CompletionProposal delegate;
		
		private JGCompletionProposal(
				String category, CompletionProposal delegate) {
			this.category = category;
			this.delegate = delegate;
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
			return delegate.getDisplayString();
		}
		
		@Override
		public StyledString getStyledDisplayString() {
			return new StyledString(getDisplayString(), StyledString.DECORATIONS_STYLER);
		}

		@Override
		public Image getImage() {
			return delegate.getImage();
		}

		@Override
		public IContextInformation getContextInformation() {
			return delegate.getContextInformation();
		}
		
		static JGCompletionProposal of(
			String category, String replacementString, int replacementOffset, int replacementLength,
			int cursorPosition, String displayString) {
			String imagePath = CAT_IMAGES.get(category);
			Image image = imagePath == null ? null : Activator.getImage(imagePath);
			return new JGCompletionProposal(category,
				new CompletionProposal(
					replacementString, replacementOffset, replacementLength, 
					cursorPosition, image, displayString, null, null));
		}

		@Override
		public int compareTo(JGCompletionProposal o) {
			int c = category.compareTo(o.category);
			if (c != 0) return c;
			return delegate.getDisplayString().compareToIgnoreCase(
					o.delegate.getDisplayString());
		}
	}
	
	/**
	 * A function to sort completion proposals
	 */
	public static final ICompletionProposalSorter SORTER =
		new ICompletionProposalSorter() {
			@Override
			public int compare(ICompletionProposal p1, ICompletionProposal p2) {
				if (p1 instanceof JGCompletionProposal &&
					p2 instanceof JGCompletionProposal) {
					return ((JGCompletionProposal) p1).compareTo((JGCompletionProposal) p2);
				}
				return 0;
			}
		};
}
