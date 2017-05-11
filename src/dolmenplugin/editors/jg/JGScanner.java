package dolmenplugin.editors.jg;

import org.eclipse.jface.text.rules.*;
import org.eclipse.swt.SWT;

import dolmenplugin.editors.ColorManager;
import dolmenplugin.editors.IColorConstants;
import dolmenplugin.editors.RegexpLineRule;
import dolmenplugin.editors.WhitespaceDetector;

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
//		IToken deflt =
//			new Token(
//				new TextAttribute(
//					manager.getColor(IJLColorConstants.DEFAULT)));
		IToken keyword =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.KEYWORD), null, SWT.BOLD));
//		IToken keywordOp =
//			new Token(
//				new TextAttribute(
//					manager.getColor(IColorConstants.KEYWORD_OP)));
		IToken comment =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.COMMENT)));
//		IToken string =
//			new Token(
//				new TextAttribute(
//					manager.getColor(IColorConstants.STRING)));
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
			new RegexpLineRule(terminalPattern, terminal);
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
		// Add rule for single-line comments and multi-line comments
		IRule slCommentRule = new EndOfLineRule("//", comment);
		IRule mlCommentRule = new MultiLineRule("/*", "*/", comment);
//		// Add rule for characters and strings literals
//		IRule stringRule = new SingleLineRule("\"", "\"", string, '\\');
//	    IRule charRule = new SingleLineRule("'", "'", string, '\\');
	            
		IRule[] rules = new IRule[] {
			terminalsRule, keywordsRule,
			slCommentRule, mlCommentRule,
//			stringRule, charRule,
			new WhitespaceRule(new WhitespaceDetector())
		};
		setRules(rules);
	}
}
