This repository contains a Java compiler plugin and an IntelliJ plugin.
The former disables exception checking in javac and the latter disables exception checking in the IDE.

Together these plugins enable code like
```java
public static void evilMethod() {
    Files.writeString(Path.of("file.txt"), "text");
    throw new IOException();
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
    annotationProcessor("net.auoeke:uncheck:0.0.1")
}
```

![](idea/resources/icon.png)
