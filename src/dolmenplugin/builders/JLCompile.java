package dolmenplugin.builders;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Collections;
import java.util.Date;
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
import codegen.BaseParser.ParsingException;
import codegen.LexBuffer.LexicalError;
import codegen.LexBuffer.Position;
import codegen.SourceMapping;
import common.CountingWriter;
import dolmenplugin.base.Marker;
import dolmenplugin.base.Utils;
import jl.JLLexerGenerated;
import jl.JLParser;
import syntax.IReport;
import syntax.Lexer;

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
		log.println("Compiling lexer description " + res);
		Marker.deleteAll(res);

		final ClassFactory cf = new ClassFactory(res);
		JLLexerGenerated jlLexer = null;
		try (FileReader reader = new FileReader(cf.file)) {
			jlLexer = new JLLexerGenerated(
				cf.file.getPath(), reader);
			JLParser jlParser = new JLParser(jlLexer, JLLexerGenerated::main);
			Lexer lexer = jlParser.parseLexer();
			log.println("├─ Lexer description successfully parsed");
			
			Automata aut = Determinize.lexer(lexer, true);
			log.println("├─ Compiled lexer description to automata");
			log.println("│  (" + aut.automataCells.length + " states in " 
					+ aut.automataEntries.size() + " automata)");
			
			List<IReport> autReports = aut.findProblems(lexer);
			if (!autReports.isEmpty()) {
				Marker.addAll(res, autReports);
				log.println("│  (" + autReports.size() + " potential problem" +
						(autReports.size() > 1 ? "s" : "") + " found)");
			}
			
			SourceMapping smap;
			try (Writer writer = 
					new CountingWriter(new FileWriter(cf.classFile, false))) {
				writer.append("package " + cf.classPackage.getElementName() + ";\n\n");
				smap = AutomataOutput.output(writer, cf.className, aut);
				log.println("└─ Generated lexer in " + cf.classResource);
			} catch (IOException e) {
				e.printStackTrace(log);
				log.println("╧  Could not output generated lexer");
				return FAILED;
			}
			
			project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			
			final IFile newRes = cf.classResource;
			if (!newRes.isDerived())
				newRes.setDerived(true, monitor);
			Date now = new Date();
			String prop = "Generated from " + cf.file.getAbsolutePath() + " (" + now + ")";
			newRes.setPersistentProperty(Utils.GENERATED_PROPERTY, prop);
			Marker.addMappings(newRes, smap);
			
			return Collections.singletonMap((IFile) newRes, smap);
		}
		catch (LexicalError e) {
			Position start = jlLexer.getLexemeStart();
			Position end = jlLexer.getLexemeEnd();
			Marker.addError(res, e.getMessage(), start.line, start.offset, end.offset);
			log.println("╧  Lexical error in lexer description");
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
			log.println("╧  Syntax error in lexer description");
		}
		catch (Lexer.IllFormedException e) {
			Marker.addAll(res, e.reports);
			log.println("╧  Lexer description is not well-formed");
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
