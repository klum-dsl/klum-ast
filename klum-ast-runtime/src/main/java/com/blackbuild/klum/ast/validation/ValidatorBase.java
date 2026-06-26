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

/**
 * Convenient base class for inner class validators. Provides access to validation reporting methods of {@link Validator}
 * from a non-static context. Issues created by the methods are added to the validation result of the outer instance
 * of the overriding validator class.
 */
public abstract class ValidatorBase {

    /**
     * Adds an issue to the validation result using the name of the current method as the member name.
     *
     * @param message the message of the issue
     */
    protected void addError(String message) {
        Validator.addError(message);
    }

    /**
     * Adds an issue to the validation result, flagging a different member than the one current validation method.
     *
     * @param member The member to add the issue to
     * @param message the message of the issue
     */
    protected void addErrorToMember(String message, String member) {
        Validator.addErrorToMember(message, member);
    }

    /**
     * Adds an issue to the validation result using the name of the current method as the member name.
     *
     * @param message the message of the issue
     * @param level   the level of the issue
     */
    protected void addIssue(String message, Validate.Level level) {
        Validator.addIssue(message, level);
    }
    
    /**
     * Adds an issue to the validation result.
     *
     * @param member The member to add the issue to. If null, uses the current method name as the member name.
     * @param message the message of the issue
     * @param level   the level of the issue
     */
    protected void addIssueToMember(String message, String member, Validate.Level level) {
        Validator.addIssueToMember(message, member, level);
    }
    
    /**
     * Adds a warning level issue with the given message to the validation result.
     *
     * @param message the message of the issue
     */
    protected void addWarning(String message) {
        Validator.addIssue(message, Validate.Level.WARNING);
    }

    /**
     * Adds a warning level issue with the given message to the validation result, flagging a specific member.
     *
     * @param member the member to add the issue to
     * @param message the message of the issue
     */
    protected void addWarningToMember(String message, String member) {
        Validator.addIssueToMember(message, member, Validate.Level.WARNING);
    }

    /**
     * Suppresses any future non-ERROR issues in the validation result.
     */
    protected void suppressAllFurtherIssues() {
        Validator.suppressFurtherIssues(Validator.ANY_MEMBER);
    }

    /**
     * Suppresses any future non-ERROR issues for the given member in the validation result. Has no effect on already registered issues.
     *
     * @param member the member to suppress issues for.
     */
    protected void suppressFurtherIssuesOn(String member) {
        Validator.suppressFurtherIssues(member);
    }

    /**
     * Suppresses any future issues up to the given level for the given member in the validation result.
     * Has no effect on already registered issues.
     *
     * @param member the member to suppress issues for.
     * @param level  the level of issues to suppress
     */
    protected void suppressFurtherIssuesOn(String member, Validate.Level level) {
        Validator.suppressFurtherIssues(member, level);
    }

    /**
     * Suppresses any future issues up to the given level for all members in the validation result.
     * Has no effect on already registered issues.
     *
     * @param level  the level of issues to suppress
     */
    protected void suppressAllFurtherIssues(Validate.Level level) {
        Validator.suppressFurtherIssues(Validator.ANY_MEMBER, level);
    }
    
    /**
     * Gets the fail level configured for the current validation run.
     *
     * @return the fail level
     */
    protected Validate.Level getFailLevel() {
        return Validator.getFailLevel();
    }

}
