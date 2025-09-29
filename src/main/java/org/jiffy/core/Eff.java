package org.jiffy.core;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Eff monad for composing and handling effects.
 * This provides a way to build computation pipelines that involve effects.
 *
 * @param <A> The type of value this Eff produces
 */
public abstract class Eff<A> {

    // Private constructor to ensure controlled instantiation
    private Eff() {}

    /**
     * Run this effect with the provided runtime.
     */
    public abstract A runWith(EffectRuntime runtime);

    /**
     * Map over the value produced by this effect.
     */
    public <B> Eff<B> map(Function<A, B> f) {
        return flatMap(a -> pure(f.apply(a)));
    }

    /**
     * FlatMap for monadic composition.
     */
    public abstract <B> Eff<B> flatMap(Function<A, Eff<B>> f);

    /**
     * Create a pure effect that produces the given value.
     */
    public static <A> Eff<A> pure(A value) {
        return new Pure<>(value);
    }

    /**
     * Create an effect that performs the given effect.
     */
    public static <A> Eff<A> perform(Effect<A> effect) {
        return new Perform<>(effect);
    }

    /**
     * Create an effect from a supplier (lazy evaluation).
     */
    public static <A> Eff<A> of(Supplier<A> supplier) {
        return new Lazy<>(supplier);
    }

    /**
     * Sequence multiple effects, discarding intermediate results.
     */
    @SuppressWarnings("unchecked")
    public static <A> Eff<A> sequence(Eff<?>... effects) {
        if (effects.length == 0) {
            return pure(null);
        }

        // Cast the first effect to Eff<Object> to work around wildcard issues
        Eff<Object> result = (Eff<Object>) effects[0];

        // Chain all effects except the last one
        for (int i = 1; i < effects.length - 1; i++) {
            Eff<Object> next = (Eff<Object>) effects[i];
            result = result.flatMap(ignored -> next);
        }

        // If there are multiple effects, chain the last one with proper type
        if (effects.length > 1) {
            Eff<A> last = (Eff<A>) effects[effects.length - 1];
            return result.flatMap(ignored -> last);
        }

        // Single effect case
        return (Eff<A>) result;
    }

    /**
     * Run multiple effects in parallel.
     */
    public static <A, B> Eff<Pair<A, B>> parallel(Eff<A> effA, Eff<B> effB) {
        return new Parallel<>(effA, effB);
    }

    /**
     * Recover from errors in this effect.
     */
    public Eff<A> recover(Function<Throwable, A> recovery) {
        return new Recover<>(this, recovery);
    }

    /**
     * Recover with another effect.
     */
    public Eff<A> recoverWith(Function<Throwable, Eff<A>> recovery) {
        return new RecoverWith<>(this, recovery);
    }

    // For comprehension factory methods

    /**
     * Creates a For comprehension with one effect.
     */
    public static <A> For1<A> For(Eff<A> effA) {
        return new For1<>(effA);
    }

    /**
     * Creates a For comprehension with two effects.
     */
    public static <A, B> For2<A, B> For(Eff<A> effA, Eff<B> effB) {
        return new For2<>(effA, effB);
    }

    /**
     * Creates a For comprehension with three effects.
     */
    public static <A, B, C> For3<A, B, C> For(Eff<A> effA, Eff<B> effB, Eff<C> effC) {
        return new For3<>(effA, effB, effC);
    }

    /**
     * Creates a For comprehension with four effects.
     */
    public static <A, B, C, D> For4<A, B, C, D> For(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD) {
        return new For4<>(effA, effB, effC, effD);
    }

    /**
     * Creates a For comprehension with five effects.
     */
    public static <A, B, C, D, E> For5<A, B, C, D, E> For(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD, Eff<E> effE) {
        return new For5<>(effA, effB, effC, effD, effE);
    }

    /**
     * Creates a For comprehension with six effects.
     */
    public static <A, B, C, D, E, F> For6<A, B, C, D, E, F> For(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD, Eff<E> effE, Eff<F> effF) {
        return new For6<>(effA, effB, effC, effD, effE, effF);
    }

    /**
     * Creates a For comprehension with seven effects.
     */
    public static <A, B, C, D, E, F, G> For7<A, B, C, D, E, F, G> For(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD, Eff<E> effE, Eff<F> effF, Eff<G> effG) {
        return new For7<>(effA, effB, effC, effD, effE, effF, effG);
    }

