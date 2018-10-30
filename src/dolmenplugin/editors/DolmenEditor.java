package dolmenplugin.editors;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;

import common.Maps;
import dolmenplugin.editors.jg.JGEditor;
import dolmenplugin.editors.jl.JLEditor;
import dolmenplugin.lib.ByRef;
import syntax.Grammar;
import syntax.Lexer;
import syntax.Located;

/**
 * Base implementation common to editors for both lexer
 * descriptions and parser descriptions. 
 * 
 * <p> 
 * It shares the task of handling the <i>model description</i>
 * of the editor's contents, namely a {@link Lexer} for 
 * {@link JLEditor} and a {@link Grammar} for {@link JGEditor}.
 * <p>
 * This class updates the model every time the editor's contents
 * are saved. Subclassers can make additional calls to 
 * {@link #updateModel()} whenever suitable.
 * 
 * @author Stéphane Lescuyer
 *
 * @param <T>	the type of the concrete model for the editor's contents
 */
public abstract class DolmenEditor<T> extends TextEditor
	implements ISelectionListener {
	
	/**
	 * The Dolmen editors' common keybindings scope
	 */
	public static final String DOLMEN_EDITOR_SCOPE = "dolmenplugin.editors.DolmenScope";
	
	/** 
	 * See {@link #getModel(ByRef)}.
	 */
	protected T model;
	
	/**
	 * Is {@code true} if and only if {@code lexer} is up-to-date with
	 * the resource's state (i.e. not the document's contents necessarily,
	 * but the contents that were saved last).
	 */
	protected boolean uptodate;
	
	protected DolmenEditor() {
		this.model = null;
		this.uptodate = true;
	}
	
	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		// Register a listener for selection changes
		getEditorSite().getPage().addPostSelectionListener(this);
	}

	@Override
	protected void initializeKeyBindingScopes() {
		setKeyBindingScopes(new String[] { DOLMEN_EDITOR_SCOPE });
	}
	
	/**
	 * @return the document opened by this editor
	 */
	public final IDocument getDocument() {
		return this.getDocumentProvider().getDocument(this.getEditorInput());
	}

	/**
	 * The model description parsed from this editor's contents.
	 * It represents the last time the editor was saved and its contents
	 * were correctly parsed into a model description.
	 * <p>
	 * Can be {@code null} if the model was never successfully parsed.
	 * 
	 *  @param stale	if non-null, will contain {@code true} or {@code false}
	 *  				on return depending on whether the returned model
	 *  				is stale wrt the file's contents, or up-to-date with
	 *  				the last time the editor was saved
	 */
	public T getModel(ByRef<Boolean> stale) {
		if (stale == null) return model;
		synchronized (this) {
			stale.set(!uptodate);
			return model;
		}
	}
	
	/**
	 * Same as {@link #getModel(null)}
	 * @return
	 */
	public T getModel() {
		return getModel(null);
	}

	/**
	 * @return the new model description for the current
	 * 	contents of the document, or {@code null} if 
	 *  parsing the contents failed
	 */
	protected abstract @Nullable T parseModel();
	
	/**
	 * Called every time a new model description 
	 * is successfully installed
	 * 
	 * @param model
	 */
	protected abstract void modelChanged(T model);
	
	/**
	 * Tries to update the model description from the
	 * current contents. Is called every time the 
	 * editor is saved.
	 */
	protected final synchronized void updateModel() {
		@Nullable T newModel = parseModel();
		if (newModel != null) {
			model = newModel;
			uptodate = true;
			modelChanged(model);
		}
		else
			uptodate = false;
	}
	
	@Override
	protected void editorSaved() {
		super.editorSaved();
		updateModel();
	}
	
	@Override
	public final void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (part != DolmenEditor.this) {
			System.out.println("Selection changed somewhere else");
			return;
		}
		if (!(selection instanceof ITextSelection)) return;
		ITextSelection textSelection = (ITextSelection) selection;
		String msg = String.format(
			"Selected {offset = %d, length = %d, lines = %d-%d, text = %s}",
			textSelection.getOffset(), textSelection.getLength(),
			textSelection.getStartLine(), textSelection.getEndLine(),
			textSelection.getText());
		System.out.println(msg);
		
		@Nullable Occurrences occurrences = findOccurrencesFor(textSelection);
		if (occurrences == null) {
			removeOccurrences();
			return;
		}
		System.out.println("Declaration: " + occurrences.declaration.toString());
		System.out.println("References: " + occurrences.references.toString());
		updateOccurrences(occurrences);
	}
	
	private Annotation[] markedOccurrences = null;
	
	public static final class Occurrences {
		protected final Located<String> declaration;
		protected final List<Located<?>> references;
		
		public Occurrences(Located<String> declaration, List<Located<?>> references) {
			this.declaration = declaration;
			this.references = references;
		}
	}
	
	/**
	 * NB: technically the internal search logic could be shared in DolmenEditor
	 * by simply having subclasses return an iteration of located declarations.
	 * That being said if there are many of them, this leaves the door open for
	 * some model-dependent quick pruning in the subclasses (e.g. in .jg files,
	 * tokens are all before rules, so depending on where the selection lies wrt
	 * the first actual rule...).
	 * 
	 * @WIP
	 * @param selection
	 * @return the entity selected (i.e. the caret/selection must be enclosed in 
	 *  the entity's name declaration)
	 */
	protected abstract @Nullable Occurrences findOccurrencesFor(ITextSelection selection);

	protected static boolean enclosedInLocation(Located<?> loc, ITextSelection selection) {
		if (selection.getOffset() < loc.start.offset) return false;
		if (selection.getOffset() + selection.getLength() > loc.end.offset) return false;
		return true;
	}
	
	private final static String DECL_ANNOT_TYPE = "org.eclipse.jdt.ui.occurrences.write";
	private final static String REF_ANNOT_TYPE = "org.eclipse.jdt.ui.occurrences";
	
	private void updateOccurrences(@Nullable Occurrences occurrences) {
		IDocumentProvider documentProvider = getDocumentProvider();
		if (documentProvider == null) return;
		IAnnotationModel annotationModel = documentProvider.getAnnotationModel(getEditorInput());
		if (!(annotationModel instanceof IAnnotationModelExtension)) return;
		IAnnotationModelExtension model = (IAnnotationModelExtension) annotationModel;
		
		if (occurrences == null) {
			if (markedOccurrences != null) {
				model.replaceAnnotations(markedOccurrences, null);
				markedOccurrences = null;
			}
			return;
		}
		
		final Map<Annotation, Position> newAnnotations = Maps.create();
		final String desc = occurrences.declaration.val;
		// The declaration annotation is highlighted slightly differently...
		{
			Located<String> decl = occurrences.declaration; 
			Position pos = new Position(decl.start.offset, decl.length());
			Annotation annot = new Annotation(DECL_ANNOT_TYPE, false, desc);
			newAnnotations.put(annot, pos);
		}
		// ...than the references
		for (Located<?> loc : occurrences.references) {
			Position pos = new Position(loc.start.offset, loc.length());
			Annotation annot = new Annotation(REF_ANNOT_TYPE, false, desc);
			newAnnotations.put(annot, pos);
		}
		model.replaceAnnotations(markedOccurrences, newAnnotations);
		markedOccurrences = newAnnotations.keySet().toArray(
			new Annotation[1 + occurrences.references.size()]);
	}
	
	private void removeOccurrences() {
		if (markedOccurrences == null) return;
		
		IDocumentProvider documentProvider = getDocumentProvider();
		if (documentProvider == null) return;
		IAnnotationModel annotationModel = documentProvider.getAnnotationModel(getEditorInput());
		if (!(annotationModel instanceof IAnnotationModelExtension)) return;
		IAnnotationModelExtension model = (IAnnotationModelExtension) annotationModel;
		
		model.replaceAnnotations(markedOccurrences, null);
		markedOccurrences = null;
	}
	
	/**
	 * If there are several different kinds of entities which
	 * can share the same name, which one is returned is not
	 * specified. Use {@link #findDeclarationFor(String, Class)}
	 * instead in this case.
	 * 
	 * @param name
	 * @return the place where an entity called {@code name} 
	 * 	is declared in this editor's model, or {@code null}
	 * 	if none could be found
	 */
	public abstract @Nullable Located<?> findDeclarationFor(String name);
	
	/**
	 * 
	 * @param name
	 * @param clazz
	 * @return the declaration of the type given by {@code clazz}
	 * 	with the given {@code name} in this editor's model, or
	 * 	{@code null} if none could be found
	 */
	public abstract <Decl> @Nullable Decl 
		findDeclarationFor(String name, Class<Decl> clazz);
	
}