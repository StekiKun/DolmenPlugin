package dolmenplugin.editors.jg;

import org.eclipse.jface.text.rules.*;

import dolmenplugin.editors.JavaActionRule;

/**
 * The main partition scanner for Dolmen grammar descriptions.
 * <p>
 * Besides the default partition type, this scanner can also
 * identify comments (both single-line and multi-line), Java
 * semantic actions and Java inlined arguments.
 * 
 * @author Stéphane Lescuyer
 */
public class JGPartitionScanner extends RuleBasedPartitionScanner {
	public final static String JG_COMMENT = "__jg_comment";
	public final static String JG_JAVA = "__jg_java";
	public final static String JG_ARGS = "__jg_args";

	public JGPartitionScanner() {

		IToken jgComment = new Token(JG_COMMENT);
		IToken jgJava = new Token(JG_JAVA);
		IToken jgArgs = new Token(JG_ARGS);

		IPredicateRule[] rules = new IPredicateRule[4];

		rules[0] = new EndOfLineRule("//", jgComment);
		rules[1] = new MultiLineRule("/*", "*/", jgComment);
		rules[2] = new JavaActionRule(jgJava, '{', '}');
		rules[3] = new JavaActionRule(jgArgs, '(', ')');
		
		setPredicateRules(rules);
	}
}
