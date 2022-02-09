package net.auoeke.uncheck;

import java.util.Objects;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import lombok.SneakyThrows;
import net.auoeke.reflect.Reflect;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class Uncheck implements Plugin {
    @Override public String getName() {
        return "uncheck";
    }

    @Override public void init(JavacTask task, String... args) {}

    @Override public boolean autoStart() {
        return true;
    }

    @SneakyThrows
    private static void disableFlow() {
        var target = Class.forName("com.sun.tools.javac.comp.Flow");

        Transformer transformer = (module, loader, name, type, domain, bytes) -> {
            if (target != type) {
                return bytes;
            }

            var node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);

            var analyzeTree = node.methods.stream().filter(method -> method.name.equals("analyzeTree")).findAny().get();
            var instructions = analyzeTree.instructions;

            for (var instruction : instructions) {
                if (instruction instanceof MethodInsnNode method && method.owner.endsWith("$FlowAnalyzer") && method.name.equals("analyzeTree")) {
                    instructions.remove(instruction);
                    break;
                }
            }

            var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);

            return writer.toByteArray();
        };

        var instrumentation = Objects.requireNonNullElseGet(Reflect.instrumentation(), ByteBuddyAgent::install);
        instrumentation.addTransformer(transformer, true);
        instrumentation.retransformClasses(target);
        instrumentation.removeTransformer(transformer);
    }

    static {
        disableFlow();
    }
}
