package dolmenplugin.builders;

import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.SubMonitor;

public final class JGCompile {

	private final PrintStream log;
	private final SubMonitor monitor;
	
	public JGCompile(PrintStream log, SubMonitor monitor) {
		this.log = log;
		this.monitor = monitor;
	}

	private static final List<IFile> FAILED = Collections.emptyList();
	
	public List<IFile> compile(IProject project, IFile res) {
		log.println("Compiling grammar description " + res);
		monitor.worked(1);
		return FAILED;
	}
	
}
