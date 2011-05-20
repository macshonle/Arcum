package edu.ucsd.arcum.exceptions;

/**
 * Thrown to indicate the code at the throw point is unreachable. Useful in
 * documenting code and to suppress Java compiler errors.
 * */
public class Unreachable extends RuntimeException
{
    private static final long serialVersionUID = 1L;
}
