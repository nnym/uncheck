package net.auoeke.uncheck;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Flow;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import net.auoeke.reflect.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import javax.tools.Diagnostic;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Uncheck implements Plugin, Opcodes {
	private static final boolean debug = Boolean.getBoolean("uncheck.debug");
	private static final Class<?> util;

    @Override public String getName() {
        return "uncheck";
    }

    @Override public void init(JavacTask task, String... args) {
        var context = ((BasicJavacTask) task).getContext();
        Invoker.findStatic(util, "context", void.class, Context.class).invoke(context);

        var location = Classes.location(Uncheck.class);

        if (location != null && debug) {
            JavacProcessingEnvironment.instance(context).getMessager().printMessage(Diagnostic.Kind.NOTE, "uncheck %s: %s; modified %tF %3$tT".formatted(
                Uncheck.class.getPackage().getSpecificationVersion(),
                location.getFile(),
                location.openConnection().getLastModified()
            ));
        }
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

    @Transform(name = {"com.sun.tools.javac.comp.Flow$CaptureAnalyzer", "com.sun.tools.javac.comp.Flow$FlowAnalyzer"})
    private static void disableCaptureAndFlowAnalyzers(ClassNode node) {
        var analyzeTree = method(node, "analyzeTree");
        analyzeTree.instructions.clear();
        analyzeTree.tryCatchBlocks.clear();
        analyzeTree.visitInsn(RETURN);
    }

    @Transform(Attr.class)
    private static void acceptMethodReferencesWithIncompatibleThrownTypes(ClassNode node) {
        var checkReferenceCompatible = method(node, "checkReferenceCompatible");
        checkReferenceCompatible.instructions.insertBefore(checkReferenceCompatible.instructions.getFirst(), buildList(builder -> {
            builder.visitInsn(ICONST_1);
            builder.visitVarInsn(ISTORE, 5);
        }));
    }

    @Transform(Attr.class)
    private static void allowNonConstructorFirstStatement(ClassNode node) {
		if (!isJavaSupported(22)) {
			var checkFirstConstructorStat = method(node, "checkFirstConstructorStat");

			for (var instruction : checkFirstConstructorStat.instructions) {
				if (instruction instanceof VarInsnNode var && var.var == 3) {
					((JumpInsnNode) instruction.getNext()).setOpcode(GOTO);
					break;
				}
			}
		}
    }

    @Transform(Attr.class)
    private static void allowDefinitelyAssignedFinalFieldReassignment(ClassNode node) {
        var checkAssignable = method(node, "checkAssignable");

        for (var instruction : checkAssignable.instructions) {
            if (instruction instanceof MethodInsnNode method && method.name.equals("CantAssignValTo" + (isJavaSupported(21) ? "" : "Final") + "Var")) {
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
    private static void allowPossiblyAssignedFinalFieldAssignment(ClassNode node) {
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

	@Transform(Preview.class)
	private static void enablePreview(ClassNode node) {
		var isEnabled = method(node, "isEnabled");
		isEnabled.instructions.clear();
		isEnabled.instructions.add(buildList(m -> {
			m.visitInsn(ICONST_1);
			m.visitInsn(IRETURN);
		}));
	}

	@Transform(Preview.class)
	private static void ignorePreviewVersion(ClassNode node) {
		var checkSourceLevel = method(node, "checkSourceLevel");

		for (var i : checkSourceLevel.instructions) {
			if (i instanceof MethodInsnNode m && m.name.equals("allowedInSource")) {
				checkSourceLevel.instructions.insertBefore(m, buildList(mn -> {
					var resume = new Label();
					mn.visitVarInsn(ALOAD, 2);
					mn.instructions.add(invoke(false, INVOKESTATIC, Util.INTERNAL_NAME, "enablePreview", boolean.class, Source.Feature.class));
					mn.visitJumpInsn(IFEQ, resume);
					mn.visitInsn(RETURN);
					mn.visitLabel(resume);
				}));

				break;
			}
		}
	}

	@Transform(com.sun.tools.javac.jvm.ClassWriter.class)
	private static void disablePreviewVersion(ClassNode node) {
		var writeClassFile = method(node, "writeClassFile");

		for (var instruction : writeClassFile.instructions) {
			if (instruction instanceof MethodInsnNode method && method.name.equals("usesPreview")) {
				writeClassFile.instructions.insertBefore(method, new InsnNode(POP));
				writeClassFile.instructions.insertBefore(method, new InsnNode(POP));
				writeClassFile.instructions.set(method, new InsnNode(ICONST_0));
				break;
			}
		}
	}

    private static InsnList buildList(Consumer<MethodNode> builder) {
        var method = new MethodNode();
        builder.accept(method);

        return method.instructions;
    }

	static boolean isJavaSupported(int version) {
		return Runtime.version().compareTo(Runtime.Version.parse(Integer.toString(version))) >= 0;
	}

    static {
        Modules.open(Plugin.class.getModule());

	    var instrumentation = Reflect.instrument().value();
        var loader = Plugin.class.getClassLoader();
        var u = Classes.load(loader, Util.NAME);
        util = u == null ? ClassDefiner.make().loader(loader).classFile(Util.INTERNAL_NAME).define() : u;

        Methods.of(Uncheck.class)
            .filter(method -> method.isAnnotationPresent(Transform.class))
            .flatMap(method -> {
                var transform = method.getAnnotation(Transform.class);
                //noinspection Convert2MethodRef
                return (transform.name().length == 0 ? Stream.of(transform.value()) : Stream.of(transform.name()).map(name -> Classes.load(name))).map(type -> Map.entry(type, method));
            })
            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())))
            .forEach((target, methods) -> {
                ClassTransformer transformer = (module, __, name, type, domain, bytes) -> {
					var node = new ClassNode();
					new ClassReader(bytes).accept(node, 0);

					for (var method : methods) {
						if (debug) System.out.println(name + ' ' + method.getName());
						method.invoke(null, node);
					}

					var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
					node.accept(writer);

					return writer.toByteArray();
                };

                instrumentation.addTransformer(transformer.ofType(target).exceptionLogging().singleUse(instrumentation), true);
	            instrumentation.retransformClasses(target);
            });
    }
}
