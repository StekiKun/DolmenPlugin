package dolmenplugin.editors.jl;

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
 * @author Stéphane Lescuyer
 */
public class JLDocumentProvider extends FileDocumentProvider {

	protected IDocument createDocument(Object element) throws CoreException {
		IDocument document = super.createDocument(element);
		if (document != null) {
			IDocumentPartitioner partitioner =
				new FastPartitioner(
					new JLPartitionScanner(),
					new String[] {
						JLPartitionScanner.JL_COMMENT,
						JLPartitionScanner.JL_LITERAL,
						JLPartitionScanner.JL_JAVA});
			partitioner.connect(document);
			document.setDocumentPartitioner(partitioner);
		}
		return document;
	}
}