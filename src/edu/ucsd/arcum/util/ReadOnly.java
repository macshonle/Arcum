package edu.ucsd.arcum.util;

//@Target(value = { ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.METHOD,
//    ElementType.PARAMETER })
public @interface ReadOnly
{
    // When applied to methods it's in reference to the return type of the method.
    // Thus, this should not be applied to void methods.
}