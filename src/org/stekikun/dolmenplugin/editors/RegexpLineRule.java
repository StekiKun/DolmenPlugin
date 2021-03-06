package org.stekikun.dolmenplugin.editors;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

/**
 * This generic rule for rule-based scanners matches 
 * some {@link Pattern regular expression} and returns
 * the associated {@link IToken}, or {@link Token#UNDEFINED}
 * otherwise.
 * <p>
 * For efficiency reasons, the regular expression cannot
 * match beyond a line boundary, and empty matches are not
 * allowed since tokens must be non-empty. The pattern
 * is applied to the current scanner up to end-of-input
 * or the next line terminator, and not on an char-by-char basis,
 * so that longest-match behaviour will essentially be achieved.   
 * <p>
 * For even more efficiency, an optional predicate can be 
 * provided to stop the scanner even before the next line
 * terminator. Scanning and rewinding can be expensive so 
 * bounding the maximum look-ahead for this rule is advised
 * when possible.
 * 
 * @author Stéphane Lescuyer
 */
public final class RegexpLineRule implements IRule {

	private final Pattern pattern;
	private final IToken token;
	private final Predicate<Character> stop;

	/**
	 * Returns a new rule which associates {@code token}
	 * to matches of the pattern {@code pattern}
	 * @param pattern
	 * @param token
	 * @param stop
	 */
	public RegexpLineRule(Pattern pattern, IToken token, Predicate<Character> stop) {
		this.pattern = pattern;
		this.token = token;
		this.stop = stop == null ? (Character c) -> { return false; } : stop;
	}

	@Override
	public IToken evaluate(ICharacterScanner scanner) {
		// Fetch one whole line from the scanner (until eol or eof)		
		StringBuilder buf = new StringBuilder();
		int c = scanner.read();
		int count = 1;
		while (c != ICharacterScanner.EOF
			&& c != '\r' && c != '\n' && !stop.test((char)c)) {
			buf.append((char)c);
			c = scanner.read();
			++count;
		}
		
		// Check whether the beginning of input, up to
		// eol or eof, matches the given pattern. No empty
		// match is allowed.
		Matcher matcher = pattern.matcher(buf);
		if (matcher.lookingAt() && matcher.end() != 0) {
			// Rewind to the char following the end of the match
			// 0  1  2 ... last next ....  count
			//  c  d  e  ...   f   ...    g    
			int next = matcher.end();
			for (int i = 0; i < count - next; ++i)
				scanner.unread();
			return token;
		}
		
		// No match, rewind the scanner
		for (int i = 0; i < count; ++i)
			scanner.unread();
		return Token.UNDEFINED;
	}

}
