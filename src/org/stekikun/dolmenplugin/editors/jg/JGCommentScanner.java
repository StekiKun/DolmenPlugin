package org.stekikun.dolmenplugin.editors.jg;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.stekikun.dolmenplugin.editors.ColorManager;
import org.stekikun.dolmenplugin.editors.IColorConstants;

/**
 * The scanner for the comment partitions in Dolmen
 * grammar descriptions.
 *
 * @author Stéphane Lescuyer
 */
public class JGCommentScanner extends RuleBasedScanner {

	public JGCommentScanner(ColorManager manager) {
		IToken comment =
			new Token(
				new TextAttribute(manager.getColor(IColorConstants.COMMENT)));

		setDefaultReturnToken(comment);
		IRule[] rules = new IRule[0];

		// No rule: the partitioning is enough
		setRules(rules);
	}
}
