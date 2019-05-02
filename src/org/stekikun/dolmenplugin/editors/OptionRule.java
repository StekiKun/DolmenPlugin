package org.stekikun.dolmenplugin.editors;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

/**
 * Predicate rule which matches key-value options as found
 * potentially in both .jl and .jg files. It is used both by the
 * lexer and parser editors.
 * 
 * @author Stéphane Lescuyer
 * 
 * TODO Handle resumed evaluation?
 */
public final class OptionRule implements IPredicateRule {

	private final IToken successToken;
	private final static char open = '[';
	private final static char close = ']';
	private final static char equal = '=';

	public OptionRule(IToken successToken) {
		this.successToken = successToken;
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
			System.err.println("OptionRule#evaluate with resume=true: should not happen");
			return Token.UNDEFINED;
		}
		else
			return aut.start();
	}

	private final class Automaton {
		private final ICharacterScanner scanner;
		private int read;
		
		Automaton(ICharacterScanner scanner) {
			this.scanner = scanner;
			this.read = 0;
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
			// Rewind all read characters and return UNDEFINED
			for (; read > 0; --read)
				scanner.unread();
			return Token.UNDEFINED;
		}
		
		IToken start() {
			if (next() != open)
				return abort();
			return option();
		}
		
		boolean isIdentifierStart(int c) {
			return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
		}
		
		boolean isIdentifierPart(int c) {
			return isIdentifierStart(c) || (c >= '0' && c <= '9');
		}
		
		/**
		 * Skips all whitespace and comments
		 * 
		 * @return {@code false} if EOF has been reached, {@code true} otherwise
		 */
		boolean whitespace() {
			int c;
			loop:
			while ((c = next()) != ICharacterScanner.EOF) {
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
					default:
						break loop;
					}
				}
				case ' ':
				case '\t':
				case '\b':
				case '\r':
				case '\n':
					continue;
				default:
					rewind();
					return true;
				}
			}
			return false;
		}
		
		boolean ident() {
			boolean first = true;
			int c;
			while ((c = next()) != ICharacterScanner.EOF) {
				if (first) {
					first = false;
					if (isIdentifierStart(c)) continue;
					else return false;
				}
				else {
					if (isIdentifierPart(c)) continue;
					else {
						rewind();
						return true;
					}
				}
			}
			return false;
		}
		
		IToken option() {
			if (!whitespace()) return abort();
			if (!ident()) return abort();
			if (!whitespace()) return abort();
			if (next() != equal) return abort();
			if (!whitespace()) return abort();
			if (next() != '"') return abort();
			if (!inString()) return abort();
			if (!whitespace()) return abort();
			if (next() != close) return abort();
			return OptionRule.this.successToken;
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
	}
}
