package net.auoeke.uncheck;

import com.sun.source.tree.Tree;
import com.sun.source.util.Plugin;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import java.util.List;

@SuppressWarnings("unused")
public class Util {
	public static final String NAME = "net.auoeke.uncheck.Util";
	public static final String INTERNAL_NAME = "net/auoeke/uncheck/Util";

	static final List<String> disallowedFeatures = List.of(
		"MODULES",
		"RECORDS",
		"SEALED_CLASSES",
		"PATTERN_SWITCH",
		"REDUNDANT_STRICTFP",
		"STRING_TEMPLATES"
	);

	private static Trees trees;

	static void context(Context context) {
		trees = JavacTrees.instance(context);
	}

	// VarMightAlreadyBeAssigned
	public static boolean allowFinalFieldReassignment(JCDiagnostic.DiagnosticPosition position, Symbol.VarSymbol variable) {
		if (variable.getKind() == ElementKind.FIELD) {
			var compilationUnit = trees.getPath(variable).getCompilationUnit();
			var initializer = ((JCTree.JCVariableDecl) trees.getTree(variable)).getInitializer();

			// todo: account for constant folding
			return (initializer == null || !initializer.hasTag(JCTree.Tag.LITERAL))
				&& (position.getTree() instanceof JCTree.JCIdent id && id.sym.enclClass() == variable.enclClass() ? allowFinalFieldReassignment(trees.getPath(compilationUnit, id), variable)
				: position.getTree() instanceof JCTree.JCFieldAccess field && field.sym.enclClass() == variable.enclClass() && allowFinalFieldReassignment(trees.getPath(compilationUnit, field), variable));
		}

		return false;
	}

	// CantAssignValToVar
	public static boolean allowFinalFieldReassignment(Symbol.VarSymbol variable, Env<AttrContext> env) {
		return variable.getKind() == ElementKind.FIELD
			&& env.enclClass.sym == variable.enclClass()
			&& allowFinalFieldReassignment(trees.getPath(env.toplevel, env.tree), variable);
	}

	public static boolean enablePreview(Source.Feature feature) {
		return !disallowedFeatures.contains(feature.name());
	}

	private static boolean allowFinalFieldReassignment(TreePath path, Symbol.VarSymbol variable) {
		while (true) {
			if (path == null || path.getLeaf().getKind() == Tree.Kind.LAMBDA_EXPRESSION) return false;

			var leaf = path.getLeaf();
			if (leaf instanceof JCTree.JCBlock block && path.getParentPath().getLeaf().getKind() == Tree.Kind.CLASS) return block.isStatic() == variable.isStatic();
			if (leaf instanceof JCTree.JCMethodDecl method) return method.sym.getKind() == ElementKind.CONSTRUCTOR && !variable.isStatic();
			if (leaf instanceof JCTree.JCVariableDecl field) return field.getModifiers().getFlags().contains(Modifier.STATIC) == variable.isStatic();

			path = path.getParentPath();
		}
	}

	static {
		if (Util.class.getClassLoader() != Plugin.class.getClassLoader()) {
			throw new IllegalStateException("Util must be loaded by javac's class loader");
		}
	}
}
