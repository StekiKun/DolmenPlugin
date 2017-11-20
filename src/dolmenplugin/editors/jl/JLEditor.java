package dolmenplugin.editors.jl;

import org.eclipse.ui.editors.text.TextEditor;

import dolmenplugin.editors.ColorManager;

/**
 * Custom editor for Dolmen lexer descriptions (.jl)
 * 
 * @author Stéphane Lescuyer
 */
public class JLEditor extends TextEditor {

	private ColorManager colorManager;

	public JLEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JLConfiguration(colorManager));
		// setDocumentProvider(new TextFileDocumentProvider());
	}
	
	public void dispose() {
		colorManager.dispose();
		super.dispose();
	}
	
}
