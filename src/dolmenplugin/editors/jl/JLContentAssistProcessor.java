package dolmenplugin.editors.jl;

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

import common.Iterables;
import dolmenplugin.Activator;
import syntax.Lexer;
import syntax.Located;

/**
 * WIP
 * 
 * Implements content-assist proposals for {@link JLEditor}
 * 
 * @author Stéphane Lescuyer
 */
public final class JLContentAssistProcessor implements IContentAssistProcessor {

	/**
	 * Content types for which the content-assist process can be configured
	 * 
	 * @author Stéphane Lescuyer
	 */
	public static enum ContentType {
		DEFAULT, JAVA
	}
	
	private final JLEditor editor;
	private final ContentType contentType;
	private String lastErrorMessage = null;
	
	/**
	 * Creates a new content-assist processor for the given {@link JLEditor}
	 * and the partitions of the given {@code content_type}
	 * @param editor		must not be {@code null}
	 * @param contentType	
	 */
	public JLContentAssistProcessor(JLEditor editor, ContentType contentType) {
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
			return fail("Given viewer is not attached to the configured lexer editor");
		}
		
		ProposalCollector collector = new ProposalCollector(doc, offset);
		String prefix = findPrefixAtOffset(doc, offset);
		// Try keywords corresponding to the content type
		switch (contentType) {
		case DEFAULT:
			addPrefixCompletions(collector, prefix, CAT_KEYWORD, JL_KEYWORDS);
			break;
		case JAVA:
			addPrefixCompletions(collector, prefix, CAT_KEYWORD, JAVA_KEYWORDS);
			break;
		}
		final Lexer lexer = editor.getLexer();
		if (lexer != null) {
			switch (contentType) {
			case DEFAULT:
				// Try regexps in lexer rules and regexps
				addPrefixCompletions(collector, prefix, CAT_REGEXP,
						Iterables.transform(lexer.regulars.keySet(), 
											(Located<String> l) -> l.val));
				break;
			case JAVA:
				// Try rules in semantic actions
				addMethodCompletions(collector, prefix, CAT_PRIVATE_ENTRY,
						lexer.entryPoints.stream().filter(e -> !e.visibility)
								.map(JLContentAssistProcessor::completionDescrOfEntry)
								::iterator);
				addMethodCompletions(collector, prefix, CAT_PUBLIC_ENTRY,
						lexer.entryPoints.stream().filter(e -> e.visibility)
								.map(JLContentAssistProcessor::completionDescrOfEntry)
								::iterator);
				// Try methods from LexBuffer
				addMethodCompletions(collector, prefix, CAT_LEXER_METHOD,
						LEXBUFFER_METHODS);
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
			if (!isJLWordPart(ch)) break;
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

	private static boolean isJLWordPart(char ch) {
		if (ch == '_') return true;
		if (ch >= 'a' && ch <= 'z') return true;
		if (ch >= 'A' && ch <= 'Z') return true;
		if (ch >= '0' && ch <= '9') return true;
		return false;
	}
	
	private static String[] completionDescrOfEntry(Lexer.Entry entry) {
		String display = entry.name.val + "(" + 
			(entry.args == null ? "" : entry.args.find()) + ")" +
			" : " + entry.returnType.find();
		String replacement = entry.name.val + 
			(entry.args == null ? "()" : "(?)");
		return new String[] { replacement, display };
	}
	
	private static final List<String> JL_KEYWORDS =
		Arrays.asList(
			"rule", "shortest", "public", "private", "eof",
			"as", "orelse", "import", "static"
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
	private static final List<String[]> LEXBUFFER_METHODS =
		Arrays.asList(
			new String[] { "getLexeme()", "getLexeme() : String" },
			new String[] { "getLexemeStart()", "getLexemeStart() : Position" },
			new String[] { "getLexemeEnd()", "getLexemeEnd() : Position" },
			new String[] { "getSubLexeme(start, end)", "getSubLexeme(int, int) : String" },
			new String[] { "getSubLexemeOpt(start, end)", "getSubLexemeOpt(int, int) : Optional<String>" },
			new String[] { "getSubLexemeChar(pos)", "getSubLexemeChar(int) : char" },
			new String[] { "getSubLexemeOptChar(pos)", "getSubLexemeOptChar(int) : Optional<Character>" },
			new String[] { "newline()", "newline() : void" },
			new String[] { "error(msg)", "error(String) : LexicalError" }
		);
	
	private static final String CAT_KEYWORD = "3_keyword";
	private static final String CAT_REGEXP = "0_regexp";
	private static final String CAT_PRIVATE_ENTRY = "1_pubentry";
	private static final String CAT_PUBLIC_ENTRY = "1_prientry";
	private static final String CAT_LEXER_METHOD = "2_method";
	
	private static final Map<String, String> CAT_IMAGES;
	static {
		CAT_IMAGES = new HashMap<>();
		CAT_IMAGES.put(CAT_KEYWORD, null);
		CAT_IMAGES.put(CAT_REGEXP, "icons/regexp_def.gif");
		CAT_IMAGES.put(CAT_PRIVATE_ENTRY, "icons/lexer_entry_pri.gif");
		CAT_IMAGES.put(CAT_PUBLIC_ENTRY, "icons/lexer_entry_pub.gif");
		CAT_IMAGES.put(CAT_LEXER_METHOD, "icons/protected_method.gif");
	}
	
	private void addPrefixCompletions(ProposalCollector collector, 
		String prefix, String category, Iterable<String> candidates) {
		for (String candidate : candidates) {
			if (candidate.startsWith(prefix)) {
				int pl = prefix.length();
				// Check that the candidate is not already there
				if (collector.startsWith(candidate.substring(pl))) continue;
				JLCompletionProposal proposal = JLCompletionProposal.of(
					category, 
					candidate.substring(pl), collector.offset, 0,
					candidate.length() - pl, candidate);
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
				// parentheses otherwise
				int cursor = match.length() - pl +
					(replacement.length() == 2 + match.length() ? 2 : 1);
				JLCompletionProposal proposal = JLCompletionProposal.of(
					category,
					replacement.substring(pl), collector.offset, 0,
					cursor, display);
				collector.add(proposal);
			}
		}
	}

	private /* non-static */ class ProposalCollector {
		private List<JLCompletionProposal> proposals;
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
		
		void add(JLCompletionProposal prop) {
			if (proposals == null) {
				proposals = new ArrayList<>();
			}
			proposals.add(prop);
		}
		
		JLCompletionProposal[] collect() {
			if (proposals == null) return null;
			return proposals.toArray(new JLCompletionProposal[proposals.size()]);
		}
	}
	
	private static class JLCompletionProposal 
		implements ICompletionProposal, ICompletionProposalExtension6,
				   Comparable<JLCompletionProposal> {
		final String category;
		final CompletionProposal delegate;
		
		private JLCompletionProposal(
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
		
		static JLCompletionProposal of(
			String category, String replacementString, int replacementOffset, int replacementLength,
			int cursorPosition, String displayString) {
			String imagePath = CAT_IMAGES.get(category);
			Image image = imagePath == null ? null : Activator.getImage(imagePath);
			return new JLCompletionProposal(category,
				new CompletionProposal(
					replacementString, replacementOffset, replacementLength, 
					cursorPosition, image, displayString, null, null));
		}

		@Override
		public int compareTo(JLCompletionProposal o) {
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
				if (p1 instanceof JLCompletionProposal &&
					p2 instanceof JLCompletionProposal) {
					return ((JLCompletionProposal) p1).compareTo((JLCompletionProposal) p2);
				}
				return 0;
			}
		};
}
