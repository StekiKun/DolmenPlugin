package dolmenplugin.editors.jg;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.ui.editors.text.FileDocumentProvider;

/**
 * The document provider for Dolmen grammar descriptions.
 * <p>
 * Uses the custom partition scanner in {@link JGPartitionScanner}
 * to perform the document partitioning.
 * 
 * @author Stéphane Lescuyer
 */
public class JGDocumentProvider extends FileDocumentProvider {

	protected IDocument createDocument(Object element) throws CoreException {
		IDocument document = super.createDocument(element);
		if (document != null) {
			IDocumentPartitioner partitioner =
				new FastPartitioner(
					new JGPartitionScanner(),
					new String[] {
						JGPartitionScanner.JG_COMMENT,
						JGPartitionScanner.JG_JAVA,
						JGPartitionScanner.JG_ARGS});
			partitioner.connect(document);
			document.setDocumentPartitioner(partitioner);
		}
		return document;
	}
}