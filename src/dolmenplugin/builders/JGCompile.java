package dolmenplugin.builders;

import java.io.File;
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
		File file = res.getLocation().toFile();
		String dir = file.getParent();
		String filename = file.getName();
		String className = filename.substring(0, filename.lastIndexOf('.'));
		JGLexer jgLexer = null;
		try (FileReader reader = new FileReader(file)) {
			jgLexer = new JGLexer(file.getPath(), reader);
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
			
			File gen = new File(dir, className + ".java");
			try (FileWriter writer = new FileWriter(gen, false)) {
				GrammarOutput.outputDefault(writer, className, grammar, predictTable);
				log.println("-> Generated parser in " + gen.getAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace(log);
				return FAILED;
			}
			
			res.deleteMarkers(Marker.ID, true, IResource.DEPTH_ZERO);
			project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			
			// TODO: is there a cleaner way to do that below?
			String projectDir = project.getLocation().toOSString();
			String fullGen = gen.getPath();
			if (!fullGen.startsWith(projectDir))
				throw new IllegalStateException(
					"Generated file " + fullGen + " not in project " + projectDir);
			String relGen = fullGen.substring(projectDir.length() + 1);
			IResource newRes = project.findMember(relGen);
			
			if (!newRes.isDerived())
				newRes.setDerived(true, monitor);
			Date now = new Date();
			String prop = "Generated from " + file.getAbsolutePath() + " (" + now + ")";
			newRes.setPersistentProperty(Utils.GENERATED_PROPERTY, prop);
			return Collections.singletonList((IFile) newRes);
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
