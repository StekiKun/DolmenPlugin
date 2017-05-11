package dolmenplugin.editors.jl;

import org.eclipse.jface.text.rules.*;

import dolmenplugin.editors.JavaActionRule;

/**
 * The main partition scanner for Dolmen lexer descriptions.
 * <p>
 * Besides the default partition type, this scanner can also
 * identify comments (both single-line and multi-line), Java
 * semantic actions, and string and character literals.
 * 
 * @author Stéphane Lescuyer
 */
public class JLPartitionScanner extends RuleBasedPartitionScanner {
	public final static String JL_COMMENT = "__jl_comment";
	public final static String JL_JAVA = "__jl_java";
	public final static String JL_LITERAL = "__jl_literal";

	public JLPartitionScanner() {

		IToken jlComment = new Token(JL_COMMENT);
		IToken jlJava = new Token(JL_JAVA);
		IToken jlLiteral = new Token(JL_LITERAL);

		IPredicateRule[] rules = new IPredicateRule[5];

		rules[0] = new EndOfLineRule("//", jlComment);
		rules[1] = new MultiLineRule("/*", "*/", jlComment);
		rules[2] = new SingleLineRule("\"", "\"", jlLiteral, '\\');
		rules[3] = new SingleLineRule("'", "'", jlLiteral, '\\');
		rules[4] = new JavaActionRule(jlJava);

		setPredicateRules(rules);
	}
}
