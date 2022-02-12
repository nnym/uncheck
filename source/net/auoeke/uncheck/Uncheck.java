package net.auoeke.uncheck;

import java.lang.instrument.Instrumentation;
import java.util.Objects;
import java.util.function.Consumer;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import lombok.SneakyThrows;
import net.auoeke.reflect.Reflect;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class Uncheck implements Plugin, Opcodes {
    @Override public String getName() {
        return "uncheck";
    }

    @Override public void init(JavacTask task, String... args) {}

    @Override public boolean autoStart() {
        return true;
    }

    @SneakyThrows
    private static void transform(Instrumentation instrumentation, Class<?> target, Consumer<ClassNode> transformer) {
        Transformer t = (module, loader, name, type, domain, bytes) -> {
            if (target != type) {
                return bytes;
            }

            var node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            transformer.accept(node);
            var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
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

    @SneakyThrows
    private static void disableFlowAndCaptureAnalysis(Instrumentation instrumentation) {
        transform(instrumentation, Class.forName("com.sun.tools.javac.comp.Flow"), node -> {
            var analyzeTree = method(node, "analyzeTree");
            var instructions = analyzeTree.instructions;

            for (var instruction : instructions) {
                if (instruction instanceof MethodInsnNode method && method.owner.matches(".+\\$(FlowAnalyzer|CaptureAnalyzer)$") && method.name.equals("analyzeTree")) {
                    instructions.remove(instruction);
                }
            }
        });
    }

    @SneakyThrows
    private static void allowNonConstructorFirstStatement(Instrumentation instrumentation) {
        transform(instrumentation, Class.forName("com.sun.tools.javac.comp.Attr"), node -> {
            var checkFirstConstructorStat = method(node, "checkFirstConstructorStat");

            for (var instruction : checkFirstConstructorStat.instructions) {
                if (instruction instanceof VarInsnNode var && var.var == 3) {
                    ((JumpInsnNode) instruction.getNext()).setOpcode(GOTO);

                    break;
                }
            }
        });
    }

    static {
        var instrumentation = Objects.requireNonNullElseGet(Reflect.instrumentation(), ByteBuddyAgent::install);
        disableFlowAndCaptureAnalysis(instrumentation);
        allowNonConstructorFirstStatement(instrumentation);
    }
}
