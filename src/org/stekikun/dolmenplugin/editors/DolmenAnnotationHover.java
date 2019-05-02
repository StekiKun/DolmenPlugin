package org.stekikun.dolmenplugin.editors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.SimpleMarkerAnnotation;
import org.stekikun.dolmen.syntax.Lexer;
import org.stekikun.dolmen.syntax.Located;
import org.stekikun.dolmen.syntax.PGrammarRule;
import org.stekikun.dolmen.syntax.PGrammars.Sort;
import org.stekikun.dolmenplugin.base.Marker;
import org.stekikun.dolmenplugin.editors.jg.JGEditor;
import org.stekikun.dolmenplugin.editors.jg.JGEditor.FormalDecl;
import org.stekikun.dolmenplugin.editors.jl.JLEditor;
import org.stekikun.dolmenplugin.handlers.HandlerUtils;
import org.stekikun.dolmenplugin.handlers.HandlerUtils.SelectedWord;
import org.stekikun.dolmen.syntax.Regular;
import org.stekikun.dolmen.syntax.TokenDecl;

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
public class DolmenAnnotationHover 
	implements IAnnotationHover, ITextHover, ITextHoverExtension {

	/**
	 * Helper function used to implement both annotation and text hovers.
	 * It returns the information based on the problem markers that overlap
	 * the given {@code region} of the document.
	 * 
	 * @param sourceViewer
	 * @param region
	 * @param html		if {@code true}, build an HTML description, otherwise use raw text
	 * @return an adequate hover info, or {@code null} if no info should be displayed
	 */
	private String getMarkersInfo(
			ISourceViewer sourceViewer, IRegion region, boolean html) {
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

		
		// A function to fetch a message from an annotation, trying
		// to use an HTML description if any and if HTML is required
		Function<SimpleMarkerAnnotation, String> messageOf = (annot) -> {
			if (html) {
				@Nullable String res = annot.getMarker().getAttribute(Marker.DOLMEN_MARKER_HTML_MESSAGE, null);
				if (res != null) return res;
				// Fall back to non-HTML message otherwise
			}
			String res = annot.getMarker().getAttribute(IMarker.MESSAGE, "!no message!");
			return html ? Marker.escapeHtml(res) : res;
		};

		if (annots.isEmpty()) return null;
		if (annots.size() == 1)
			return messageOf.apply(annots.get(0));
		// Concatenate the messages for the various markers 
		StringBuilder buf = new StringBuilder();
		buf.append(annots.size()).append(" problems here:");
		if (html) {
			buf.append("<ul>");
			for (int i = 0; i < annots.size(); ++i)
				buf.append("<li>").append(messageOf.apply(annots.get(i)));
			buf.append("</ul>");
		}
		else {
			for (int i = 0; i < annots.size(); ++i)
				buf.append("\n-").append(messageOf.apply(annots.get(i)));
		}
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
		return getMarkersInfo(sourceViewer, reg, false);
	}

	@Override
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		if (hoverRegion == null) return null;
		if (!(textViewer instanceof ISourceViewer)) return null;

		// Try markers in the hover region
		ISourceViewer sourceViewer = (ISourceViewer) textViewer;
		@Nullable String hover = getMarkersInfo(sourceViewer, hoverRegion, true);
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
			final String selected;
			if (hoverRegion.getLength() == 0) {
				SelectedWord sw = HandlerUtils.selectWord(doc, hoverRegion.getOffset());
				if (sw == null) return null;
				selected = sw.word;
			}
			else
				selected = doc.get(hoverRegion.getOffset(), hoverRegion.getLength());
			
			return getDescriptionFor(editor, hoverRegion, selected);
		} catch (BadLocationException e) {
			return null;
		}
	}


	@Override
	public IInformationControlCreator getHoverControlCreator() {
		return new IInformationControlCreator() {
			@Override
			public IInformationControl createInformationControl(Shell shell) {
				return new DefaultInformationControl(shell,
					EditorsUI.getTooltipAffordanceString());
			}
		};
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

	private String getDescriptionFor(DolmenEditor<?> editor, IRegion hoverRegion, String selected) {
		ITextSelection ts = new TextSelection(hoverRegion.getOffset(), hoverRegion.getLength());
		if (editor instanceof JLEditor) {
			// Supports Regular and Lexer.Entry
			Regular reg = editor.findDeclarationFor(selected, ts, Regular.class);
			if (reg != null) return getHoverInfo(selected, reg);
			Lexer.Entry entry = editor.findDeclarationFor(selected, ts, Lexer.Entry.class);
			if (entry != null) return getHoverInfo(entry);
		}
		else if (editor instanceof JGEditor) {
			JGEditor jgEditor = (JGEditor) editor;
			// Supports TokenDecl, GrammarRule and FormalDecl
			JGEditor.SelectedDeclaration decl = jgEditor.findSelectedDeclarationFor(selected, ts);
			if (decl == null) return null;
			if (decl.declarationClass == TokenDecl.class) {
				TokenDecl token = editor.findDeclarationFor(selected, ts, TokenDecl.class);
				if (token != null) return getHoverInfo(token);				
			}
			else if (decl.declarationClass == FormalDecl.class) {
				FormalDecl formal = editor.findDeclarationFor(selected, ts, FormalDecl.class);
				if (formal != null) 
					return getHoverInfo(formal.rule, formal.param, jgEditor.getFormalSorts(formal.rule.name.val));
			}
			else if (decl.declarationClass == PGrammarRule.class) {
				PGrammarRule rule = editor.findDeclarationFor(selected, ts, PGrammarRule.class);
				if (rule != null)
					return getHoverInfo(rule, null, jgEditor.getFormalSorts(rule.name.val));
			}
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
		buf.append("<b>").append(entry.name.val).append("</b>");
		if (entry.args != null) {
			buf.append("(").append(entry.args.find()).append(")");
		}
		return buf.toString();
	}
	
	private String getHoverInfo(TokenDecl decl) {
		return "token " +
				(decl.valueType == null ? "" : "{" + decl.valueType.find() + "} ") +
				"<b>" + decl.name.val + "</b>";
	}
	
	private String getHoverInfo(PGrammarRule rule, 
			@Nullable Located<String> param, @Nullable List<Sort> sorts) {
		StringBuilder buf = new StringBuilder();
		buf.append(rule.visibility ? "public" : "private");
		buf.append(" ");
		buf.append(rule.returnType.find());
		buf.append(" ");
		buf.append("<b>").append(rule.name.val).append("</b>");
		if (!rule.params.isEmpty()) {
			boolean first = true;
			buf.append("<i>").append("&lt;");
			for (Located<String> formal : rule.params) {
				if (first) first = false;
				else buf.append(", ");
				if (param != null && formal.val.equals(param.val))
					buf.append("<b>").append(formal.val).append("</b>");
				else
					buf.append(formal.val);
			}
			buf.append("&gt;").append("</i>");
		}
		if (rule.args != null) {
			buf.append("(").append(rule.args.find()).append(")");
		}
		// Add description of sorts inferred for formals if available
		if (sorts != null && !sorts.isEmpty()) {
			buf.append("<p/><br/>");
			buf.append("<ul>\n");
			for (int i = 0; i < sorts.size(); ++i) {
				Located<String> fi = rule.params.get(i);
				boolean focussed = fi.equals(param);
				Sort si = sorts.get(i);
				buf.append("<li>");
				if (focussed) buf.append("<b>");
				buf.append("<i>").append(fi.val).append("</i> ");
				switch (si) {
				case ALL:
					buf.append(" can be anything");
					break;
				case ARGS:
					buf.append(" must expect arguments");
					break;
				case ARGS_VALUED:
					buf.append(" must be valued and expect arguments");
					break;
				case NO_ARGS:
					buf.append(" must not expect arguments");
					break;
				case NO_ARGS_VALUED:
					buf.append(" must be valued and not expect arguments");
					break;
				case VALUED:
					buf.append(" must be valued");
					break;
				}
				if (focussed) buf.append("</b>");
			}
			buf.append("</ul>\n");
		}
		return buf.toString();
	}
}
