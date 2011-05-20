package edu.ucsd.arcum.util;

// EXAMPLE: The Java element this annotation is applied to can only be used, read or
// written by the sets of methods defined. (The constructor is always allowed write
// access for fields and does not need to be in the method group.)
public @interface ReadWriteAccess
{
    MethodGroup[] value();
}
