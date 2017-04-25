package dolmenplugin.editors;

import org.eclipse.jface.text.rules.*;
import org.eclipse.swt.SWT;
import org.eclipse.jface.text.*;

public class JLScanner extends RuleBasedScanner {

	public JLScanner(ColorManager manager) {
		// Single text attribute tokens
//		IToken deflt =
//			new Token(
//				new TextAttribute(
//					manager.getColor(IJLColorConstants.DEFAULT)));
		IToken keyword =
			new Token(
				new TextAttribute(
					manager.getColor(IJLColorConstants.KEYWORD), null, SWT.BOLD));
		IToken keywordOp =
			new Token(
				new TextAttribute(
					manager.getColor(IJLColorConstants.KEYWORD_OP)));
		IToken comment =
			new Token(
				new TextAttribute(
					manager.getColor(IJLColorConstants.COMMENT)));
		IToken string =
			new Token(
				new TextAttribute(
					manager.getColor(IJLColorConstants.STRING)));
		IToken ident =
			new Token(
				new TextAttribute(
					manager.getColor(IJLColorConstants.IDENT), null, SWT.ITALIC));
		
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
		keywordsRule.addWord("import", keyword);
		keywordsRule.addWord("static", keyword);
		keywordsRule.addWord("public", keyword);
		keywordsRule.addWord("private", keyword);
		// Add rule for single-line comments and multi-line comments
		IRule slCommentRule = new EndOfLineRule("//", comment);
		IRule mlCommentRule = new MultiLineRule("/*", "*/", comment);
		// Add rule for characters and strings literals
		IRule stringRule = new SingleLineRule("\"", "\"", string, '\\');
	    IRule charRule = new SingleLineRule("'", "'", string, '\\');
	            
		IRule[] rules = new IRule[] {
			keywordsRule, slCommentRule, mlCommentRule,
			stringRule, charRule,
			new WhitespaceRule(new JLWhitespaceDetector())
		};
		setRules(rules);
	}
}
