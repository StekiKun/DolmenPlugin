package dolmenplugin.editors.jg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import codegen.BaseParser;
import codegen.Config;
import dolmenplugin.editors.DolmenCompletionProposal;
import dolmenplugin.editors.DolmenCompletionProposal.Category;
import dolmenplugin.editors.DolmenContentAssistProcessor;
import syntax.PGrammar;
import syntax.PGrammarRule;

/**
 * Implements content-assist proposals for {@link JGEditor}
 * <p>
 * For now, it supports content-assist for two partition types:
 * <ul>
 * <li> default: proposes .jg keywords, declared tokens and grammar rules
 * <li> semantic actions: proposes Java keywords, as well as methods 
 * 		and fields inherited from {@link BaseParser}
 * </ul>
 * 
 * @author Stéphane Lescuyer
 */
public final class JGContentAssistProcessor
	extends DolmenContentAssistProcessor<PGrammar, JGEditor> {

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
	 * Creates a new content-assist processor for the given {@link JGEditor}
	 * and the partitions of the given {@code content_type}
	 * @param editor		must not be {@code null}
	 * @param contentType	
	 */
	public JGContentAssistProcessor(JGEditor editor, ContentType contentType) {
		super(editor);
		this.contentType = contentType;
	}
	
	@Override
	protected void collectCompletionProposals(ProposalCollector collector, String prefix) {
		// Try keywords corresponding to the content type
		switch (contentType) {
		case DEFAULT:
			addSimpleCompletions(collector, prefix, Category.GRAMMAR_KEYWORD, JG_KEYWORDS);
			break;
		case JAVA:
			addSimpleCompletions(collector, prefix, Category.JAVA_KEYWORD, JAVA_KEYWORDS);
			break;
		case OPTIONS:
			addSimpleCompletions(collector, prefix, Category.OPTION_KEY, JG_OPTIONS);
			break;
		}
		final PGrammar grammar = editor.getModel();
		if (grammar != null) {
			switch (contentType) {
			case DEFAULT:
				// Try rules and tokens in grammar rules, as well as the local formals if any
				addPrefixCompletions(collector, prefix, grammar.tokenDecls,
						t -> t.name.val,
						(t, i) -> DolmenCompletionProposal.token(t, i, collector.offset - i));
				addPrefixCompletions(collector, prefix, grammar.rules.values(),
						r -> r.name.val + "(",
						(r, i) -> DolmenCompletionProposal.rule(r, i, collector.offset - i));
				@Nullable PGrammarRule rule = editor.findRuleAtOffset(collector.offset);
				if (rule == null || rule.params.isEmpty()) break;
				addPrefixCompletions(collector, prefix, rule.params,
						f -> f.val,
						(f, i) -> DolmenCompletionProposal.formal(f.val, i, collector.offset - i));
				break;
			case JAVA:
				// Try methods and fields from BaseParser
				addJavaCompletions(collector, prefix, BASEPARSER_METHODS,
						(m, i) -> DolmenCompletionProposal.method(
									Category.PARSER_METHOD, m[0], m[1], i,
									collector.offset - i));
				addJavaCompletions(collector, prefix, BASEPARSER_FIELDS,
						(m, i) -> DolmenCompletionProposal.field(
									Category.PARSER_FIELD, m[0], m[1], i,
									collector.offset - i));
				break;
			case OPTIONS:
				break;
			}
		}
		return;
	}

	private static final List<String> JG_KEYWORDS =
		Arrays.asList(
			"rule", "token", "public", "private", "import", "static", "continue"
		);
	private static final List<String[]> BASEPARSER_METHODS =
		Arrays.asList(
			new String[] { "parsingError(msg)", "parsingError(String) : ParsingException - BaseParser" },
			new String[] { "tokenError(found, expected...)", "tokenError(Object, Object...) : ParsingException - BaseParser" },
			// TODO: filter methods from BaseParser.WithPositions depending on config?
			new String[] { "getStartPos()", "getStartPos() : Position - BaseParser.WithPositions" },
			new String[] { "getEndPos()", "getEndPos() : Position - BaseParser.WithPositions" },
			new String[] { "getSymbolStartPos()", "getSymbolStartPos() : Position - BaseParser.WithPositions" },
			new String[] { "getStartPos(i)", "getStartPos(int) : Position - BaseParser.WithPositions" },
			new String[] { "getEndPos(i)", "getEndPos(int) : Position - BaseParser.WithPositions" },
			new String[] { "getStartPos(id)", "getStartPos(String) : Position - BaseParser.WithPositions" },
			new String[] { "getEndPos(id)", "getEndPos(String) : Position - BaseParser.WithPositions" }
		);
	private static final List<String[]> BASEPARSER_FIELDS =
			Arrays.asList(
				new String[] { "_jl_lexbuf", "LexBuffer" },
				new String[] { "_jl_lastTokenStart", "LexBuffer.Position" },
				new String[] { "_jl_lastTokenEnd", "LexBuffer.Position" }
			);
	private static final List<String> JG_OPTIONS = new ArrayList<>();
	static {
		for (Config.Keys key : Config.Keys.values()) {
			if (key.relevance == Config.Relevance.LEXER) continue;
			JG_OPTIONS.add(key.key);
		}
	}	
}
