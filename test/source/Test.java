import java.io.IOException;

public class Test {
    public static void main(String... args) {
        if (true) {
            throw new IOException();
        }

        System.out.println("test");
    }
}
