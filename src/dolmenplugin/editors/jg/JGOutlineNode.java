package dolmenplugin.editors.jg;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;

import codegen.BaseParser;
import codegen.LexBuffer;
import common.Lists;
import dolmenplugin.Activator;
import dolmenplugin.editors.OutlineNode;
import syntax.Extent;
import syntax.Grammar;
import syntax.Grammar.TokenDecl;
import syntax.GrammarRule;
import syntax.IReport;
import syntax.Located;
import syntax.Production;
import syntax.Production.Actual;

/**
 * {@link JGOutlineNode} is a specialization of {@link OutlineNode}
 * for the structured contents of the outline view associated with
 * the {@link JGEditor grammar editor}.
 * <p>
 * A grammar description's outline view has the following structure
 * when the description is parsed successfully:
 * <ul>
 * <li> one node for each {@link Token token declaration}
 * <li> the grammar's header
 * <li> one node for each {@link Rule grammar rule} with:
 * 	<ul>
 * 	<li> one node for each of the {@link Prod productions} in this rule
 * 	</ul>
 * </ul>
 * 
 * @author Stéphane Lescuyer
 */
public abstract class JGOutlineNode extends OutlineNode<JGOutlineNode> {
	
	/**
	 * @param input
	 * @return the roots of the outline view's contents for the
	 * 	given input, which is assumed to be either a {@link Grammar grammar
	 *  description} or an exception
	 */
	public static List<OutlineNode<JGOutlineNode>> roots(Object input) {
		if (input instanceof Grammar) {
			Grammar grammar = (Grammar) input;
			List<OutlineNode<JGOutlineNode>> roots =
				new ArrayList<>(grammar.tokenDecls.size() + grammar.rules.size() + 2);
			for (TokenDecl decl : grammar.tokenDecls)
				roots.add(of(decl));
			roots.add(of("Header", grammar.header));
			for (GrammarRule rule : grammar.rules.values())
				roots.add(of(rule));
			roots.add(of("Footer", grammar.footer));
			return roots;
		}
		else if (input instanceof LexBuffer.LexicalError) {
			return Lists.singleton(of((LexBuffer.LexicalError) input));
		}
		else if (input instanceof BaseParser.ParsingException) {
			return Lists.singleton(of((BaseParser.ParsingException) input));
		}
		else if (input instanceof Grammar.IllFormedException) {
			List<IReport> reports = ((Grammar.IllFormedException) input).reports;
			List<OutlineNode<JGOutlineNode>> roots = new ArrayList<>(reports.size());
			for (IReport report : reports)
				roots.add(of(report));
			return roots;
		}
		return null;
	}
	
	private static final JGOutlineNode[] NO_CHILDREN = new JGOutlineNode[0];
	
	private static abstract class Leaf extends JGOutlineNode {
		@Override
		public final boolean hasChildren() {
			return false;
		}
		
		@Override
		public final JGOutlineNode[] getChildren() {
			return NO_CHILDREN;
		}
	}
	
	/**
	 * Abstract class for internal nodes, with a caching
	 * mechanism for children computation and styled label
	 * 
	 * @author Stéphane Lescuyer
	 */
	private static abstract class Internal extends JGOutlineNode {
		private JGOutlineNode[] children = null;
		private StyledString label = null;
		
		protected abstract StyledString computeText(IDocument document);
		protected abstract JGOutlineNode[] computeChildren();
		
		@Override
		public final StyledString getText(IDocument document) {
			if (label == null) {
				label = computeText(document);
			}
			return label;
		}
		
		@Override
		public final JGOutlineNode[] getChildren() {
			if (children == null) {
				children = computeChildren();
			}
			return children;
		}
	}

	/**
	 * Outline node representing a {@link TokenDecl token declaration}
	 * 
	 * @author Stéphane Lescuyer
	 */
	static final class Token extends Leaf {
		final TokenDecl decl;
		
		Token(TokenDecl decl) {
			this.decl = decl;
		}

