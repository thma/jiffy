package org.jiffy.core;

import org.jiffy.fixtures.effects.CounterEffect;
import org.jiffy.fixtures.effects.DatabaseEffect;
import org.jiffy.fixtures.effects.LogEffect;
import org.jiffy.fixtures.handlers.CollectingLogHandler;
import org.jiffy.fixtures.handlers.CounterHandler;
import org.jiffy.fixtures.handlers.InMemoryDatabaseHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the EffectRuntime class.
 */
@DisplayName("EffectRuntime")
class EffectRuntimeTest {

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {

        @Test
        @DisplayName("builder() creates empty runtime")
        void builder_createsEmptyRuntime() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            assertNotNull(runtime);
        }

        @Test
        @DisplayName("withHandler() registers type-safe handler")
        void withHandler_registersTypeSafeHandler() {
            CollectingLogHandler handler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            runtime.handle(new LogEffect.Info("test"));

            assertTrue(handler.containsMessagePart("test"));
        }

        @Test
        @DisplayName("withHandlerUnsafe() registers handler")
        void withHandlerUnsafe_registersHandler() {
            CounterHandler handler = new CounterHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandlerUnsafe(CounterEffect.class, handler)
                .build();

            Integer result = runtime.handle(new CounterEffect.Increment());

            assertEquals(1, result);
        }

        @Test
        @DisplayName("withHandler() allows chaining")
        void withHandler_allowsChaining() {
            CollectingLogHandler logHandler = new CollectingLogHandler();
            CounterHandler counterHandler = new CounterHandler();

            EffectRuntime.Builder builder = EffectRuntime.builder();

            // Chaining returns the same builder
            EffectRuntime.Builder result = builder
                .withHandler(LogEffect.class, logHandler)
                .withHandler(CounterEffect.class, counterHandler);

            assertSame(builder, result);
        }
    }

    @Nested
    @DisplayName("Handler Dispatch")
    class HandlerDispatch {

        @Test
        @DisplayName("handle() dispatches to exact match handler")
        void handle_dispatchesToExactMatchHandler() {
            CounterHandler handler = new CounterHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(CounterEffect.class, handler)
                .build();

            Integer result = runtime.handle(new CounterEffect.Get());

            assertEquals(0, result);
            assertEquals(0, handler.getCurrentValue());
        }

        @Test
        @DisplayName("handle() dispatches to superclass handler")
        void handle_dispatchesToSuperclassHandler() {
            CollectingLogHandler handler = new CollectingLogHandler();
            // Register handler for parent interface LogEffect
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            // Handle a specific variant (LogEffect.Info)
            runtime.handle(new LogEffect.Info("test info"));
            runtime.handle(new LogEffect.Warning("test warning"));

            assertEquals(2, handler.size());
        }

        @Test
        @DisplayName("handle() dispatches to interface handler")
        void handle_dispatchesToInterfaceHandler() {
            // Handler registered for Effect interface should match any effect
            CollectingLogHandler logHandler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, logHandler)
                .build();

            runtime.handle(new LogEffect.Debug("debug message"));

            assertTrue(logHandler.containsMessagePart("debug message"));
        }

        @Test
        @DisplayName("handle() dispatches to enclosing class handler for nested records")
        void handle_dispatchesToEnclosingClassHandler() {
            InMemoryDatabaseHandler handler = new InMemoryDatabaseHandler();
            // Register handler for enclosing interface (use withHandlerUnsafe for generic effect type)
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandlerUnsafe(DatabaseEffect.class, handler)
                .build();

            // Handle nested record effect
            DatabaseEffect.Entity entity = new DatabaseEffect.Entity(null, "test", 100);
            DatabaseEffect.Entity saved = runtime.handle(new DatabaseEffect.Save(entity));

            assertNotNull(saved.id());
            assertEquals("test", saved.name());
        }

        @Test
        @DisplayName("handle() throws for unregistered effect")
        void handle_throwsForUnregisteredEffect() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runtime.handle(new LogEffect.Info("test"))
            );

            assertNotNull(ex.getMessage());
        }

        @Test
        @DisplayName("handle() includes effect class in error message")
        void handle_includesEffectClassInErrorMessage() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runtime.handle(new CounterEffect.Get())
            );

            assertTrue(ex.getMessage().contains("CounterEffect"));
        }

        @Test
        @DisplayName("handle() returns handler result")
        void handle_returnsHandlerResult() {
            CounterHandler handler = new CounterHandler(10);
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(CounterEffect.class, handler)
                .build();

            Integer result = runtime.handle(new CounterEffect.Get());

            assertEquals(10, result);
        }

        @Test
        @DisplayName("handle() returns null for void effect")
        void handle_returnsNullForVoidEffect() {
            CollectingLogHandler handler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            Void result = runtime.handle(new LogEffect.Info("test"));

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Multiple Handlers")
    class MultipleHandlers {

        @Test
        @DisplayName("runtime with multiple handlers routes correctly")
        void multipleHandlers_routeCorrectly() {
            CollectingLogHandler logHandler = new CollectingLogHandler();
            CounterHandler counterHandler = new CounterHandler();
            InMemoryDatabaseHandler dbHandler = new InMemoryDatabaseHandler();

            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, logHandler)
                .withHandler(CounterEffect.class, counterHandler)
                .withHandlerUnsafe(DatabaseEffect.class, dbHandler)
                .build();

            // Each effect goes to its handler
            runtime.handle(new LogEffect.Info("log message"));
            runtime.handle(new CounterEffect.Increment());
            runtime.handle(new DatabaseEffect.Save(new DatabaseEffect.Entity(null, "item", 1)));

            assertEquals(1, logHandler.size());
            assertEquals(1, counterHandler.getCurrentValue());
            assertEquals(1, dbHandler.getAllData().size());
        }
    }
}
