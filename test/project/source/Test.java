import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.jar.JarFile;

public class Test {
    static final Unsafe U = (Unsafe) MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup()).findStaticVarHandle(Unsafe.class, "theUnsafe", Unsafe.class).get();

    public static void main(String... args) {
        evilMethod("output.txt", "I know what I'm doing.");
    }

    public static void evilMethod(String file, String contents) {
        Files.writeString(Path.of(file), contents);
    }

    public static void evilMethod() {
        Files.writeString(Path.of("file.txt"), "text");
        throw new IOException();
    }
}