		@Override
		public Image getImage() {
			String valued = decl.isValued() ? "_valued" : "";
			return Activator.getImage("icons/token_decl" + valued + ".gif");
		}

		@Override
		public StyledString getText(IDocument document) {
			StyledString text = new StyledString(decl.name.val);
			if (decl.valueType != null) {
				text.append(" : ", StyledString.DECORATIONS_STYLER)
					.append(resolveExtentIn(document, decl.valueType), StyledString.DECORATIONS_STYLER);
			}
			return text;
		}

		@Override
		public int getOffset() {
			return decl.name.start.offset;
		}

		@Override
		public int getLength() {
			return decl.name.length();
		}
	}
	/**
	 * @param decl
	 * @return the outline node associated to the given token declaration
	 */
	public static JGOutlineNode of(TokenDecl decl) {
		return new Token(decl);
	}	

	/**
	 * Outline node representing a {@link GrammarRule grammar rule}
	 * 
	 * @author Stéphane Lescuyer
	 */
	static final class Rule extends Internal {
		final GrammarRule rule;
		
		Rule(GrammarRule rule) {
			this.rule = rule;
		}

		@Override
		public boolean hasChildren() {
			return !rule.productions.isEmpty();
		}
		
		@Override
		protected JGOutlineNode[] computeChildren() {
			JGOutlineNode[] children = new JGOutlineNode[rule.productions.size()];
			int i = 0;
			for (Production prod : rule.productions) {
				children[i++] = of(prod);
			}
			return children;
		}

		@Override
		public Image getImage() {
			String vis = rule.visibility ? "pub" : "pri";
			return Activator.getImage("icons/rule_" + vis + ".gif");
		}

		@Override
		public StyledString computeText(IDocument document) {
			StyledString text = new StyledString(rule.name.val);
			if (rule.args != null) {
				text.append("(")
					.append(resolveExtentIn(document, rule.args), StyledString.QUALIFIER_STYLER)
					.append(")");
			}
			text.append(" : ", StyledString.DECORATIONS_STYLER)
				.append(resolveExtentIn(document, rule.returnType), StyledString.DECORATIONS_STYLER);
			return text;
		}
		
		@Override
		public int getOffset() {
			return rule.name.start.offset;
		}

		@Override
		public int getLength() {
			return rule.name.length();
		}
	}
	/**
	 * @param rule
	 * @return the outline node representing the given rule
	 */
	public static JGOutlineNode of(GrammarRule rule) {
		return new Rule(rule);
	}
	
	/**
	 * Outline node representing a {@link Production production}
	 * in a grammar rule.
	 * <i>It does not have associated input location for now.</i>
	 * 
	 * @author Stéphane Lescuyer
	 */
	static final class Prod extends Leaf {
		final Production prod;
		
		Prod(Production prod) {
			this.prod = prod;
		}

		@Override
		public Image getImage() {
			return Activator.getImage("icons/rightarrow.gif");
		}

		@Override
		public StyledString getText(IDocument document) {
			StyledString text = new StyledString();
			boolean first = true;
			for (Actual actual : prod.actuals()) {
				if (first) first = false;
				else text.append(" ");
				
				Located<String> bind = actual.binding;
				if (bind != null)
					text.append(bind.val, StyledString.QUALIFIER_STYLER)
						.append(" = ", StyledString.QUALIFIER_STYLER);
				if (!actual.isTerminal())
					text.append(actual.item.val);
				else
					text.append(actual.item.val, StyledString.COUNTER_STYLER);
				@Nullable Extent args_ = actual.args;
				if (args_ != null)
					text.append("(")
						.append(resolveExtentIn(document, args_), StyledString.QUALIFIER_STYLER)
						.append(")");
			}
			return text;
		}

		@Override
		public int getOffset() {
			return -1;	// Not located
		}

		@Override
		public int getLength() {
			return -1;	// Not located
		}
	}
	/**
	 * @param prod
	 * @return the outline node representing the given production
	 */
	public static JGOutlineNode of(Production prod) {
		return new Prod(prod);
	}
}
