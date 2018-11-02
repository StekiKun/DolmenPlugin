package dolmenplugin.editors.jg;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

import dolmenplugin.editors.ColorManager;
import dolmenplugin.editors.IColorConstants;
import dolmenplugin.editors.JavaScanner;
import dolmenplugin.editors.DolmenAnnotationHover;
import dolmenplugin.editors.OptionsScanner;
import dolmenplugin.editors.jg.JGContentAssistProcessor;
import dolmenplugin.editors.jg.JGContentAssistProcessor.ContentType;
import dolmenplugin.editors.jg.JGDocumentSetupParticipant;
import dolmenplugin.editors.jg.JGPartitionScanner;

/**
 * The custom editor configuration for Dolmen grammar descriptions.
 * <p>
 * Besides the default content type, this editor uses special
 * rules to highlight Java semantic actions, comments and 
 * string/char literals.
 * 
 * @author Stéphane Lescuyer
 */
public class JGConfiguration extends SourceViewerConfiguration {
	private DolmenAnnotationHover jgAnnotationHover;
//	private JGDoubleClickStrategy doubleClickStrategy;
	private JGScanner jgScanner;
	private OptionsScanner jgOptionsScanner;
	private JGCommentScanner jgCommentScanner;
	private JavaScanner jgJavaScanner;
	private JavaScanner jgArgsScanner;
	
	private ColorManager colorManager;
	private JGEditor jgEditor;

	public JGConfiguration(ColorManager colorManager, JGEditor jgEditor) {
		this.colorManager = colorManager;
		this.jgAnnotationHover = new DolmenAnnotationHover();
		this.jgEditor = jgEditor;
	}

	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return new String[] {
			IDocument.DEFAULT_CONTENT_TYPE,
			JGPartitionScanner.JG_OPTIONS,
			JGPartitionScanner.JG_COMMENT,
			JGPartitionScanner.JG_JAVA,
			JGPartitionScanner.JG_ARGS
		};
	}

	@Override
	public String getConfiguredDocumentPartitioning(ISourceViewer sourceViewer) {
		return JGDocumentSetupParticipant.PARTITIONING_ID;
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
	
	protected OptionsScanner getJGOptionsScanner() {
		if (jgOptionsScanner == null) {
			jgOptionsScanner = new OptionsScanner(colorManager);
		}
		return jgOptionsScanner;
	}
	
	protected JGCommentScanner getJGCommentScanner() {
		if (jgCommentScanner == null) {
			jgCommentScanner = new JGCommentScanner(colorManager);
		}
		return jgCommentScanner;
	}
	
	protected JavaScanner getJGJavaScanner() {
		if (jgJavaScanner == null) {
			jgJavaScanner = new JavaScanner(colorManager, IColorConstants.JAVA_BG);
		}
		return jgJavaScanner;
	}
	
	protected JavaScanner getJGArgsScanner() {
		if (jgArgsScanner == null) {
			jgArgsScanner = new JavaScanner(colorManager, null);
		}
		return jgArgsScanner;
	}
	
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		reconciler.setDocumentPartitioning(JGDocumentSetupParticipant.PARTITIONING_ID);

        DefaultDamagerRepairer ddr = new DefaultDamagerRepairer(getJGScanner());
        reconciler.setRepairer(ddr, IDocument.DEFAULT_CONTENT_TYPE);
        reconciler.setDamager(ddr, IDocument.DEFAULT_CONTENT_TYPE);

        ddr = new DefaultDamagerRepairer(getJGOptionsScanner());
        reconciler.setRepairer(ddr, JGPartitionScanner.JG_OPTIONS);
        reconciler.setDamager(ddr, JGPartitionScanner.JG_OPTIONS);

        ddr = new DefaultDamagerRepairer(getJGCommentScanner());
        reconciler.setRepairer(ddr, JGPartitionScanner.JG_COMMENT);
        reconciler.setDamager(ddr, JGPartitionScanner.JG_COMMENT);

        ddr = new DefaultDamagerRepairer(getJGJavaScanner());
        reconciler.setRepairer(ddr, JGPartitionScanner.JG_JAVA);
        reconciler.setDamager(ddr, JGPartitionScanner.JG_JAVA);

        ddr = new DefaultDamagerRepairer(getJGArgsScanner());
        reconciler.setRepairer(ddr, JGPartitionScanner.JG_ARGS);
        reconciler.setDamager(ddr, JGPartitionScanner.JG_ARGS);

		return reconciler;
	}

	@Override
	public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
		return jgAnnotationHover;
	}

	@Override
	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
		return jgAnnotationHover;
	}
	
	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		ContentAssistant assistant = new ContentAssistant();
		// General configuration
		assistant.setContextInformationPopupOrientation(ContentAssistant.CONTEXT_INFO_BELOW);
		assistant.setDocumentPartitioning(JGDocumentSetupParticipant.PARTITIONING_ID);
		assistant.setEmptyMessage("Di me mas!");
		// assistant.setProposalPopupOrientation(ContentAssistant.PROPOSAL_STACKED);
		assistant.setShowEmptyList(true); // for now, for debug
		assistant.setStatusLineVisible(true);
		assistant.enableAutoInsert(true);
		assistant.enableColoredLabels(true);
		assistant.setSorter(JGContentAssistProcessor.SORTER);
		
		// Now, the actual processors
		assistant.setContentAssistProcessor(
			new JGContentAssistProcessor(jgEditor, ContentType.JAVA), JGPartitionScanner.JG_JAVA);
		assistant.setContentAssistProcessor(
				new JGContentAssistProcessor(jgEditor, ContentType.OPTIONS), JGPartitionScanner.JG_OPTIONS);
		assistant.setContentAssistProcessor(
			new JGContentAssistProcessor(jgEditor, ContentType.DEFAULT), IDocument.DEFAULT_CONTENT_TYPE);
		return assistant;
	}

}