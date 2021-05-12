package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.groovy.configdsl.transform.Validation;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static java.util.Arrays.stream;
import static org.codehaus.groovy.runtime.InvokerHelper.getAttribute;

public class Validator {

    private GroovyObject instance;
    private boolean manualValidation;

    public Validator(GroovyObject instance) {
        this.instance = instance;
    }

    public void execute() {
        Class<?> level = instance.getClass();
        while (isDslType(level)) {
            //noinspection unchecked
            validateLevel((Class<? extends GroovyObject>) level);
            level = level.getSuperclass();
        }
    }

    private void validateLevel(Class<? extends GroovyObject> type) {
        try {
            validateFields(type);
            validateCustomMethods(type);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void validateCustomMethods(Class<? extends GroovyObject> type) {
        stream(type.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Validate.class)).forEach(this::validateCustomMethod);
    }

    private void validateCustomMethod(Method method) {
        InvokerHelper.invokeMethod(instance, method.getName(), new Object[0]);
    }

    private boolean isDslType(Class<?> type) {
        return type != null && GroovyObject.class.isAssignableFrom(type) && type.isAnnotationPresent(DSL.class);
    }

    private void validateFields(Class<?> type) {
        Validation.Option mode = getValidationMode(type);
        stream(type.getDeclaredFields()).filter(f -> shouldValidate(f, mode)).forEach(this::validateField);
    }

    @NotNull
    private Validation.Option getValidationMode(Class<?> type) {
        Validation annotation = type.getDeclaredAnnotation(Validation.class);
        return annotation != null ? annotation.option() : Validation.Option.IGNORE_UNMARKED;
    }

    private boolean shouldValidate(Field field, Validation.Option validationMode) {
        if (field.getName().startsWith("$")) return false;
        if (Modifier.isTransient(field.getModifiers())) return false;

        Validate validate = field.getAnnotation(Validate.class);
        Class<?> validateClosure = validate != null ? validate.value() : Validate.GroovyTruth.class;

        if (validateClosure == Validate.Ignore.class)
            return false;

        return validationMode != Validation.Option.IGNORE_UNMARKED || validate != null;
    }

    private void validateField(Field field) {
        Validate validate = field.getAnnotation(Validate.class);

        Object value = getAttribute(instance, field.getName());
        Class<?> validationValue = validate.value();
        if (validationValue == Validate.GroovyTruth.class) {
            if (!toBoolean(value)) {
                InvokerHelper.assertFailed(field.getName() + " as Boolean", null);
            }
            return;
        }

        Closure<?> closure = ClosureHelper.createClosureInstance(validationValue);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(instance);
        closure.call(value);
    }

    private boolean toBoolean(Object value) {
        // asBoolean uses Java compile time binding, i.e. compares to null. Invoke Helper uses runtime binding
        return (boolean) InvokerHelper.invokeMethod(DefaultGroovyMethods.class, "asBoolean", value);
    }

}
