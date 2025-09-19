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

import com.blackbuild.groovy.configdsl.transform.Validate;

import java.io.Serializable;
import java.util.*;

/**
 * Validation results for a single object.
 */
public class KlumValidationResult implements Serializable {
    public static final String METADATA_KEY = KlumValidationResult.class.getName();
    private final NavigableSet<KlumValidationIssue> validationProblems = new TreeSet<>();
    private final String breadcrumbPath;
    private final Set<String> suppressedIssues = new HashSet<>();

    public KlumValidationResult(String breadcrumbPath) {
        this.breadcrumbPath = breadcrumbPath;
    }

    public String getBreadcrumbPath() {
        return breadcrumbPath;
    }

    public void addProblem(KlumValidationIssue problem) {
        if (suppressedIssues.contains(Validator.ANY_MEMBER)) return;
        if (!suppressedIssues.contains(problem.getMember()))
            this.validationProblems.add(problem);
    }

    public void addProblems(List<KlumValidationIssue> problems) {
        if (suppressedIssues.contains(Validator.ANY_MEMBER)) return;
        for (KlumValidationIssue problem : problems)
            addProblem(problem);
    }

    public Validate.Level getMaxLevel() {
        return validationProblems.stream()
                .map(KlumValidationIssue::getLevel)
                .max(Validate.Level::compareTo)
                .orElse(Validate.Level.NONE);
    }

    public String getMessage(Validate.Level minimumLevel) {
        if (breadcrumbPath == null)
            return getMessageWithFullPaths();

        if (validationProblems.isEmpty())
            return breadcrumbPath + ": NONE";

        StringBuilder sb = new StringBuilder();
        sb.append(breadcrumbPath).append(":\n");
        for (KlumValidationIssue e : validationProblems)
            if (e.getLevel().equalOrWorseThan(minimumLevel))
                sb.append("- ")
                        .append(e.getLocalMessage())
                        .append("\n");
        // remove trailing newline
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    public String getMessage() {
        return getMessage(Validate.Level.NONE);
    }

    String getMessageWithFullPaths(Validate.Level minimumLevel) {
        if (validationProblems.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        for (KlumValidationIssue e : validationProblems)
            if (e.getLevel().equalOrWorseThan(minimumLevel))
                sb.append(e.getFullMessage()).append("\n");
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    String getMessageWithFullPaths() {
        return getMessageWithFullPaths(Validate.Level.NONE);
    }

    public boolean has(Validate.Level level) {
        return getMaxLevel().equalOrWorseThan(level);
    }

    public void throwOn(Validate.Level level) throws KlumValidationException {
        if (getMaxLevel().equalOrWorseThan(level))
            throw new KlumValidationException(List.of(this));
    }

    public Collection<KlumValidationIssue> getProblems() {
        return validationProblems;
    }

    public void suppressIssues(String member) {
        suppressedIssues.add(member);
    }
}
