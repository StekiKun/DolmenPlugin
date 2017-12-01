package dolmenplugin.editors;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;

/**
 * The common partition scanner for Dolmen lexer and parser
 * descriptions.
 * <p>
 * This {@link RuleBasedPartitionScanner rule-based partition scanner}
 * is tweaked in order to avoid resuming scanning in the middle
 * of a partition. This is avoided because semantic actions in Dolmen
 * files are partitions on their own and they allow a nested pattern
 * of opening/closing braces, which cannot be properly scanned unless
 * starting at the beginning.
 * 
 * @author Stéphane Lescuyer
 */
public abstract class DolmenPartitionScanner extends RuleBasedPartitionScanner {
	@SuppressWarnings("unused")
	private final String nestedContentType;
	
	public DolmenPartitionScanner(String nestedContentType) {
		this.nestedContentType = nestedContentType;
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * {@link DolmenPartitionScanner} overrides this method to prevent the partitioner
	 * from resuming into partitions that represent semantic actions. Indeed it is not
	 * possible to scan semantic actions correctly from inside such a partition 
	 * because of the nested '{' '}' delimiters; in this case scanning must resume
	 * from the start of the partition.
	 * <p>
	 * Other partitions could be dealt with normally but for now we prevent resuming
	 * in any partition content type.
	 */
	@Override
	public final void setPartialRange(IDocument document, int offset, int length, 
			String contentType, int partitionOffset) {
//		System.out.println(String.format(" [offset=%d, length=%d, ctype=%s, partOffset=%d]",
//				offset, length, Objects.toString(contentType), partitionOffset));

// TODO: Try and only allow resume when the content types is that of semantic actions
//		if (!nestedContentType.equals(contentType)) {
//			super.setPartialRange(document, offset, length, contentType, partitionOffset);
//			return;
//		}
		if (partitionOffset < 0) {
			super.setPartialRange(document, offset, length, contentType, partitionOffset);
			return;
		}
		
		// A java partition: prevent resuming
		if (offset < partitionOffset)
			System.out.println("Bizarre partitioning offsets: " + offset + partitionOffset);
//		else if (offset > partitionOffset)
//			System.out.println("Preventing resume in " + this.getClass().getSimpleName());

		super.setPartialRange(document, partitionOffset, 
				length + (offset - partitionOffset), contentType, partitionOffset);
	}

}