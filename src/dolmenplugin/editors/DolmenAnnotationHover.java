package dolmenplugin.editors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
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
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.SimpleMarkerAnnotation;

import dolmenplugin.editors.jg.JGEditor;
import dolmenplugin.editors.jl.JLEditor;
import dolmenplugin.handlers.HandlerUtils;
import syntax.Grammar.TokenDecl;
import syntax.GrammarRule;
import syntax.Lexer;
import syntax.Regular;

/**
 * Class which provides hover info for annotation markers both in
 * the ruler and in the actual text. It is used for both Dolmen
 * lexer and parser viewers.
 * <p>
 * It shows the messages associated to the local markers,
 * as expected. For text hovers, if there are no markers
 * and the text corresponds to some reference to a declared entity
 * (lexer entry, parser terminal or non-terminal, see 
 * {@link DolmenEditor#findDeclarationFor(String)}), a description
 * of the entity is displayed instead.
 * 
 * @author Stéphane Lescuyer
 */
public class DolmenAnnotationHover implements IAnnotationHover, ITextHover {

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

		// Try markers in the hover region
		ISourceViewer sourceViewer = (ISourceViewer) textViewer;
		@Nullable String hover = getMarkersInfo(sourceViewer, hoverRegion);
		if (hover != null) return hover;
		
		// Otherwise find a declaration corresponding to the hovered region
		IDocument doc = sourceViewer.getDocument();
		if (doc == null) return null;
		DolmenEditor<?> editor = null;
		// TODO: Not the best of ways to find the current editor back,
		//	but getActivePage() returns null... Shall I keep a static map
		//  associating Documents to their editors in DolmenEditor ?
		windows:
		for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
			for (IWorkbenchPage page : window.getPages()) {
				for (IEditorReference edref : page.getEditorReferences()) {
					IEditorPart ed = edref.getEditor(false);
					if (ed instanceof DolmenEditor<?>
						&& ((DolmenEditor<?>) ed).getDocument() == doc) {
						editor = (DolmenEditor<?>) ed;
						break windows;
					}
				}
			}
		}
		if (editor == null) return null;
		try {
			String selected = hoverRegion.getLength() == 0 ?
				HandlerUtils.selectWord(doc, hoverRegion.getOffset()).word :
				doc.get(hoverRegion.getOffset(), hoverRegion.getLength());
			
			return getDescriptionFor(editor, selected);
		} catch (BadLocationException e) {
			return null;
		}
	}

	@Override
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		// If the cursor hovers over a selection, use the whole selection
		// Otherwise, use the position of the cursor
		Point selection = textViewer.getSelectedRange();
		if (selection.x <= offset && offset < selection.x + selection.y)
			return new Region(selection.x, selection.y);
		return new Region(offset, 0);
	}

	private String getDescriptionFor(DolmenEditor<?> editor, String selected) {
		if (editor instanceof JLEditor) {
			// Supports Regular and Lexer.Entry
			Regular reg = editor.findDeclarationFor(selected, Regular.class);
			if (reg != null) return getHoverInfo(selected, reg);
			Lexer.Entry entry = editor.findDeclarationFor(selected, Lexer.Entry.class);
			if (entry != null) return getHoverInfo(entry);
		}
		else if (editor instanceof JGEditor) {
			// Supports TokenDecl and GrammarRule
			TokenDecl token = editor.findDeclarationFor(selected, TokenDecl.class);
			if (token != null) return getHoverInfo(token);
			GrammarRule rule = editor.findDeclarationFor(selected, GrammarRule.class);
			if (rule != null) return getHoverInfo(rule);
		}
		return null;
	}
	
	private String getHoverInfo(String selected, Regular regular) {
		return String.format("%s := %s", selected, regular.toString());
	}
	
	private String getHoverInfo(Lexer.Entry entry) {
		StringBuilder buf = new StringBuilder();
		buf.append(entry.visibility ? "public" : "private");
		buf.append(" ");
		buf.append(entry.returnType.find());
		buf.append(" ");
		buf.append(entry.name.val);
		if (entry.args != null) {
			buf.append("(").append(entry.args.find()).append(")");
		}
		return buf.toString();
	}
	
	private String getHoverInfo(TokenDecl decl) {
		return decl.toString();
	}
	
	private String getHoverInfo(GrammarRule rule) {
		StringBuilder buf = new StringBuilder();
		buf.append(rule.visibility ? "public" : "private");
		buf.append(" ");
		buf.append(rule.returnType.find());
		buf.append(" ");
		buf.append(rule.name.val);
		if (rule.args != null) {
			buf.append("(").append(rule.args.find()).append(")");
		}
		return buf.toString();
	}
}
