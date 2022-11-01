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
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.util.PsiUtil;

public class HighlightFilter implements HighlightInfoFilter {
    private static final String ID = "[.$\\w]+";
    private static final Map<Locale, Map<String, Pattern>> messages = new IdentityHashMap<>();

    @Override public boolean accept(HighlightInfo info, PsiFile file) {
        if (info.getSeverity().compareTo(HighlightSeverity.ERROR) >= 0 && Uncheck.enable(file)) {
            if (info.type == HighlightInfoType.UNHANDLED_EXCEPTION
                || matches(info, "constructor.call.must.be.first.statement", "(this|super)\\(\\)")
                || matches(info, "exception.never.thrown.try", ID)
                || matches(info, "resource.variable.must.be.final")
                || matches(info, "guarded.pattern.variable.must.be.final")
            ) {
                return false;
            }

            var element = file.findElementAt(info.getStartOffset());

            if (matches(info, "variable.must.be.final.or.effectively.final", ID) || matches(info, "lambda.variable.must.be.final")) {
                var parent = element.getParent();
                var grandparent = parent.getParent();

                if (!(grandparent instanceof PsiAssignmentExpression assignment && parent == assignment.getLExpression())) {
                    if (grandparent instanceof PsiUnaryExpression unary) {
                        var type = unary.getOperationTokenType();
                        return type == ElementType.PLUSPLUS || type == ElementType.MINUSMINUS;
                    }

                    return false;
                }
            } else if (matches(info, "variable.not.initialized", ID)) {
                while (true) {
                    if (element == null) {
                        return true;
                    }

                    if (element instanceof PsiField field) {
                        if (field.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
                            return true;
                        }

                        var constructors = field.getContainingClass().getConstructors();
                        return constructors.length == 0 || !Stream.of(constructors).allMatch(constructor -> initialized(constructor, field));
                    }

                    element = element.getParent();
                }
            } else if (matches(info, "assignment.to.final.variable", ID) || matches(info, "variable.already.assigned", ID) /*|| matches(info, "variable.assigned.in.loop", ID)*/) {
                var initializer = PsiUtil.findEnclosingConstructorOrInitializer(element);
                var parent = element.getParent();
                var declaration = parent.getParent() instanceof PsiReference reference ? reference.resolve() : ((PsiReference) parent).resolve();

                return !(declaration instanceof PsiField field
	                && initializer != null
                    && initializer.hasModifier(JvmModifier.STATIC) == field.hasModifier(JvmModifier.STATIC)
                    && !PsiUtil.isConstantExpression(field.getInitializer())
                    && PsiUtil.findEnclosingConstructorOrInitializer(LambdaUtil.getContainingClassOrLambda(element)) != initializer
                );
            }
        }

        return true;
    }

    private static boolean initialized(PsiMethod constructor, PsiField field) {
        if (constructor == null || constructor.getBody() == null) {
            return false;
        }

        return HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, constructor.getBody())
               || Stream.of(constructor.getBody().getChildren())
                   .filter(PsiExpressionStatement.class::isInstance)
                   .map(statement -> ((PsiExpressionStatement) statement).getExpression())
                   .filter(PsiMethodCallExpression.class::isInstance)
                   .map(call -> ((PsiMethodCallExpression) call).resolveMethod())
                   .filter(c -> c != constructor)
                   .anyMatch(c -> initialized(c, field));
    }

    private static boolean matches(HighlightInfo info, String key, String... arguments) {
        return messages.computeIfAbsent(JavaErrorBundle.getLocale(), l -> new IdentityHashMap<>())
            .computeIfAbsent(key, k -> Pattern.compile(JavaErrorBundle.message(k, arguments)))
            .matcher(info.getDescription())
            .matches();
    }
}
