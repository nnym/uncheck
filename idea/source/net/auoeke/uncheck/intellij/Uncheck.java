package net.auoeke.uncheck.intellij;

import java.io.File;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiElement;

public class Uncheck {
    public static boolean disableChecking(Module module) {
        return module != null && Stream.of(CompilerConfiguration.getInstance(module.getProject()).getAnnotationProcessingConfiguration(module).getProcessorPath().split(File.pathSeparator))
            .map(File::new)
            .filter(path -> path.exists() && path.getName().endsWith(".jar"))
            .anyMatch(jar -> {
                try (var file = new JarFile(jar)) {
                    return file.getEntry("net.auoeke.uncheck") != null;
                }
            });
    }

    public static boolean disableChecking(PsiElement element) {
        return element != null && disableChecking(ModuleUtil.findModuleForPsiElement(element));
    }
}
