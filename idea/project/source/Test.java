import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Test {
    public static void main(String... args) {
        evilMethod("output.txt", "I know what I'm doing.");
    }

    public static void evilMethod(String file, String contents) throws IOException {
        Files.writeString(Path.of(file), contents);
    }
}
