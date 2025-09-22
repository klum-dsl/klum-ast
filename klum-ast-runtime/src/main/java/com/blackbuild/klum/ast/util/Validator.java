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

import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.util.layer3.ModelVisitor;
import com.blackbuild.klum.ast.util.layer3.StructureUtil;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToBoolean;

/**
 * Validates an instance of a DSL object, checking for the presence of required fields,
 * and custom validation methods.
 */
public class Validator {

    public static final String FAIL_ON_LEVEL_PROPERTY = "klum.validation.failOnLevel";
    public static final String ANY_MEMBER = "*";

    private final Object instance;
    private final String breadcrumbPath;
    private boolean classHasValidateAnnotation;
    private Class<?> currentType;
    private final KlumValidationResult validationIssues;


    /**
     * Retrieves the validation results from the given instance and its child objects.
     *
     * @param instance the instance to validate the structure of. Must be a DSL object.
     * @return a list of {@link KlumValidationResult} containing validation errors.
     */
    public static List<KlumValidationResult> getValidationResultsFromStructure(Object instance) {
        ValidationResultCollector visitor = new ValidationResultCollector();
        StructureUtil.visit(instance, visitor);
        return visitor.aggregatedErrors;
    }

    /**
     * Retrieves all validation issues from the given instance and its child objects, and throws a {@link KlumValidationException}
     * if any validation issues with the level defined using the system property "klum.validation.failOnLevel" (or ERROR by default).
     *
     * @param instance the instance to validate the structure of. Must be a DSL object.
     * @return a list of {@link KlumValidationResult} containing validation errors.
     * @throws KlumValidationException if validation errors are found with a level equal or worse than the given level.
     */
    public static List<KlumValidationResult> verifyStructure(Object instance) throws KlumValidationException {
        return verifyStructure(instance, getFailLevel());
    }

    /**
     * Retrieves all validation issues from the given instance and its child objects, and throws a {@link KlumValidationException}
     * if any validation issues with the given fail level or higher are found.
     * @param instance the instance to validate the structure of. Must be a DSL object.
     * @param failLevel the minimum encountered level to result in an exception.
     * @return a list of {@link KlumValidationResult} containing validation errors.
     * @throws KlumValidationException if validation errors are found with a level equal or worse than the given level.
     */
    public static List<KlumValidationResult> verifyStructure(Object instance, Validate.Level failLevel) throws KlumValidationException {
        ValidationResultCollector visitor = new ValidationResultCollector();
        StructureUtil.visit(instance, visitor);
        KlumValidationResult.throwOn(visitor.aggregatedErrors, failLevel);
        return visitor.aggregatedErrors;
    }

    static class ValidationResultCollector implements ModelVisitor {

        private final List<KlumValidationResult> aggregatedErrors = new ArrayList<>();

