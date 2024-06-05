package net.auoeke.uncheck;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Transform {
    Class<?> value() default Uncheck.class;

    String[] name() default {};

	String method() default "";

	Class<?>[] parameters() default Transform.class;
}
