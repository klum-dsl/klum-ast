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
package com.blackbuild.klum.ast.validation;

import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.util.*;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToBoolean;

/**
 * Validates an instance of a DSL object, checking for the presence of required fields,
 * and custom validation methods.
 */
class ObjectValidator {

    public static final String FAIL_ON_LEVEL_PROPERTY = "klum.validation.failOnLevel";
    public static final String ANY_MEMBER = "*";

    private final Object instance;
    private final String breadcrumbPath;
    private boolean classHasValidateAnnotation;
    private Class<?> currentType;
    private final KlumValidationResult validationIssues;


    protected ObjectValidator(Object instance) {
        this.instance = instance;
        KlumValidationResult existingResult = KlumInstanceProxy.getProxyFor(instance).getMetaData(KlumValidationResult.METADATA_KEY, KlumValidationResult.class);

        if (existingResult != null) {
            this.validationIssues = existingResult;
            this.breadcrumbPath = existingResult.getBreadcrumbPath();
        } else {
            this.breadcrumbPath = DslHelper.getModelAndBreadcrumbPath(instance);
            this.validationIssues = new KlumValidationResult(breadcrumbPath);
            KlumInstanceProxy.getProxyFor(instance).setMetaData(KlumValidationResult.METADATA_KEY, this.validationIssues);
        }
    }

    public void execute() {
        try {
            PhaseDriver.getContext().setInstance(instance);
            DslHelper.getDslHierarchyOf(instance.getClass()).forEach(this::validateType);
        } finally {
            PhaseDriver.getContext().setInstance(null);
        }
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
            validateCustomMethod(m).ifPresent(validationIssues::addIssue);
        }
    }

    private Optional<KlumValidationIssue> validateCustomMethod(Method method) {
        Validate.Level level = getValidateAnnotationOrDefault(method).level();
        return withExceptionCheck(
                method.getName() + "()",
                level,
                () -> InvokerHelper.invokeMethod(instance, method.getName(), null)
        );
    }

    private Optional<KlumValidationIssue> withExceptionCheck(String memberName, Validate.Level level, Runnable runnable) {
        try {
            PhaseDriver.getContext().setMember(memberName);
            runnable.run();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new KlumValidationIssue(breadcrumbPath, memberName, e.getMessage(), e, level));
        } catch (AssertionError e) {
            return Optional.of(new KlumValidationIssue(breadcrumbPath, memberName, e.getMessage(), null, level));
        } finally {
            PhaseDriver.getContext().setMember(null);
        }
    }

    private void validateFields() {
        for (Field field : currentType.getDeclaredFields()) {
            if (!isNotExplicitlyIgnored(field)) continue;
            validateField(field).ifPresent(validationIssues::addIssue);
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

        return classHasValidateAnnotation || field.isAnnotationPresent(Validate.class);
    }

    private Optional<KlumValidationIssue> validateField(Field field) {
        if (!shouldValidate(field))
            return Optional.empty();

        Object value = DslHelper.getAttributeValue(field.getName(), instance);

        if (instance.getClass().isAnnotationPresent(Validate.class) && field.isAnnotationPresent(Deprecated.class) && !field.isAnnotationPresent(Validate.class))
            return Optional.empty();

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

    private Optional<KlumValidationIssue> checkAgainstGroovyTruth(Field field, Object value, Validate validate) {
        if (isGroovyTruth(field, value)) return Optional.empty();

        String message = validate.message();

        if (message.isEmpty())
            message = String.format("Field '%s' must be set", field.getName());

        return Optional.of(new KlumValidationIssue(breadcrumbPath, field.getName(), message, null, validate.level()));
    }

    @SuppressWarnings("java:S1126")
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

    KlumValidationResult getValidationIssues() {
        return validationIssues;
    }
}
