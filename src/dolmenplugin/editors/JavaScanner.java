package dolmenplugin.editors;

import java.util.Arrays;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;

import common.Java;
import dolmenplugin.editors.ColorManager;
import dolmenplugin.editors.IColorConstants;

/**
 * The scanner for the Java semantic actions in both Dolmen
 * lexer and grammar descriptions.
 * <p>
 * It uses a rule-based scanner to recognizes all Java
 * keywords, as well as Java comments and string literals.
 *
 * @author Stéphane Lescuyer
 */
public class JavaScanner extends RuleBasedScanner {

	public JavaScanner(ColorManager manager, RGB background) {
		Color bg =
			background == null ? null : manager.getColor(background);
		
		// Single text attribute tokens
		IToken deflt =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.DEFAULT), bg, 0));
		IToken keyword =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.KEYWORD), bg, SWT.BOLD));
		IToken comment =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.COMMENT), bg, 0));
		IToken string =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.STRING), bg, 0));
		
		// Rule for keywords
		WordRule keywordsRule = new WordRule(new IWordDetector() {
			@Override
			public boolean isWordStart(char c) {
				return Character.isJavaIdentifierStart(c);
			}
			
			@Override
			public boolean isWordPart(char c) {
				return Character.isJavaIdentifierPart(c);
			}
		}, deflt);
		for (String kw : Arrays.asList(Java.KEYWORDS))
			keywordsRule.addWord(kw, keyword);
		
		// Add rule for single-line comments and multi-line comments
		IRule slCommentRule = new EndOfLineRule("//", comment);
		IRule mlCommentRule = new MultiLineRule("/*", "*/", comment);
		// Add rule for characters and strings literals
		IRule stringRule = new SingleLineRule("\"", "\"", string, '\\');
	    IRule charRule = new SingleLineRule("'", "'", string, '\\');
	            
		IRule[] rules = new IRule[] {
			keywordsRule, slCommentRule, mlCommentRule,
			stringRule, charRule
		};
		setRules(rules);
		
		setDefaultReturnToken(
			new Token(
				new TextAttribute(null, bg, 0)));
	}
}