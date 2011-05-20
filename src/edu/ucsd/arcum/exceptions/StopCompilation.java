package edu.ucsd.arcum.exceptions;

// Used for when the user has already been notified of any errors (via, e.g., the
// placement of markers) and the compilation process should stop
public class StopCompilation extends ArcumError
{
    private static final long serialVersionUID = 1L;

    public StopCompilation() {
        super("Internal message");
    }
}
