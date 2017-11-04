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

import automaton.Automata;
import automaton.Determinize;
import codegen.AutomataOutput;
import codegen.BaseParser.ParsingException;
import codegen.LexBuffer.LexicalError;
import codegen.LexBuffer.Position;
import dolmenplugin.base.Marker;
import dolmenplugin.base.Utils;
import jl.JLLexerGenerated;
import jl.JLParser;
import syntax.Lexer;

public final class JLCompile {

	private final PrintStream log;
	private final SubMonitor monitor;
	
	public JLCompile(PrintStream log, SubMonitor monitor) {
		this.log = log;
		this.monitor = monitor;
	}

	private static final List<IFile> FAILED = Collections.emptyList();
	
	public List<IFile> compile(IProject project, IFile res) {
		if (res == null || !res.exists())
			return FAILED;
		log.println("Compiling lexer description " + res);
		Marker.delete(res);

		final ClassFactory cf = new ClassFactory(res);
//		File file = res.getLocation().toFile();
//		String dir = file.getParent();
//		String filename = file.getName();
//		String className = filename.substring(0, filename.lastIndexOf('.'));
		JLLexerGenerated jlLexer = null;
		try (FileReader reader = new FileReader(cf.file)) {
			jlLexer = new JLLexerGenerated(
				cf.file.getPath(), reader);
			JLParser jlParser = new JLParser(jlLexer, JLLexerGenerated::main);
			Lexer lexer = jlParser.parseLexer();
			log.println(".. Lexer description successfully parsed");
			
			Automata aut = Determinize.lexer(lexer, true);
			log.println(".. Compiled lexer description to automata");
			log.println(".. (" + aut.automataCells.length + " states in " 
					+ aut.automataEntries.size() + " automata)");
			
			try (FileWriter writer = new FileWriter(cf.classFile, false)) {
				writer.append("package " + cf.classPackage.getElementName() + ";\n\n");
				AutomataOutput.output(writer, cf.className, aut);
				log.println("-> Generated lexer in " + cf.classResource);
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
			return Collections.singletonList((IFile) newRes);
		}
		catch (LexicalError | ParsingException e) {
			// e.printStackTrace();
			Position start = jlLexer.getLexemeStart();
			Position end = jlLexer.getLexemeEnd();
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
