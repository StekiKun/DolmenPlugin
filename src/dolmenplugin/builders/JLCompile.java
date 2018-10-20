package dolmenplugin.builders;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.SubMonitor;

import automaton.Automata;
import automaton.Determinize;
import codegen.AutomataOutput;
import codegen.Config;
import codegen.BaseParser.ParsingException;
import codegen.LexBuffer.LexicalError;
import codegen.LexBuffer.Position;
import codegen.SourceMapping;
import common.Bookkeeper;
import common.CountingWriter;
import dolmenplugin.base.Marker;
import dolmenplugin.base.Utils;
import jle.JLELexer;
import jle.JLEParser;
import syntax.IReport;
import syntax.Lexer;
import syntax.Reporter;

public final class JLCompile {

	private final PrintStream log;
	private final SubMonitor monitor;
	
	public JLCompile(PrintStream log, SubMonitor monitor) {
		this.log = log;
		this.monitor = monitor;
	}

	private static final Map<IFile, SourceMapping> FAILED = 
		Collections.emptyMap();
	
	public Map<IFile, SourceMapping> compile(IProject project, IFile res) {
		if (res == null || !res.exists())
			return FAILED;
		final Bookkeeper tasks = Bookkeeper.start(log, "Compiling lexer description " + res);
		Marker.deleteAll(res);

		final ClassFactory cf = new ClassFactory(res);
		if (!cf.isStale()) {
			tasks.leaveWith("Up-to-date lexer " + cf.classResource);
			return Collections.emptyMap();
		}
		
		JLELexer jlLexer = null;
		try (FileReader reader = new FileReader(cf.file)) {
			jlLexer = new JLELexer(cf.file.getPath(), reader);
			JLEParser jlParser = new JLEParser(jlLexer, JLELexer::main);
			Lexer lexer = jlParser.lexer();
			tasks.done("Lexer description successfully parsed");
			
			Reporter configReporter = new Reporter();
			Config config = Config.ofLexer(lexer, configReporter);
			List<IReport> configReports = configReporter.getReports();
			if (!configReports.isEmpty()) {
				Marker.addAll(res, configReports);
				tasks.infos("(" + configReports.size() + " potential problem" +
						(configReports.size() > 1 ? "s" : "") + " found)");
			}
			
			Automata aut = Determinize.lexer(lexer, true);
			tasks.done("Compiled lexer description to automata");
			tasks.infos("(" + aut.automataCells.length + " states in " 
					+ aut.automataEntries.size() + " automata)");
			
			List<IReport> autReports = aut.findProblems(lexer);
			if (!autReports.isEmpty()) {
				Marker.addAll(res, autReports);
				tasks.infos("(" + autReports.size() + " potential problem" +
						(autReports.size() > 1 ? "s" : "") + " found)");
			}
			
			SourceMapping smap;
			try (Writer writer = 
					new CountingWriter(new FileWriter(cf.classFile, false))) {
				writer.append("package " + cf.classPackage.getElementName() + ";\n\n");
				smap = AutomataOutput.output(writer, cf.className, config, aut);
				tasks.leaveWith("Generated lexer in " + cf.classResource);
			} catch (IOException e) {
				e.printStackTrace(log);
				tasks.aborted("Could not output generated lexer");
				return FAILED;
			}
			
			project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			
			final IFile newRes = cf.classResource;
			if (!newRes.isDerived())
				newRes.setDerived(true, monitor);
			String now = Instant.now().toString();
			String prop = "Generated from " + cf.file.getAbsolutePath() + " (" + now + ")";
			newRes.setPersistentProperty(Utils.GENERATED_PROPERTY, prop);
			Marker.addMappings(newRes, smap);
			
			return Collections.singletonMap((IFile) newRes, smap);
		}
		catch (LexicalError e) {
			Position start = jlLexer.getLexemeStart();
			Position end = jlLexer.getLexemeEnd();
			Marker.addError(res, e.getMessage(), start.line, start.offset, end.offset);
			tasks.aborted("Lexical error in lexer description");
		}
		catch (ParsingException e) {
			final Position start;
			final int end;
			if (e.pos == null) {
				start = jlLexer.getLexemeStart();
				end = jlLexer.getLexemeEnd().offset;
			} else {
				start = e.pos;
				end = e.pos.offset + e.length;
			}
			Marker.addError(res, e.getMessage(), start.line, start.offset, end);
			tasks.aborted("Syntax error in lexer description");
		}
		catch (Lexer.IllFormedException e) {
			Marker.addAll(res, e.reports);
			tasks.aborted("Lexer description is not well-formed");
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
