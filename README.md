This project comprises a Java compiler plugin and an IntelliJ plugin.
The former modifies javac and the latter fixes the IDE's error reporting.

### Features
- No exception checking,
- no restriction on the first statement of a constructor and
- reassigned variable usage in lambdas and inner classes.

### Example
Together these plugins enable code like
```java
class Example {
    final String a, b;

    Example(String a, String b) {
        this.a = a = "not effectively final";
        this.b = b;
        Runnable r = () -> System.out.println(a);
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
to be compiled successfully.

### Using the IntelliJ plugin
#### [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/18575-uncheck)
#### [GitHub release](https://github.com/auoeke/uncheck/releases)
Download and [install manually](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk).

### Using the compiler plugin
It currently requires Java 17 or later and is hosted as `net.auoeke:uncheck` at https://maven.auoeke.net.

#### Gradle
```groovy
repositories {
    maven {url = "https://maven.auoeke.net"}
}

dependencies {
    annotationProcessor("net.auoeke:uncheck:latest.release")
}
```

![](idea/resources/icon.png)
