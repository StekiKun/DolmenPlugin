package dolmenplugin.editors;

import org.eclipse.jface.text.rules.IWhitespaceDetector;

public class JLWhitespaceDetector implements IWhitespaceDetector {

	public boolean isWhitespace(char c) {
		switch (c) {
		case ' ':
		case '\t':
		case '\f':
		case '\n':
		case '\r':
			return true;
		default:
			return false;
		}
	}

}