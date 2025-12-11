package org.jiffy.annotations;

import org.jiffy.fixtures.effects.CounterEffect;
import org.jiffy.fixtures.effects.LogEffect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that annotations are retained at runtime and attributes work correctly.
 */
@DisplayName("Annotation Reflection")
class AnnotationReflectionTest {

    @Nested
    @DisplayName("@Uses Annotation")
    class UsesAnnotationTests {

        @Test
        @DisplayName("@Uses is retained at runtime")
        void uses_isRetainedAtRuntime() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("methodWithUses");
            Uses uses = method.getAnnotation(Uses.class);

            assertNotNull(uses);
        }

        @Test
        @DisplayName("@Uses value() returns effect classes")
        void uses_value_returnsEffectClasses() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("methodWithMultipleEffects");
            Uses uses = method.getAnnotation(Uses.class);

            Class<?>[] effects = uses.value();

            assertEquals(2, effects.length);
            assertEquals(LogEffect.class, effects[0]);
            assertEquals(CounterEffect.class, effects[1]);
        }

        @Test
        @DisplayName("@Uses enforced() defaults to true")
        void uses_enforced_defaultsToTrue() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("methodWithUses");
            Uses uses = method.getAnnotation(Uses.class);

            assertTrue(uses.enforced());
        }

        @Test
        @DisplayName("@Uses enforced() can be set false")
        void uses_enforced_canBeSetFalse() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("methodWithEnforcedFalse");
            Uses uses = method.getAnnotation(Uses.class);

            assertFalse(uses.enforced());
        }

        @Test
        @DisplayName("@Uses level() defaults to ERROR")
        void uses_level_defaultsToError() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("methodWithUses");
            Uses uses = method.getAnnotation(Uses.class);

            assertEquals(Uses.Level.ERROR, uses.level());
        }

        @Test
        @DisplayName("@Uses level() can be set to WARNING")
        void uses_level_canBeSetToWarning() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("methodWithLevelWarning");
            Uses uses = method.getAnnotation(Uses.class);

            assertEquals(Uses.Level.WARNING, uses.level());
        }

        @Test
        @DisplayName("@Uses level() can be set to INFO")
        void uses_level_canBeSetToInfo() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("methodWithLevelInfo");
            Uses uses = method.getAnnotation(Uses.class);

            assertEquals(Uses.Level.INFO, uses.level());
        }

        @Test
        @DisplayName("@Uses description() defaults to empty")
        void uses_description_defaultsToEmpty() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("methodWithUses");
            Uses uses = method.getAnnotation(Uses.class);

            assertEquals("", uses.description());
        }

        @Test
        @DisplayName("@Uses description() stores custom text")
        void uses_description_storesCustomText() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("methodWithDescription");
            Uses uses = method.getAnnotation(Uses.class);

            assertEquals("Logs for auditing", uses.description());
        }
    }

    @Nested
    @DisplayName("@Pure Annotation")
    class PureAnnotationTests {

        @Test
        @DisplayName("@Pure is retained at runtime")
        void pure_isRetainedAtRuntime() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("pureMethod");
            Pure pure = method.getAnnotation(Pure.class);

            assertNotNull(pure);
        }

        @Test
        @DisplayName("@Pure can be applied to method")
        void pure_canBeAppliedToMethod() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("pureMethod");

            assertTrue(method.isAnnotationPresent(Pure.class));
        }

        @Test
        @DisplayName("@Pure can be applied to type")
        void pure_canBeAppliedToType() {
            Pure pure = PureClass.class.getAnnotation(Pure.class);

            assertNotNull(pure);
        }

        @Test
        @DisplayName("@Pure reason() defaults to empty")
        void pure_reason_defaultsToEmpty() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("pureMethod");
            Pure pure = method.getAnnotation(Pure.class);

            assertEquals("", pure.reason());
        }

        @Test
        @DisplayName("@Pure reason() stores custom text")
        void pure_reason_storesCustomText() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("pureMethodWithReason");
            Pure pure = method.getAnnotation(Pure.class);

            assertEquals("Simple arithmetic, no side effects", pure.reason());
        }

        @Test
        @DisplayName("@Pure verify() defaults to true")
        void pure_verify_defaultsToTrue() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("pureMethod");
            Pure pure = method.getAnnotation(Pure.class);

            assertTrue(pure.verify());
        }

        @Test
        @DisplayName("@Pure verify() can be set false")
        void pure_verify_canBeSetFalse() throws NoSuchMethodException {
            Method method = TestMethods.class.getMethod("pureMethodNoVerify");
            Pure pure = method.getAnnotation(Pure.class);

            assertFalse(pure.verify());
        }
    }

    @Nested
    @DisplayName("Annotation Targets")
    class AnnotationTargets {

        @Test
        @DisplayName("@Uses can be applied to constructor")
        void uses_canBeAppliedToConstructor() throws NoSuchMethodException {
            var constructor = ClassWithAnnotatedConstructor.class.getConstructor(String.class);
            Uses uses = constructor.getAnnotation(Uses.class);

            assertNotNull(uses);
            assertEquals(1, uses.value().length);
        }
    }

    // Test helper classes and methods

    @SuppressWarnings("unused")
    public static class TestMethods {

        @Uses(LogEffect.class)
        public void methodWithUses() {}

        @Uses({LogEffect.class, CounterEffect.class})
        public void methodWithMultipleEffects() {}

        @Uses(value = LogEffect.class, enforced = false)
        public void methodWithEnforcedFalse() {}

        @Uses(value = LogEffect.class, level = Uses.Level.WARNING)
        public void methodWithLevelWarning() {}

        @Uses(value = LogEffect.class, level = Uses.Level.INFO)
        public void methodWithLevelInfo() {}

        @Uses(value = LogEffect.class, description = "Logs for auditing")
        public void methodWithDescription() {}

        @Pure
        public int pureMethod() { return 42; }

        @Pure(reason = "Simple arithmetic, no side effects")
        public int pureMethodWithReason() { return 1 + 1; }

        @Pure(verify = false)
        public int pureMethodNoVerify() { return 0; }
    }

    @Pure
    public static class PureClass {
        public int calculate(int a, int b) {
            return a + b;
        }
    }

    public static class ClassWithAnnotatedConstructor {
        @Uses(LogEffect.class)
        public ClassWithAnnotatedConstructor(String name) {}
    }
}
