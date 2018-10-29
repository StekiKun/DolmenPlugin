package dolmenplugin.editors.jg;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;

import dolmenplugin.editors.DolmenPartitioner;

/**
 * The document setup participant for Dolmen grammar descriptions.
 * <p>
 * It is referenced in the plugin.xml file and is configured to
 * be run on all .jg files. The participant is used to associate 
 * these documents with the custom partition scanner in 
 * {@link JGPartitionScanner} to perform the document partitioning.
 * 
 * @author Stéphane Lescuyer
 */
public class JGDocumentSetupParticipant implements IDocumentSetupParticipant {

	/**
	 * The unique ID of the partitioning applied to .jg files by this
	 * setup participant, and implemented by {@link JGPartitionScanner}
	 */
	public static final String PARTITIONING_ID =
		"dolmenplugin.editors.jg.partitioning";
	
	@Override
	public void setup(IDocument document_) {
		if (document_ instanceof IDocumentExtension3) {
			IDocumentExtension3 document = (IDocumentExtension3) document_;
			IDocumentPartitioner partitioner =
				new DolmenPartitioner(
					new JGPartitionScanner(),
					JGPartitionScanner.CONTENT_TYPES);
			document.setDocumentPartitioner(PARTITIONING_ID, partitioner);
			partitioner.connect(document_);
		}
	}
	
}