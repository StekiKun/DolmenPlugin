package dolmenplugin.editors.jg;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

import dolmenplugin.editors.ColorManager;
import dolmenplugin.editors.IColorConstants;
import dolmenplugin.editors.JavaScanner;

/**
 * The custom editor configuration for Dolmen grammar descriptions.
 * <p>
 * Besides the default content type, this editor uses special
 * rules to highlight Java semantic actions, comments and 
 * string/char literals.
 * 
 * @author St�phane Lescuyer
 */
public class JGConfiguration extends SourceViewerConfiguration {
//	private JGDoubleClickStrategy doubleClickStrategy;
	private JGScanner jgScanner;
	private JGCommentScanner jgCommentScanner;
	private JavaScanner jgJavaScanner;
	
	private ColorManager colorManager;

	public JGConfiguration(ColorManager colorManager) {
		this.colorManager = colorManager;
	}

	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return new String[] {
			IDocument.DEFAULT_CONTENT_TYPE,
			JGPartitionScanner.JG_COMMENT,
			JGPartitionScanner.JG_JAVA
		};
	}

//	public ITextDoubleClickStrategy getDoubleClickStrategy(
//		ISourceViewer sourceViewer,
//		String contentType) {
//		if (doubleClickStrategy == null)
//			doubleClickStrategy = new JGDoubleClickStrategy();
//		return doubleClickStrategy;
//	}

	protected JGScanner getJGScanner() {
		if (jgScanner == null) {
			jgScanner = new JGScanner(colorManager);
			jgScanner.setDefaultReturnToken(
				new Token(
					new TextAttribute(
						colorManager.getColor(IColorConstants.DEFAULT))));
		}
		return jgScanner;
	}

	protected JGCommentScanner getJGCommentScanner() {
		if (jgCommentScanner == null) {
			jgCommentScanner = new JGCommentScanner(colorManager);
		}
		return jgCommentScanner;
	}
	
	protected JavaScanner getJGJavaScanner() {
		if (jgJavaScanner == null) {
			jgJavaScanner = new JavaScanner(colorManager);
		}
		return jgJavaScanner;
	}
	
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();

        DefaultDamagerRepairer ddr = new DefaultDamagerRepairer(getJGScanner());
        reconciler.setRepairer(ddr, IDocument.DEFAULT_CONTENT_TYPE);
        reconciler.setDamager(ddr, IDocument.DEFAULT_CONTENT_TYPE);

        ddr = new DefaultDamagerRepairer(getJGCommentScanner());
        reconciler.setRepairer(ddr, JGPartitionScanner.JG_COMMENT);
        reconciler.setDamager(ddr, JGPartitionScanner.JG_COMMENT);

        ddr = new DefaultDamagerRepairer(getJGJavaScanner());
        reconciler.setRepairer(ddr, JGPartitionScanner.JG_JAVA);
        reconciler.setDamager(ddr, JGPartitionScanner.JG_JAVA);

		return reconciler;
	}

}