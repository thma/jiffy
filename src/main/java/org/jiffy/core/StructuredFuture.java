package org.jiffy.core;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * A structured alternative to CompletableFuture for async effect execution.
 * Runs computations on virtual threads and provides a clean API for waiting on results.
 * Unlike CompletableFuture, StructuredFuture:
 * - Always uses virtual threads for efficient blocking
 * - Integrates naturally with StructuredTaskScope for internal parallelism
 * - Provides a simpler, more focused API for effect execution
 *
 * @param <A> the result type
 */
public final class StructuredFuture<A> {

    private final Thread virtualThread;
    private volatile A result;
    private volatile Throwable exception;
    private volatile boolean completed = false;

    private StructuredFuture(Callable<A> computation) {
        this.virtualThread = Thread.ofVirtual().start(() -> {
            try {
                this.result = computation.call();
            } catch (Throwable t) {
                this.exception = t;
            } finally {
                this.completed = true;
            }
        });
    }

    /**
     * Start an async computation on a virtual thread.
     *
     * @param computation the computation to run
     * @param <A> the result type
     * @return a StructuredFuture that will complete with the result
     */
    public static <A> StructuredFuture<A> start(Callable<A> computation) {
        return new StructuredFuture<>(computation);
    }

    /**
     * Block until the computation completes and return the result.
     *
     * @return the computed result
     * @throws RuntimeException if the computation failed
     */
    public A join() {
        try {
            virtualThread.join();
            if (exception != null) {
                throw new RuntimeException("Async computation failed", exception);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for result", e);
        }
    }

    /**
     * Block until the computation completes or timeout expires.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return the computed result
     * @throws TimeoutException if the timeout expires before completion
     * @throws RuntimeException if the computation failed
     */
    public A join(long timeout, TimeUnit unit) throws TimeoutException {
        try {
            boolean finished = virtualThread.join(java.time.Duration.of(timeout, unit.toChronoUnit()));
            if (!finished) {
                throw new TimeoutException("Computation did not complete within " + timeout + " " + unit);
            }
            if (exception != null) {
                throw new RuntimeException("Async computation failed", exception);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for result", e);
        }
    }

    /**
     * Check if the computation has completed (successfully or with an error).
     *
     * @return true if completed
     */
    public boolean isDone() {
        return completed;
    }

    /**
     * Cancel the computation by interrupting the virtual thread.
     * Note: The computation must be responsive to interruption for this to be effective.
     *
     * @return true if cancellation was requested
     */
    public boolean cancel() {
        if (!completed) {
            virtualThread.interrupt();
            return true;
        }
        return false;
    }

    /**
     * Transform the result when it completes.
     *
     * @param mapper the transformation function
     * @param <B> the new result type
     * @return a new StructuredFuture with the transformed result
     */
    public <B> StructuredFuture<B> map(Function<A, B> mapper) {
        return start(() -> mapper.apply(this.join()));
    }

    /**
     * Chain another async computation after this one completes.
     *
     * @param mapper the function producing the next computation
     * @param <B> the new result type
     * @return a new StructuredFuture with the chained result
     */
    public <B> StructuredFuture<B> flatMap(Function<A, StructuredFuture<B>> mapper) {
        return start(() -> mapper.apply(this.join()).join());
    }

    /**
     * Convert to a CompletableFuture for interop with existing APIs.
     * This bridges the structured concurrency world with the traditional future API.
     *
     * @return a CompletableFuture that will complete with the same result
     */
    public CompletableFuture<A> toCompletableFuture() {
        CompletableFuture<A> cf = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                cf.complete(this.join());
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    /**
     * Create a StructuredFuture from a CompletableFuture.
     * Useful for integrating with existing async APIs.
     *
     * @param cf the CompletableFuture to wrap
     * @param <A> the result type
     * @return a StructuredFuture that completes when the CompletableFuture completes
     */
    public static <A> StructuredFuture<A> fromCompletableFuture(CompletableFuture<A> cf) {
        return start(() -> {
            try {
                return cf.get();
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        });
    }
}
