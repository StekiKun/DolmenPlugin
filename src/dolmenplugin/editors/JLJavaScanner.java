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

public class JLJavaScanner extends RuleBasedScanner {

	private String[] JAVA_KEYWORDS = {
		"false", "null", "true",	// technically reserved literals, not keywords
		"abstract", "continue", "for", "new", "switch",
		"assert", "default", "if", "package", "synchronized",
		"boolean", "do", "goto", "private", "this",
		"break", "double", "implements", "protected", "throw",
		"byte", "else", "import", "public", "throws",
	    "case", "enum", "instanceof", "return", "transient",
        "catch", "extends", "int", "short", "try",
        "char", "final", "interface", "static", "void",
        "class", "finally", "long", "strictfp", "volatile",
        "const", "float", "native", "super", "while"
	};
	
	public JLJavaScanner(ColorManager manager) {
		Color bg = manager.getColor(IJLColorConstants.JAVA_BG);
		
		// Single text attribute tokens
		IToken deflt =
			new Token(
				new TextAttribute(
					manager.getColor(IJLColorConstants.DEFAULT), bg, 0));
		IToken keyword =
			new Token(
				new TextAttribute(
					manager.getColor(IJLColorConstants.KEYWORD), bg, SWT.BOLD));
		IToken comment =
			new Token(
				new TextAttribute(
					manager.getColor(IJLColorConstants.COMMENT), bg, 0));
		IToken string =
			new Token(
				new TextAttribute(
					manager.getColor(IJLColorConstants.STRING), bg, 0));
		
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
		for (String kw : Arrays.asList(JAVA_KEYWORDS))
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
				new TextAttribute(null, 
						manager.getColor(IJLColorConstants.JAVA_BG), 0)));
	}
}