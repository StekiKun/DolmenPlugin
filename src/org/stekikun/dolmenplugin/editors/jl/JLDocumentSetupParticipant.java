package org.stekikun.dolmenplugin.editors.jl;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.rules.IPartitionTokenScanner;
import org.stekikun.dolmenplugin.editors.DolmenPartitioner;

/**
 * The document setup participant for Dolmen lexer descriptions.
 * <p>
 * It is referenced in the plugin.xml file and is configured to
 * be run on all .jl files. The participant is used to associate 
 * these documents with the custom partition scanner in 
 * {@link JLPartitionScanner} to perform the document partitioning.
 * 
 * @author Stéphane Lescuyer
 */
public class JLDocumentSetupParticipant implements IDocumentSetupParticipant {

	/**
	 * The unique ID of the partitioning applied to .jl files by this
	 * setup participant, and implemented by {@link JLPartitionScanner}
	 */
	public static final String PARTITIONING_ID =
		"org.stekikun.dolmenplugin.editors.jl.partitioning";
	
	@Override
	public void setup(IDocument document_) {
		if (document_ instanceof IDocumentExtension3) {
			IDocumentExtension3 document = (IDocumentExtension3) document_;
			IDocumentPartitioner partitioner =
				new DolmenPartitioner(
					new JLPartitionScanner(),
					JLPartitionScanner.CONTENT_TYPES);
			document.setDocumentPartitioner(PARTITIONING_ID, partitioner);
			partitioner.connect(document_);
		}
	}

	/**
	 * Wrapper around {@link FastPartitioner} which logs the various
	 * partitions computed as the document changes
	 * 
	 * @author Stéphane Lescuyer
	 */
	protected static class DebugFastPartitioner extends FastPartitioner {

		public DebugFastPartitioner(IPartitionTokenScanner scanner, String[] legalContentTypes) {
			super(scanner, legalContentTypes);
		}
		
		@Override
		public void connect(IDocument document, boolean delayInitialise)
		{
			super.connect(document, delayInitialise);
			printPartitions(document);
		}
		
		@Override
		public final ITypedRegion[] computePartitioning(int offset, int length, boolean b) {
			ITypedRegion[] partitions = super.computePartitioning(offset, length, b);
			
			StringBuffer buffer = new StringBuffer();
			for (int i = 0; i < partitions.length; i++)
			{
				buffer.append("Partition type: " 
						+ partitions[i].getType() 
						+ ", offset: " + partitions[i].getOffset()
						+ ", length: " + partitions[i].getLength());
				buffer.append("\n");
				buffer.append("\n---------------------------\n\n\n");
			}
			System.out.print(buffer);
			return partitions;
		}

		@Override
		public IRegion documentChanged2(DocumentEvent e) {
			IRegion res = super.documentChanged2(e);
			if (res != null)
				printPartitions(e.getDocument());
			return res;
		}

		public void printPartitions(IDocument document)
		{
			StringBuffer buffer = new StringBuffer();

			ITypedRegion[] partitions = computePartitioning(0, document.getLength());
			for (int i = 0; i < partitions.length; i++)
			{
				try
				{
					buffer.append("Partition type: " 
							+ partitions[i].getType() 
							+ ", offset: " + partitions[i].getOffset()
							+ ", length: " + partitions[i].getLength());
					buffer.append("\n");
					buffer.append("Text:\n");
					buffer.append(document.get(partitions[i].getOffset(), 
							partitions[i].getLength()));
					buffer.append("\n---------------------------\n\n\n");
				}
				catch (BadLocationException e)
				{
					e.printStackTrace();
				}
			}
			System.out.print(buffer);
		}
	}
	
}