    /**
     * Creates a For comprehension with eight effects.
     */
    public static <A, B, C, D, E, F, G, H> For8<A, B, C, D, E, F, G, H> For(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD, Eff<E> effE, Eff<F> effF, Eff<G> effG, Eff<H> effH) {
        return new For8<>(effA, effB, effC, effD, effE, effF, effG, effH);
    }

    // Implementation classes

    private static class Pure<A> extends Eff<A> {
        private final A value;

        Pure(A value) {
            this.value = value;
        }

        @Override
        public A runWith(EffectRuntime runtime) {
            return value;
        }

        @Override
        public <B> Eff<B> flatMap(Function<A, Eff<B>> f) {
            return f.apply(value);
        }
    }

    private static class Perform<A> extends Eff<A> {
        private final Effect<A> effect;

        Perform(Effect<A> effect) {
            this.effect = effect;
        }

        @Override
        public A runWith(EffectRuntime runtime) {
            return runtime.handle(effect);
        }

        @Override
        public <B> Eff<B> flatMap(Function<A, Eff<B>> f) {
            return new FlatMap<>(this, f);
        }

        public Effect<A> getEffect() {
            return effect;
        }
    }

    private static class FlatMap<A, B> extends Eff<B> {
        private final Eff<A> source;
        private final Function<A, Eff<B>> f;

        FlatMap(Eff<A> source, Function<A, Eff<B>> f) {
            this.source = source;
            this.f = f;
        }

        @Override
        public B runWith(EffectRuntime runtime) {
            A a = source.runWith(runtime);
            return f.apply(a).runWith(runtime);
        }

        @Override
        public <C> Eff<C> flatMap(Function<B, Eff<C>> g) {
            return source.flatMap(a -> f.apply(a).flatMap(g));
        }
    }

    private static class Lazy<A> extends Eff<A> {
        private final Supplier<A> supplier;

        Lazy(Supplier<A> supplier) {
            this.supplier = supplier;
        }

        @Override
        public A runWith(EffectRuntime runtime) {
            return supplier.get();
        }

        @Override
        public <B> Eff<B> flatMap(Function<A, Eff<B>> f) {
            return new FlatMap<>(this, f);
        }
    }

    private static class Parallel<A, B> extends Eff<Pair<A, B>> {
        private final Eff<A> effA;
        private final Eff<B> effB;

        Parallel(Eff<A> effA, Eff<B> effB) {
            this.effA = effA;
            this.effB = effB;
        }

        @Override
        public Pair<A, B> runWith(EffectRuntime runtime) {
            CompletableFuture<A> futureA = CompletableFuture.supplyAsync(() -> effA.runWith(runtime));
            CompletableFuture<B> futureB = CompletableFuture.supplyAsync(() -> effB.runWith(runtime));

            try {
                return new Pair<>(futureA.get(), futureB.get());
            } catch (Exception e) {
                throw new RuntimeException("Error in parallel execution", e);
            }
        }

        @Override
        public <C> Eff<C> flatMap(Function<Pair<A, B>, Eff<C>> f) {
            return new FlatMap<>(this, f);
        }
    }

    private static class Recover<A> extends Eff<A> {
        private final Eff<A> source;
        private final Function<Throwable, A> recovery;

        Recover(Eff<A> source, Function<Throwable, A> recovery) {
            this.source = source;
            this.recovery = recovery;
        }

        @Override
        public A runWith(EffectRuntime runtime) {
            try {
                return source.runWith(runtime);
            } catch (Throwable t) {
                return recovery.apply(t);
            }
        }

        @Override
        public <B> Eff<B> flatMap(Function<A, Eff<B>> f) {
            return new FlatMap<>(this, f);
        }
    }

    private static class RecoverWith<A> extends Eff<A> {
        private final Eff<A> source;
        private final Function<Throwable, Eff<A>> recovery;

        RecoverWith(Eff<A> source, Function<Throwable, Eff<A>> recovery) {
            this.source = source;
            this.recovery = recovery;
        }

        @Override
        public A runWith(EffectRuntime runtime) {
            try {
                return source.runWith(runtime);
            } catch (Throwable t) {
                return recovery.apply(t).runWith(runtime);
            }
        }

        @Override
        public <B> Eff<B> flatMap(Function<A, Eff<B>> f) {
            return new FlatMap<>(this, f);
        }
    }

    /**
     * Simple pair class for parallel results.
     */
    public static class Pair<A, B> {
        private final A first;
        private final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        public A getFirst() {
            return first;
        }

        public B getSecond() {
            return second;
        }

        public <C> C fold(BiFunction<A, B, C> f) {
            return f.apply(first, second);
        }
    }

