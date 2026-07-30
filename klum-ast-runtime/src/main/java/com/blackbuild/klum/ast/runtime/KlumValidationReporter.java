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
package com.blackbuild.klum.ast.runtime;

import com.blackbuild.klum.ast.Validate;
import com.blackbuild.klum.ast.runtime.internal.InternalKlumObjectSupport;
import com.blackbuild.klum.ast.runtime.internal.process.PhaseDriver;
import com.blackbuild.klum.ast.runtime.validation.KlumValidationIssue;
import com.blackbuild.klum.ast.runtime.validation.KlumValidationResult;

import java.util.Objects;

/**
 * Reports validation diagnostics during a framework-managed lifecycle callback or closure.
 *
 * <p>Instances obtained from {@link KlumSchemaSupport#getKlumValidation()} use the current lifecycle target and its
 * member as the default issue location. Instances obtained from {@link KlumSchemaSupport#klumValidationForObject(Object)}
 * always report on that explicit target and use no member unless an {@code *At} operation supplies one.</p>
 */
public final class KlumValidationReporter {

    private static final String ANY_MEMBER = "*";
    private static final String FAIL_ON_LEVEL_PROPERTY = "klum.validation.failOnLevel";

    private final Object explicitTarget;

    KlumValidationReporter() {
        this(null);
    }

    KlumValidationReporter(Object explicitTarget) {
        this.explicitTarget = explicitTarget;
    }

    /** Records an error at the current lifecycle member or at the explicit target. */
    public void error(String message) {
        issue(message, Validate.Level.ERROR);
    }

    /** Records an error at {@code member}. */
    public void errorAt(String member, String message) {
        issueAt(member, message, Validate.Level.ERROR);
    }

    /** Records an issue at the current lifecycle member or at the explicit target. */
    public void issue(String message, Validate.Level level) {
        report(null, message, level);
    }

    /** Records an issue at {@code member}. */
    public void issueAt(String member, String message, Validate.Level level) {
        report(Objects.requireNonNull(member, "member"), message, level);
    }

    /** Suppresses future non-error issues for {@code member}. */
    public void suppressOn(String member) {
        suppressOn(member, Validate.Level.DEPRECATION);
    }

    /** Suppresses future issues through {@code level} for {@code member}. */
    public void suppressOn(String member, Validate.Level level) {
        InternalKlumObjectSupport.getOrCreateValidationResult(resolveTarget().object())
                .suppressIssues(Objects.requireNonNull(member, "member"), Objects.requireNonNull(level, "level"));
    }

    /** Suppresses future non-error issues for every member. */
    public void suppressAll() {
        suppressAll(Validate.Level.DEPRECATION);
    }

    /** Suppresses future issues through {@code level} for every member. */
    public void suppressAll(Validate.Level level) {
        InternalKlumObjectSupport.getOrCreateValidationResult(resolveTarget().object())
                .suppressIssues(ANY_MEMBER, Objects.requireNonNull(level, "level"));
    }

    /** Returns the fail level configured by {@code klum.validation.failOnLevel}. */
    public Validate.Level getFailLevel() {
        return Validate.Level.fromString(System.getProperty(FAIL_ON_LEVEL_PROPERTY, Validate.Level.ERROR.name()));
    }

    private void report(String member, String message, Validate.Level level) {
        Target target = resolveTarget();
        KlumValidationResult validationResult = InternalKlumObjectSupport.getOrCreateValidationResult(target.object());
        validationResult.addIssue(new KlumValidationIssue(validationResult.getBreadcrumbPath(),
                member != null ? member : target.defaultMember(), Objects.requireNonNull(message, "message"), null,
                Objects.requireNonNull(level, "level")));
    }

    private Target resolveTarget() {
        if (explicitTarget != null)
            return new Target(explicitTarget, null);

        PhaseDriver.Context context = PhaseDriver.getContext();
        if (context == null || context.getInstance() == null)
            throw new KlumSchemaException("Klum validation reporting is only available in a framework-managed lifecycle callback or closure.");
        return new Target(context.getInstance(), context.getMember());
    }

    private record Target(Object object, String defaultMember) {
    }
}
