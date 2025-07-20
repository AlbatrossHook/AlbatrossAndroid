package qing.albatross.annotation;

import static qing.albatross.annotation.CompileOption.COMPILE_DEFAULT;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StaticMethodBackup {
  String[] value() default {};
  String[] name() default {};
  Class<?> targetClass() default TargetClass.class;
  String[] className() default {};
  int compileTarget() default COMPILE_DEFAULT;
  boolean required() default false;
  int option() default MethodDefOption.DEFAULT;
}