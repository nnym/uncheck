This project comprises a Java compiler plugin and an IntelliJ plugin.
The former modifies javac and the latter fixes the IDE's error reporting.

## Features
- no exception checking
- no restriction on the first statement of a constructor
- reassigned variable usage in lambdas and inner classes
- final field reassignment in initializers

## Example
These plugins extend Java to make the following example legal.
```java
class Example {
    final String a, b;

    Example(String a, String b) {
        this.a = a = "not effectively final";
        this.b = b;
        Runnable capturingRunnable = () -> System.out.println(a);
        Runnable throwingRunnable = Thread.currentThread()::join;
    }

    Example(String s) {
        var ab = s.split(":");
        this(ab[0], ab[1]);
    }

    void evilMethod() {
        Files.writeString(Path.of("file.txt"), "text");
        throw new IOException();
    }
}
```

## Using the IntelliJ plugin
[JetBrains Marketplace](https://plugins.jetbrains.com/plugin/18575-uncheck)

[GitHub releases](https://github.com/auoeke/uncheck/releases): download and [install manually](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk)

## Using the compiler plugin
It requires Java 17 or later and is hosted as `net.auoeke:uncheck` at https://maven.auoeke.net.

### Gradle
```groovy
repositories {
    maven {url = "https://maven.auoeke.net"}
}

dependencies {
    annotationProcessor("net.auoeke:uncheck:latest.release")
}
```

![](idea/resources/icon.png)
