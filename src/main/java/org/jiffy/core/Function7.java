package org.jiffy.core;

/**
 * Represents a function that accepts seven arguments and produces a result.
 * This is the seven-arity specialization of {@link java.util.function.Function}.
 *
 * @param <A> the type of the first argument to the function
 * @param <B> the type of the second argument to the function
 * @param <C> the type of the third argument to the function
 * @param <D> the type of the fourth argument to the function
 * @param <E> the type of the fifth argument to the function
 * @param <F> the type of the sixth argument to the function
 * @param <G> the type of the seventh argument to the function
 * @param <R> the type of the result of the function
 */
@FunctionalInterface
public interface Function7<A, B, C, D, E, F, G, R> {
    /**
     * Applies this function to the given arguments.
     *
     * @param a the first function argument
     * @param b the second function argument
     * @param c the third function argument
     * @param d the fourth function argument
     * @param e the fifth function argument
     * @param f the sixth function argument
     * @param g the seventh function argument
     * @return the function result
     */
    R apply(A a, B b, C c, D d, E e, F f, G g);
}