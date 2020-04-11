package org.stekikun.dolmenplugin.editors.jl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.stekikun.dolmen.codegen.Config;
import org.stekikun.dolmen.codegen.LexBuffer;
import org.stekikun.dolmen.common.Lists;
import org.stekikun.dolmen.syntax.Lexer;
import org.stekikun.dolmenplugin.editors.DolmenCompletionProposal;
import org.stekikun.dolmenplugin.editors.DolmenContentAssistProcessor;
import org.stekikun.dolmenplugin.editors.DolmenCompletionProposal.Category;

/**
 * Implements content-assist proposals for {@link JLEditor}
 * <p>
 * For now, it supports content-assist for three partition types:
 * <ul>
 * <li> default: proposes .jl keywords and defined regexps
 * <li> semantic actions: proposes Java keywords, lexer entries and
 * 		methods inherited from {@link LexBuffer}
 * <li> configuration options: proposes option names supported in lexers
 * </ul>
 * 
 * @author Stéphane Lescuyer
 */
public final class JLContentAssistProcessor
	extends DolmenContentAssistProcessor<Lexer, JLEditor>{

	/**
	 * Content types for which the content-assist process can be configured
	 * 
	 * @author Stéphane Lescuyer
	 */
	public static enum ContentType {
		DEFAULT, JAVA, OPTIONS
	}
	
	private final ContentType contentType;
	
	/**
	 * Creates a new content-assist processor for the given {@link JLEditor}
	 * and the partitions of the given {@code content_type}
	 * @param editor		must not be {@code null}
	 * @param contentType	
	 */
	public JLContentAssistProcessor(JLEditor editor, ContentType contentType) {
		super(editor);
		this.contentType = contentType;
	}
	
	/**
	 * @param document
	 * @param prefix
	 * @return whether {@code prefix} was preceded by a "continue" keyword
	 * 	in {@code document}, as this may help us propose more adapted
	 *  completion proposals
	 */
	private boolean followsContinue(IDocument document, Prefix prefix) {
		int offset = prefix.offset;
		int n = "continue ".length();
		if (offset >= n) {
			String prec;
			try {
				prec = document.get(offset - n, n);
			} catch (BadLocationException e) {
				return false;
			}
			return (prec.trim().endsWith("continue"));
		}
		return false;
	}
	
	/**
	 * @param lexer
	 * @param offset
	 * @return the lexer entry in which the semantic action at
	 * 	offset {@code offset} lies, or {@code null} if it does not
	 *  seem to belong to a semantic action in a rule
	 */
	private Lexer.@Nullable Entry findCurrentRule(Lexer lexer, int offset) {
		Lexer.@Nullable Entry prev = null;
		for (Lexer.Entry entry : lexer.entryPoints) {
			if (entry.name.start.offset > offset) return prev;
			prev = entry;
		}
		return null;
	}
	
	@Override
	protected void collectCompletionProposals(ProposalCollector collector, 
			IDocument document, Prefix prefix_) {
		final String prefix = prefix_.prefix;
		// Try keywords corresponding to the content type, and recognized options
		switch (contentType) {
		case DEFAULT:
			addSimpleCompletions(collector, prefix, Category.LEXER_KEYWORD, JL_KEYWORDS);
			break;
		case JAVA:
			addSimpleCompletions(collector, prefix, Category.JAVA_KEYWORD, JAVA_KEYWORDS);
			break;
		case OPTIONS:
			addSimpleCompletions(collector, prefix, Category.OPTION_KEY, JL_OPTIONS);
			break;
		}
		final Lexer lexer = editor.getModel();
		if (lexer != null) {
			switch (contentType) {
			case DEFAULT:
				// Try regexps in lexer rules and regexps
				addPrefixCompletions(collector, prefix, lexer.regulars.entrySet(),
					e -> e.getKey().val,
					(e, i) -> DolmenCompletionProposal.regexp(
									e.getKey().val, e.getValue(), i, collector.offset - i));
				break;
			case JAVA: {
				if (followsContinue(document, prefix_)) {
					// If we follow a continue statement, propose the current rule
					// without arguments
					Lexer.@Nullable Entry entry = findCurrentRule(lexer, prefix_.offset);
					if (entry != null) {
						addPrefixCompletions(collector, prefix,
							Lists.singleton(entry),
							e -> e.name.val + ";",
							(e, i) -> DolmenCompletionProposal.lexerContinue(entry, i, collector.offset - i));
						break;
					}
					// Otherwise fall back to default as we are in the prelude or postlude
				}
				// Try rules in semantic actions
				addPrefixCompletions(collector, prefix, lexer.entryPoints,
					e -> e.name.val + "(",
					(e, i) -> DolmenCompletionProposal.lexerEntry(e, i, collector.offset - i));
				// Try methods from LexBuffer
				addJavaCompletions(collector, prefix, LEXBUFFER_METHODS,
					(m, i) -> DolmenCompletionProposal.method(
								Category.LEXER_METHOD, m[0], m[1], i, collector.offset - i));
				break;
			}
			case OPTIONS:
				break;
			}
		}
	}

	private static final List<String> JL_KEYWORDS =
		Arrays.asList(
			"rule", "shortest", "public", "private", "eof",
			"as", "orelse", "import", "static"
		);

	private static final List<String[]> LEXBUFFER_METHODS =
		Arrays.asList(
			new String[] { "getLexeme()", "getLexeme() : String - LexBuffer" },
			new String[] { "getLexemeStart()", "getLexemeStart() : Position - LexBuffer" },
			new String[] { "getLexemeEnd()", "getLexemeEnd() : Position - LexBuffer" },
			new String[] { "getLexemeLength()", "getLexemeLength() : int - LexBuffer" },
			new String[] { "getLexemeCharAt(idx)", "getLexemeCharAt(int) : char - LexBuffer" },
			new String[] { "getLexemeChars()", "getLexemeChars() : CharSequence - LexBuffer" },
			new String[] { "appendLexeme(buf)", "appendLexeme(StringBuilder) : void - LexBuffer" },
//	These methods are accessible but are meant for used by the generator
//			new String[] { "getSubLexeme(start, end)", "getSubLexeme(int, int) : String - LexBuffer" },
//			new String[] { "getSubLexemeOpt(start, end)", "getSubLexemeOpt(int, int) : Optional<String> - LexBuffer" },
//			new String[] { "getSubLexemeChar(pos)", "getSubLexemeChar(int) : char - LexBuffer" },
//			new String[] { "getSubLexemeOptChar(pos)", "getSubLexemeOptChar(int) : Optional<Character> - LexBuffer" },
			new String[] { "newline()", "newline() : void - LexBuffer" },
			new String[] { "error(msg)", "error(String) : LexicalError - LexBuffer" },
			new String[] { "savePosition(supplier, pos)", "savePosition(Supplier<T>, Position) : T - LexBuffer" },
			new String[] { "savePosition(supplier)", "saveStart(Supplier<T>) : T - LexBuffer" },
			new String[] { "savePosition(runnable, pos)", "savePosition(Runnable, Position) : void - LexBuffer" },
			new String[] { "savePosition(runnable)", "saveStart(Runnable) : void - LexBuffer" },
			new String[] { "peekNextChar()", "peekNextChar() : char - LexBuffer" },
			new String[] { "peekNextChars(buf)", "peekNextChars(char[]) : int - LexBuffer" }
		);
	
	private static final List<String> JL_OPTIONS = new ArrayList<>();
	static {
		for (Config.Keys key : Config.Keys.values()) {
			if (key.relevance == Config.Relevance.PARSER) continue;
			JL_OPTIONS.add(key.key);
		}
	}
}
