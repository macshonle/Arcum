package edu.ucsd.arcum.util;

import java.lang.reflect.*;

import com.google.common.base.Function;
import com.google.inject.TypeLiteral;

public class Accessor
{
    public static <R, D> Function<R, D> getFunction(Class<R> range, Class<D> domain,
        String methodName)
    {
        try {
            final Method method = range.getMethod(methodName);
            return new Function<R, D>() {
                public D apply(R from) {
                    try {
                        return (D)method.invoke(from);
                    }
                    catch (IllegalAccessException e) {
                        throw new RuntimeException(e);

                    }
                    catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static <R, D> Function<R, D> getFunction(TypeLiteral<R> range,
        TypeLiteral<D> domain, String methodName)
    {
        // The raw type contains the actual method; we provide the TypeLiterals
        // only to allow Functions to be created with more specific types
        Class<?> rawRange = getRawType(range.getType());
        Class<?> rawDomain = getRawType(domain.getType());
        return (Function<R, D>)getFunction(rawRange, rawDomain, methodName);
    }

    private static Class<?> getRawType(Type type) {
        // Copied implementation from com.google.inject.TypeLiteral#getRawType, 2006 Google
        if (type instanceof Class<?>) {
            return (Class<?>)type;
        }
        else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType)type;
            return (Class<?>)parameterizedType.getRawType();
        }
        else if (type instanceof GenericArrayType) {
            return Object[].class;
        }
        return null;
    }
}