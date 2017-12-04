package dolmenplugin.editors;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.editors.text.TextEditor;

import dolmenplugin.editors.jg.JGEditor;
import dolmenplugin.editors.jl.JLEditor;
import dolmenplugin.lib.ByRef;
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
public abstract class DolmenEditor<T> extends TextEditor {
	
	/**
	 * The Dolmen editors' common keybindings scope
	 */
	public static final String DOLMEN_EDITOR_SCOPE = "dolmenplugin.editors.DolmenScope";
	
	/** 
	 * See {@link #getLexer()}
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