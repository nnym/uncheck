package net.auoeke.uncheck.intellij;

import java.io.File;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiElement;

public class Uncheck {
    public static boolean disableChecking(PsiElement element) {
        if (element == null) {
            return false;
        }

        var module = ModuleUtil.findModuleForPsiElement(element);
        return module != null && Stream.of(CompilerConfiguration.getInstance(element.getProject()).getAnnotationProcessingConfiguration(module).getProcessorPath().split(File.pathSeparator))
            .filter(path -> path.endsWith(".jar"))
            .anyMatch(path -> new JarFile(path).getEntry("net.auoeke.uncheck") != null);
    }
}
