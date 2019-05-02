package dolmenplugin.editors.jl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.stekikun.dolmen.codegen.Config;
import org.stekikun.dolmen.codegen.LexBuffer;
import org.stekikun.dolmen.syntax.Lexer;

import dolmenplugin.editors.DolmenCompletionProposal;
import dolmenplugin.editors.DolmenCompletionProposal.Category;
import dolmenplugin.editors.DolmenContentAssistProcessor;

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
	
	@Override
	protected void collectCompletionProposals(ProposalCollector collector, String prefix) {
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
			case JAVA:
				// Try rules in semantic actions
				addPrefixCompletions(collector, prefix, lexer.entryPoints,
					e -> e.name.val + "(",
					(e, i) -> DolmenCompletionProposal.lexerEntry(e, i, collector.offset - i));
				// Try methods from LexBuffer
				addJavaCompletions(collector, prefix, LEXBUFFER_METHODS,
					(m, i) -> DolmenCompletionProposal.method(
								Category.LEXER_METHOD, m[0], m[1], i, collector.offset - i));
				break;
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
			new String[] { "getLexemeEnd()", "getLexemeEnd() : Position" },
			new String[] { "getSubLexeme(start, end)", "getSubLexeme(int, int) : String - LexBuffer" },
			new String[] { "getSubLexemeOpt(start, end)", "getSubLexemeOpt(int, int) : Optional<String> - LexBuffer" },
			new String[] { "getSubLexemeChar(pos)", "getSubLexemeChar(int) : char - LexBuffer" },
			new String[] { "getSubLexemeOptChar(pos)", "getSubLexemeOptChar(int) : Optional<Character> - LexBuffer" },
			new String[] { "newline()", "newline() : void - LexBuffer" },
			new String[] { "error(msg)", "error(String) : LexicalError - LexBuffer" }
		);
	
	private static final List<String> JL_OPTIONS = new ArrayList<>();
	static {
		for (Config.Keys key : Config.Keys.values()) {
			if (key.relevance == Config.Relevance.PARSER) continue;
			JL_OPTIONS.add(key.key);
		}
	}
}
