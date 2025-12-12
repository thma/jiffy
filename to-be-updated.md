# README.md Code Examples - Sync Issues

This document lists code examples in `README.md` that are out of sync with the current codebase implementation.

---

## 1. EffectHandler Interface (Lines 47-55)

### README shows:
```java
public class LogHandler implements EffectHandler<LogEffect> {
    @Override
    public void handle(LogEffect effect, EffectRuntime runtime) {
        switch (effect) {
            case LogEffect.Info(var message) -> System.out.println("[INFO] " + message);
            case LogEffect.Error(var message, var cause) -> System.err.println("[ERROR] " + message);
        }
    }
}
```

### Actual API (`src/main/java/org/jiffy/core/EffectHandler.java`):
```java
public interface EffectHandler<E extends Effect<?>> {
    <T> T handle(E effect);
}
```

### Issues:
- **Return type wrong**: Actual returns `<T> T`, README shows `void`
- **Method signature wrong**: Actual is `handle(E effect)`, README shows `handle(LogEffect effect, EffectRuntime runtime)` - the `EffectRuntime` parameter does not exist
- **LogEffect.Error parameters wrong**: Actual has only `message`, README shows `message` and `cause`

### Suggested fix:
```java
public class LogHandler implements EffectHandler<LogEffect> {
    @Override
    @SuppressWarnings("unchecked")
    public <T> T handle(LogEffect effect) {
        switch (effect) {
            case LogEffect.Info(var message) -> System.out.println("[INFO] " + message);
            case LogEffect.Error(var message) -> System.err.println("[ERROR] " + message);
        }
        return null;
    }
}
```

---

## 2. EffectRuntime Construction (Lines 61-64)

### README shows:
```java
EffectRuntime runtime = new EffectRuntime()
    .with(LogEffect.class, new LogHandler())
    .with(DatabaseEffect.class, new DatabaseHandler());
```

### Actual API (`src/main/java/org/jiffy/core/EffectRuntime.java`):
```java
EffectRuntime runtime = EffectRuntime.builder()
    .withHandler(LogEffect.class, new LogHandler())
    .withHandler(DatabaseEffect.class, new DatabaseHandler())
    .build();
```

### Issues:
- **Construction pattern wrong**: Actual uses Builder pattern with `EffectRuntime.builder()`, not `new EffectRuntime()`
- **Method name wrong**: Actual is `withHandler()`, README shows `with()`
- **Missing `.build()` call**: Builder pattern requires `.build()` at the end

---

## 3. LogEffect Definition (Lines 18-22)

### README shows:
```java
public sealed interface LogEffect extends Effect<Void> {
    record Info(String message) implements LogEffect {}
    record Error(String message, Throwable cause) implements LogEffect {}
}
```

### Actual code (`src/test/java/org/jiffy/fixtures/effects/LogEffect.java`):
```java
public sealed interface LogEffect extends Effect<Void> {
    String message();
    record Info(String message) implements LogEffect {}
    record Warning(String message) implements LogEffect {}
    record Error(String message) implements LogEffect {}
    record Debug(String message) implements LogEffect {}
}
```

### Issues:
- **Error record signature wrong**: Actual has only `String message`, README shows `String message, Throwable cause`
- **Missing variants**: Actual has `Warning` and `Debug` variants not shown in README
- **Missing method**: Actual has `message()` method declaration

---

## 4. Non-existent Annotations (Lines 109-114)

### README claims:
```
- `@UncheckedEffects` - Allows specific effects without declaration
- `@Provides` - Indicates a method provides effect handlers
```

### Actual status:
- **`@UncheckedEffects`**: Does NOT exist in codebase
- **`@Provides`**: Commented out in `EffectProcessor.java` (lines 54-57), NOT implemented

### Existing annotations (in `src/main/java/org/jiffy/annotations/`):
- `@Uses` - EXISTS
- `@Pure` - EXISTS

---

## 5. Non-existent Documentation Links (Lines 144-148, 160)

### README references:
| Link | Status |
|------|--------|
| `docs/USER_GUIDE.md` | **DOES NOT EXIST** |
| `docs/API.md` | **DOES NOT EXIST** |
| `docs/EFFECT_COMPARISON.md` | **DOES NOT EXIST** |
| `examples/` | **DOES NOT EXIST** |
| `CONTRIBUTING.md` | **DOES NOT EXIST** |

### Note:
`LICENSE` file exists and is correct.

---

## 6. Effect Recovery Example - Minor Style Issue (Lines 127-131)

### README shows:
```java
fetchData()
    .recover(error -> {
        log("Failed to fetch data: " + error);
        return defaultData();
    });
```

### Issue:
The `recover` API signature is correct (`Function<Throwable, A>`), but the example calls `log()` inside the recovery function, which is a side effect. This is misleading given the library's purpose of tracking effects. Consider using `recoverWith` to properly handle the logging effect:

### Suggested alternative:
```java
fetchData()
    .recoverWith(error ->
        Eff.perform(new LogEffect.Error("Failed to fetch data: " + error))
            .map(v -> defaultData())
    );
```

---

## Summary

| Issue | Severity | Location |
|-------|----------|----------|
| EffectHandler signature completely wrong | **HIGH** | Lines 47-55 |
| EffectRuntime construction pattern wrong | **HIGH** | Lines 61-64 |
| LogEffect.Error missing Throwable parameter | **MEDIUM** | Lines 18-22 |
| Non-existent annotations documented | **HIGH** | Lines 112-113 |
| Dead documentation links | **MEDIUM** | Lines 144-148, 160 |
| Side effect in recover example | **LOW** | Lines 127-131 |
