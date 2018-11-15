package dolmenplugin.editors.jg;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.Token;

import dolmenplugin.editors.DolmenPartitionScanner;
import dolmenplugin.editors.JavaActionRule;
import dolmenplugin.editors.OptionRule;

/**
 * The main partition scanner for Dolmen grammar descriptions.
 * <p>
 * Besides the default partition type, this scanner can also
 * identify configuration options, comments (both single-line 
 * and multi-line), Java semantic actions and Java inlined 
 * arguments.
 * 
 * @author Stéphane Lescuyer
 */
public class JGPartitionScanner extends DolmenPartitionScanner {
	public final static String JG_OPTIONS = "__jg_options";
	public final static String JG_COMMENT = "__jg_comment";
	public final static String JG_JAVA = "__jg_java";
	public final static String JG_ARGS = "__jg_args";

	public final static String[] CONTENT_TYPES = 
		new String[] { JG_OPTIONS, JG_COMMENT, JG_JAVA, JG_ARGS };
	
	public JGPartitionScanner() {
		super(JG_JAVA);
		IToken jgOptions = new Token(JG_OPTIONS);
		IToken jgComment = new Token(JG_COMMENT);
		IToken jgJava = new Token(JG_JAVA);
		IToken jgArgs = new Token(JG_ARGS);

		IPredicateRule[] rules = new IPredicateRule[5];

		rules[0] = new EndOfLineRule("//", jgComment);
		rules[1] = new MultiLineRule("/*", "*/", jgComment);
		rules[2] = new OptionRule(jgOptions);
		// -> the simpler MultiLine rule should be sufficient in .jg files
		//	  since square brackets are not used in other parts of the syntax
		// rules[2] = new MultiLineRule("[", "]", jgOptions);
		rules[3] = new JavaActionRule(jgJava, '{', '}', true);
		rules[4] = new JavaActionRule(jgArgs, '(', ')', true);
		
		setPredicateRules(rules);
	}
}