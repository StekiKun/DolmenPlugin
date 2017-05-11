package dolmenplugin.editors.jl;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;

import dolmenplugin.editors.ColorManager;
import dolmenplugin.editors.IColorConstants;

/**
 * The scanner for the comment partitions in Dolmen
 * lexer descriptions.
 *
 * @author Stéphane Lescuyer
 */
public class JLCommentScanner extends RuleBasedScanner {

	public JLCommentScanner(ColorManager manager) {
		IToken comment =
			new Token(
				new TextAttribute(manager.getColor(IColorConstants.COMMENT)));

		setDefaultReturnToken(comment);
		IRule[] rules = new IRule[0];

		// No rule: the partitioning is enough
		setRules(rules);
	}
}
