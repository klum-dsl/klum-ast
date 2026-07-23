/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Validates an instance of a DSL object, checking for the presence of required fields,
 * and custom validation methods.
 */
public class Validator {

    public static final String FAIL_ON_LEVEL_PROPERTY = "klum.validation.failOnLevel";
    public static final String ANY_MEMBER = "*";

    private Validator() {
        // static only
    }


    /**
     * Retrieves the validation results from the given instance and its child objects.
     *
     * @param instance the instance to validate the structure of. Must be a DSL object.
     * @return a list of {@link KlumValidationResult} containing validation errors.
     * @deprecated use {@code KlumObjectSupport.of(instance).getValidation().getSubtreeResults()}
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("java:S1133") // source-compatible OS-2 migration adapter
    public static List<KlumValidationResult> getValidationResultsFromStructure(Object instance) {
        return KlumObjectSupport.of(instance).getValidation().getSubtreeResults();
    }

    /**
     * Retrieves all validation issues from the given instance and its child objects, and throws a {@link KlumValidationException}
     * if any validation issues with the level defined using the system property "klum.validation.failOnLevel" (or ERROR by default).
     *
     * @param instance the instance to validate the structure of. Must be a DSL object.
     * @return a list of {@link KlumValidationResult} containing validation errors.
     * @throws KlumValidationException if validation errors are found with a level equal or worse than the given level.
     * @deprecated use {@code KlumObjectSupport.of(instance).getValidation().verify()}
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("java:S1133") // source-compatible OS-2 migration adapter
    public static List<KlumValidationResult> verifyStructure(Object instance) throws KlumValidationException {
        return KlumObjectSupport.of(instance).getValidation().verify();
    }

    /**
     * Retrieves all validation issues from the given instance and its child objects, and throws a {@link KlumValidationException}
     * if any validation issues with the given fail level or higher are found.
     * @param instance the instance to validate the structure of. Must be a DSL object.
     * @param failLevel the minimum encountered level to result in an exception.
     * @return a list of {@link KlumValidationResult} containing validation errors.
     * @throws KlumValidationException if validation errors are found with a level equal or worse than the given level.
     * @deprecated use {@code KlumObjectSupport.of(instance).getValidation().verify(failLevel)}
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("java:S1133") // source-compatible OS-2 migration adapter
    public static List<KlumValidationResult> verifyStructure(Object instance, Validate.Level failLevel) throws KlumValidationException {
        return KlumObjectSupport.of(instance).getValidation().verify(failLevel);
    }

    /**
     * Retrieves the validation result already stored for the given instance without executing validators.
     *
     * @param instance the instance whose stored validation result is requested
     * @return the stored {@link KlumValidationResult}, or {@code null} when none was recorded
     * @deprecated use {@code KlumObjectSupport.of(instance).getValidation().getResult()} for a completed DSL Object
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("java:S1133") // source-compatible OS-2 migration adapter
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
        return InternalKlumObjectSupport.getValidationResult(instance);
    }

    private static KlumValidationResult doGetOrCreateValidationResult(Object instance) {
        return InternalKlumObjectSupport.getOrCreateValidationResult(instance);
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
}
