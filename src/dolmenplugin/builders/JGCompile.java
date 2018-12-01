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

import codegen.BaseParser.ParsingException;
import codegen.Config;
import codegen.GrammarOutput;
import codegen.LexBuffer.LexicalError;
import codegen.LexBuffer.Position;
import codegen.SourceMapping;
import common.Bookkeeper;
import common.CountingWriter;
import common.Lists;
import dolmenplugin.base.Marker;
import dolmenplugin.base.Utils;
import jge.JGELexer;
import jge.JGEParser;
import syntax.IReport;
import syntax.PGrammar;
import syntax.PGrammars;
import syntax.Reporter;
import unparam.Expansion;
import unparam.Expansion.PGrammarNotExpandable;
import unparam.Grammar;
import unparam.Grammars;

public final class JGCompile {

	private final PrintStream log;
	private final SubMonitor monitor;
	
	public JGCompile(PrintStream log, SubMonitor monitor) {
		this.log = log;
		this.monitor = monitor;
	}

	private static final Map<IFile, SourceMapping> FAILED = Collections.emptyMap();

	private static void logAndMark(Bookkeeper tasks, IFile res, List<IReport> reports) {
		if (reports.isEmpty()) return;
		Marker.addAll(res, reports);
		tasks.problems(reports.size());
	}
	
	public Map<IFile, SourceMapping> compile(IProject project, IFile res) {
		if (res == null || !res.exists())
			return FAILED;
		final Bookkeeper tasks = Bookkeeper.start(log, "Compiling grammar description " + res);
		Marker.deleteAll(res);
		
		final ClassFactory cf = new ClassFactory(res);
		if (!cf.isStale()) {
			tasks.leaveWith("Up-to-date grammar " + cf.classResource);
			return Collections.emptyMap();
		}

		JGELexer jgLexer = null;
		try (FileReader reader = new FileReader(cf.file)) {
			jgLexer = new JGELexer(cf.file.getPath(), reader);
			JGEParser jgParser = new JGEParser(jgLexer, JGELexer::main);
			PGrammar pgrammar = jgParser.start();
			tasks.done("Grammar description successfully parsed");

			Reporter configReporter = new Reporter();
			Config config = Config.ofPGrammar(pgrammar, configReporter);
			logAndMark(tasks, res, configReporter.getReports());
			
			tasks.enter("Grammar expansion");
			Reporter pdepsReporter = new Reporter();
			PGrammars.Dependencies deps = PGrammars.dependencies(pgrammar.rules);
			PGrammars.findUnusedSymbols(pgrammar, deps, pdepsReporter);
			PGrammars.analyseGrammar(pgrammar, deps, pdepsReporter);
			tasks.done("Analysed parametric rules");
			logAndMark(tasks, res, pdepsReporter.getReports());
			if (pdepsReporter.hasErrors()) {
				tasks.aborted("Inconsistent use of parametric rules");
				return FAILED;
			}

			Expansion.checkExpandability(pgrammar);
			tasks.done("Expandability check successful");
			Grammar grammar = Expansion.of(pgrammar);
			tasks.leaveWith("Expanded to ground grammar");
			tasks.infos("(" + grammar.rules.size() + " ground non-terminals"
					+ " from " + pgrammar.rules.size() + " rules)");
			
			Reporter depsReporter = new Reporter();
			Grammars.PredictionTable predictTable =
				Grammars.predictionTable(grammar, 
					Grammars.analyseGrammar(grammar, null, depsReporter));
			tasks.done("Analysed expanded grammar and built prediction table");
			logAndMark(tasks, res, depsReporter.getReports());
			List<IReport> conflicts = predictTable.findConflicts();
			if (!conflicts.isEmpty()) {
				Marker.addAll(res, conflicts);
				tasks.aborted("Expanded grammar is not LL(1)");
				return FAILED;
			}
			tasks.done("Expanded grammar is LL(1)");
			
			SourceMapping smap;
			try (Writer writer =
					new CountingWriter(new FileWriter(cf.classFile, false))) {
				writer.append("package " + cf.classPackage.getElementName() + ";\n\n");
				smap = GrammarOutput.output(writer, cf.className, config, grammar, predictTable);
				tasks.leaveWith("Generated parser in " + cf.classResource);
			} catch (IOException e) {
				e.printStackTrace(log);
				tasks.aborted("Could not output generated parser");
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
			
			return Collections.singletonMap(newRes, smap);
		}
		catch (LexicalError e) {
			Position start = jgLexer.getLexemeStart();
			Position end = jgLexer.getLexemeEnd();
			Marker.addError(res, e.getMessage(), start.line, start.offset, end.offset);
			tasks.aborted("Lexical error in grammar description");
		}
		catch (ParsingException e) {
			final Position start;
			final int end;
			if (e.pos == null) {
				start = jgLexer.getLexemeStart();
				end = jgLexer.getLexemeEnd().offset;
			} else {
				start = e.pos;
				end = e.pos.offset + e.length;
			}
			Marker.addError(res, e.getMessage(), start.line, start.offset, end);
			tasks.aborted("Syntax error in grammar description");
		}
		catch (PGrammar.IllFormedException e) {
			Marker.addAll(res, e.reports);
			tasks.aborted("Grammar description is not well-formed");
		}
		catch (Grammar.IllFormedException e) {
			Marker.addAll(res, e.reports);
			tasks.aborted("Grammar description is not well-formed");
		}
		catch (PGrammarNotExpandable e) {
			Marker.addAll(res, Lists.singleton(e.getReport()));
			tasks.aborted("Grammar is not expandable");
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
