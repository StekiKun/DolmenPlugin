package dolmenplugin.editors;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

/**
 * Predicate rule which matches Java semantic actions
 * enclosed in (possibly nested) some specifiable opening
 * and closing characters. It is used both by the
 * lexer and parser editors.
 * 
 * @author Stéphane Lescuyer
 */
public class JavaActionRule implements IPredicateRule {

	private final IToken successToken;
	private final char open;
	private final char close;

	public JavaActionRule(IToken successToken, char open, char close) {
		this.successToken = successToken;
		this.open = open;
		this.close = close;
	}

	@Override
	public IToken evaluate(ICharacterScanner scanner) {
		return evaluate(scanner, false);
	}

	@Override
	public IToken getSuccessToken() {
		return successToken;
	}

	@Override
	public IToken evaluate(ICharacterScanner scanner, boolean resume) {
		Automaton aut = new Automaton(scanner);
		if (resume) {
			System.err.println("JavaActionRule#evaluate with resume=true: should not happen");
			return Token.UNDEFINED;
		}
		else
			return aut.start();
	}

	private final class Automaton {
		private final ICharacterScanner scanner;
		private int read;
		private int depth;
		
		// Minimum positive depth reached on a closing character
		private int minDepth = Integer.MAX_VALUE;
		// Number of character reads up to that minimal closing character, if any
		private int minRead = -1;
		
		Automaton(ICharacterScanner scanner) {
			this.scanner = scanner;
			this.read = 0;
			this.depth = 0;
		}
		
		private int next() {
			int c = scanner.read();
			++read;
			return c;
		}
		
		private void rewind() {
			scanner.unread();
			--read;
		}
		
		private IToken abort() {
			// Fall back to last 'acceptable state' if any,
			// otherwise rewind all read characters and return UNDEFINED
			int fallback = minRead < 0 ? 0 : minRead;
			IToken token = minRead < 0 ? Token.UNDEFINED : JavaActionRule.this.successToken;
			for (; read > fallback; --read)
				scanner.unread();
			return token;
		}
		
		IToken start() {
			if (next() != open)
				return abort();
			++depth;
			return inAction();
		}
		
		IToken inAction() {
			int c;
			loop:
			while ((c = next()) != ICharacterScanner.EOF) {
				if (c == open) {
					++depth;
					continue;
				}
				else if (c == close) {
					--depth;
					if (depth == 0)
						return JavaActionRule.this.successToken;
					// < -> shortest match, <= -> longest match
					// Assuming the remainder of the file is well-braced,
					// it is more likely that we should stop at the shortest match
					if (depth < minDepth) { 
						minDepth = depth;
						minRead = read;
					}
					continue;					
				}
				switch (c) {
				case '/': {
					switch ((int) next()) {
					case '/': {
						// Single line comment
						if (!inSLComment()) break loop;
						continue;
					}
					case '*': {
						// Multi line comment
						if (!inMLComment()) break loop;
						continue;
					}
					case ICharacterScanner.EOF:
						break loop;
					default:
						continue;
				}
				}
				case '"': {
					// String literal
					if (!inString()) break loop;
					continue;
				}
				case '\'': {
					// Char literal
					if (!inChar()) break loop;
					continue;
				}
				default:
					continue;
				}
			}
			return abort();
		}
		
		private boolean inSLComment() {
			int c;
			while ((c = next()) != ICharacterScanner.EOF) {
				switch (c) {
				case '\n': return true;
				case '\r': {
					c = next();
					if (c == '\n') return true;
					rewind();
					return true;
				}
				default:
					continue;
				}
			}
			return false;
		}
		
		private boolean inMLComment() {
			int c;
			while ((c = next()) != ICharacterScanner.EOF) {
				switch (c) {
				case '*': {
					switch ((int) next()) {
					case '/': return true;
					case '*': rewind(); continue;
					case ICharacterScanner.EOF: return false;
					default: continue;
					}
				}
				default:
					continue;
				}
			}
			return false;
		}
		
		private boolean inString() {
			int c;
			while ((c = next()) != ICharacterScanner.EOF){
				switch (c) {
				case '"': return true;
				case '\\': {
					if (next() == ICharacterScanner.EOF)
						return false;
					continue;
				}
				default:
					continue;
				}
			}
			return false;
		}
		
		private boolean inChar() {
			int c;
			while ((c = next()) != ICharacterScanner.EOF){
				switch (c) {
				case '\'': return true;
				case '\\': {
					if (next() == ICharacterScanner.EOF)
						return false;
					continue;
				}
				default:
					continue;
				}
			}
			return false;
		}
	}
	
}
