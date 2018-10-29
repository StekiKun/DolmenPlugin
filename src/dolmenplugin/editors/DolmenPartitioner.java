package dolmenplugin.editors;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TypedPosition;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.rules.IPartitionTokenScanner;
import org.eclipse.jface.text.rules.IToken;

/**
 * This is a customization of {@link FastPartitioner} to solve issues
 * raised by the partitioning of complex partitions such as Java
 * semantic actions or option headers. These partitions are not easily
 * resumable and cannot be scanned from inside the partition.
 * <p>
 * Unfortunately, scanning on a document change in {@link FastPartitioner}
 * often restarts from the beginning of the line where the change occurred. 
 * This implementation overrides {@link #documentChanged2(DocumentEvent)}
 * in order to ensure that scanning is always performed from the start
 * of a former partition. This means partitioning may end up being slower
 * than with {@link FastPartitioner} but this avoids annoying effects when
 * editing a partially complete semantic action or when writing a multi-line
 * option value.
 * <p>
 * <i><b>Implementation notes</b><br/>
 * This partitioner has a debug mode which can be activated with the
 * {@link #withDebug} flag to ease understand how partitions are recomputed
 * at each document change.
 * <br/>
 * Judging by its documentation, {@link FastPartitioner} is supposedly available
 * for sub-classing. However its design in that regard is bad: its delayed
 * initialization flag in particular is private so subclasses are compelled to
 * force initialization, there is much inconsistency about which fields and methods
 * are private or protected and this forces a sub-class to reimplement part of
 * the underlying helpers and implementation.
 * </i>
 * 
 * @author Stéphane Lescuyer
 */
public final class DolmenPartitioner extends FastPartitioner {

	private final String fPositionCategory;
	
	private static final boolean withDebug = false;

	private static final String CONTENT_TYPES_CATEGORY= "__content_types_category"; //$NON-NLS-1$

	
	public DolmenPartitioner(IPartitionTokenScanner scanner, String[] legalContentTypes) {
		super(scanner, legalContentTypes);
		// This must be recomputed because super.fPositionCategory and CONTENT_TYPES_CATEGORY
		// are private in FastPartitioner...
		this.fPositionCategory = CONTENT_TYPES_CATEGORY + hashCode();
	}

	private void debugReparseStart(DocumentEvent e, int reparseStart) {
		if (!withDebug) return;
		System.out.println("== Document changed at ==");
		displayPointInContext(e.fOffset);
		System.out.println(" [length = " + e.getLength() + " characters]");
		System.out.println("-- Document reparsed at --");
		displayPointInContext(reparseStart);
	}
	
	private void displayPointInContext(int offset) {
		try {
			IRegion lineInfo = fDocument.getLineInformationOfOffset(offset);
			
			String line = fDocument.get(lineInfo.getOffset(), lineInfo.getLength());
			int col = offset -lineInfo.getOffset();
			StringBuilder buf = new StringBuilder();
			buf.append(line.substring(0, col));
			buf.append("┃");
			buf.append(line.substring(col));
			
			int lineno = fDocument.getLineOfOffset(offset);
			System.out.println(String.format("%d: %s", lineno, buf.toString()));
		} catch (BadLocationException e) {
			// Should not happen here
			return;
		}
	}
	
	// NB: On the three following methods: they are private in FastPartitioner,
	// 	for no good reason since their only job is to help update three fields which
	//  happen to not be private... I just copied them here but really the design
	//  in FastPartitioner regarding subclassing is atrocious.
	
	/**
	 * Copied from {@link FastPartitioner}
	 *
	 * @param offset the offset
	 * @param length the length
	 */
	private void rememberRegion(int offset, int length) {
		// remember start offset
		if (fStartOffset == -1)
			fStartOffset= offset;
		else if (offset < fStartOffset)
			fStartOffset= offset;

		// remember end offset
		int endOffset= offset + length;
		if (fEndOffset == -1)
			fEndOffset= endOffset;
		else if (endOffset > fEndOffset)
			fEndOffset= endOffset;
	}

	/**
	 * Copied from {@link FastPartitioner}
	 *
	 * @param offset the offset
	 */
	private void rememberDeletedOffset(int offset) {
		fDeleteOffset= offset;
	}

	/**
	 * Copied from {@link FastPartitioner}
	 *
	 * @return the minimal region containing all the partition changes
	 */
	private IRegion createRegion() {
		if (fDeleteOffset == -1) {
			if (fStartOffset == -1 || fEndOffset == -1)
				return null;
			return new Region(fStartOffset, fEndOffset - fStartOffset);
		} else if (fStartOffset == -1 || fEndOffset == -1) {
			return new Region(fDeleteOffset, 0);
		} else {
			int offset= Math.min(fDeleteOffset, fStartOffset);
			int endOffset= Math.max(fDeleteOffset, fEndOffset);
			return new Region(offset, endOffset - offset);
		}
	}

