package net.auoeke.uncheck;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.instrument.Instrumentation;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Flow;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import lombok.SneakyThrows;
import net.auoeke.reflect.ClassDefiner;
import net.auoeke.reflect.ClassTransformer;
import net.auoeke.reflect.Classes;
import net.auoeke.reflect.Invoker;
import net.auoeke.reflect.Methods;
import net.auoeke.reflect.Modules;
import net.auoeke.reflect.Reflect;
import net.gudenau.lib.unsafe.Unsafe;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class Uncheck implements Plugin, Opcodes {
    private static final Instrumentation instrumentation = Reflect.instrument().value();
    private static final Class<?> util;

    @Override public String getName() {
        return "uncheck";
    }

    @SneakyThrows
    @Override public void init(JavacTask task, String... args) {
        var context = ((BasicJavacTask) task).getContext();
        Invoker.findStatic(util, "context", void.class, Context.class).invoke(context);

        try {
            JavacProcessingEnvironment.instance(context).getMessager().printMessage(Diagnostic.Kind.NOTE, "uncheck version %s: %s".formatted(
                Uncheck.class.getPackage().getSpecificationVersion(),
                Uncheck.class.getProtectionDomain().getCodeSource().getLocation()
            ));
        } catch (NullPointerException no) {}
    }

    @Override public boolean autoStart() {
        return true;
    }

    private static MethodInsnNode invoke(boolean isInterface, int opcode, String owner, String name, Class<?> returnType, Class<?>... parameterTypes) {
        return new MethodInsnNode(opcode, owner, name, Type.getMethodDescriptor(Type.getType(returnType), Stream.of(parameterTypes).map(Type::getType).toArray(Type[]::new)), isInterface);
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(method -> method.name.equals(name)).findAny().get();
    }

    @Transform(Flow.class)
    private static void disableFlowAndCaptureAnalysis(ClassNode node) {
        var analyzeTree = method(node, "analyzeTree");
        var instructions = analyzeTree.instructions;

        for (var instruction : instructions) {
            if (instruction instanceof MethodInsnNode method && method.owner.matches(".+\\$(FlowAnalyzer|CaptureAnalyzer)$") && method.name.equals("analyzeTree")) {
                instructions.remove(instruction);
            }
        }
    }

    @Transform(Attr.class)
    private static void allowNonConstructorFirstStatement(ClassNode node) {
        var checkFirstConstructorStat = method(node, "checkFirstConstructorStat");

        for (var instruction : checkFirstConstructorStat.instructions) {
            if (instruction instanceof VarInsnNode var && var.var == 3) {
                ((JumpInsnNode) instruction.getNext()).setOpcode(GOTO);

                break;
            }
        }
    }

    @Transform(Attr.class)
    private static void allowDefinitelyAssignedFinalFieldReassignment(ClassNode node) {
        var checkAssignable = method(node, "checkAssignable");

        for (var instruction : checkAssignable.instructions) {
            if (instruction instanceof MethodInsnNode method && method.name.equals("CantAssignValToFinalVar")) {
                checkAssignable.instructions.insertBefore(instruction, buildList(builder -> {
                    var resume = new Label();
                    builder.visitVarInsn(ALOAD, 2);
                    builder.visitVarInsn(ALOAD, 4);
                    builder.instructions.add(invoke(false, INVOKESTATIC, Util.INTERNAL_NAME, "allowFinalFieldReassignment", boolean.class, Symbol.VarSymbol.class, Env.class));
                    builder.visitJumpInsn(IFEQ, resume);
                    builder.visitInsn(RETURN);
                    builder.visitLabel(resume);
                }));

                break;
            }
        }
    }

    @Transform(Flow.AssignAnalyzer.class)
    private static void allowUnknownFinalFieldAssignment(ClassNode node) {
        var letInit = node.methods.stream().filter(method -> method.name.equals("letInit") && method.desc.contains(";L")).findAny().get();

        for (var instruction : letInit.instructions) {
            if (instruction instanceof MethodInsnNode method && method.name.equals("VarMightAlreadyBeAssigned") || instruction instanceof FieldInsnNode field && field.name.equals("errKey")) {
                letInit.instructions.insertBefore(instruction, buildList(builder -> {
                    var resume = new Label();
                    builder.visitVarInsn(ALOAD, 1);
                    builder.visitVarInsn(ALOAD, 2);
                    builder.instructions.add(invoke(false, INVOKESTATIC, Util.INTERNAL_NAME, "allowFinalFieldReassignment", boolean.class, JCDiagnostic.DiagnosticPosition.class, Symbol.VarSymbol.class));
                    builder.visitJumpInsn(IFEQ, resume);
                    builder.visitInsn(RETURN);
                    builder.visitLabel(resume);
                }));
            }
        }
    }

    private static InsnList buildList(Consumer<MethodNode> builder) {
        var method = new MethodNode();
        builder.accept(method);

        return method.instructions;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Transform {
        Class<?> value();
    }

    static {
        Modules.open(Plugin.class.getModule());
        var loader = Plugin.class.getClassLoader();

        if (Classes.findLoadedClass(loader, Util.NAME) == null) {
            ClassDefiner.make().loader(loader).classFile(Util.INTERNAL_NAME).define();
        }

        Methods.of(Uncheck.class)
            .filter(method -> method.isAnnotationPresent(Transform.class))
            .collect(Collectors.groupingBy(method -> method.getAnnotation(Transform.class).value()))
            .forEach((target, methods) -> {
                ClassTransformer transformer = (module, __, name, type, domain, bytes) -> {
                    if (type != target) {
                        return null;
                    }

                    try {
                        var node = new ClassNode();
                        new ClassReader(bytes).accept(node, 0);

                        for (var method : methods) {
                            method.invoke(null, node);
                        }

                        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                        node.accept(writer);

                        return writer.toByteArray();
                    } catch (Throwable trouble) {
                        trouble.printStackTrace();

                        throw Unsafe.throwException(trouble);
                    }
                };

                instrumentation.addTransformer(transformer, true);

                try {
                    instrumentation.retransformClasses(target);
                } catch (Throwable trouble) {
                    throw Unsafe.throwException(trouble);
                } finally {
                    instrumentation.removeTransformer(transformer);
                }
            });

        util = Classes.load(loader, Util.NAME);
    }
}
