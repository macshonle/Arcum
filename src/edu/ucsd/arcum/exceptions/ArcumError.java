package edu.ucsd.arcum.exceptions;

import org.eclipse.core.runtime.CoreException;

import edu.ucsd.arcum.ui.UIUtil;

public class ArcumError extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public ArcumError(String message) {
        super(message);
    }

    public ArcumError(Exception exception) {
        super(exception);
    }

    public static void userError(SourceLocation location, String format, Object... args) {
        String message = String.format(format, args);
        System.out.flush();
        System.err.printf("%s%n", message);
        message = message.trim();
        try {
            if (location != null) {
                location.createMarker(message);
            }
            else {
                UIUtil.error(message, "An Error Has Occurred");
            }
        }
        catch (CoreException e) {
            e.printStackTrace();
            ArcumError.fatalError(format, args);
        }
    }

    // EXAMPLE: This function does not return. An extra annotation and checking on it
    // will help prevent coding errors.
    public static void fatalUserError(SourceLocation location, String format,
        Object... args)
    {
        userError(location, format, args);
        throw new FatalArcumError(String.format(format, args));
    }

    public static void fatalError(String format, Object... args) {
        String message = String.format(format, args);
        System.out.flush();
        System.err.printf("%s%n", message);
        UIUtil.error(message, "An Error Has Occurred");
        throw new ArcumError(message);
    }

    public static void stop() {
        throw new StopCompilation();
    }
}
