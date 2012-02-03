package com.vividsolutions.jump.workbench.ui.plugin;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.PrintStream;
import java.io.Reader;
import bsh.ConsoleInterface;
import bsh.EvalError;
import bsh.Interpreter;
import bsh.util.JConsole;

import com.vividsolutions.jump.I18N;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;
import com.vividsolutions.jump.workbench.ui.MenuNames;
import com.vividsolutions.jump.workbench.ui.toolbox.ToolboxDialog;
import com.vividsolutions.jump.workbench.ui.toolbox.ToolboxPlugIn;
public class BeanShellPlugIn extends ToolboxPlugIn {
	public void initialize(PlugInContext context) throws Exception {
        // [Michael Michaud 2007-03-23]
        // Moves MenuNames.TOOLS/MenuNames.TOOLS_PROGRAMMING to MenuNames.CUSTOMIZE
		createMainMenuItem(new String[]{MenuNames.CUSTOMIZE}, null, context
				.getWorkbenchContext());
	}

	public String getName() {
        // [Michael Michaud 2007-03-23] Rename BeanShell to BeanShell Console to differentiate
        // from BeanShell scripts menus
		return "BeanShell Console";
	}

	protected void initializeToolbox(ToolboxDialog toolbox) {
		try {
			final JConsole console = new JConsole();
			console.setPreferredSize(new Dimension(430, 240));
			console.print(I18N.get("ui.plugin.BeanShellPlugIn.the-workbenchcontext-may-be-referred-to-as-wc"));
			console.print(I18N.get("ui.plugin.BeanShellPlugIn.warning-pasting-in-multiple-statements-may-cause-the-application-to-freeze"));
			toolbox.getCenterPanel().add(console, BorderLayout.CENTER);
			Interpreter interpreter = new Interpreter(console);
			interpreter.setClassLoader(toolbox.getContext().getWorkbench()
					.getPlugInManager().getClassLoader());
			interpreter.set("wc", toolbox.getContext());
			interpreter.eval("setAccessibility(true)");
			interpreter.eval("import com.vividsolutions.jts.geom.*");
			interpreter.eval("import com.vividsolutions.jump.feature.*");
			new Thread(interpreter).start();
		} catch (EvalError e) {
			toolbox.getContext().getErrorHandler().handleThrowable(e);
		}
	}
}