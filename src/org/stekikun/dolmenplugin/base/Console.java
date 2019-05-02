package org.stekikun.dolmenplugin.base;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleFactory;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.stekikun.dolmenplugin.Activator;

/**
 * Implementation of a custom console mechanism for Dolmen.
 * This class is the <i>factory</i> which takes care of creating
 * and opening a Dolmen console. See {@link DolmenConsole} for
 * the console view itself.
 * 
 * @author Stéphane Lescuyer
 */
public final class Console implements IConsoleFactory {
	
	private static final String DOLMEN_CONSOLE_ICON = "icons/sample.gif";
	
	/** The name of the custom Dolmen console */
	public static final String DOLMEN_CONSOLE = "Dolmen Console";
	
	@Override
	public void openConsole() {
		findDolmenConsole();
	}

	/**
	 * @return the Dolmen console if any, or creates one and adds it
	 * 	to the console manager
	 */
	public static DolmenConsole findDolmenConsole() {
		IConsoleManager consoleManager = ConsolePlugin.getDefault().getConsoleManager();
		IConsole[] consoles = consoleManager.getConsoles();
		for (IConsole console : consoles)
			if (DOLMEN_CONSOLE.equals(console.getName()))
				return (DolmenConsole) console;
		// Create one
		DolmenConsole console = new DolmenConsole();
		consoleManager.addConsoles( new IConsole[] { console } );
		consoleManager.showConsoleView(console);
		return console;
	}
	
	/**
	 * The custom console used by the Dolmen plug-in, for now
	 * a simple {@link MessageConsole message console}
	 * 
	 * @author Stéphane Lescuyer
	 */
	public static final class DolmenConsole extends MessageConsole {
		DolmenConsole() {
			super(DOLMEN_CONSOLE, Activator.getImageDescriptor(DOLMEN_CONSOLE_ICON));
		}
	}
	
}
