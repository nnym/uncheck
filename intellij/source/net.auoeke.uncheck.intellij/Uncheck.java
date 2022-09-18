package net.auoeke.uncheck.intellij;

import java.io.File;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiFile;

public class Uncheck {
    public static boolean enable(PsiFile file) {
        if (file != null && !file.isWritable()) {
            return true;
        }

        var module = ModuleUtil.findModuleForFile(file);
        return module != null && Stream.of(CompilerConfiguration.getInstance(module.getProject()).getAnnotationProcessingConfiguration(module).getProcessorPath().split(File.pathSeparator))
            .map(File::new)
            .filter(path -> path.exists() && path.getName().endsWith(".jar"))
            .anyMatch(jar -> {
                try (var jarFile = new JarFile(jar)) {
                    return jarFile.getEntry("net.auoeke.uncheck") != null;
                }
            });
    }
}
