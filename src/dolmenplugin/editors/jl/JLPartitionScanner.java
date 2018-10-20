package dolmenplugin.editors.jl;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;

import dolmenplugin.editors.DolmenPartitionScanner;
import dolmenplugin.editors.JavaActionRule;
import dolmenplugin.editors.OptionRule;

/**
 * The main partition scanner for Dolmen lexer descriptions.
 * <p>
 * Besides the default partition type, this scanner can also
 * identify comments (both single-line and multi-line), Java
 * semantic actions, and string and character literals.
 * 
 * @author Stéphane Lescuyer
 */
public class JLPartitionScanner extends DolmenPartitionScanner {
	public final static String JL_OPTIONS = "__jl_options";
	public final static String JL_COMMENT = "__jl_comment";
	public final static String JL_JAVA = "__jl_java";
	public final static String JL_LITERAL = "__jl_literal";

	public final static String[] CONTENT_TYPES =
		new String[] { JL_OPTIONS, JL_COMMENT, JL_LITERAL, JL_JAVA };
	
	public JLPartitionScanner() {
		super(JL_JAVA);
		IToken jlOptions = new Token(JL_OPTIONS);
		IToken jlComment = new Token(JL_COMMENT);
		IToken jlJava = new Token(JL_JAVA);
		IToken jlLiteral = new Token(JL_LITERAL);

		IPredicateRule[] rules = new IPredicateRule[6];

		rules[0] = new EndOfLineRule("//", jlComment);
		rules[1] = new MultiLineRule("/*", "*/", jlComment);
		rules[2] = new SingleLineRule("\"", "\"", jlLiteral, '\\');
		rules[3] = new SingleLineRule("'", "'", jlLiteral, '\\');
		rules[4] = new OptionRule(jlOptions);
		rules[5] = new JavaActionRule(jlJava, '{', '}');

		setPredicateRules(rules);
	}
}
