package org.stekikun.dolmenplugin.editors.jg;

import org.eclipse.jface.text.rules.*;
import org.eclipse.swt.SWT;
import org.stekikun.dolmenplugin.editors.ColorManager;
import org.stekikun.dolmenplugin.editors.IColorConstants;
import org.stekikun.dolmenplugin.editors.RegexpLineRule;
import org.stekikun.dolmenplugin.editors.WhitespaceDetector;

import java.util.regex.Pattern;

import org.eclipse.jface.text.*;

/**
 * The scanner for the default partitions in Dolmen
 * grammar descriptions.
 * <p>
 * It uses a rule-based scanner to fontify keywords,
 * terminals, identifiers, etc.
 * 
 * @author Stéphane Lescuyer
 */
public class JGScanner extends RuleBasedScanner {

	public JGScanner(ColorManager manager) {
		// Single text attribute tokens
		IToken deflt =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.DEFAULT)));
		IToken keyword =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.KEYWORD), null, SWT.BOLD));
		IToken comment =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.COMMENT)));
		IToken terminal =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.KEYWORD_OP), null, SWT.BOLD));
		IToken ident =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.IDENT), null, SWT.ITALIC));
		
		// Rule for terminals, i.e. identifiers in all caps
		final Pattern terminalPattern =
			Pattern.compile("[A-Z][A-Z_0-9]*\\b");
		RegexpLineRule terminalsRule =
			new RegexpLineRule(terminalPattern, terminal, 
				ch -> !Character.isJavaIdentifierPart(ch));
		// Rule for bindings, i.e. identifiers followed by '='
		final Pattern bindingPattern =
			Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*[ \t\b\f]*=");
		RegexpLineRule bindingsRule =
			new RegexpLineRule(bindingPattern, deflt,
				ch -> !Character.isJavaIdentifierPart(ch) &&
					!Character.isWhitespace(ch) && ch != '=');
		// Rule for keywords or other identifiers
		WordRule keywordsRule = new WordRule(new IWordDetector() {
			@Override
			public boolean isWordStart(char c) {
				if (c >= 'a' && c <= 'z') return true;
				if (c >= 'A' && c <= 'Z') return true;
				if (c == '_') return true;
				return false;
			}
			
			@Override
			public boolean isWordPart(char c) {
				return isWordStart(c) || (c >= '0' && c <= '9');
			}
		}, ident);
		keywordsRule.addWord("rule", keyword);
		keywordsRule.addWord("token", keyword);
		keywordsRule.addWord("import", keyword);
		keywordsRule.addWord("static", keyword);
		keywordsRule.addWord("public", keyword);
		keywordsRule.addWord("private", keyword);
		keywordsRule.addWord("continue", keyword);
		// Add rule for single-line comments and multi-line comments
		IRule slCommentRule = new EndOfLineRule("//", comment);
		IRule mlCommentRule = new MultiLineRule("/*", "*/", comment);
	            
		IRule[] rules = new IRule[] {
			slCommentRule, mlCommentRule,
			new WhitespaceRule(new WhitespaceDetector()),
			terminalsRule, bindingsRule, keywordsRule
		};
		setRules(rules);
	}
}