	@Override
	public IRegion documentChanged2(DocumentEvent e) {
		checkInitialization(); // Has to force the initialization because
							   // the fInitialized field is private...

		try {
			Assert.isTrue(e.getDocument() == fDocument);
	
			Position[] category= getPositions();
			IRegion line= fDocument.getLineInformationOfOffset(e.getOffset());
			int reparseStart= line.getOffset();
			int partitionStart= -1;
			String contentType= null;
			int newLength= e.getText() == null ? 0 : e.getText().length();
// !--- This is the part changed from FastPartitioner ---!
//	We always restart at the beginning of the partition that precedes or contains
//	the change (or at the start of the document if any).
//	In particular there are never any resumes in the middle of a partition.
			int first= fDocument.computeIndexInCategory(fPositionCategory, reparseStart);
			if (first > 0)	{
				TypedPosition partition= (TypedPosition) category[first - 1];
				partitionStart= partition.getOffset();
				contentType= partition.getType();
				reparseStart= partitionStart;
				-- first;
//				
//				if (partition.includes(reparseStart)) {
//					partitionStart= partition.getOffset();
//					contentType= partition.getType();
//					reparseStart= partitionStart;
//					-- first;
//				} else if (reparseStart == e.getOffset() && reparseStart == partition.getOffset() + partition.getLength()) {
//					partitionStart= partition.getOffset();
//					contentType= partition.getType();
//					reparseStart= partitionStart;
//					-- first;
//				} else {
//					partitionStart= partition.getOffset() + partition.getLength();
//					contentType= IDocument.DEFAULT_CONTENT_TYPE;
//				}
// !-----------------------------------------------------!
			} else {
				partitionStart= 0;
				reparseStart= 0;
			}
	
			fPositionUpdater.update(e);
			for (int i= first; i < category.length; i++) {
				Position p= category[i];
				if (p.isDeleted) {
					rememberDeletedOffset(e.getOffset());
					break;
				}
			}
			clearPositionCache();
			category= getPositions();
	
			debugReparseStart(e, reparseStart);
			fScanner.setPartialRange(fDocument, reparseStart, fDocument.getLength() - reparseStart, contentType, partitionStart);
	
			int behindLastScannedPosition= reparseStart;
			IToken token= fScanner.nextToken();
	
			while (!token.isEOF()) {
	
				contentType= getTokenContentType(token);
	
				if (!isSupportedContentType(contentType)) {
					token= fScanner.nextToken();
					continue;
				}
	
				int start= fScanner.getTokenOffset();
				int length= fScanner.getTokenLength();
	
				behindLastScannedPosition= start + length;
				int lastScannedPosition= behindLastScannedPosition - 1;
	
				// remove all affected positions
				while (first < category.length) {
					TypedPosition p= (TypedPosition) category[first];
					if (lastScannedPosition >= p.offset + p.length ||
							(p.overlapsWith(start, length) &&
							 	(!fDocument.containsPosition(fPositionCategory, start, length) ||
							 	 !contentType.equals(p.getType())))) {
	
						rememberRegion(p.offset, p.length);
						fDocument.removePosition(fPositionCategory, p);
						++ first;
	
					} else
						break;
				}
	
				// if position already exists and we have scanned at least the
				// area covered by the event, we are done
				if (fDocument.containsPosition(fPositionCategory, start, length)) {
					if (lastScannedPosition >= e.getOffset() + newLength)
						return createRegion();
					++ first;
				} else {
					// insert the new type position
					try {
						fDocument.addPosition(fPositionCategory, new TypedPosition(start, length, contentType));
						rememberRegion(start, length);
					} catch (BadPositionCategoryException x) {
					} catch (BadLocationException x) {
					}
				}
	
				token= fScanner.nextToken();
			}
	
			first= fDocument.computeIndexInCategory(fPositionCategory, behindLastScannedPosition);
	
			clearPositionCache();
			category= getPositions();
			TypedPosition p;
			while (first < category.length) {
				p= (TypedPosition) category[first++];
				fDocument.removePosition(fPositionCategory, p);
				rememberRegion(p.offset, p.length);
			}
	
		} catch (BadPositionCategoryException x) {
			// should never happen on connected documents
		} catch (BadLocationException x) {
		} finally {
			clearPositionCache();
		}
	
		return createRegion();
	}
}