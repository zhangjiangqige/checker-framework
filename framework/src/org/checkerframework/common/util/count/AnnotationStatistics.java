package org.checkerframework.common.util.count;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Name;
import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.framework.source.SourceVisitor;
import org.checkerframework.framework.source.SupportedOptions;
import org.checkerframework.javacutil.AnnotationProvider;

/**
 * An annotation processor for listing the potential locations of annotations. To invoke it, use
 *
 * <pre>
 * javac -proc:only -processor org.checkerframework.common.util.count.AnnotationStatistics <em>MyFile.java ...</em>
 * </pre>
 *
 * <p>You probably want to pipe the output through another program:
 *
 * <ul>
 *   <li>Total annotation count: {@code ... | wc}.
 *   <li>Breakdown by location type: {@code ... | sort | uniq -c}
 *   <li>Count for only certain location types: use {@code grep}
 * </ul>
 *
 * <p>By default, this utility displays annotation locations only. The following two options may be
 * used to adjust the output:
 *
 * <ul>
 *   <li>{@code -Aannotations}: prints information about the annotations
 *   <li>{@code -Anolocations}: suppresses location output; only makes sense in conjunction with
 *       {@code -Aannotations}
 * </ul>
 */
/*
 * TODO: add an option to only list declaration or type annotations.
 * This e.g. influences the output of "method return", which is only valid
 * for type annotations for non-void methods.
 */
