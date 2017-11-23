package dolmenplugin.editors;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Image;

import codegen.BaseParser;
import codegen.LexBuffer;
import dolmenplugin.Activator;
import syntax.Extent;
import syntax.IReport;

public abstract class OutlineNode<T extends OutlineNode<T>> {

	public abstract boolean hasChildren();
	
	public abstract T[] getChildren();
	
	public abstract Image getImage();
	
	public abstract String getText(IDocument document);
	
	public abstract int getOffset();
	
	public abstract int getLength();

	private static final OutlineNode<?>[] NO_CHILDREN = new OutlineNode<?>[0];
	
	private static abstract
		class Exn<U extends OutlineNode<U>, T extends Exception>
		extends OutlineNode<U> {
		final T exn;
		
		Exn(T exn) {
			this.exn = exn;
		}
		
		@Override
		public Image getImage() {
			return Activator.getImage("icons/warning_sign.gif");
		}
		
		@Override
		public String getText(IDocument document) {
			return exn.getMessage();
		}
		
		@Override
		public final boolean hasChildren() {
			return false;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final U[] getChildren() {
			return (U[]) NO_CHILDREN;
		}
	}
	
	private static final class LexicalError<T extends OutlineNode<T>> 
		extends Exn<T, LexBuffer.LexicalError> {
		LexicalError(LexBuffer.LexicalError e) {
			super(e);
		}

		@Override
		public int getOffset() {
			return exn.pos.offset;
		}

		@Override
		public int getLength() {
			return 0;
		}
	}
	public static <T extends OutlineNode<T>> 
		OutlineNode<T> of(LexBuffer.LexicalError e) {
		return new LexicalError<T>(e);
	}

	private static final class ParsingException<T extends OutlineNode<T>>
		extends Exn<T, BaseParser.ParsingException> {
		ParsingException(BaseParser.ParsingException e) {
			super(e);
		}

		@Override
		public int getOffset() {
			return exn.pos.offset;
		}

		@Override
		public int getLength() {
			return 0;
		}
	}
	public static <T extends OutlineNode<T>> 
		OutlineNode<T> of(BaseParser.ParsingException e) {
		return new ParsingException<T>(e);
	}	
	
	private static final class Report<T extends OutlineNode<T>> extends OutlineNode<T> {
		final IReport report;
		
		Report(IReport report) {
			this.report = report;
		}

		@Override
		public Image getImage() {
			return Activator.getImage("icons/warning_sign.gif");
		}

		@Override
		public String getText(IDocument document) {
			return report.getMessage();
		}

		@Override
		public int getOffset() {
			return report.getOffset();
		}

		@Override
		public int getLength() {
			return report.getLength();
		}
		
		@Override
		public final boolean hasChildren() {
			return false;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final T[] getChildren() {
			return (T[]) NO_CHILDREN;
		}
	}
	public static <T extends OutlineNode<T>> OutlineNode<T> of(IReport report) {
		return new Report<T>(report);
	}
	
	protected static String resolveExtentIn(IDocument doc, Extent ext) {
		if (doc == null)
			return "??";
		try {
			return doc.get(ext.startPos, ext.length());
		} catch (BadLocationException e) {
			return "??";
		}
	}
}
