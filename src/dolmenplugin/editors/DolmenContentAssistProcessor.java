package dolmenplugin.editors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalSorter;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import dolmenplugin.base.Utils;
import dolmenplugin.editors.DolmenCompletionProposal.Category;
import dolmenplugin.editors.jg.JGContentAssistProcessor;
import dolmenplugin.editors.jl.JLContentAssistProcessor;

/**
 * Base implementation for content assist processors, shared between
 * {@link JLContentAssistProcessor} and {@link JGContentAssistProcessor}
 * 
 * @author Stéphane Lescuyer
 *
 * @param <T>	the model type for the editor of type {@code U}
 * @param <U>	the type of editor for which this content assistant is launched
 */
public abstract class DolmenContentAssistProcessor<T, U extends DolmenEditor<T>> 
	implements IContentAssistProcessor {
	
	protected final U editor;
	private String lastErrorMessage = null;
	
	/**
	 * Creates a new content-assist processor for the given {@code editor}
	 * @param editor		must not be {@code null}
	 */
	public DolmenContentAssistProcessor(U editor) {
		if (editor == null)
			throw new IllegalArgumentException(
				"Cannot create content-assist processor with null editor");
		this.editor = editor;
	}
	
	/**
	 * @param message
	 * @return {@code null} and sets the given error message
	 */
	protected ICompletionProposal[] fail(String message) {
		lastErrorMessage = message;
		return null;
	}
	
	/**
	 * Same as {@link #fail}{@code (String.format(format, objects))}
	 * 
	 * @param format
	 * @param objects
	 * @return {@code null}
	 */
	@SuppressWarnings("unused")
	private ICompletionProposal[] failf(String format, Object ...objects) {
		return fail(String.format(format, objects));
	}
	
	/**
	 * Sub-classers must override this method to compute potential
	 * completion proposals and add them to the given {@code collector}
	 * 
	 * @param collector
	 * @param prefix 	the word-prefix in the document before the cursor offset
	 */
	protected abstract void collectCompletionProposals(ProposalCollector collector, String prefix);
	
	@Override
	public final ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		if (viewer == null) return null;
		lastErrorMessage = null; // in case of success
		final IDocument doc = viewer.getDocument();
		if (!doc.equals(editor.getDocument())) {
			return fail("Given viewer is not attached to the configured editor");
		}
		
		ProposalCollector collector = new ProposalCollector(doc, offset);
		String prefix = findPrefixAtOffset(doc, offset);
		
		collectCompletionProposals(collector, prefix);
		return collector.collect();
	}

	@Override
	public final IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		// No context information for now
		return null;
	}

	@Override
	public final char[] getCompletionProposalAutoActivationCharacters() {
		// Activate completion on Ctrl+space
		return new char[] {' '};
	}

	@Override
	public final char[] getContextInformationAutoActivationCharacters() {
		// No context information for now
		return null;
	}

	@Override
	public final String getErrorMessage() {
		return lastErrorMessage;
	}

	@Override
	public final IContextInformationValidator getContextInformationValidator() {
		// No context information for now
		return null;
	}
	
	private static String findPrefixAtOffset(IDocument document, int offset) {
		int soffset = offset - 1;
		while (soffset >= 0) {
			char ch;
			try {
				ch = document.getChar(soffset);
			} catch (BadLocationException e) {
				e.printStackTrace();
				return "";
			}
			if (!Utils.isDolmenWordPart(ch)) break;
			--soffset;
		}
		++soffset;
		if (soffset >= offset) return "";
		try {
			String prefix = document.get(soffset, offset - soffset);
			return prefix;
		} catch (BadLocationException e) {
			e.printStackTrace();
			return "";
		}
	}

	protected static final List<String> JAVA_KEYWORDS =
		Arrays.asList(
			"abstract", "continue", "for", "new", "switch",
			"assert", "default", "if", "package", "synchronized",
			"boolean", "do",         "goto",         "private",    "this",
			"break",      "double",     "implements",   "protected",   "throw",
			"byte",       "else",       "import",       "public",     "throws",
			"case",       "enum",      "instanceof",   "return",      "transient",
			"catch",      "extends",    "int",          "short",       "try",
			"char",     "final",      "interface",    "static",      "void",
			"class",      "finally",    "long",         "strictfp",    "volatile",
			"const",      "float",      "native",       "super",       "while",
			"true", "false", "null"
		);
	
	protected final <E> void addPrefixCompletions(ProposalCollector collector, 
		String prefix, Iterable<E> candidates, Function<E, String> toCandidate,
		BiFunction<E, Integer, DolmenCompletionProposal> propfun) {
		for (E element : candidates) {
			String candidate = toCandidate.apply(element);
			if (candidate.startsWith(prefix)) {
				int pl = prefix.length();
				// Check that the candidate is not already there
				if (collector.startsWith(candidate.substring(pl))) continue;

				collector.add(propfun.apply(element, collector.offset - pl));
			}
		}
	}
	
	protected void addSimpleCompletions(ProposalCollector collector, 
			String prefix, Category category, Iterable<String> candidates) {
		addPrefixCompletions(collector, prefix, candidates, 
			s -> s, 
			(kw, i) -> 
				DolmenCompletionProposal.keyword(category, kw, i, collector.offset - i));
	}
	
	protected final void addJavaCompletions(ProposalCollector collector, 
			String prefix, Iterable<String[]> candidates,
			BiFunction<String[], Integer, DolmenCompletionProposal> propfun) {
		for (String[] candidate : candidates) {
			String replacement = candidate[0];
			int k = replacement.indexOf('(');
			String match = k < 0 ? replacement : replacement.substring(0, k);
			if (match.startsWith(prefix)) {
				int pl = prefix.length();
				// Check that the candidate is not already there
				if (collector.startsWith(match.substring(pl)))
					continue;
				collector.add(propfun.apply(candidate, collector.offset - pl));
			}
		}
	}
	
	/**
	 * An instance of this class is passed to subclassers
	 * and used to collect all final completion proposals
	 * 
	 * @author Stéphane Lescuyer
	 */
	protected /* non-static */ class ProposalCollector {
		private List<DolmenCompletionProposal> proposals;
		public final IDocument doc;
		public final int offset;
		
		ProposalCollector(IDocument doc, int offset) {
			this.doc = doc;
			this.offset = offset;
			this.proposals = null;
		}
		
		public boolean startsWith(String s) {
			try {
				String p = doc.get(offset, s.length());
				return (p.equals(s));
			} catch (BadLocationException e) {
				return false;
			}
		}
		
		public void add(DolmenCompletionProposal prop) {
			if (proposals == null) {
				proposals = new ArrayList<>();
			}
			proposals.add(prop);
		}
		
		protected DolmenCompletionProposal[] collect() {
			if (proposals == null) return null;
			return proposals.toArray(new DolmenCompletionProposal[proposals.size()]);
		}
	}
		
	/**
	 * A function to sort Dolmen completion proposals
	 */
	public static final ICompletionProposalSorter SORTER =
		new ICompletionProposalSorter() {
			@Override
			public int compare(ICompletionProposal p1, ICompletionProposal p2) {
				if (p1 instanceof DolmenCompletionProposal &&
					p2 instanceof DolmenCompletionProposal) {
					return ((DolmenCompletionProposal) p1).compareTo((DolmenCompletionProposal) p2);
				}
				return 0;
			}
		};
}