    /**
     * Collect all effects in this computation (for testing).
     */
    public List<Effect<?>> collectEffects() {
        List<Effect<?>> effects = new ArrayList<>();
        collectEffectsInternal(effects);
        return effects;
    }

    private void collectEffectsInternal(List<Effect<?>> effects) {
        switch (this) {
            case Perform<?> perform -> effects.add(perform.getEffect());
            case FlatMap<?, ?> flatMap -> flatMap.source.collectEffectsInternal(effects);
            case Parallel<?, ?> parallel -> {
                parallel.effA.collectEffectsInternal(effects);
                parallel.effB.collectEffectsInternal(effects);
            }
            default -> {
            }
        }
    }

    // For comprehension classes

    /**
     * For comprehension with one effect.
     */
    public static final class For1<A> {
        private final Eff<A> effA;

        private For1(Eff<A> effA) {
            this.effA = effA;
        }

        /**
         * Yields a pure value from the effect.
         */
        public <R> Eff<R> yield(Function<A, R> f) {
            return effA.map(f);
        }

        /**
         * Yields an effectful computation from the effect.
         */
        public <R> Eff<R> yieldEff(Function<A, Eff<R>> f) {
            return effA.flatMap(f);
        }
    }

    /**
     * For comprehension with two effects.
     */
    public static final class For2<A, B> {
        private final Eff<A> effA;
        private final Eff<B> effB;

        private For2(Eff<A> effA, Eff<B> effB) {
            this.effA = effA;
            this.effB = effB;
        }

        /**
         * Yields a pure value from the effects.
         */
        public <R> Eff<R> yield(BiFunction<A, B, R> f) {
            return effA.flatMap(a -> effB.map(b -> f.apply(a, b)));
        }

        /**
         * Yields an effectful computation from the effects.
         */
        public <R> Eff<R> yieldEff(BiFunction<A, B, Eff<R>> f) {
            return effA.flatMap(a -> effB.flatMap(b -> f.apply(a, b)));
        }
    }

    /**
     * For comprehension with three effects.
     */
    public static final class For3<A, B, C> {
        private final Eff<A> effA;
        private final Eff<B> effB;
        private final Eff<C> effC;

        private For3(Eff<A> effA, Eff<B> effB, Eff<C> effC) {
            this.effA = effA;
            this.effB = effB;
            this.effC = effC;
        }

