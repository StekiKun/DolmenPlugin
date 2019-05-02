package dolmenplugin.editors.jl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.stekikun.dolmen.codegen.BaseParser;
import org.stekikun.dolmen.codegen.LexBuffer;
import org.stekikun.dolmen.common.Lists;
import org.stekikun.dolmen.syntax.Extent;
import org.stekikun.dolmen.syntax.IReport;
import org.stekikun.dolmen.syntax.Lexer;
import org.stekikun.dolmen.syntax.Located;
import org.stekikun.dolmen.syntax.Regular;

import dolmenplugin.Activator;
import dolmenplugin.base.Images;
import dolmenplugin.editors.OutlineNode;

/**
 * {@link JLOutlineNode} is a specialization of {@link OutlineNode}
 * for the structured contents of the outline view associated with
 * the {@link JLEditor lexer editor}.
 * <p>
 * A lexer description's outline view has the following structure
 * when the description is parsed successfully:
 * <ul>
 * <li> the lexer's header
 * <li> one node for each regular expression {@link Definition definition}
 * <li> one node for each {@link LexerEntry lexer entry} with:
 * 	<ul>
 * 	<li> one node for each of the {@link Clause clauses} in this entry
 * 	</ul>
 * </ul>
 * 
 * @author Stéphane Lescuyer
 */
public abstract class JLOutlineNode extends OutlineNode<JLOutlineNode> {
	
	/**
	 * @param input
	 * @return the roots of the outline view's contents for the
	 * 	given input, which is assumed to be either a {@link Lexer lexer
	 *  description} or an exception
	 */
	public static List<OutlineNode<JLOutlineNode>> roots(Object input) {
		if (input instanceof Lexer) {
			Lexer lexer = (Lexer) input;
			List<OutlineNode<JLOutlineNode>> roots =
				new ArrayList<>(lexer.entryPoints.size() + lexer.regulars.size());
			roots.add(of("Header", lexer.header));
			for (Map.Entry<Located<String>, Regular> def : lexer.regulars.entrySet())
				roots.add(ofDefinition(def));
			for (Lexer.Entry entry : lexer.entryPoints)
				roots.add(of(entry));
			roots.add(of("Footer", lexer.footer));
			return roots;
		}
		else if (input instanceof LexBuffer.LexicalError) {
			return Lists.singleton(of((LexBuffer.LexicalError) input));
		}
		else if (input instanceof BaseParser.ParsingException) {
			return Lists.singleton(of((BaseParser.ParsingException) input));
		}
		else if (input instanceof Lexer.IllFormedException) {
			List<IReport> reports = ((Lexer.IllFormedException) input).reports;
			List<OutlineNode<JLOutlineNode>> roots = new ArrayList<>(reports.size());
			for (IReport report : reports)
				roots.add(of(report));
			return roots;
		}
		return null;
	}
	
	private static final JLOutlineNode[] NO_CHILDREN = new JLOutlineNode[0];
	
	private static abstract class Leaf extends JLOutlineNode {
		@Override
		public final boolean hasChildren() {
			return false;
		}
		
		@Override
		public final JLOutlineNode[] getChildren() {
			return NO_CHILDREN;
		}
	}

	/**
	 * Abstract class for internal nodes, with a caching
	 * mechanism for children computation and styled label
	 * 
	 * @author Stéphane Lescuyer
	 */
	private static abstract class Internal extends JLOutlineNode {
		private JLOutlineNode[] children = null;
		private StyledString label = null;
		
		protected abstract StyledString computeText(IDocument document);
		protected abstract JLOutlineNode[] computeChildren();
		
		@Override
		public final StyledString getText(IDocument document) {
			if (label == null) {
				label = computeText(document);
			}
			return label;
		}
		
		@Override
		public final JLOutlineNode[] getChildren() {
			if (children == null) {
				children = computeChildren();
			}
			return children;
		}
	}
	
