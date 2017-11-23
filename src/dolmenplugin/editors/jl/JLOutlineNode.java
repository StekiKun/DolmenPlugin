package dolmenplugin.editors.jl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Image;

import codegen.BaseParser;
import codegen.LexBuffer;
import common.Lists;
import dolmenplugin.Activator;
import dolmenplugin.editors.OutlineNode;
import syntax.Extent;
import syntax.IReport;
import syntax.Lexer;
import syntax.Located;
import syntax.Regular;

public abstract class JLOutlineNode extends OutlineNode<JLOutlineNode> {
	
	public static List<OutlineNode<JLOutlineNode>> roots(Object input) {
		if (input instanceof Lexer) {
			Lexer lexer = (Lexer) input;
			List<OutlineNode<JLOutlineNode>> roots =
				new ArrayList<>(lexer.entryPoints.size() + lexer.regulars.size());
			for (Map.Entry<Located<String>, Regular> def : lexer.regulars.entrySet())
				roots.add(ofDefinition(def));
			for (Lexer.Entry entry : lexer.entryPoints)
				roots.add(of(entry));
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
	
	private static abstract class Internal extends JLOutlineNode {
		private JLOutlineNode[] children = null;
		
		protected abstract JLOutlineNode[] computeChildren();
		
		@Override
		public final JLOutlineNode[] getChildren() {
			if (children == null) {
				children = computeChildren();
			}
			return children;
		}
	}
	
	private static final class Definition extends Leaf {
		final Located<String> ident;
		@SuppressWarnings("unused")
		final Regular reg;
		
		Definition(Map.Entry<Located<String>, Regular> entry) {
			this.ident = entry.getKey();
			this.reg = entry.getValue();
		}

		@Override
		public Image getImage() {
			return Activator.getImage("icons/regexp_def.gif");
		}

		@Override
		public String getText(IDocument document) {
			return ident.val;
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
	public static JLOutlineNode ofDefinition(Map.Entry<Located<String>, Regular> entry) {
		return new Definition(entry);
	}	
	
	private static final class LexerEntry extends Internal {
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
			for (Map.Entry<Located<Regular>, Extent> e : entry.clauses.entrySet()) {	
				children[i++] = ofClause(e);
			}
			return children;
		}

		@Override
		public Image getImage() {
			String vis = entry.visibility ? "pub" : "pri";
			return Activator.getImage("icons/lexer_entry_" + vis + ".gif");
		}

		@Override
		public String getText(IDocument document) {
			String text = entry.name.val;
			if (entry.args != null) {
				text += "(" + resolveExtentIn(document, entry.args) + ")";
			}
			text += " : ";
			text += resolveExtentIn(document, entry.returnType);
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
	public static JLOutlineNode of(Lexer.Entry entry) {
		return new LexerEntry(entry);
	}

	private static final class Clause extends Leaf {
		final Located<Regular> reg;
		@SuppressWarnings("unused")
		final Extent extent;
		
		Clause(Map.Entry<Located<Regular>, Extent> entry) {
			this.reg = entry.getKey();
			this.extent = entry.getValue();
		}

		@Override
		public Image getImage() {
			return Activator.getImage("icons/rightarrow.gif");
		}

		@Override
		public String getText(IDocument doc) {
			if (doc == null) return "[Document is unavailable]";
			try {
				return doc.get(reg.start.offset, reg.length());
			} catch (BadLocationException e1) {
				return "[Position is invalid in document: start=" + reg.start.offset + 
						", length=" + reg.length() + "]";
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
	public static JLOutlineNode ofClause(Map.Entry<Located<Regular>, Extent> entry) {
		return new Clause(entry);
	}
}
