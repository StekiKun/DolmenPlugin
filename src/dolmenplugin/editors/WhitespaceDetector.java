package dolmenplugin.editors;

import org.eclipse.jface.text.rules.IWhitespaceDetector;

/**
 * Common implementation of {@link IWhitespaceDetector}
 * used by the lexer and parser editors
 * 
 * @author Stéphane Lescuyer
 */
public class WhitespaceDetector implements IWhitespaceDetector {

	public boolean isWhitespace(char c) {
		switch (c) {
		case ' ':
		case '\t':
		case '\b':
		case '\f':
		case '\n':
		case '\r':
			return true;
		default:
			return false;
		}
	}

}