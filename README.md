# Jiffy - Algebraic Effects for Java

<img src="jiffy.png" alt="jiffy" width="100"/> 

Jiffy is a lightweight library that brings algebraic effects to Java with compile-time effect checking through annotations.

## Features

- 🔍 **Compile-time effect checking** - Catch missing effect declarations during compilation
- 📝 **Annotation-based** - Use familiar Java annotations to declare effects
- 🎯 **Type-safe** - Effects are visible in method signatures
- 🔧 **Extensible** - Easy to add new effects and handlers
- 🏗️ **Spring-friendly** - Integrates well with Spring Boot applications
- ⚡ **Minimal overhead** - Efficient runtime with direct handler dispatch

## Demo Project

This demo shows how to use jiffy in a SpringBoot application.
It also has a nice introduction to core concepts used:

https://github.com/thma/jiffy-clean-architecture/

## Quick Start

### Define an Effect

```java
public sealed interface LogEffect extends Effect<Void> {
    record Info(String message) implements LogEffect {}
    record Error(String message) implements LogEffect {}
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
    public <T> T handle(LogEffect effect) {
        switch (effect) {
            case LogEffect.Info(var message) -> System.out.println("[INFO] " + message);
            case LogEffect.Error(var message) -> System.err.println("[ERROR] " + message);
        }
        return null;  // LogEffect returns Void
    }
}
```

### Run with Runtime

```java
EffectRuntime runtime = EffectRuntime.builder()
    .withHandler(LogEffect.class, new LogHandler())
    .withHandler(DatabaseEffect.class, new DatabaseHandler())
    .build();

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
// Simple recovery with a fallback value
fetchData()
    .recover(error -> defaultData());

// Recovery with logging (using recoverWith for proper effect handling)
fetchData()
    .recoverWith(error ->
        Eff.perform(new LogEffect.Error("Failed: " + error.getMessage()))
            .map(v -> defaultData())
    );
```

### Sequential Composition
```java
Eff.sequence(
    validateInput(data),
    saveToDatabase(data),
    notifyUser(userId)
);
```

## Building from Source

```bash
git clone https://github.com/yourusername/jiffy.git
cd jiffy
mvn clean install
```

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

## License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by algebraic effects in Haskell
- Similar projects: [Jeff](https://github.com/lpld/jeff)
- Built for the Java community with ❤️
