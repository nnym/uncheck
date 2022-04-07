import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import sun.misc.Unsafe;

public class Test {
    static final Unsafe U = (Unsafe) MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup()).findStaticVarHandle(Unsafe.class, "theUnsafe", Unsafe.class).get();
    static final Object U1 = U = (Unsafe) U.getObject(Unsafe.class, U.staticFieldOffset(Unsafe.class.getDeclaredField("theUnsafe")));
    static final JarFile O = new JarFile("");
    static final int a;
    final int i = 12;

    public Test() {
        System.out.println("pre-Object::new");
        super();
        System.out.println("post-Object::new");
        this.i = 14;
    }

    public static void main(String... args) {
        evilMethod("output.txt", "I know what I'm doing.");
    }

    public static void evilMethod(String file, String contents) {
        Files.writeString(Path.of(file), contents);
    }

    static {
        Runnable r = () -> a = 3;
        O = null;
        a = 0;

        for (var b = 0; b < 3; b++) {
            // a += b;
        }
    }
}

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
