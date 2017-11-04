package dolmenplugin.builders;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.SubMonitor;

import codegen.BaseParser.ParsingException;
import codegen.GrammarOutput;
import codegen.LexBuffer.LexicalError;
import codegen.LexBuffer.Position;
import dolmenplugin.base.Marker;
import dolmenplugin.base.Utils;
import jg.JGLexer;
import jg.JGParserGenerated;
import syntax.Grammar;
import syntax.Grammars;

public final class JGCompile {

	private final PrintStream log;
	private final SubMonitor monitor;
	
	public JGCompile(PrintStream log, SubMonitor monitor) {
		this.log = log;
		this.monitor = monitor;
	}

	private static final List<IFile> FAILED = Collections.emptyList();
	
	public List<IFile> compile(IProject project, IFile res) {
		if (res == null || !res.exists())
			return FAILED;
		log.println("Compiling grammar description " + res);
		Marker.delete(res);
		
		final ClassFactory cf = new ClassFactory(res);
		JGLexer jgLexer = null;
		try (FileReader reader = new FileReader(cf.file)) {
			jgLexer = new JGLexer(cf.file.getPath(), reader);
			JGParserGenerated jgParser = new JGParserGenerated(jgLexer, JGLexer::main);
			Grammar grammar = jgParser.start();
			log.println(".. Grammar description successfully parsed");
			
			Grammars.PredictionTable predictTable =
				Grammars.predictionTable(grammar, Grammars.analyseGrammar(grammar, null));
			log.println(".. Analysed grammar and built prediction table");
			if (!predictTable.isLL1()) {
				// TODO: return error report instead, and add markers
				System.out.println(predictTable.toString());
				return FAILED;
			}
			log.println(".. Grammar is LL(1)!");
			
			try (FileWriter writer = new FileWriter(cf.classFile, false)) {
				writer.append("package " + cf.classPackage.getElementName() + ";\n\n");
				GrammarOutput.outputDefault(writer, cf.className, grammar, predictTable);
				log.println("-> Generated parser in " + cf.classResource);
			} catch (IOException e) {
				e.printStackTrace(log);
				return FAILED;
			}
			
			project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			
			final IFile newRes = cf.classResource;
			if (!newRes.isDerived())
				newRes.setDerived(true, monitor);
			Date now = new Date();
			String prop = "Generated from " + cf.file.getAbsolutePath() + " (" + now + ")";
			newRes.setPersistentProperty(Utils.GENERATED_PROPERTY, prop);
			return Collections.singletonList(newRes);
		}
		catch (LexicalError | ParsingException e) {
			// e.printStackTrace();
			Position start = jgLexer.getLexemeStart();
			Position end = jgLexer.getLexemeEnd();
			try {
				IMarker report = res.createMarker(Marker.ID);
				report.setAttribute(IMarker.MESSAGE, e.getMessage());
				report.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
				report.setAttribute(IMarker.LINE_NUMBER, start.line);
				report.setAttribute(IMarker.CHAR_START, start.offset);
				report.setAttribute(IMarker.CHAR_END, end.offset);
			} catch (CoreException e1) {
				e1.printStackTrace();
			}
		}
		catch (FileNotFoundException e) {
			e.printStackTrace(log);
		}
		catch (IOException e) {
			e.printStackTrace(log);
		} 
		catch (CoreException e) {
			e.printStackTrace(log);
		}
		return FAILED;
	}
	
}
