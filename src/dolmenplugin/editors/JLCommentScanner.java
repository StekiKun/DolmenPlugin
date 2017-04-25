package dolmenplugin.editors;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;

public class JLCommentScanner extends RuleBasedScanner {

	public JLCommentScanner(ColorManager manager) {
		IToken comment =
			new Token(
				new TextAttribute(manager.getColor(IJLColorConstants.COMMENT)));

		setDefaultReturnToken(comment);
		IRule[] rules = new IRule[0];

		// No rule: the partitioning is enough
		setRules(rules);
	}
}