@SupportedOptions({"nolocations", "annotations"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class AnnotationStatistics extends SourceChecker {

    /** Index Checker annotations */
    String[] annotations = {
        "EnsuresLTLengthOf",
        "EnsuresLTLengthOfIf",
        "GTENegativeOne",
        "IndexFor",
        "IndexOrHigh",
        "IndexOrLow",
        "LTEqLengthOf",
        "LTLengthOf",
        "LTOMLengthOf",
        "LengthOf",
        "LessThan",
        "LessThanBottom",
        "LessThanUnknown",
        "LowerBoundBottom",
        "LowerBoundUnknown",
        "NegativeIndexFor",
        "NonNegative",
        "PolyIndex",
        "PolyLength",
        "PolyLowerBound",
        "PolySameLen",
        "PolyUpperBound",
        "Positive",
        "SameLen",
        "SameLenBottom",
        "SameLenUnknown",
        "SearchIndexBottom",
        "SearchIndexFor",
        "SearchIndexUnknown",
        "SubstringIndexBottom",
        "SubstringIndexFor",
        "SubstringIndexUnknown",
        "UpperBoundBottom",
        "UpperBoundUnknown",
        "ArrayLen",
        "ArrayLenRange",
        "BoolVal",
        "BottomVal",
        "DoubleVal",
        "EnsuresMinLenIf",
        "IntRange",
        "IntRangeFromGTENegativeOne",
        "IntRangeFromNonNegative",
        "IntRangeFromPositive",
        "IntVal",
        "MinLen",
        "MinLenFieldInvariant",
        "PolyValue",
        "StaticallyExecutable",
        "StringVal",
        "UnknownVal"
    };

    final Map<Name, Integer> annotationCount = new HashMap<>();
    final Map<String, Integer> simpleNameCount = new HashMap<>();

    @Override
    protected boolean shouldAddShutdownHook() {
        return true;
    }

    @Override
    protected void shutdownHook() {
        super.shutdownHook();
        if (annotationCount.isEmpty()) {
            System.out.printf("No annotations found.%n");
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("Found annotations: %n"));
            for (Map.Entry<Name, Integer> entry : annotationCount.entrySet()) {
                builder.append(String.format("%s: %d%n", entry.getKey(), entry.getValue()));
            }
            System.out.print(builder.toString());
        }
        StringBuilder builder = new StringBuilder("Index Checker annotations: \n");
        for (String simpleName : annotations) {
            Integer count = simpleNameCount.get(simpleName);
            count = count == null ? 0 : count;
            builder.append(String.format("@%s:\t %d%n", simpleName, count));
        }
        System.out.print(builder.toString());
    }

    /** Increment the number of times annotation with name {@code annoName} has appeared. */
    protected void incrementCount(Name annoName) {
        if (!annotationCount.containsKey(annoName)) {
            annotationCount.put(annoName, 1);
        } else {
            annotationCount.put(annoName, annotationCount.get(annoName) + 1);
        }
    }

    /**
     * Increment the number of times annotation with the simple name {@code simpleName} has
     * appeared.
     */
    void incrementSimpleNameCount(String simpleName) {
        if (!simpleNameCount.containsKey(simpleName)) {
            simpleNameCount.put(simpleName, 1);
        } else {
            simpleNameCount.put(simpleName, simpleNameCount.get(simpleName) + 1);
        }
    }

    @Override
    protected SourceVisitor<?, ?> createSourceVisitor() {
        return new Visitor(this);
    }

    class Visitor extends SourceVisitor<Void, Void> {

        /** Whether annotation locations should be printed. */
        private final boolean locations;

        /** Whether annotation details should be printed. */
        private final boolean annotations;

        public Visitor(AnnotationStatistics l) {
            super(l);

            locations = !l.hasOption("nolocations");
            annotations = l.hasOption("annotations");
        }

        @Override
        public Void visitAnnotation(AnnotationTree tree, Void p) {
            Name annoName = ((JCAnnotation) tree).annotationType.type.tsym.getQualifiedName();
            incrementCount(annoName);
            incrementSimpleNameCount(
                    ((JCAnnotation) tree).annotationType.type.tsym.getSimpleName().toString());
            if (annotations) {
                // An annotation is a body annotation if, while ascending the
                // AST from the annotation to the root, we find a block
                // immediately enclosed by a method.
                //
                // If an annotation is not a body annotation, it's a signature
                // (declaration) annotation.

                boolean isBodyAnnotation = false;
                TreePath path = getCurrentPath();
                Tree prev = null;
                for (Tree t : path) {
                    if (prev != null
                            && prev.getKind() == Tree.Kind.BLOCK
                            && t.getKind() == Tree.Kind.METHOD) {
                        isBodyAnnotation = true;
                        break;
                    }
                    prev = t;
                }

                System.out.printf(
                        ":annotation %s %s %s %s%n",
                        tree.getAnnotationType(),
                        tree,
                        root.getSourceFile().getName(),
                        (isBodyAnnotation ? "body" : "sig"));
            }
            return super.visitAnnotation(tree, p);
        }

        @Override
        public Void visitArrayType(ArrayTypeTree tree, Void p) {
            if (locations) {
                System.out.println("array type");
            }
            return super.visitArrayType(tree, p);
        }

        @Override
        public Void visitClass(ClassTree tree, Void p) {
            if (shouldSkipDefs(tree)) {
                // Not "return super.visitClass(classTree, p);" because that would
                // recursively call visitors on subtrees; we want to skip the
                // class entirely.
                return null;
            }
            if (locations) {
                System.out.println("class");
                if (tree.getExtendsClause() != null) {
                    System.out.println("class extends");
                }
                for (@SuppressWarnings("unused") Tree t : tree.getImplementsClause()) {
                    System.out.println("class implements");
                }
            }
            return super.visitClass(tree, p);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void p) {
            if (locations) {
                System.out.println("method return");
                System.out.println("method receiver");
                for (@SuppressWarnings("unused") Tree t : tree.getThrows()) {
                    System.out.println("method throws");
                }
                for (@SuppressWarnings("unused") Tree t : tree.getParameters()) {
                    System.out.println("method param");
                }
            }
            return super.visitMethod(tree, p);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void p) {
            if (locations) {
                System.out.println("variable");
            }
            return super.visitVariable(tree, p);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, Void p) {
            if (locations) {
                for (@SuppressWarnings("unused") Tree t : tree.getTypeArguments()) {
                    System.out.println("method invocation type argument");
                }
            }
            return super.visitMethodInvocation(tree, p);
        }

        @Override
        public Void visitNewClass(NewClassTree tree, Void p) {
            if (locations) {
                System.out.println("new class");
                for (@SuppressWarnings("unused") Tree t : tree.getTypeArguments()) {
                    System.out.println("new class type argument");
                }
            }
            return super.visitNewClass(tree, p);
        }

        @Override
        public Void visitNewArray(NewArrayTree tree, Void p) {
            if (locations) {
                System.out.println("new array");
                for (@SuppressWarnings("unused") Tree t : tree.getDimensions()) {
                    System.out.println("new array dimension");
                }
            }
            return super.visitNewArray(tree, p);
        }

        @Override
        public Void visitTypeCast(TypeCastTree tree, Void p) {
            if (locations) {
                System.out.println("typecast");
            }
            return super.visitTypeCast(tree, p);
        }

        @Override
        public Void visitInstanceOf(InstanceOfTree tree, Void p) {
            if (locations) {
                System.out.println("instanceof");
            }
            return super.visitInstanceOf(tree, p);
        }

        @Override
        public Void visitParameterizedType(ParameterizedTypeTree tree, Void p) {
            if (locations) {
                for (@SuppressWarnings("unused") Tree t : tree.getTypeArguments()) {
                    System.out.println("parameterized type");
                }
            }
            return super.visitParameterizedType(tree, p);
        }

        @Override
        public Void visitTypeParameter(TypeParameterTree tree, Void p) {
            if (locations) {
                for (@SuppressWarnings("unused") Tree t : tree.getBounds()) {
                    System.out.println("type parameter bound");
                }
            }
            return super.visitTypeParameter(tree, p);
        }

        @Override
        public Void visitWildcard(WildcardTree tree, Void p) {
            if (locations) {
                System.out.println("wildcard");
            }
            return super.visitWildcard(tree, p);
        }
    }

    @Override
    public AnnotationProvider getAnnotationProvider() {
        throw new UnsupportedOperationException(
                "getAnnotationProvider is not implemented for this class.");
    }
}
