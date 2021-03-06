package org.stekikun.dolmenplugin.editors;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.stekikun.dolmen.codegen.BaseParser;
import org.stekikun.dolmen.codegen.LexBuffer;
import org.stekikun.dolmen.syntax.Extent;
import org.stekikun.dolmen.syntax.IReport;
import org.stekikun.dolmenplugin.Activator;
import org.stekikun.dolmenplugin.base.Images;
import org.stekikun.dolmenplugin.editors.jg.JGEditor;
import org.stekikun.dolmenplugin.editors.jl.JLEditor;

/**
 * Common base class of nodes used to represent the
 * structured contents of outline views, both for {@link JLEditor}
 * and {@link JGEditor}.
 * <p>
 * Implementations of this class must
 * fill in the various abstract methods, which together provide
 * the necessary features to implement instances of
 * {@link IStyledLabelProvider} and {@link ITreeContentProvider} 
 * for the outline view. Outline nodes can also be associated to
 * a range of characters in the editor's contents, to handle
 * selection via the outline.
 * <p>
 * Various useful implementations common to both kinds of
 * editors are also provided:
 * <ul>
 * <li> {@link LexicalError}
 * <li> {@link ParsingException}
 * <li> {@link IReport}
 * </ul>
 * 
 * @author Stéphane Lescuyer
 *
 * @param <T>	The type of children nodes. It must be
 * 				an outline node as well.
 */
public abstract class OutlineNode<T extends OutlineNode<T>> {

	/**
	 * @return {@code true} iff the node has children
	 */
	public abstract boolean hasChildren();
	
	/**
	 * @return the children of this node
	 */
	public abstract T[] getChildren();
	
	/**
	 * @return the associated image in the tree viewer, if any
	 */
	public abstract Image getImage();
	
	/**
	 * @param document	the editor's document
	 * @return the associated label in the tree viewer, if any
	 */
	public abstract StyledString getText(IDocument document);
	
	/**
	 * @return the absolute offset of the range in 
	 * 	input which corresponds to this node, or -1 if none
	 */
	public abstract int getOffset();
	
	/**
	 * @return the length of the range in input which
	 * 	corresponds to this node, or -1 if none
	 */
	public abstract int getLength();

	private static final OutlineNode<?>[] NO_CHILDREN = new OutlineNode<?>[0];
	
	/**
	 * Common implementation for outline nodes corresponding to exceptions:
	 * they are childless and use a "warning sign" icon
	 *
	 * @author Stéphane Lescuyer
	 *
	 * @param <U>	type of children
	 * @param <T>	type of the wrapped exception
	 */
	private static abstract
		class Exn<U extends OutlineNode<U>, T extends Exception>
		extends OutlineNode<U> {
		final T exn;
		
		Exn(T exn) {
			this.exn = exn;
		}
		
		@Override
		public Image getImage() {
			return Activator.getImage(Images.WARNING_SIGN);
		}
		
		@Override
		public StyledString getText(IDocument document) {
			return new StyledString(exn.getMessage());
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
	
	/**
	 * Outline node representing {@link LexBuffer.LexicalError} exceptions
	 * 
	 * @author Stéphane Lescuyer
	 *
	 * @param <T>
	 */
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
	/**
	 * @param e
	 * @return an outline node representing this exception
	 */
	public static <T extends OutlineNode<T>> 
		OutlineNode<T> of(LexBuffer.LexicalError e) {
		return new LexicalError<T>(e);
	}

	/**
	 * Outline node representing {@link BaseParser.ParsingException} exceptions
	 * 
	 * @author Stéphane Lescuyer
	 *
	 * @param <T>
	 */
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
			return exn.length;
		}
	}
	/**
	 * @param e
	 * @return an outline node representing this exception
	 */
	public static <T extends OutlineNode<T>> 
		OutlineNode<T> of(BaseParser.ParsingException e) {
		return new ParsingException<T>(e);
	}	
	
	/**
	 * Outline node representing an {@link IReport}
	 * 
	 * @author Stéphane Lescuyer
	 *
	 * @param <T>
	 */
	private static final class Report<T extends OutlineNode<T>> extends OutlineNode<T> {
		final IReport report;
		
		Report(IReport report) {
			this.report = report;
		}

		@Override
		public Image getImage() {
			return Activator.getImage(Images.WARNING_SIGN);
		}

		@Override
		public StyledString getText(IDocument document) {
			return new StyledString(report.getMessage());
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
	/**
	 * @param report
	 * @return an outline node representing this report
	 */
	public static <T extends OutlineNode<T>> OutlineNode<T> of(IReport report) {
		return new Report<T>(report);
	}
	
	/**
	 * Outline node representing an {@link Extent extent}, i.e. a semantic action
	 * 
	 * @author Stéphane Lescuyer
	 *
	 * @param <T>
	 */
	private static class Snippet<T extends OutlineNode<T>> extends OutlineNode<T> {
		final String text;
		final Extent extent;
		
		Snippet(String text, Extent extent) {
			this.text = text;
			this.extent = extent;
		}

		@Override
		public boolean hasChildren() {
			return false;
		}

		@SuppressWarnings("unchecked")
		@Override
		public T[] getChildren() {
			return (T[]) NO_CHILDREN;
		}

		@Override
		public Image getImage() {
			return Activator.getImage(Images.SEMANTIC_ACTION);
		}

		@Override
		public StyledString getText(IDocument document) {
			return new StyledString(text);
		}

		@Override
		public int getOffset() {
			return extent.startPos;
		}

		@Override
		public int getLength() {
			return extent.length();
		}
	}
	/**
	 * @param name	name used as label for the node
	 * @param extent
	 * @return an outline node representing the given extent
	 */
	public static <T extends OutlineNode<T>> OutlineNode<T>
		of(String name, Extent extent) {
		return new Snippet<T>(name, extent);
	}
	
	/**
	 * @param doc
	 * @param ext
	 * @return the string which corresponds to the range described
	 * 	by the given extent {@code ext} in {@code doc}, or "??" if
	 *  the range or document is invalid
	 */
	protected static String resolveExtentIn(IDocument doc, Extent ext) {
		if (doc == null)
			return NO_RESOLUTION;
		// We will try and adapt the extent's position to the document's
		// current contents, which may be fresher than the source the
		// extent is referring to. For that we retrieve the associated
		// tracker if we can.
		@Nullable DocumentTracker tracker = DolmenEditor.getTracker(doc);
		if (tracker == null)
			return NO_RESOLUTION;
		DocumentTracker. @Nullable Range range = tracker.transform(ext);
		if (range == null)
			return NO_RESOLUTION;
		try {
			return doc.get(range.getOffset(), range.getLength());
		} catch (BadLocationException e) {
			return NO_RESOLUTION;
		}
	}
	private final static String NO_RESOLUTION = "??";
}
