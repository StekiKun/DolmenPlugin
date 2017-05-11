package dolmenplugin.editors.jg;

import org.eclipse.jface.text.rules.*;

import dolmenplugin.editors.JavaActionRule;

/**
 * The main partition scanner for Dolmen grammar descriptions.
 * <p>
 * Besides the default partition type, this scanner can also
 * identify comments (both single-line and multi-line), Java
 * semantic actions, and string and character literals.
 * 
 * @author Stéphane Lescuyer
 */
public class JGPartitionScanner extends RuleBasedPartitionScanner {
	public final static String JG_COMMENT = "__jg_comment";
	public final static String JG_JAVA = "__jg_java";

	public JGPartitionScanner() {

		IToken jgComment = new Token(JG_COMMENT);
		IToken jgJava = new Token(JG_JAVA);

		IPredicateRule[] rules = new IPredicateRule[3];

		rules[0] = new EndOfLineRule("//", jgComment);
		rules[1] = new MultiLineRule("/*", "*/", jgComment);
//		rules[2] = new SingleLineRule("\"", "\"", jgLiteral, '\\');
//		rules[3] = new SingleLineRule("'", "'", jgLiteral, '\\');
		rules[2] = new JavaActionRule(jgJava);

		setPredicateRules(rules);
	}
}
