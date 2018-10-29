package dolmenplugin.editors.jl;

import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.formatter.IContentFormatter;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlinkPresenter;
import org.eclipse.jface.text.information.IInformationPresenter;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

import dolmenplugin.editors.ColorManager;
import dolmenplugin.editors.IColorConstants;
import dolmenplugin.editors.JavaScanner;
import dolmenplugin.editors.MarkerAnnotationHover;
import dolmenplugin.editors.OptionsScanner;
import dolmenplugin.editors.jl.JLContentAssistProcessor.ContentType;

/**
 * The custom editor configuration for Dolmen lexer descriptions.
 * <p>
 * Besides the default content type, this editor uses special
 * rules to highlight Java semantic actions, comments and 
 * string/char literals.
 * 
 * @author Stéphane Lescuyer
 */
public class JLConfiguration extends SourceViewerConfiguration {
	private MarkerAnnotationHover jlAnnotationHover;
//	private JLDoubleClickStrategy doubleClickStrategy;
	private JLScanner jlScanner;
	private OptionsScanner jlOptionsScanner;
	private JLCommentScanner jlCommentScanner;
	private JavaScanner jlJavaScanner;
	
	private ColorManager colorManager;
	private JLEditor jlEditor;

	public JLConfiguration(ColorManager colorManager, JLEditor jlEditor) {
		this.colorManager = colorManager;
		this.jlAnnotationHover = new MarkerAnnotationHover();
		this.jlEditor = jlEditor;
	}

	@Override
	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return new String[] {
			IDocument.DEFAULT_CONTENT_TYPE,
			JLPartitionScanner.JL_COMMENT,
			JLPartitionScanner.JL_JAVA
		};
	}

	@Override
	public String getConfiguredDocumentPartitioning(ISourceViewer sourceViewer) {
		return JLDocumentSetupParticipant.PARTITIONING_ID;
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
						colorManager.getColor(IColorConstants.DEFAULT))));
		}
		return jlScanner;
	}

	protected OptionsScanner getJLOptionsScanner() {
		if (jlOptionsScanner == null) {
			jlOptionsScanner = new OptionsScanner(colorManager);
		}
		return jlOptionsScanner;
	}

	protected JLCommentScanner getJLCommentScanner() {
		if (jlCommentScanner == null) {
			jlCommentScanner = new JLCommentScanner(colorManager);
		}
		return jlCommentScanner;
	}

	protected JavaScanner getJLJavaScanner() {
		if (jlJavaScanner == null) {
			jlJavaScanner = new JavaScanner(colorManager, IColorConstants.JAVA_BG);
		}
		return jlJavaScanner;
	}
	
	@Override
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		reconciler.setDocumentPartitioning(JLDocumentSetupParticipant.PARTITIONING_ID);

        DefaultDamagerRepairer ddr = new DefaultDamagerRepairer(getJLScanner());
        reconciler.setRepairer(ddr, IDocument.DEFAULT_CONTENT_TYPE);
        reconciler.setDamager(ddr, IDocument.DEFAULT_CONTENT_TYPE);

        ddr = new DefaultDamagerRepairer(getJLOptionsScanner());
        reconciler.setRepairer(ddr, JLPartitionScanner.JL_OPTIONS);
        reconciler.setDamager(ddr, JLPartitionScanner.JL_OPTIONS);

        ddr = new DefaultDamagerRepairer(getJLCommentScanner());
        reconciler.setRepairer(ddr, JLPartitionScanner.JL_COMMENT);
        reconciler.setDamager(ddr, JLPartitionScanner.JL_COMMENT);

        ddr = new DefaultDamagerRepairer(getJLJavaScanner());
        reconciler.setRepairer(ddr, JLPartitionScanner.JL_JAVA);
        reconciler.setDamager(ddr, JLPartitionScanner.JL_JAVA);

		return reconciler;
	}
	
	@Override
	public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
		return jlAnnotationHover;
	}

	@Override
	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
		return jlAnnotationHover;
	}
	
	@Override
	public IContentFormatter getContentFormatter(ISourceViewer sourceViewer) {
		// TODO Auto-generated method stub
		return super.getContentFormatter(sourceViewer);
	}

	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		ContentAssistant assistant = new ContentAssistant();
		// General configuration
		assistant.setContextInformationPopupOrientation(ContentAssistant.CONTEXT_INFO_BELOW);
		assistant.setDocumentPartitioning(JLDocumentSetupParticipant.PARTITIONING_ID);
		assistant.setEmptyMessage("Di me mas!");
		// assistant.setProposalPopupOrientation(ContentAssistant.PROPOSAL_STACKED);
		assistant.setShowEmptyList(true); // for now, for debug
		assistant.setStatusLineVisible(true);
		assistant.enableAutoInsert(true);
		assistant.enableColoredLabels(true);
		assistant.setSorter(JLContentAssistProcessor.SORTER);
		
		// Now, the actual processors
		assistant.setContentAssistProcessor(
			new JLContentAssistProcessor(jlEditor, ContentType.JAVA), JLPartitionScanner.JL_JAVA);
		assistant.setContentAssistProcessor(
			new JLContentAssistProcessor(jlEditor, ContentType.OPTIONS), JLPartitionScanner.JL_OPTIONS);
		assistant.setContentAssistProcessor(
			new JLContentAssistProcessor(jlEditor, ContentType.DEFAULT), IDocument.DEFAULT_CONTENT_TYPE);
		return assistant;
	}

	@Override
	public IQuickAssistAssistant getQuickAssistAssistant(ISourceViewer sourceViewer) {
		// TODO Auto-generated method stub
		return super.getQuickAssistAssistant(sourceViewer);
	}

	@Override
	public IAutoEditStrategy[] getAutoEditStrategies(ISourceViewer sourceViewer, String contentType) {
		// TODO Auto-generated method stub
		return super.getAutoEditStrategies(sourceViewer, contentType);
	}

	@Override
	public String[] getDefaultPrefixes(ISourceViewer sourceViewer, String contentType) {
		// TODO Auto-generated method stub
		return super.getDefaultPrefixes(sourceViewer, contentType);
	}

	@Override
	public ITextDoubleClickStrategy getDoubleClickStrategy(ISourceViewer sourceViewer, String contentType) {
		// TODO Auto-generated method stub
		return super.getDoubleClickStrategy(sourceViewer, contentType);
	}

	@Override
	public int[] getConfiguredTextHoverStateMasks(ISourceViewer sourceViewer, String contentType) {
		// TODO Auto-generated method stub
		return super.getConfiguredTextHoverStateMasks(sourceViewer, contentType);
	}

	@Override
	public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
		// TODO Auto-generated method stub
		return super.getInformationControlCreator(sourceViewer);
	}

	@Override
	public IInformationPresenter getInformationPresenter(ISourceViewer sourceViewer) {
		// TODO Auto-generated method stub
		return super.getInformationPresenter(sourceViewer);
	}

	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
		// TODO Auto-generated method stub
		return super.getHyperlinkDetectors(sourceViewer);
	}

	@Override
	public IHyperlinkPresenter getHyperlinkPresenter(ISourceViewer sourceViewer) {
		// TODO Auto-generated method stub
		return super.getHyperlinkPresenter(sourceViewer);
	}

	@Override
	public int getHyperlinkStateMask(ISourceViewer sourceViewer) {
		// TODO Auto-generated method stub
		return super.getHyperlinkStateMask(sourceViewer);
	}

}