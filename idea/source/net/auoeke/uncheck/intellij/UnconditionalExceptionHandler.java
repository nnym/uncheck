package net.auoeke.uncheck.intellij;

import java.io.File;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import com.intellij.codeInsight.CustomExceptionHandler;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnconditionalExceptionHandler extends CustomExceptionHandler {
    @Override public boolean isHandled(@Nullable PsiElement element, @NotNull PsiClassType exceptionType, PsiElement topElement) {
        if (element != null) {
            var module = ModuleUtil.findModuleForPsiElement(element);
            return module != null && Stream.of(CompilerConfiguration.getInstance(element.getProject()).getAnnotationProcessingConfiguration(module).getProcessorPath().split(File.pathSeparator))
                .filter(path -> path.endsWith(".jar"))
                .anyMatch(path -> jar(path).getEntry("net.auoeke.uncheck") != null);
        }

        return false;
    }

    @SneakyThrows
    private static JarFile jar(String path) {
        return new JarFile(new File(path));
    }
}
