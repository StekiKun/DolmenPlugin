package dolmenplugin.editors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.texteditor.SimpleMarkerAnnotation;

/**
 * Class which provides hover info for annotation markers both in
 * the ruler and in the actual text. It is used for both Dolmen
 * lexer and parser viewers.
 * <p>
 * It shows the messages associated to the local markers,
 * as expected.
 * 
 * @author Stéphane Lescuyer
 */
public class MarkerAnnotationHover implements IAnnotationHover, ITextHover {

	/**
	 * Helper function used to implement both annotation and text hovers.
	 * It returns the information based on the problem markers that overlap
	 * the given {@code region} of the document.
	 * 
	 * @param sourceViewer
	 * @param region
	 * @return an adequate hover info, or {@code null} if no info should be displayed
	 */
	private String getMarkersInfo(ISourceViewer sourceViewer, IRegion region) {
		IAnnotationModel annotModel = sourceViewer.getAnnotationModel();
		
		// Find all *marker* annotations in the given source 
		// viewer which overlap the specified region
		List<SimpleMarkerAnnotation> annots = new ArrayList<>();
		for (Iterator<Annotation> it = annotModel.getAnnotationIterator();
				it.hasNext(); ) {
			Annotation a = it.next();
			if (a.isMarkedDeleted()) continue;
			if (!(a instanceof SimpleMarkerAnnotation)) continue;
			SimpleMarkerAnnotation ma = (SimpleMarkerAnnotation) a;
			
			Position p = annotModel.getPosition(a);
			if (p == null) continue;			
			if (p.overlapsWith(region.getOffset(), region.getLength()))
				annots.add(ma);
		}
		
		if (annots.isEmpty()) return null;
		if (annots.size() == 1)
			return annots.get(0).getText();
		// Concatenate the messages for the various markers 
		StringBuilder buf = new StringBuilder();
		buf.append(annots.size()).append(" problems here:");
		for (int i = 0; i < annots.size(); ++i)
			buf.append("\n-").append(annots.get(i).getText());
		return buf.toString();
	}
	
	@Override
	public String getHoverInfo(ISourceViewer sourceViewer, int lineNumber) {
		IDocument document = sourceViewer.getDocument();
		if (document == null) return null;
		final IRegion reg;
		try {
			reg = document.getLineInformation(lineNumber);
		} catch (BadLocationException e) {
			return null;
		}
		return getMarkersInfo(sourceViewer, reg);
	}

	@Override
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		if (hoverRegion == null) return null;
		if (!(textViewer instanceof ISourceViewer)) return null;
		ISourceViewer sourceViewer = (ISourceViewer) textViewer;
		return getMarkersInfo(sourceViewer, hoverRegion);
	}

	@Override
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		// If the cursor hovers over a selection, use the whole section
		// Otherwise, use the position of the cursor
		Point selection = textViewer.getSelectedRange();
		if (selection.x <= offset && offset < selection.x + selection.y)
			return new Region(selection.x, selection.y);
		return new Region(offset, 0);
	}

}
