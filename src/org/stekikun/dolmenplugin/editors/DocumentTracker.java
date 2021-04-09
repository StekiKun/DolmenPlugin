package org.stekikun.dolmenplugin.editors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.ui.IEditorInput;
import org.stekikun.dolmen.syntax.Extent;
import org.stekikun.dolmen.syntax.Located;

/**
 * An instance of this class is associated to every document displayed
 * in a {@link DolmenEditor} and is used to track the changes to a
 * dirty document. This information in turn is used to transform positions
 * (for marking occurrences, jump-to commands, etc) from the last saved
 * state to the current dirty one.
 * 
 * @author Stéphane Lescuyer
 */
public final class DocumentTracker implements IDocumentListener {
	public final DolmenEditor<?> editor;
	public final IEditorInput input;
	public final IDocument document;

	private int numChanges = 0;
	private int changes = 0;
	
	private @Nullable String original = null;
	private List<Edit> edits = new ArrayList<>();

	private static boolean debug = false;
	
	private void logf(String format, Object... args) {
		if (!debug) return;
		System.out.println(String.format(format, args));
	}
	
	DocumentTracker(DolmenEditor<?> editor, IEditorInput input, IDocument document) {
		this.editor = Objects.requireNonNull(editor);
		this.input = Objects.requireNonNull(input);
		this.document = Objects.requireNonNull(document);
		logf("[Tracker] Created %s", input.getName());
	}
	
	public static final class Range {
		private int offset;	// >= 0 if valid or -1 if deleted
		private int length;	// >= 0
		
		private Range(int offset, int length) {
			this.offset = offset;
			this.length = length;
		}
		
		public int getOffset() {
			return offset;
		}
		
		public int getLength() {
			return length;
		}
		
		void delete() {
			this.offset = -1;
		}
		
		boolean isDeleted() {
			return offset < 0;
		}
	}
	
	private static final class Edit {
		// Non-final because we may aggregate changes later
		int offset;
		int length;
		int newLength;
		
		Edit(int offset, int length, int newLength) {
			this.offset = offset;
			this.length = length;
			this.newLength = newLength;
		}
		
		void transform(Range range) {
			// If the range has already been deleted, return
			if (range.isDeleted()) return;
			
			int end = offset + length;
			int rstart = range.offset, rlen = range.length, rend = range.offset + rlen;
			
			// If the edit deletes the range, delete it and return
			if (offset < rstart && rend < end) {
				range.delete();
				return;
			}
			
			// Otherwise, if the edit just strictly replaces some text inside the
			// range, adapt the length of the range and return
			if (length > 0 && rstart <= offset && end <= rend) {
				range.length += newLength - length;
				return;
			}
			
			// Otherwise, we treat it as the deletion of the replaced text (if any)
			// followed by the insertion of the new text (if any). We do not delete
			// the range when it simply becomes empty in the first phase, as it may
			// expand again in the next phase.
			// - handle the deletion part
			if (length > 0) {
				if (offset >= rend) {
					// There is nothing to adapt when the edit is after the range
				}
				else if (end <= rstart) {
					// If the deletion is before, the range is shifted
					range.offset -= length;
				}
				else {
					// At this point, we have a definite overlap between the range
					// and the deletion. We know the latter is not included in the
					// former, and does not contain it strictly either.
					if (offset <= rstart) {
						if (offset != rstart && end > rend) throw new IllegalStateException();
						// We shrink the range (possibly to 0 if it was contained
						// but not strictly contained) and shift it as well.
						range.offset -= rstart - offset;
						range.length -= Math.min(range.length, end - rstart);
					}
					else {
						if (end <= rend) throw new IllegalStateException();
						// We shrink the range (can't be to 0 in this case)
						range.length -= rend - offset;
					}
				}
			}
			// - handle the insertion part
			rstart = range.offset; rlen = range.length; rend = range.offset + rlen;
			if (newLength > 0) {
				if (offset >= rend) {
					// There is nothing to adapt when the edit is after the range					
				}
				else if (rstart < offset) {
					// We extend the range to account for the replacement
					range.length += newLength;
				}
				else {
					// We shift the range as the new text will be inserted before
					range.offset += newLength;
				}
			}
		}
	}
    
	private @Nullable Range transform(int start, int length) {
		Range range = new Range(start, length);
		for (Edit edit : edits) {
			if (range.isDeleted()) return null;
			edit.transform(range);
		}
		if (range.isDeleted()) return null;
		return range;
	}
	
	public @Nullable Range transform(Extent extent) {
		return transform(extent.startPos, extent.length());
	}

	public @Nullable Range transform(Located<?> loc) {
		return transform(loc.start.offset, loc.length());
	}
	
	public @Nullable Range transform(OutlineNode<?> node) {
		Range r = transform(node.getOffset(), node.getLength());
		logf("[Tracker] Transform [%d, %d + %d[: %s",
				node.getOffset(), node.getOffset(), node.getLength(),
				r == null ? "null" : 
					String.format("[%d, %d + %d[", r.offset, r.offset, r.length));
		return r;
	}

	public int getNumChanges() {
		return numChanges;
	}

	public int getNetChanges() {
		return changes;
	}

	public void wasSaved() {
		logf("[Tracker] Reset %s", input.getName());
		this.numChanges = 0;
		this.changes = 0;
		this.edits.clear();
	}
	
	public void dispose() {
		logf("[Tracker] Disposed %s", input.getName());
	}

	@Override
	public String toString() {
		return String.format("[Tracker] %s: changes = %d, delta = %d", input.getName(), numChanges, changes);
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		// Nothing to do
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		// Handle the initial document content loading specially
		if (event.fModificationStamp == 0L) {
			if (original != null)
				logf("[Tracker] %s: original already set for initial event");
			original = event.fText;
			return;
		}
		logf("[Tracker] Changed %s: stamp=%d, offset=%d, length=%d, replacement=%s",
				input.getName(), 
				event.fModificationStamp, event.fOffset, event.fLength, event.fText);
		int delta = event.fText.length() - event.fLength;
		this.changes += delta;
		this.numChanges++;
		this.edits.add(new Edit(event.fOffset, event.fLength, event.fText.length()));
	}
	
}