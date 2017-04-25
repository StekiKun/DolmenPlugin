package dolmenplugin.editors;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

public class JLConfiguration extends SourceViewerConfiguration {
//	private JLDoubleClickStrategy doubleClickStrategy;
	private JLScanner jlScanner;
	private JLLiteralScanner jlLiteralScanner;
	private JLCommentScanner jlCommentScanner;
	private JLJavaScanner jlJavaScanner;
	
	private ColorManager colorManager;

	public JLConfiguration(ColorManager colorManager) {
		this.colorManager = colorManager;
	}

	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return new String[] {
			IDocument.DEFAULT_CONTENT_TYPE,
			JLPartitionScanner.JL_COMMENT,
			JLPartitionScanner.JL_JAVA,
			JLPartitionScanner.JL_LITERAL
		};
	}

//	public ITextDoubleClickStrategy getDoubleClickStrategy(
//		ISourceViewer sourceViewer,
//		String contentType) {
//		if (doubleClickStrategy == null)
//			doubleClickStrategy = new JLDoubleClickStrategy();
//		return doubleClickStrategy;
//	}

	protected JLScanner getJLScanner() {
		if (jlScanner == null) {
			jlScanner = new JLScanner(colorManager);
			jlScanner.setDefaultReturnToken(
				new Token(
					new TextAttribute(
						colorManager.getColor(IJLColorConstants.DEFAULT))));
		}
		return jlScanner;
	}

	protected JLLiteralScanner getJLLiteralScanner() {
		if (jlLiteralScanner == null) {
			jlLiteralScanner = new JLLiteralScanner(colorManager);
		}
		return jlLiteralScanner;
	}
	
	protected JLCommentScanner getJLCommentScanner() {
		if (jlCommentScanner == null) {
			jlCommentScanner = new JLCommentScanner(colorManager);
		}
		return jlCommentScanner;
	}
	
	protected JLJavaScanner getJLJavaScanner() {
		if (jlJavaScanner == null) {
			jlJavaScanner = new JLJavaScanner(colorManager);
		}
		return jlJavaScanner;
	}
	
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();

        DefaultDamagerRepairer ddr = new DefaultDamagerRepairer(getJLScanner());
        reconciler.setRepairer(ddr, IDocument.DEFAULT_CONTENT_TYPE);
        reconciler.setDamager(ddr, IDocument.DEFAULT_CONTENT_TYPE);

        ddr = new DefaultDamagerRepairer(getJLLiteralScanner());
        reconciler.setRepairer(ddr, JLPartitionScanner.JL_LITERAL);
        reconciler.setDamager(ddr, JLPartitionScanner.JL_LITERAL);

        ddr = new DefaultDamagerRepairer(getJLCommentScanner());
        reconciler.setRepairer(ddr, JLPartitionScanner.JL_COMMENT);
        reconciler.setDamager(ddr, JLPartitionScanner.JL_COMMENT);

        ddr = new DefaultDamagerRepairer(getJLJavaScanner());
        reconciler.setRepairer(ddr, JLPartitionScanner.JL_JAVA);
        reconciler.setDamager(ddr, JLPartitionScanner.JL_JAVA);

		return reconciler;
	}

}