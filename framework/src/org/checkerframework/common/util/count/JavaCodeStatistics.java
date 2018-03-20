package org.checkerframework.common.util.count;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import java.util.List;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;
import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.framework.source.SourceVisitor;
import org.checkerframework.javacutil.AnnotationProvider;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;

/**
 * An annotation processor for listing the facts about Java code.
 *
 * <p>To invoke it, use
 *
 * <pre>
 * javac -proc:only -processor org.checkerframework.common.util.count.JavaCodeStatistics <em>MyFile.java ...</em>
 * </pre>
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class JavaCodeStatistics extends SourceChecker {

    /** The number of type parameters declarations and uses. */
    int generics = 0;
    /** The number of array accesses and dimensions in array creations. */
    int arrayAccesses = 0;
    /** The number of type casts. */
    int typecasts = 0;

    String[] warningKeys = {
        "index", "lowerbound", "samelen", "searchindex", "substringindex", "upperbound"
    };
    /**
     * The number of warning suppressions with at least one key that matches one of the Index
     * Checker subcheckers.
     */
    int numberOfIndexWarningSuppressions = 0;

    int arrayField = 0;
    int finalField = 0;
    int nonPrivateNonFinal = 0;
    int arrayAssignment = 0;

    @Override
    protected boolean shouldAddShutdownHook() {
        return true;
    }

    @Override
    protected void shutdownHook() {
        super.shutdownHook();
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Found %d generic type uses.\n", generics));
        builder.append(String.format("Found %d array accesses and creations.\n", arrayAccesses));
        builder.append(String.format("Found %d typecasts.\n", typecasts));
        builder.append(String.format("Found %d array fields.\n", arrayField));
        builder.append(String.format("Found %d final array fields.\n", finalField));
        builder.append(
                String.format(
                        "Found %d non-private, non-final array fields.\n", nonPrivateNonFinal));
        builder.append(String.format("Found %d array fields assignments.\n", arrayAssignment));

        builder.append(
                String.format(
                        "Found %d warning suppression annotations for the Index Checker.\n",
                        numberOfIndexWarningSuppressions));
        System.out.print(builder.toString());
    }

    @Override
    protected SourceVisitor<?, ?> createSourceVisitor() {
        return new Visitor(this);
    }

    class Visitor extends SourceVisitor<Void, Void> {

        public Visitor(JavaCodeStatistics l) {
            super(l);
        }

        @Override
        public Void visitAnnotation(AnnotationTree node, Void aVoid) {
            AnnotationMirror annotationMirror = TreeUtils.annotationFromAnnotationTree(node);
            if (AnnotationUtils.annotationName(annotationMirror)
                    .equals(SuppressWarnings.class.getCanonicalName())) {
                List<String> keys =
                        AnnotationUtils.getElementValueArray(
                                annotationMirror, "value", String.class, true);
                for (String foundKey : keys) {
                    for (String indexKey : warningKeys) {
                        if (foundKey.startsWith(indexKey)) {
                            numberOfIndexWarningSuppressions++;
                            return super.visitAnnotation(node, aVoid);
                        }
                    }
                }
            }
            return super.visitAnnotation(node, aVoid);
        }

        @Override
        public Void visitClass(ClassTree tree, Void p) {
            if (shouldSkipDefs(tree)) {
                // Not "return super.visitClass(classTree, p);" because that would
                // recursively call visitors on subtrees; we want to skip the
                // class entirely.
                return null;
            }
            generics += tree.getTypeParameters().size();
            return super.visitClass(tree, p);
        }

        @Override
        public Void visitNewArray(NewArrayTree node, Void aVoid) {
            arrayAccesses += node.getDimensions().size();

            return super.visitNewArray(node, aVoid);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void aVoid) {
            if (TreeUtils.isDiamondTree(node)) {
                generics++;
            }
            generics += node.getTypeArguments().size();
            return super.visitNewClass(node, aVoid);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void aVoid) {
            generics += node.getTypeArguments().size();
            return super.visitMethodInvocation(node, aVoid);
        }

        @Override
        public Void visitMethod(MethodTree node, Void aVoid) {
            generics += node.getTypeParameters().size();
            return super.visitMethod(node, aVoid);
        }

        @Override
        public Void visitParameterizedType(ParameterizedTypeTree tree, Void p) {
            generics += tree.getTypeArguments().size();
            return super.visitParameterizedType(tree, p);
        }

        @Override
        public Void visitArrayAccess(ArrayAccessTree node, Void aVoid) {
            arrayAccesses++;
            return super.visitArrayAccess(node, aVoid);
        }

        @Override
        public Void visitTypeCast(TypeCastTree node, Void aVoid) {
            typecasts++;
            return super.visitTypeCast(node, aVoid);
        }

        @Override
        public Void visitVariable(VariableTree node, Void aVoid) {
            Element ele = TreeUtils.elementFromTree(node);
            if (TreeUtils.typeOf(node).getKind() == TypeKind.ARRAY
                    && ele.getKind() == ElementKind.FIELD) {
                arrayField++;
                if (ElementUtils.isEffectivelyFinal(ele)) {
                    finalField++;
                } else if (!((com.sun.tools.javac.code.Symbol) ele).isPrivate()) {
                    nonPrivateNonFinal++;
                }
                if (node.getInitializer() != null) {
                    arrayAssignment++;
                }
            }
            return super.visitVariable(node, aVoid);
        }

        @Override
        public Void visitAssignment(AssignmentTree node, Void aVoid) {
            Element ele = TreeUtils.elementFromTree(node.getVariable());
            if (TreeUtils.typeOf(node).getKind() == TypeKind.ARRAY
                    && ele.getKind() == ElementKind.FIELD) {
                arrayAssignment++;
            }

            return super.visitAssignment(node, aVoid);
        }
    }

    @Override
    public AnnotationProvider getAnnotationProvider() {
        throw new UnsupportedOperationException(
                "getAnnotationProvider is not implemented for this class.");
    }
}
