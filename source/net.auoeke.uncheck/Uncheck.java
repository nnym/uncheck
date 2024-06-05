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
import com.sun.tools.javac.jvm.ClassWriter;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import net.auoeke.reflect.*;

import javax.tools.Diagnostic;
import java.lang.classfile.*;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Uncheck implements Plugin {
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

	@Transform(name = {"com.sun.tools.javac.comp.Flow$CaptureAnalyzer", "com.sun.tools.javac.comp.Flow$FlowAnalyzer"}, method = "analyzeTree")
	private static void disableCaptureAndFlowAnalyzers(ClassBuilder builder, MethodModel method) {
		withMethod(builder, method, mb -> mb.withCode(CodeBuilder::return_));
	}

	@Transform(value = Attr.class, method = "checkReferenceCompatible")
	private static void acceptMethodReferencesWithIncompatibleThrownTypes(ClassBuilder builder, MethodModel method) {
		transformCode(builder, method, (code, iterator, instruction) -> {
			code.iconst_1().istore(5);
			code.accept(instruction);
			iterator.forEachRemaining(code::accept);
		});
	}

	@Transform(value = Attr.class, method = "checkFirstConstructorStat")
	private static void allowNonConstructorFirstStatement(ClassBuilder builder, MethodModel method) {
		if (isJavaSupported(22)) builder.accept(method);
		else transformCode(builder, method, (code, iterator, instruction) -> {
			code.accept(instruction);
			if (instruction instanceof LoadInstruction load && load.slot() == 3) code.goto_(((BranchInstruction) iterator.next()).target());
		});
	}

	@Transform(value = Attr.class, method = "checkAssignable")
	private static void allowDefinitelyAssignedFinalFieldReassignment(ClassBuilder builder, MethodModel method) {
		transformCode(builder, method, (code, instruction) -> {
			if (instruction instanceof InvokeInstruction call && call.name().equalsString(STR."CantAssignValTo\{isJavaSupported(21) ? "" : "Final"}Var")) {
				code.aload(2)
					.aload(4)
					.invokestatic(ClassDesc.of(Util.NAME), "allowFinalFieldReassignment", methodType(boolean.class, Symbol.VarSymbol.class, Env.class))
					.ifThen(Opcode.IFNE, CodeBuilder::return_);
			}

			code.accept(instruction);
		});
	}

	@Transform(value = Flow.AssignAnalyzer.class, method = "letInit", parameters = {JCDiagnostic.DiagnosticPosition.class, Symbol.VarSymbol.class})
	private static void allowPossiblyAssignedFinalFieldAssignment(ClassBuilder builder, MethodModel method) {
		transformCode(builder, method, (code, instruction) -> {
			if (instruction instanceof InvokeInstruction call && call.name().equalsString("VarMightAlreadyBeAssigned")
			|| instruction instanceof FieldInstruction field && field.name().equalsString("errKey")) {
				code.aload(1)
					.aload(2)
					.invokestatic(ClassDesc.of(Util.NAME), "allowFinalFieldReassignment", methodType(boolean.class, JCDiagnostic.DiagnosticPosition.class, Symbol.VarSymbol.class))
					.ifThen(Opcode.IFNE, CodeBuilder::return_);
			}

			code.accept(instruction);
		});
	}

	@Transform(value = Preview.class, method = "isEnabled")
	private static void enablePreview(ClassBuilder builder, MethodModel method) {
		withMethod(builder, method, mb -> mb.withCode(code -> code.iconst_1().ireturn()));
	}

	@Transform(value = Source.Feature.class, method = "allowedInSource")
	private static void ignorePreviewVersion(ClassBuilder builder, MethodModel method) {
		transformCode(builder, method, (code, iterator, instruction) -> {
			code.aload(0)
				.invokestatic(ClassDesc.of(Util.NAME), "enablePreview", methodType(boolean.class, Source.Feature.class))
				.ifThen(Opcode.IFNE, c -> c.iconst_1().ireturn());

			code.accept(instruction);
			iterator.forEachRemaining(code::accept);
		});
	}

	@Transform(value = ClassWriter.class, method = "writeClassFile")
	private static void disablePreviewVersion(ClassBuilder builder, MethodModel method) {
		transformCode(builder, method, (code, instruction) -> {
			if (instruction instanceof InvokeInstruction call && call.owner().asSymbol().equals(Preview.class.describeConstable().get()) && call.name().equalsString("isEnabled")) {
				code.pop().iconst_0();
			} else code.accept(instruction);
		});
	}

	static void transformCode(ClassBuilder builder, MethodModel method, TriConsumer<CodeBuilder, Iterator<CodeElement>, CodeElement> transformer) {
		withMethod(builder, method, mb -> mb.withCode(code -> {
			for (var iterator = method.code().get().iterator(); iterator.hasNext();) transformer.accept(code, iterator, iterator.next());
		}));
	}

	static void withMethod(ClassBuilder builder, MethodModel method, Consumer<MethodBuilder> transformer) {
		builder.withMethod(method.methodName(), method.methodType(), method.flags().flagsMask(), transformer);
	}

	static void transformCode(ClassBuilder builder, MethodModel method, BiConsumer<CodeBuilder, CodeElement> transformer) {
		transformCode(builder, method, (code, iterator, instruction) -> transformer.accept(code, instruction));
	}

	static void transformCode(ClassBuilder builder, MethodModel method, Predicate<MethodModel> predicate, TriConsumer<CodeBuilder, Iterator<CodeElement>, CodeElement> transformer) {
		if (predicate.test(method)) transformCode(builder, method, transformer);
		else builder.accept(method);
	}

	static void transformCode(ClassBuilder builder, MethodModel method, Predicate<MethodModel> predicate, BiConsumer<CodeBuilder, CodeElement> transformer) {
		if (predicate.test(method)) transformCode(builder, method, transformer);
	}

	static void withMethod(ClassBuilder builder, MethodModel method, Predicate<MethodModel> predicate, Consumer<MethodBuilder> transformer) {
		if (predicate.test(method)) withMethod(builder, method, transformer);
		else builder.accept(method);
	}

	static MethodTypeDesc methodType(Class<?> result, Class<?>... parameters) {
		return MethodTypeDesc.of(result.describeConstable().get(), Stream.of(parameters).map(p -> p.describeConstable().get()).toArray(ClassDesc[]::new));
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
				return (transform.name().length == 0 ? Stream.of(transform.value()) : Stream.of(transform.name()).map(name -> Classes.load(name))).map(type -> Map.entry(type, Map.entry(method, transform)));
			})
			.collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())))
			.forEach((target, methods) -> {
				ClassTransformer transformer = (module, __, name, type, domain, bytes) -> {
					var file = ClassFile.of();
					var model = file.parse(bytes);

					return file.build(model.thisClass().asSymbol(), builder -> {
						var remaining = new ArrayList<>(model.elementList());

						A: for (var m : methods) {
							var at = m.getValue();
							if (debug) System.out.println(name + ' ' + m.getKey().getName());

							for (var member : model) {
								if (member instanceof MethodModel method && method.methodName().equalsString(at.method()) && (Arrays.equals(at.parameters(), new Class<?>[]{Transform.class}) || Stream.of(at.parameters()).map(t -> t.describeConstable().get()).toList().equals(MethodTypeDesc.ofDescriptor(method.methodType().stringValue()).parameterList()))) {
									m.getKey().invoke(null, builder, method);
									remaining.remove(method);

									continue A;
								}
							}

							System.err.println(m.getKey().getName() + " did not match.");
						}

						remaining.forEach(builder::with);
					});
				};

				instrumentation.addTransformer(transformer.ofType(target).exceptionLogging().singleUse(instrumentation), true);
				instrumentation.retransformClasses(target);
			});
	}
}
