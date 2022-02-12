package net.auoeke.uncheck.intellij;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HighlightFilter implements HighlightInfoFilter {
    private static final Map<Locale, Map<String, Pattern>> messages = new IdentityHashMap<>();

    @Override public boolean accept(@NotNull HighlightInfo info, @Nullable PsiFile file) {
        if (file == null || info.getSeverity().compareTo(HighlightSeverity.ERROR) < 0 || file.isWritable() && !Uncheck.disableChecking(ModuleUtil.findModuleForFile(file))) {
            return true;
        }

        if (info.type == HighlightInfoType.UNHANDLED_EXCEPTION
            || matches(info, "constructor.call.must.be.first.statement", "(this|super)\\(\\)")
            || matches(info, "exception.never.thrown.try", "[.$\\w]+")) {
            return false;
        }

        if (!matches(info, "variable.not.initialized", "[.$\\w]+")) {
            return true;
        }

        var field = file.findElementAt(info.getStartOffset());

        while (true) {
            if (field == null) return true;
            if (field instanceof PsiField) break;

            field = field.getParent();
        }

        var f = (PsiField) field;
        return f.getModifierList().hasModifierProperty(PsiModifier.STATIC) || !Stream.of(f.getContainingClass().getConstructors()).allMatch(constructor -> initialized(constructor, f));
    }

    private static boolean matches(HighlightInfo info, String key, String... arguments) {
        return messages.computeIfAbsent(JavaErrorBundle.getLocale(), l -> new IdentityHashMap<>())
            .computeIfAbsent(key, k -> Pattern.compile(JavaErrorBundle.message(k, arguments))).matcher(info.getDescription()).matches();
    }

    private static boolean initialized(PsiMethod constructor, PsiField field) {
        return HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, constructor.getBody()) || Stream.of(constructor.getBody().getChildren()).anyMatch(child -> {
            if (child instanceof PsiExpressionStatement) {
                var expression = ((PsiExpressionStatement) child).getExpression();

                if (expression instanceof PsiMethodCallExpression) {
                    return initialized(((PsiMethodCallExpression) expression).resolveMethod(), field);
                }
            }

            return false;
        });

    }
}