        /**
         * Yields a pure value from the effects.
         */
        public <R> Eff<R> yield(Function3<A, B, C, R> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.map(c -> f.apply(a, b, c))
                )
            );
        }

        /**
         * Yields an effectful computation from the effects.
         */
        public <R> Eff<R> yieldEff(Function3<A, B, C, Eff<R>> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c -> f.apply(a, b, c))
                )
            );
        }
    }

    /**
     * For comprehension with four effects.
     */
    public static final class For4<A, B, C, D> {
        private final Eff<A> effA;
        private final Eff<B> effB;
        private final Eff<C> effC;
        private final Eff<D> effD;

        private For4(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD) {
            this.effA = effA;
            this.effB = effB;
            this.effC = effC;
            this.effD = effD;
        }

        /**
         * Yields a pure value from the effects.
         */
        public <R> Eff<R> yield(Function4<A, B, C, D, R> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.map(d -> f.apply(a, b, c, d))
                    )
                )
            );
        }

        /**
         * Yields an effectful computation from the effects.
         */
        public <R> Eff<R> yieldEff(Function4<A, B, C, D, Eff<R>> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.flatMap(d -> f.apply(a, b, c, d))
                    )
                )
            );
        }
    }

    /**
     * For comprehension with five effects.
     */
    public static final class For5<A, B, C, D, E> {
        private final Eff<A> effA;
        private final Eff<B> effB;
        private final Eff<C> effC;
        private final Eff<D> effD;
        private final Eff<E> effE;

        private For5(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD, Eff<E> effE) {
            this.effA = effA;
            this.effB = effB;
            this.effC = effC;
            this.effD = effD;
            this.effE = effE;
        }

        /**
         * Yields a pure value from the effects.
         */
        public <R> Eff<R> yield(Function5<A, B, C, D, E, R> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.flatMap(d ->
                            effE.map(e -> f.apply(a, b, c, d, e))
                        )
                    )
                )
            );
        }

        /**
         * Yields an effectful computation from the effects.
         */
        public <R> Eff<R> yieldEff(Function5<A, B, C, D, E, Eff<R>> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.flatMap(d ->
                            effE.flatMap(e -> f.apply(a, b, c, d, e))
                        )
                    )
                )
            );
        }
    }

    /**
     * For comprehension with six effects.
     */
    public static final class For6<A, B, C, D, E, F> {
        private final Eff<A> effA;
        private final Eff<B> effB;
        private final Eff<C> effC;
        private final Eff<D> effD;
        private final Eff<E> effE;
        private final Eff<F> effF;

        private For6(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD, Eff<E> effE, Eff<F> effF) {
            this.effA = effA;
            this.effB = effB;
            this.effC = effC;
            this.effD = effD;
            this.effE = effE;
            this.effF = effF;
        }

        /**
         * Yields a pure value from the effects.
         */
        public <R> Eff<R> yield(Function6<A, B, C, D, E, F, R> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.flatMap(d ->
                            effE.flatMap(e ->
                                effF.map(f_ -> f.apply(a, b, c, d, e, f_))
                            )
                        )
                    )
                )
            );
        }

        /**
         * Yields an effectful computation from the effects.
         */
        public <R> Eff<R> yieldEff(Function6<A, B, C, D, E, F, Eff<R>> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.flatMap(d ->
                            effE.flatMap(e ->
                                effF.flatMap(f_ -> f.apply(a, b, c, d, e, f_))
                            )
                        )
                    )
                )
            );
        }
    }

    /**
     * For comprehension with seven effects.
     */
    public static final class For7<A, B, C, D, E, F, G> {
        private final Eff<A> effA;
        private final Eff<B> effB;
        private final Eff<C> effC;
        private final Eff<D> effD;
        private final Eff<E> effE;
        private final Eff<F> effF;
        private final Eff<G> effG;

        private For7(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD, Eff<E> effE, Eff<F> effF, Eff<G> effG) {
            this.effA = effA;
            this.effB = effB;
            this.effC = effC;
            this.effD = effD;
            this.effE = effE;
            this.effF = effF;
            this.effG = effG;
        }

        /**
         * Yields a pure value from the effects.
         */
        public <R> Eff<R> yield(Function7<A, B, C, D, E, F, G, R> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.flatMap(d ->
                            effE.flatMap(e ->
                                effF.flatMap(f_ ->
                                    effG.map(g -> f.apply(a, b, c, d, e, f_, g))
                                )
                            )
                        )
                    )
                )
            );
        }

        /**
         * Yields an effectful computation from the effects.
         */
        public <R> Eff<R> yieldEff(Function7<A, B, C, D, E, F, G, Eff<R>> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.flatMap(d ->
                            effE.flatMap(e ->
                                effF.flatMap(f_ ->
                                    effG.flatMap(g -> f.apply(a, b, c, d, e, f_, g))
                                )
                            )
                        )
                    )
                )
            );
        }
    }

    /**
     * For comprehension with eight effects.
     */
    public static final class For8<A, B, C, D, E, F, G, H> {
        private final Eff<A> effA;
        private final Eff<B> effB;
        private final Eff<C> effC;
        private final Eff<D> effD;
        private final Eff<E> effE;
        private final Eff<F> effF;
        private final Eff<G> effG;
        private final Eff<H> effH;

        private For8(Eff<A> effA, Eff<B> effB, Eff<C> effC, Eff<D> effD, Eff<E> effE, Eff<F> effF, Eff<G> effG, Eff<H> effH) {
            this.effA = effA;
            this.effB = effB;
            this.effC = effC;
            this.effD = effD;
            this.effE = effE;
            this.effF = effF;
            this.effG = effG;
            this.effH = effH;
        }

        /**
         * Yields a pure value from the effects.
         */
        public <R> Eff<R> yield(Function8<A, B, C, D, E, F, G, H, R> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.flatMap(d ->
                            effE.flatMap(e ->
                                effF.flatMap(f_ ->
                                    effG.flatMap(g ->
                                        effH.map(h -> f.apply(a, b, c, d, e, f_, g, h))
                                    )
                                )
                            )
                        )
                    )
                )
            );
        }

        /**
         * Yields an effectful computation from the effects.
         */
        public <R> Eff<R> yieldEff(Function8<A, B, C, D, E, F, G, H, Eff<R>> f) {
            return effA.flatMap(a ->
                effB.flatMap(b ->
                    effC.flatMap(c ->
                        effD.flatMap(d ->
                            effE.flatMap(e ->
                                effF.flatMap(f_ ->
                                    effG.flatMap(g ->
                                        effH.flatMap(h -> f.apply(a, b, c, d, e, f_, g, h))
                                    )
                                )
                            )
                        )
                    )
                )
            );
        }
    }
}