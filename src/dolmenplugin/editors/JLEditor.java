package dolmenplugin.editors;

import org.eclipse.ui.editors.text.TextEditor;

public class JLEditor extends TextEditor {

	private ColorManager colorManager;

	public JLEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new JLConfiguration(colorManager));
		setDocumentProvider(new JLDocumentProvider());
	}
	public void dispose() {
		colorManager.dispose();
		super.dispose();
	}

}
