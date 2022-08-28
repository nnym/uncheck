package net.auoeke.uncheck.intellij;

import com.intellij.codeInsight.CustomExceptionHandler;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnconditionalExceptionHandler extends CustomExceptionHandler {
    @Override public boolean isHandled(@Nullable PsiElement element, @NotNull PsiClassType exceptionType, PsiElement topElement) {
        return element != null && Uncheck.enable(ModuleUtil.findModuleForPsiElement(element));
    }
}