        @Override
        public void visit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
            KlumValidationResult result = getValidationResult(element);
            if (result != null && result.has(Validate.Level.INFO))
                aggregatedErrors.add(result);
        }
    }

    /**
     * Retrieves the validation result for the given instance, either from the proxy or by performing a lenient validation.
     * This method is useful when you want to check the validation results without throwing an exception.
     *
     * @param instance the instance to validate
     * @return a {@link KlumValidationResult} containing validation errors
     */
    public static KlumValidationResult getValidationResult(Object instance) {
        return doGetValidationResult(instance);
    }

    /**
     * Adds an error issue to the validation result of the given instance. The issue will not be associated with a specific member.
     *
     * @param instance the instance to add the issue to
     * @param message  the message of the issue
     */
    public static void addErrorTo(Object instance, String message) {
        addErrorTo(instance, null, message);
    }

    /**
     * Adds an error issue to the validation result of the given instance.
     *
     * @param instance the instance to add the issue to
     * @param member The member to add the issue to, can be null
     * @param message  the message of the issue
     */
    public static void addErrorTo(Object instance, @Nullable String member, String message) {
        addIssueTo(instance, member, message, Validate.Level.ERROR);
    }

    /**
     * Adds an issue to the validation result of the given instance.
     *
     * @param instance the instance to add the issue to
     * @param member The member to add the issue to, can be null
     * @param message  the message of the issue
     * @param level    the level of the issue
     */
    public static void addIssueTo(@NotNull Object instance, @Nullable String member, String message, Validate.Level level) {
        KlumValidationResult validationResult = doGetOrCreateValidationResult(instance);
        validationResult.addIssue(new KlumValidationIssue(validationResult.getBreadcrumbPath(), member, message, null, level));
    }

    /**
     * Adds an issue to the validation result of the current instance.
     * This is only valid in the context of a lifecycle method/closure.
     *
     * @param message the message of the issue
     */
    public static void addError(@NotNull String message) {
        addIssueToMember(null, message, Validate.Level.ERROR);
    }

    /**
     * Adds an issue to the validation result of the current instance, flagging a different member than the one currently being validated.
     * This is only valid in the context of a lifecycle method/closure.
     *
     * @param member The member to add the issue to
     * @param message the message of the issue
     */
    public static void addErrorToMember(@NotNull String member, @NotNull String message) {
        addIssueToMember(member, message, Validate.Level.ERROR);
    }

    /**
     * Adds an issue to the validation result of the current instance.
     * This is only valid in the context of a lifecycle method/closure and uses the name of the current method as the member name.
     *
     * @param message the message of the issue
     * @param level   the level of the issue
     */
    public static void addIssue(String message, Validate.Level level) {
        addIssueToMember(null, message, level);
    }

    /**
     * Adds an issue to the validation result of the current instance.
     * This is only valid in the context of a lifecycle method/closure.
     *
     * @param member The member to add the issue to. If null, use the member of the current context
     * @param message the message of the issue
     * @param level   the level of the issue
     */
    public static void addIssueToMember(String member, String message, Validate.Level level) {
        addIssueTo(getCurrentInstance(), member != null ? member : PhaseDriver.getContext().getMember(), message, level);
    }

    /**
     * Suppresses any future non-ERROR issues of level for the given member in the validation result of the given instance.
     * This is only valid in the context of a lifecycle method/closure. Has no effect on already registered issues.
     *
     * @param member the member to suppress issues for. Can be {@link Validator#ANY_MEMBER} to suppress all further issues on the whole instance.
     */
    public static void suppressFurtherIssues(String member) {
        suppressFurtherIssues(member, Validate.Level.DEPRECATION);
    }

    /**
     * Suppresses any future issues up to the given level for the given member in the validation result of the given instance.
     * This is only valid in the context of a lifecycle method/closure. Has no effect on already registered issues.
     *
     * @param member the member to suppress issues for. Can be {@link Validator#ANY_MEMBER} to suppress all further issues on the whole instance.
     * @param upToLevel the level to suppress issues up to.
     */
    public static void suppressFurtherIssues(String member, Validate.Level upToLevel) {
        suppressFurtherIssues(getCurrentInstance(), member, upToLevel);
    }

    private static Object getCurrentInstance() {
        PhaseDriver.Context currentContext = PhaseDriver.getContext();
        if (currentContext == null || currentContext.getInstance() == null)
            throw new KlumSchemaException("Method called outside of lifecycle method/closure.");
        return currentContext.getInstance();
    }

    /**
     * Suppresses any future non-ERROR issues for the given member in the validation result of the given instance.
     * Has no effect on already registered issues.
     *
     * @param instance the instance to suppress issues for
     * @param member   the member to suppress issues for. Can be {@link Validator#ANY_MEMBER} to suppress all further issues on the whole instance.
     */
    public static void suppressFurtherIssues(Object instance, String member) {
        suppressFurtherIssues(instance, member, Validate.Level.DEPRECATION);
    }

    /**
     * Suppresses any future issues up to the given level for the given member in the validation result of the given instance.
     * Has no effect on already registered issues.
     * @param instance the instance to suppress issues for
     * @param member the member to suppress issues for. Can be {@link Validator#ANY_MEMBER} to suppress all further issues on the whole instance.
     * @param upToLevel the level to suppress issues up to.
     */
    public static void suppressFurtherIssues(Object instance, String member, Validate.Level upToLevel) {
        KlumValidationResult validationResult = doGetOrCreateValidationResult(instance);
        validationResult.suppressIssues(member, upToLevel);
    }

    private static KlumValidationResult doGetValidationResult(Object instance) {
        return KlumInstanceProxy.getProxyFor(instance).getMetaData(KlumValidationResult.METADATA_KEY, KlumValidationResult.class);
    }

    private static KlumValidationResult doGetOrCreateValidationResult(Object instance) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(instance);

        if (!proxy.hasMetaData(KlumValidationResult.METADATA_KEY))
            proxy.setMetaData(KlumValidationResult.METADATA_KEY, new KlumValidationResult(DslHelper.getModelAndBreadcrumbPath(instance)));
        return doGetValidationResult(instance);
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

    protected Validator(Object instance) {
        this.instance = instance;
        KlumValidationResult existingResult = doGetValidationResult(instance);

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
