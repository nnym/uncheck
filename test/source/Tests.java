import java.io.File;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

class Tests {
    @Test
    void test() {
        GradleRunner.create().withProjectDir(new File("test/project")).withArguments("-s", "clean", "compileJava").forwardOutput().build();
    }
}
