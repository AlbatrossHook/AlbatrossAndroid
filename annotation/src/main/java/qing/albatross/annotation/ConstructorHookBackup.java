package qing.albatross.annotation;

import static qing.albatross.annotation.CompileOption.COMPILE_DEFAULT;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConstructorHookBackup {
  String[] value() default {};
  String[] className() default {};
  Class<?> targetClass() default TargetClass.class;
  int compileHooker() default COMPILE_DEFAULT;
  int compileTarget() default COMPILE_DEFAULT;
  boolean required() default false;
  int option() default MethodDefOption.DEFAULT;
}
