package net.auoeke.uncheck.intellij;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import com.intellij.codeInsight.ExceptionUtil;
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

        // Make checked exceptions catchable.
        transform(instrumentation, HighlightUtil.class, node -> returnEmptyListIfNoChecking(node.methods.stream().filter(m -> m.name.equals("checkExceptionThrownInTry")).findAny().get()));

        // Workaround for IDEA-288417.
        transform(instrumentation, ExceptionUtil.class, node -> returnEmptyListIfNoChecking(node.methods.stream().filter(m -> m.name.equals("getOwnUnhandledExceptions")).findAny().get()));
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

    private static void returnEmptyListIfNoChecking(MethodNode method) {
        var insertion = new InsnList();
        var resume = new LabelNode();
        insertion.add(new VarInsnNode(ALOAD, 0));
        insertion.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Uncheck.class), "disableChecking", "(Lcom/intellij/psi/PsiElement;)Z", false)); // I
        insertion.add(new JumpInsnNode(IFEQ, resume));
        insertion.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(List.class), "of", "()Ljava/util/List;", true)); // List
        insertion.add(new InsnNode(ARETURN));
        insertion.add(resume);

        var parameterTypes = Stream.of(Type.getMethodType(method.desc).getArgumentTypes()).map(Type::getInternalName).toArray();
        insertion.add(new FrameNode(F_FULL, parameterTypes.length, parameterTypes, 0, new Object[0]));

        method.instructions.insertBefore(method.instructions.getFirst(), insertion);
    }
}
