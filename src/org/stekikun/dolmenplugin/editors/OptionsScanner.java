package org.stekikun.dolmenplugin.editors;

import java.util.regex.Pattern;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.PatternRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.SWT;
import org.stekikun.dolmenplugin.editors.ColorManager;
import org.stekikun.dolmenplugin.editors.IColorConstants;
import org.stekikun.dolmenplugin.editors.RegexpLineRule;

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
						manager.getColor(IColorConstants.DEFAULT)));
		IToken symbols =
				new Token(
					new TextAttribute(
						manager.getColor(IColorConstants.DEFAULT), null, SWT.BOLD));
		IToken comment =
				new Token(
					new TextAttribute(
						manager.getColor(IColorConstants.COMMENT)));
		IToken key =
				new Token(
					new TextAttribute(
						manager.getColor(IColorConstants.KEYWORD_OP), null, 0));
		IToken value =
			new Token(
				new TextAttribute(manager.getColor(IColorConstants.STRING)));

		setDefaultReturnToken(deflt);
		IRule[] rules = new IRule[5];

		// Rules for single-line comments and multi-line comments
		IRule slCommentRule = new EndOfLineRule("//", comment);
		IRule mlCommentRule = new MultiLineRule("/*", "*/", comment);
		// Scanning rules for the key and for the string value
		final Pattern bindingPattern =
			Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*[ \t\b\f]*=");
		RegexpLineRule bindingsRule =
			new RegexpLineRule(bindingPattern, key,
				ch -> !Character.isJavaIdentifierPart(ch) &&
					!Character.isWhitespace(ch) && ch != '=');
		// Scanning rules for the square bracket delimiters
		final Pattern delimPattern = Pattern.compile("\\[|\\]");
		RegexpLineRule delimRule =
			new RegexpLineRule(delimPattern, symbols,
				ch -> ch != '[' && ch != ']');
		
		rules[0] = delimRule;
		rules[1] = new PatternRule("\"", "\"", value, '\\', false, false, false);
		rules[2] = bindingsRule;
		rules[3] = slCommentRule;
		rules[4] = mlCommentRule;
		
		setRules(rules);
	}
}
