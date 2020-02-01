package org.stekikun.dolmenplugin.editors.jl;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.ui.editors.text.FileDocumentProvider;

/**
 * The document provider for Dolmen lexer descriptions.
 * <p>
 * Uses the custom partition scanner in {@link JLPartitionScanner}
 * to perform the document partitioning.
 * 
 * <i>Since 20/11/2017: usage of this class is deprecated. To install
 * 	partitioners, the system now relies on {@link IDocumentSetupParticipant}s.
 * </i>
 * 
 * @author Stéphane Lescuyer
 */
@Deprecated
public class JLDocumentProvider extends FileDocumentProvider {

	@Override
	protected IDocument createDocument(Object element) throws CoreException {
		IDocument document = super.createDocument(element);
		if (document != null) {
			IDocumentPartitioner partitioner =
				new FastPartitioner(
					new JLPartitionScanner(),
					JLPartitionScanner.CONTENT_TYPES);
			partitioner.connect(document);
			document.setDocumentPartitioner(partitioner);
		}
		return document;
	}
}