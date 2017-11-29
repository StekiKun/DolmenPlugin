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

import codegen.BaseParser.ParsingException;
import codegen.GrammarOutput;
import codegen.LexBuffer.LexicalError;
import codegen.LexBuffer.Position;
import codegen.SourceMapping;
import common.CountingWriter;
import dolmenplugin.base.Marker;
import dolmenplugin.base.Utils;
import jg.JGLexer;
import jg.JGParserGenerated;
import syntax.Grammar;
import syntax.Grammars;
import syntax.IReport;

public final class JGCompile {

	private final PrintStream log;
	private final SubMonitor monitor;
	
	public JGCompile(PrintStream log, SubMonitor monitor) {
		this.log = log;
		this.monitor = monitor;
	}

	private static final Map<IFile, SourceMapping> FAILED = Collections.emptyMap();
	
	public Map<IFile, SourceMapping> compile(IProject project, IFile res) {
		if (res == null || !res.exists())
			return FAILED;
		log.println("Compiling grammar description " + res);
		Marker.deleteAll(res);
		
		final ClassFactory cf = new ClassFactory(res);
		JGLexer jgLexer = null;
		try (FileReader reader = new FileReader(cf.file)) {
			jgLexer = new JGLexer(cf.file.getPath(), reader);
			JGParserGenerated jgParser = new JGParserGenerated(jgLexer, JGLexer::main);
			Grammar grammar = jgParser.start();
			log.println("├─ Grammar description successfully parsed");
			
			Grammars.PredictionTable predictTable =
				Grammars.predictionTable(grammar, Grammars.analyseGrammar(grammar, null));
			log.println("├─ Analysed grammar and built prediction table");
			List<IReport> conflicts = predictTable.findConflicts();
			if (!conflicts.isEmpty()) {
				Marker.addAll(res, conflicts);
				log.println("╧  Grammar is not LL(1)");
				return FAILED;
			}
			log.println("├─ Grammar is LL(1)");
			
			SourceMapping smap;
			try (Writer writer =
					new CountingWriter(new FileWriter(cf.classFile, false))) {
				writer.append("package " + cf.classPackage.getElementName() + ";\n\n");
				smap = GrammarOutput.outputDefault(writer, cf.className, grammar, predictTable);
				log.println("└─ Generated parser in " + cf.classResource);
			} catch (IOException e) {
				e.printStackTrace(log);
				log.println("╧  Could not output generated parser");
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
			
			return Collections.singletonMap(newRes, smap);
		}
		catch (LexicalError e) {
			Position start = jgLexer.getLexemeStart();
			Position end = jgLexer.getLexemeEnd();
			Marker.addError(res, e.getMessage(), start.line, start.offset, end.offset);
			log.println("╧  Lexical error in grammar description");
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
			log.println("╧  Syntax error in grammar description");
		}
		catch (Grammar.IllFormedException e) {
			Marker.addAll(res, e.reports);
			log.println("╧  Grammar description is not well-formed");
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