	/**
	 * Outline node representing an auxiliary regular expression definition
	 * 
	 * @see Lexer#regulars
	 * @author Stéphane Lescuyer
	 */
	static final class Definition extends Leaf {
		final Located<String> ident;
		final Regular reg;
		
		Definition(Map.Entry<Located<String>, Regular> entry) {
			this.ident = entry.getKey();
			this.reg = entry.getValue();
		}

		@Override
		public Image getImage() {
			return Activator.getImage(Images.REGEXP_DEF);
		}

		@Override
		public StyledString getText(IDocument document) {
			return new StyledString(ident.val);
		}

		@Override
		public int getOffset() {
			return ident.start.offset;
		}

		@Override
		public int getLength() {
			return ident.length();
		}
	}
	/**
	 * @param entry
	 * @return the outline node representing the regular
	 * 	expression definition given by {@code entry}
	 */
	public static JLOutlineNode ofDefinition(Map.Entry<Located<String>, Regular> entry) {
		return new Definition(entry);
	}	
	
	/**
	 * Outline node representing a {@link Lexer.Entry lexer entry}
	 * 
	 * @author Stéphane Lescuyer
	 */
	static final class LexerEntry extends Internal {
		final Lexer.Entry entry;
		
		LexerEntry(Lexer.Entry entry) {
			this.entry = entry;
		}

		@Override
		public boolean hasChildren() {
			return !entry.clauses.isEmpty();
		}

		@Override
		public JLOutlineNode[] computeChildren() {
			JLOutlineNode[] children = new JLOutlineNode[entry.clauses.size()];
			int i = 0;
			for (Lexer.Clause clause : entry.clauses) {	
				children[i++] = ofClause(clause);
			}
			return children;
		}

		@Override
		public Image getImage() {
			return Activator.getImage(Images.LEXER_ENTRY(entry.visibility));
		}

		@Override
		protected StyledString computeText(IDocument document) {
			StyledString text = new StyledString(entry.name.val);
			if (entry.args != null) {
				text.append("(")
					.append(resolveExtentIn(document, entry.args), StyledString.QUALIFIER_STYLER)
					.append(")");
			}
			text.append(" : ", StyledString.DECORATIONS_STYLER)
				.append(resolveExtentIn(document, entry.returnType), StyledString.DECORATIONS_STYLER);
			return text;
		}

		@Override
		public int getOffset() {
			return entry.name.start.offset;
		}

		@Override
		public int getLength() {
			return entry.name.length();
		}
	}
	/**
	 * @param entry
	 * @return the outline node representing the given lexer entry
	 */
	public static JLOutlineNode of(Lexer.Entry entry) {
		return new LexerEntry(entry);
	}

	/**
	 * Outline node representing an entry's clause.
	 * 
	 * @author Stéphane Lescuyer
	 */
	static final class Clause extends Leaf {
		final Located<Regular> reg;
		final Extent extent;
		
		Clause(Lexer.Clause clause) {
			this.reg = clause.regular;
			this.extent = clause.action;
		}

		@Override
		public Image getImage() {
			return Activator.getImage(Images.PRODUCTION);
		}

		@Override
		public StyledString getText(IDocument doc) {
			if (doc == null) 
				return new StyledString("[Document is unavailable]", StyledString.QUALIFIER_STYLER);
			try {
				return new StyledString(doc.get(reg.start.offset, reg.length()));
			} catch (BadLocationException e1) {
				return new StyledString(
					"[Position is invalid in document: start=" + reg.start.offset + 
					", length=" + reg.length() + "]", StyledString.QUALIFIER_STYLER);
			}
		}

		@Override
		public int getOffset() {
			return reg.start.offset;
		}

		@Override
		public int getLength() {
			return reg.length();
		}
	}
	/**
	 * @param clause
	 * @return the outline node associated to the given clause
	 */
	public static JLOutlineNode ofClause(Lexer.Clause clause) {
		return new Clause(clause);
	}
}
