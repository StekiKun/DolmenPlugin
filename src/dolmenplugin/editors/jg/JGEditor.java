package dolmenplugin.editors.jg;

import org.eclipse.ui.editors.text.TextEditor;

import dolmenplugin.editors.ColorManager;

/**
 * Custom editor for Dolmen grammar descriptions (.jg)
 * 
 * @author St�phane Lescuyer
 */
public class JGEditor extends TextEditor {

	private ColorManager colorManager;

	public JGEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JGConfiguration(colorManager));
		setDocumentProvider(new JGDocumentProvider());
	}
	public void dispose() {
		colorManager.dispose();
		super.dispose();
	}

}
