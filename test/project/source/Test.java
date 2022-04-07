import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import sun.misc.Unsafe;

public class Test {
    static final Unsafe U = (Unsafe) MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup()).findStaticVarHandle(Unsafe.class, "theUnsafe", Unsafe.class).get();
    static final Object U1 = U = (Unsafe) U.getObject(Unsafe.class, U.staticFieldOffset(Unsafe.class.getDeclaredField("theUnsafe")));
    static final Field field = Test.class.getDeclaredField("U");
    static final int a;

    public Test() {
        log("pre-Object::new");
        super();
        log("post-Object::new");
    }

    public static void main(String... args) {
        evilMethod("output.txt", "I know what I'm doing.");
        new Test();
    }

    public static void evilMethod(String file, String contents) {
        Files.writeString(Path.of(file), contents);
    }

    private static void log(Object output) {
        System.out.println(output);
    }

    static {
        // todo: should not be allowed; fix
        Runnable r = () -> a = 3;

        log(field);
        field = null;
        log(field);

        a = 0;
        log(a);

        for (var b = 0; b < 3; b++) {
            a += b;
            log(a);
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
