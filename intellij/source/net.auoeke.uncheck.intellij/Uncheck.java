package net.auoeke.uncheck.intellij;

import java.io.File;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.module.Module;

public class Uncheck {
    public static boolean enable(Module module) {
        return module != null && Stream.of(CompilerConfiguration.getInstance(module.getProject()).getAnnotationProcessingConfiguration(module).getProcessorPath().split(File.pathSeparator))
            .map(File::new)
            .filter(path -> path.exists() && path.getName().endsWith(".jar"))
            .anyMatch(jar -> {
                try (var file = new JarFile(jar)) {
                    return file.getEntry("net.auoeke.uncheck") != null;
                }
            });
    }
}
