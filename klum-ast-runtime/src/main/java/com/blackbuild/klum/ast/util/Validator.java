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
import com.blackbuild.klum.ast.util.layer3.StructureUtil;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToBoolean;

/**
 * Validates an instance of a DSL object, checking for the presence of required fields,
 * custom validation methods, and deprecated fields.
 */
public class Validator {

    public static final String FAIL_ON_LEVEL_PROPERTY = "klum.validation.failOnLevel";

    private final Object instance;
    private final String breadcrumbPath;
    private boolean classHasValidateAnnotation;
    private Class<?> currentType;
    private final KlumValidationResult validationIssues;

    /**
     * Validates the given instance, throwing a {@link KlumValidationException} if any validation errors are found.
     *
     * @param instance the instance to validate
     * @return The encountered issues if below fail level
     * @throws KlumValidationException if validation errors are found
     */
    public static KlumValidationResult validate(Object instance) throws KlumValidationException {
        return validate(instance, null);
    }

    /**
     * Validates the given instance, throwing a {@link KlumValidationException} if any validation errors are found.
     *
     * @param instance the instance to validate
     * @param path     an optional path to be stored in the validation problem
     * @return The encountered issues if below fail level
     * @throws KlumValidationException if validation errors are found
     */
    public static KlumValidationResult validate(Object instance, String path) throws KlumValidationException{
        KlumValidationResult result = lenientValidate(instance, path);
        result.throwOn(getFailLevel());
        return result;
    }

    /**
     * Validates the given instance, returning a {@link KlumValidationResult} containing any validation errors.
     * This method does not throw an exception, allowing for lenient validation.
     *
     * @param instance the instance to validate
     * @param path
     * @return a {@link KlumValidationResult} containing validation errors
     */
    public static KlumValidationResult lenientValidate(Object instance, String path) {
        Validator validator = new Validator(instance, path);
        validator.execute();
        return validator.validationIssues;
    }

    /**
     * Validates the structure of the given instance, checking for required fields and custom validation methods.
     * This method uses the default fail level defined by the system property {@code klum.validation.failOnLevel}.
     *
     * @param instance the instance to validate
     * @throws KlumValidationException if validation errors are found
     */
    public static List<KlumValidationResult> validateStructure(Object instance) throws KlumValidationException {
        return validateStructure(instance, getFailLevel());
    }

    /**
     * Validates the structure of the given instance, checking for required fields and custom validation methods,
     * throwing a {@link KlumValidationException} if any validation problems with the fail level are found.
     *
     * @param instance the instance to validate
     * @param failLevel the maximum allowed validation level
     * @return a list of {@link KlumValidationResult} containing validation errors
     * @throws KlumValidationException if validation errors are found
     */
    public static List<KlumValidationResult> validateStructure(Object instance, Validate.Level failLevel) throws KlumValidationException {
        StructureUtil.visit(instance, new ValidationPhase());
        return new VerifyPhase.Visitor().executeOn(instance, failLevel);
    }

    /**
     * Retrieves the validation result for the given instance, either from the proxy or by performing a lenient validation.
     * This method is useful when you want to check the validation results without throwing an exception.
     *
     * @param instance the instance to validate
     * @return a {@link KlumValidationResult} containing validation errors
     */
    public static KlumValidationResult getValidationResult(Object instance) {
        KlumValidationResult validationResult = doGetValidationResult(instance);
        if (validationResult != null) return validationResult;
        return lenientValidate(instance, null);
    }

    private static KlumValidationResult doGetValidationResult(Object instance) {
        return KlumInstanceProxy.getProxyFor(instance).getMetaData(KlumValidationResult.METADATA_KEY, KlumValidationResult.class);
    }

    /**
     * Retrieves the fail level for validation, which is defined by the system property {@code klum.validation.failOnLevel}.
     * If the property is not set, it defaults to {@link Validate.Level#ERROR}.
     *
     * @return the fail level for validation
     */
    public static Validate.Level getFailLevel() {
        return Validate.Level.fromString(System.getProperty(FAIL_ON_LEVEL_PROPERTY, Validate.Level.ERROR.name()));
    }

    protected Validator(Object instance, String path) {
        this.instance = instance;
        KlumValidationResult existingResult = doGetValidationResult(instance);

        if (existingResult != null) {
            this.validationIssues = existingResult;
            this.breadcrumbPath = existingResult.getBreadcrumbPath();
        } else {
            if (path != null) {
                this.breadcrumbPath = path + "(" + DslHelper.getBreadcrumbPath(instance) + ")";
            } else {
                // Use the default breadcrumb path from the instance
                this.breadcrumbPath = DslHelper.getBreadcrumbPath(instance);
            }
            this.validationIssues = new KlumValidationResult(breadcrumbPath);
        }
    }

    public void execute() {
        DslHelper.getDslHierarchyOf(instance.getClass()).forEach(this::validateType);
        KlumInstanceProxy klumInstanceProxy = KlumInstanceProxy.getProxyFor(instance);
        klumInstanceProxy.setMetaData(KlumValidationResult.METADATA_KEY, validationIssues);
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
            validateCustomMethod(m).ifPresent(validationIssues::addProblem);
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
            validateField(field).ifPresent(validationIssues::addProblem);
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
}
