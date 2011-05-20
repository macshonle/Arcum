package edu.ucsd.arcum.util;

import java.io.PrintStream;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.*;

public class SystemUtil
{
    private static PrintStream outStream;
    private static PrintStream errStream;

    // Compares the identity hash code of "a" with the identity hash code of "b",
    // which should allow consistent total orders for each given run of the program
    // TODO: Note that there's a small comparator issue with identity codes not
    // necessarily being unique on 64-bit systems
    public static int compareIdentityCodesConsistently(Object a, Object b) {
        int codeA = System.identityHashCode(a);
        int codeB = System.identityHashCode(b);
        return new Integer(codeA).compareTo(codeB);
    }

    public static PrintStream getOutStream() {
        if (outStream == null) {
            MessageConsole myConsole = findConsole("Arcum Concept Framework");
            MessageConsoleStream output = myConsole.newMessageStream();
            SystemUtil.outStream = new PrintStream(output);
        }
        return outStream;
    }

    public static PrintStream getErrStream() {
        if (errStream == null) {
            MessageConsole myConsole = findConsole("Arcum Concept Framework");
            MessageConsoleStream output = myConsole.newMessageStream();
            Color red = new Color(Display.getCurrent(), 0xFF, 0, 0);
            output.setColor(red);
            SystemUtil.errStream = new PrintStream(output);
        }
        return errStream;
    }

    private static MessageConsole findConsole(String name) {
        ConsolePlugin plugin = ConsolePlugin.getDefault();
        IConsoleManager conMan = plugin.getConsoleManager();
        IConsole[] existing = conMan.getConsoles();
        for (int i = 0; i < existing.length; i++) {
            if (name.equals(existing[i].getName())) {
                return (MessageConsole)existing[i];
            }
        }
        // no console found, so create a new one
        MessageConsole myConsole = new MessageConsole(name, null);
        conMan.addConsoles(new IConsole[] { myConsole });
        return myConsole;
    }
}