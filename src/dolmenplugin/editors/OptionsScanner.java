package dolmenplugin.editors;

import java.util.regex.Pattern;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.SWT;

import dolmenplugin.editors.ColorManager;
import dolmenplugin.editors.IColorConstants;
import dolmenplugin.editors.RegexpLineRule;

/**
 * The scanner for the option partitions in Dolmen
 * lexer and grammar descriptions. It is used in
 * both the lexer and parser editors.
 *
 * @author Stéphane Lescuyer
 */
public class OptionsScanner extends RuleBasedScanner {

	public OptionsScanner(ColorManager manager) {
		IToken deflt =
				new Token(
					new TextAttribute(
						manager.getColor(IColorConstants.DEFAULT), null, SWT.BOLD));
		IToken key =
				new Token(
					new TextAttribute(
						manager.getColor(IColorConstants.KEYWORD_OP), null, 0));
		IToken value =
			new Token(
				new TextAttribute(manager.getColor(IColorConstants.STRING)));

		setDefaultReturnToken(deflt);
		IRule[] rules = new IRule[2];

		// Scanning rules for the key and for the string value
		
		final Pattern bindingPattern =
			Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*[ \t\b\f]*=");
		RegexpLineRule bindingsRule =
			new RegexpLineRule(bindingPattern, key,
				ch -> !Character.isJavaIdentifierPart(ch) &&
					!Character.isWhitespace(ch) && ch != '=');
		
		rules[0] = new MultiLineRule("\"", "\"", value, '\\', false);
		rules[1] = bindingsRule;
		
		setRules(rules);
	}
}
