package edu.ucsd.arcum.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) public @interface Pure
{
    // the result of a Pure function is expected to be used: otherwise the
    // method doesn't need to be called
}
