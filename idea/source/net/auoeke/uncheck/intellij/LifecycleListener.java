package net.auoeke.uncheck.intellij;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Consumer;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.ide.AppLifecycleListener;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import sun.misc.Unsafe;

public class LifecycleListener implements AppLifecycleListener, Opcodes {
    @Override public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        var unsafe = (Unsafe) MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup()).findStaticGetter(Unsafe.class, "theUnsafe", Unsafe.class).invoke();
        var lookup = (MethodHandles.Lookup) unsafe.getObject(MethodHandles.Lookup.class, unsafe.staticFieldOffset(MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP")));
        var uncheckBytecode = Uncheck.class.getResourceAsStream("Uncheck.class").readAllBytes();
        lookup.bind(HighlightUtil.class.getClassLoader(), "defineClass", MethodType.methodType(Class.class, String.class, byte[].class, int.class, int.class)).invoke(
            Uncheck.class.getName(), uncheckBytecode, 0, uncheckBytecode.length
        );

        var instrumentation = ByteBuddyAgent.install();

        if (true) return;

        // Make checked exceptions catchable.
        transform(instrumentation, HighlightUtil.class, node -> returnEmptyListIfNoChecking(method(node, "checkExceptionThrownInTry")));

        // Workaround for IDEA-288417.
        transform(instrumentation, ExceptionUtil.class, node -> returnEmptyListIfNoChecking(method(node, "getOwnUnhandledExceptions")));

        // Allow the first statement to not be a constructor invocation.
        transform(instrumentation, HighlightMethodUtil.class, node -> {
            var method = method(node, "checkConstructorCallProblems");
            var instructions = method.instructions;
            var instruction = instructions.getLast();

            while (instruction.getOpcode() != ARETURN) {
                instruction = instruction.getPrevious();
            }

            instructions.insertBefore(instruction, ifNoChecking(new FrameNode(F_SAME1, 0, null, 1, new Object[]{Type.getReturnType(method.desc).getInternalName()}), i -> {
                i.add(new InsnNode(POP));
                i.add(new InsnNode(ACONST_NULL));
            }));
        });
    }

    private static void transform(Instrumentation instrumentation, Class<?> target, Consumer<ClassNode> transformer) {
        Transformer t = (module, loader, name, type, domain, bytes) -> {
            if (type != target) {
                return bytes;
            }

            var node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            transformer.accept(node);
            var writer = new ClassWriter(0);
            node.accept(writer);

            return writer.toByteArray();
        };

        instrumentation.addTransformer(t, true);
        instrumentation.retransformClasses(target);
        instrumentation.removeTransformer(t);
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(method -> method.name.equals(name)).findAny().get();
    }

    private static InsnList ifNoChecking(FrameNode frame, Consumer<InsnList> action) {
        var insertion = new InsnList();
        var resume = new LabelNode();
        insertion.add(new VarInsnNode(ALOAD, 0));
        insertion.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Uncheck.class), "disableChecking", "(Lcom/intellij/psi/PsiElement;)Z", false));
        insertion.add(new JumpInsnNode(IFEQ, resume));
        action.accept(insertion);
        insertion.add(resume);
        insertion.add(frame);

        return insertion;
    }

    private static void returnEmptyListIfNoChecking(MethodNode method) {
        method.instructions.insertBefore(method.instructions.getFirst(), ifNoChecking(new FrameNode(F_SAME, 0, null, 0, null), i -> {
            i.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(List.class), "of", "()Ljava/util/List;", true));
            i.add(new InsnNode(ARETURN));
        }));
    }
}
