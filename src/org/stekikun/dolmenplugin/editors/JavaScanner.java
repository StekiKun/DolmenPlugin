package org.stekikun.dolmenplugin.editors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.PatternRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.stekikun.dolmen.common.Java;
import org.stekikun.dolmenplugin.base.Utils;

/**
 * The scanner for the Java semantic actions in both Dolmen
 * lexer and grammar descriptions.
 * <p>
 * It uses a rule-based scanner to recognizes all Java
 * keywords, as well as Java comments and string literals.
 * It can be configured to recognize holes in
 * semantic actions or to ignore them.
 *
 * @author Stéphane Lescuyer
 */
public class JavaScanner extends RuleBasedScanner {

	public JavaScanner(boolean withHoles, ColorManager manager, RGB background) {
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
		IToken hole =
				new Token(
					new TextAttribute(
						manager.getColor(IColorConstants.IDENT), bg, SWT.BOLD));
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
		IRule stringRule = new PatternRule("\"", "\"", string, '\\', false, false, false);
		IRule charRule = new PatternRule("'", "'", string, '\\', false, false, false);
		// Add rule for holes if needed
		Pattern holePattern = Pattern.compile("#[a-z]\\w*");
		IRule holeRule = new RegexpLineRule(holePattern, hole,
			ch -> ch != '#' && !Utils.isDolmenWordPart(ch));
	    
		List<IRule> rules = new ArrayList<IRule>(6);
		rules.add(keywordsRule);
		rules.add(slCommentRule);
		rules.add(mlCommentRule);
		rules.add(stringRule);
		rules.add(charRule);
		if (withHoles)
			rules.add(holeRule);
		setRules(rules.toArray(new IRule[rules.size()]));
		
		setDefaultReturnToken(
			new Token(
				new TextAttribute(null, bg, 0)));
	}
}