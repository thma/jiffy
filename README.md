# Jiffy - Algebraic Effects for Java

Jiffy is a lightweight library that brings algebraic effects to Java with compile-time effect checking through annotations.

## Features

- 🔍 **Compile-time effect checking** - Catch missing effect declarations during compilation
- 📝 **Annotation-based** - Use familiar Java annotations to declare effects
- 🎯 **Type-safe** - Effects are visible in method signatures
- 🔧 **Extensible** - Easy to add new effects and handlers
- 🏗️ **Spring-friendly** - Integrates well with Spring Boot applications
- ⚡ **Minimal overhead** - Efficient runtime with direct handler dispatch

## Quick Start

### Define an Effect

```java
public sealed interface LogEffect extends Effect<Void> {
    record Info(String message) implements LogEffect {}
    record Error(String message, Throwable cause) implements LogEffect {}
}
```

### Use Effects in Your Code

```java
import org.jiffy.annotations.Uses;
import org.jiffy.core.Eff;

public class UserService {

    @Uses({LogEffect.class, DatabaseEffect.class})
    public Eff<User> findUser(Long id) {
        return Eff.perform(new LogEffect.Info("Finding user " + id))
            .flatMap(ignored ->
                Eff.perform(new DatabaseEffect.Query("SELECT * FROM users WHERE id = " + id))
            )
            .map(result -> parseUser(result));
    }
}
```

### Handle Effects

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

### Run with Runtime

```java
EffectRuntime runtime = new EffectRuntime()
    .with(LogEffect.class, new LogHandler())
    .with(DatabaseEffect.class, new DatabaseHandler());

User user = findUser(123L).runWith(runtime);
```

## Compile-Time Checking

The annotation processor validates that all effects used in a method are declared:

```java
@Uses({LogEffect.class})  // Missing DatabaseEffect!
public Eff<User> findUser(Long id) {
    return Eff.perform(new DatabaseEffect.Query("..."))  // Compile error!
        .map(this::parseUser);
}
```

## Installation

### Maven

```xml
<dependency>
    <groupId>org.jiffy</groupId>
    <artifactId>jiffy</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```gradle
implementation 'org.jiffy:jiffy:1.0.0-SNAPSHOT'
```

## Core Concepts

### Effects
Effects represent side effects as data. They extend the `Effect<T>` interface where `T` is the return type.

### Eff Monad
`Eff<T>` is a monadic type that represents a computation that may perform effects and produce a value of type `T`.

### Effect Handlers
Handlers interpret effects. They implement `EffectHandler<E>` where `E` is the effect type.

### Annotations
- `@Uses` - Declares which effects a method may use
- `@Pure` - Marks methods as effect-free
- `@UncheckedEffects` - Allows specific effects without declaration
- `@Provides` - Indicates a method provides effect handlers

## Advanced Features

### Parallel Effects
```java
Eff.parallel(
    fetchUserData(userId),
    fetchUserOrders(userId)
).map(pair -> new UserProfile(pair.getFirst(), pair.getSecond()));
```

### Effect Recovery
```java
fetchData()
    .recover(error -> {
        log("Failed to fetch data: " + error);
        return defaultData();
    });
```

### Sequential Composition
```java
Eff.sequence(
    validateInput(data),
    saveToDatabase(data),
    notifyUser(userId)
);
```

## Documentation

- [User Guide](docs/USER_GUIDE.md)
- [API Documentation](docs/API.md)
- [Effect Comparison](docs/EFFECT_COMPARISON.md)
- [Examples](examples/)

## Building from Source

```bash
git clone https://github.com/yourusername/jiffy.git
cd jiffy
mvn clean install
```

## Contributing

Contributions are welcome! Please read our [Contributing Guide](CONTRIBUTING.md) for details.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by algebraic effects in OCaml, Koka, and Haskell
- Similar projects: [Jeff](https://github.com/lpld/jeff), ZIO, Arrow-kt
- Built for the Java community with ❤️