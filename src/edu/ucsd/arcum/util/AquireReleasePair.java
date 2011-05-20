package edu.ucsd.arcum.util;

// EXAMPLE: When two methods (belonging to the same class) should be called in
// strict pairs, using the "try/finally" structure. The methods are identified by
// name.
public @interface AquireReleasePair
{
    String aquire();
    String release();
}
