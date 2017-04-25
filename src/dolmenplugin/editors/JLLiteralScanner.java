package dolmenplugin.editors;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;

public class JLLiteralScanner extends RuleBasedScanner {

	public JLLiteralScanner(ColorManager manager) {
		IToken string =
			new Token(
				new TextAttribute(manager.getColor(IJLColorConstants.STRING)));

		setDefaultReturnToken(string);
		IRule[] rules = new IRule[0];

		// No rule: the partitioning is enough
		setRules(rules);
	}
}
