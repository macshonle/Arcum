package edu.ucsd.arcum.exceptions;

import org.eclipse.jdt.core.dom.Message;

public class JavaFragmentCompilationProblem extends Exception
{
    private static final long serialVersionUID = 1L;

    private final Message[] messages;

    public JavaFragmentCompilationProblem(Message[] messages) {
        this.messages = messages;
    }

    public String[] getMessages() {
        final String[] result = new String[messages.length];
        for (int i = 0; i < messages.length; ++i) {
            result[i] = messages[i].getMessage();
        }
        return result;
    }
}
