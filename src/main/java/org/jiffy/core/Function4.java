package org.jiffy.core;

/**
 * Represents a function that accepts four arguments and produces a result.
 * This is the four-arity specialization of {@link java.util.function.Function}.
 *
 * @param <A> the type of the first argument to the function
 * @param <B> the type of the second argument to the function
 * @param <C> the type of the third argument to the function
 * @param <D> the type of the fourth argument to the function
 * @param <R> the type of the result of the function
 */
@FunctionalInterface
public interface Function4<A, B, C, D, R> {
    /**
     * Applies this function to the given arguments.
     *
     * @param a the first function argument
     * @param b the second function argument
     * @param c the third function argument
     * @param d the fourth function argument
     * @return the function result
     */
    R apply(A a, B b, C c, D d);
}