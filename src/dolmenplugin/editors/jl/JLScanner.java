package dolmenplugin.editors.jl;

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
 * lexer descriptions.
 * <p>
 * It uses a rule-based scanner to fontify keywords,
 * identifiers, literals, etc.
 * 
 * @author Stéphane Lescuyer
 */
public class JLScanner extends RuleBasedScanner {

	public JLScanner(ColorManager manager) {
		// Single text attribute tokens
		IToken keyword =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.KEYWORD), null, SWT.BOLD));
		IToken keywordOp =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.KEYWORD_OP)));
		IToken comment =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.COMMENT)));
		IToken string =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.STRING)));
		IToken numeral =
				new Token(
					new TextAttribute(
						manager.getColor(IColorConstants.KEYWORD_OP), null, SWT.ITALIC));
		IToken ident =
			new Token(
				new TextAttribute(
					manager.getColor(IColorConstants.IDENT), null, SWT.ITALIC));
		
		// Rule for keywords
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
		keywordsRule.addWord("shortest", keyword);
		keywordsRule.addWord("as", keywordOp);
		keywordsRule.addWord("eof", keywordOp);
		keywordsRule.addWord("_", keywordOp);
		keywordsRule.addWord("orelse", keywordOp);
		keywordsRule.addWord("import", keyword);
		keywordsRule.addWord("static", keyword);
		keywordsRule.addWord("public", keyword);
		keywordsRule.addWord("private", keyword);
		// Add rule for single-line comments and multi-line comments
		IRule slCommentRule = new EndOfLineRule("//", comment);
		IRule mlCommentRule = new MultiLineRule("/*", "*/", comment);
		// Add rule for characters and strings literals
		IRule stringRule = new PatternRule("\"", "\"", string, '\\', false, false, false);
		IRule charRule = new PatternRule("'", "'", string, '\\', false, false, false);
	    // Add rule for integer literals
	    Pattern integer = Pattern.compile("0|([1-9][0-9]*)");
	    IRule integerRule = new RegexpLineRule(integer, numeral,
	    	c -> !Character.isDigit(c));       
	    
		IRule[] rules = new IRule[] {
			keywordsRule, slCommentRule, mlCommentRule,
			stringRule, charRule, integerRule,
			new WhitespaceRule(new WhitespaceDetector())
		};
		setRules(rules);
	}
}
