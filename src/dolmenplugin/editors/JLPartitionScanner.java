package dolmenplugin.editors;

import org.eclipse.jface.text.rules.*;

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
