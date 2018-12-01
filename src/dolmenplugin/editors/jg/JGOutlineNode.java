package dolmenplugin.editors.jg;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.swt.graphics.Image;

import codegen.BaseParser;
import codegen.LexBuffer;
import common.Lists;
import dolmenplugin.Activator;
import dolmenplugin.base.Images;
import dolmenplugin.editors.OutlineNode;
import syntax.Extent;
import syntax.PGrammar;
import syntax.TokenDecl;
import syntax.PGrammarRule;
import syntax.IReport;
import syntax.Located;
import syntax.PProduction;
import syntax.PProduction.Actual;
import syntax.PProduction.ActualExpr;

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
		if (input instanceof PGrammar) {
			PGrammar grammar = (PGrammar) input;
			List<OutlineNode<JGOutlineNode>> roots =
				new ArrayList<>(grammar.tokenDecls.size() + grammar.rules.size() + 2);
			for (TokenDecl decl : grammar.tokenDecls)
				roots.add(of(decl));
			roots.add(of("Header", grammar.header));
			for (PGrammarRule rule : grammar.rules.values())
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
		else if (input instanceof PGrammar.IllFormedException) {
			List<IReport> reports = ((PGrammar.IllFormedException) input).reports;
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
			return Activator.getImage(Images.TOKEN_DECL(decl.isValued()));
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
	 * Outline node representing a {@link PGrammarRule parametric grammar rule}
	 * 
	 * @author Stéphane Lescuyer
	 */
	static final class Rule extends Internal {
		final PGrammarRule rule;
		
		Rule(PGrammarRule rule) {
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
			for (PProduction prod : rule.productions) {
				children[i++] = of(prod);
			}
			return children;
		}

		@Override
		public Image getImage() {
			return Activator.getImage(Images.RULE(rule.visibility));
		}

		@Override
		public StyledString computeText(IDocument document) {
			StyledString text = new StyledString(rule.name.val);
			if (!rule.params.isEmpty()) {
				final Styler pstyler = StyledString.COUNTER_STYLER;
				boolean first = true;
				text.append("<");
				for (Located<String> param : rule.params) {
					if (first) first = false;
					else text.append(", ");
					text.append(param.val, pstyler);
				}
				text.append(">");
			}
			
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
	public static JGOutlineNode of(PGrammarRule rule) {
		return new Rule(rule);
	}
	
	/**
	 * Outline node representing a {@link PProduction production}
	 * in a grammar rule.
	 * <i>It does not have associated input location for now.</i>
	 * 
	 * @author Stéphane Lescuyer
	 */
	static final class Prod extends Leaf {
		final PProduction prod;
		
		Prod(PProduction prod) {
			this.prod = prod;
		}

		@Override
		public Image getImage() {
			return Activator.getImage(Images.PRODUCTION);
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
				append(text, actual.item);
				@Nullable Extent args_ = actual.args;
				if (args_ != null)
					text.append("(")
						.append(resolveExtentIn(document, args_), StyledString.QUALIFIER_STYLER)
						.append(")");
			}
			if (prod.continuation() != null)
				text.append(" ++");
			return text;
		}
		
		private void append(StyledString text, ActualExpr aexpr) {
			if (!aexpr.isTerminal()) {
				text.append(aexpr.symb.val);
				if (!aexpr.params.isEmpty()) {
					boolean first = true;
					text.append("<");
					for (ActualExpr sexpr : aexpr.params) {
						if (first) first = false;
						else text.append(", ");
						append(text, sexpr);
					}
					text.append(">");
				}
			}
			else
				text.append(aexpr.symb.val, StyledString.COUNTER_STYLER);			
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
	public static JGOutlineNode of(PProduction prod) {
		return new Prod(prod);
	}
}
