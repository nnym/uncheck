package net.auoeke.uncheck.intellij;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HighlightFilter implements HighlightInfoFilter {
    private static final Map<Locale, Map<String, Pattern>> messages = new IdentityHashMap<>();

    @Override public boolean accept(@NotNull HighlightInfo info, @Nullable PsiFile file) {
        if (file == null || info.getSeverity().compareTo(HighlightSeverity.ERROR) < 0 || !Uncheck.disableChecking(ModuleUtil.findModuleForFile(file))) {
            return true;
        }

        return info.type != HighlightInfoType.UNHANDLED_EXCEPTION
               && !message("constructor.call.must.be.first.statement", "(this|super)\\(\\)").matcher(info.getDescription()).matches()
               && !message("exception.never.thrown.try", "[.A-Za-z]+").matcher(info.getDescription()).matches();
    }

    private static Pattern message(String key, String... arguments) {
        return messages.computeIfAbsent(JavaErrorBundle.getLocale(), l -> new IdentityHashMap<>()).computeIfAbsent(key, k -> Pattern.compile(JavaErrorBundle.message(k, arguments)));
    }
}
