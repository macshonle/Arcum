package edu.ucsd.arcum.exceptions;

public class FatalArcumError extends ArcumError
{
    private static final long serialVersionUID = 1L;

    public FatalArcumError(String message) {
        super(message);
    }
}