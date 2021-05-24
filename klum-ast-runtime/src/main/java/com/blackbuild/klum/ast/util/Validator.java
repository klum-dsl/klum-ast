package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.groovy.configdsl.transform.Validation;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static org.codehaus.groovy.runtime.InvokerHelper.getAttribute;
import static org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToBoolean;

public class Validator {

    private final Object instance;
    private boolean manualValidation;
    private Validation.Option currentValidationMode;
    private Class<?> currentType;

    public Validator(Object instance) {
        this.instance = instance;
    }

    public void execute() {
        DslHelper.getDslHierarchyOf(instance.getClass()).forEach(this::validateType);
    }

    private void validateType(Class<?> type) {
        currentType = type;
        currentValidationMode = getValidationMode();
        try {
            validateFields();
            executeCustomValidationMethods();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void executeCustomValidationMethods() {
        stream(currentType.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Validate.class))
                .forEach(this::validateCustomMethod);
    }

    private void validateCustomMethod(Method method) {
        InvokerHelper.invokeMethod(instance, method.getName(), null);
    }

    private void validateFields() {
        stream(currentType.getDeclaredFields())
                .filter(this::isNotExplicitlyIgnored)
                .forEach(this::validateField);
    }

    @NotNull
    private Validation.Option getValidationMode() {
        Validation annotation = currentType.getDeclaredAnnotation(Validation.class);
        return annotation != null ? annotation.option() : Validation.Option.IGNORE_UNMARKED;
    }

    private boolean isNotExplicitlyIgnored(Field field) {
        Validate validate = field.getAnnotation(Validate.class);
        return validate == null || validate.value() !=  Validate.Ignore.class;
    }

    private boolean shouldValidate(Field field) {
        if (field.getName().startsWith("$")) return false;
        if (Modifier.isTransient(field.getModifiers())) return false;

        return currentValidationMode == Validation.Option.VALIDATE_UNMARKED || field.isAnnotationPresent(Validate.class);
    }

    private void validateField(Field field) {
        if (field.isAnnotationPresent(Owner.class))
            return;

        validateInnerDslObjects(field);

        if (!shouldValidate(field))
            return;

        Object value = getAttribute(instance, field.getName());

        Validate validate = field.getAnnotation(Validate.class);
        Class<?> validationValue = validate != null ? validate.value() : Validate.GroovyTruth.class;

        if (validationValue == Validate.GroovyTruth.class) {
            if (!castToBoolean(value)) {
                String message = validate != null ? validate.message() : "";
                if (message.isEmpty())
                    message = String.format("'%s' must be set.", field.getName());
                InvokerHelper.assertFailed(field.getName() + " as Boolean", message);
            }
            return;
        }

        ClosureHelper.invokeClosureWithDelegate((Class<? extends Closure<Void>>) validationValue, instance, value);
    }

    private void validateInnerDslObjects(Field field) {
        getDslObjectsFor(field).filter(Objects::nonNull).map(Validator::new).forEach(Validator::execute);
    }

    private Stream<?> getDslObjectsFor(Field field) {
        Class<?> type = field.getType();
        if (DslHelper.isDslType(type))
            return Stream.of(InvokerHelper.getAttribute(instance, field.getName()));

        if (!Collection.class.isAssignableFrom(type) && !Map.class.isAssignableFrom(type))
            return Stream.empty();

        Type elementType = getElementType(field);

        if (Collection.class.isAssignableFrom(type) && DslHelper.isDslType(elementType))
            return ((Collection<?>) InvokerHelper.getAttribute(instance, field.getName())).stream();

        if (Map.class.isAssignableFrom(type) && DslHelper.isDslType(elementType))
            return ((Map<?, ?>) InvokerHelper.getAttribute(instance, field.getName())).values().stream();

        return Stream.empty();
    }

    private Type getElementType(Field field) {
        Type genericType = field.getGenericType();

        if (!(genericType instanceof ParameterizedType))
            return null;

        ParameterizedType parameterizedType = (ParameterizedType) genericType;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

        return actualTypeArguments[actualTypeArguments.length - 1];

    }

}
