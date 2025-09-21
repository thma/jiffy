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
    @SafeVarargs
    public static <A> Eff<A> sequence(Eff<?>... effects) {
        if (effects.length == 0) {
            return pure(null);
        }

        Eff<?> result = effects[0];
        for (int i = 1; i < effects.length - 1; i++) {
            Eff<?> next = effects[i];
            result = result.flatMap(ignored -> next);
        }

        if (effects.length > 1) {
            @SuppressWarnings("unchecked")
            Eff<A> last = (Eff<A>) effects[effects.length - 1];
            return result.flatMap(ignored -> last);
        }

        @SuppressWarnings("unchecked")
        Eff<A> single = (Eff<A>) result;
        return single;
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
        if (this instanceof Perform<?> perform) {
            effects.add(perform.getEffect());
        } else if (this instanceof FlatMap<?, ?> flatMap) {
            flatMap.source.collectEffectsInternal(effects);
        } else if (this instanceof Parallel<?, ?> parallel) {
            parallel.effA.collectEffectsInternal(effects);
            parallel.effB.collectEffectsInternal(effects);
        }
    }
}