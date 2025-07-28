/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.klum.ast.util;

import com.blackbuild.annodocimal.annotations.AnnoDoc;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.Validate;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

import static org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToBoolean;

public class Validator {

    private final Object instance;
    private final String breadcrumbPath;
    private boolean classHasValidateAnnotation;
    private Class<?> currentType;
    private final KlumValidationResult validationErrors;

    public static void validate(Object instance) throws KlumValidationException{
        Validator validator = new Validator(instance);
        validator.execute();
        validator.validationErrors.throwOn(Validate.Level.ERROR);
    }

    public static KlumValidationResult lenientValidate(Object instance) {
        Validator validator = new Validator(instance);
        validator.execute();
        return validator.validationErrors;
    }

    public static void validateStructure(Object instance) throws KlumValidationException {
        validateStructure(instance, Validate.Level.DEPRECATION);
    }

    public static void validateStructure(Object instance, Validate.Level maxAllowedLevel) throws KlumValidationException {
        new ValidationPhase.Visitor().executeOn(instance, maxAllowedLevel);
    }

    protected Validator(Object instance) {
        this.instance = instance;
        this.breadcrumbPath = DslHelper.getBreadcrumbPath(instance);
        this.validationErrors = new KlumValidationResult(breadcrumbPath);
    }

    public void execute() {
        DslHelper.getDslHierarchyOf(instance.getClass()).forEach(this::validateType);
        KlumInstanceProxy.getProxyFor(instance).setValidationResults(validationErrors);
    }

    private void validateType(Class<?> type) {
        currentType = type;
        classHasValidateAnnotation = type.isAnnotationPresent(Validate.class);
        validateFields();
        executeCustomValidationMethods();
    }

    private void executeCustomValidationMethods() {
        for (Method m : currentType.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Validate.class)) continue;
            validateCustomMethod(m).ifPresent(validationErrors::addProblem);
        }
    }

    private Optional<KlumValidationProblem> validateCustomMethod(Method method) {
        Validate.Level level = getValidateAnnotationOrDefault(method).level();
        return withExceptionCheck(
                method.getName() + "()",
                level,
                () -> InvokerHelper.invokeMethod(instance, method.getName(), null)
        );
    }

    private Optional<KlumValidationProblem> withExceptionCheck(String memberName, Validate.Level level, Runnable runnable) {
        try {
            runnable.run();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new KlumValidationProblem(breadcrumbPath, memberName, e.getMessage(), e, level));
        } catch (AssertionError e) {
            return Optional.of(new KlumValidationProblem(breadcrumbPath, memberName, e.getMessage(), null, level));
        }
    }

    private void validateFields() {
        for (Field field : currentType.getDeclaredFields()) {
            if (!isNotExplicitlyIgnored(field)) continue;
            validateField(field).ifPresent(validationErrors::addProblem);
        }
    }

    private boolean isNotExplicitlyIgnored(Field field) {
        if (Modifier.isStatic(field.getModifiers())) return false;
        return getValidateAnnotationOrDefault(field).value() != Validate.Ignore.class;
    }

    private boolean shouldValidate(Field field) {
        if (field.getName().startsWith("$")) return false;
        if (Modifier.isTransient(field.getModifiers())) return false;
        if (field.isAnnotationPresent(Owner.class)) return false;
        if (field.getType() == boolean.class) return false;

        return classHasValidateAnnotation || field.isAnnotationPresent(Validate.class) || field.isAnnotationPresent(Deprecated.class);
    }

    private Optional<KlumValidationProblem> validateField(Field field) {
        if (!shouldValidate(field))
            return Optional.empty();

        Object value = DslHelper.getAttributeValue(field.getName(), instance);

        if (field.isAnnotationPresent(Deprecated.class) && !field.isAnnotationPresent(Validate.class))
            return checkForDeprecation(field, value);

        Validate validate = getValidateAnnotationOrDefault(field);

        if (validate.value() == Validate.GroovyTruth.class)
            return checkAgainstGroovyTruth(field, value, validate);
        else
            return withExceptionCheck(
                    field.getName(),
                    validate.level(),
                    () -> ClosureHelper.invokeClosureWithDelegate((Class<? extends Closure<Void>>) validate.value(), instance, value)
            );
    }

    private Optional<KlumValidationProblem> checkForDeprecation(Field field, Object value) {
        if (!isGroovyTruth(field, value)) return Optional.empty();

        String message = getDeprecationMessage(field);

        return Optional.of(new KlumValidationProblem(breadcrumbPath, field.getName(), message, null, Validate.Level.DEPRECATION));
    }

    private String getDeprecationMessage(Field field) {
        AnnoDoc annoDoc = field.getAnnotation(AnnoDoc.class);

        if (annoDoc == null)
            return String.format("Field '%s' is deprecated", field.getName());

        return Arrays.stream(annoDoc.value().split("\\R"))
                .filter(l -> l.startsWith("@deprecated "))
                .map(l -> l.replaceFirst("@deprecated ", "").trim())
                .findAny()
                .orElse(String.format("Field '%s' is deprecated", field.getName()));
    }

    private Optional<KlumValidationProblem> checkAgainstGroovyTruth(Field field, Object value, Validate validate) {
        if (isGroovyTruth(field, value)) return Optional.empty();

        String message = validate.message();

        if (message.isEmpty())
            message = String.format("Field '%s' must be set", field.getName());

        return Optional.of(new KlumValidationProblem(breadcrumbPath, field.getName(), message, null, validate.level()));
    }

    private boolean isGroovyTruth(Field field, Object value) {
        if (field.getType() == Boolean.class && value != null) return true;
        if (castToBoolean(value)) return true;
        return false;
    }

    private Validate getValidateAnnotationOrDefault(AnnotatedElement member) {
        Validate validate = member.getAnnotation(Validate.class);
        if (validate != null) return validate;
        return Validate.DefaultImpl.INSTANCE;
    }
}
