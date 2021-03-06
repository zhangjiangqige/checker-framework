package org.checkerframework.framework.qual;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Declaration annotation applied to type declarations to specify that the annotation on the type
 * declaration should not be applied to unannotated uses of the type. Instead, another default
 * should be applied based on the location of the type or some other defaulting rule.
 */
@Target(ElementType.TYPE)
public @interface NoDefaultQualifierForUse {
    /** Top qualifier in hierarchies for which no default annotation for use should be applied. */
    Class<? extends Annotation>[] value() default {};
}
