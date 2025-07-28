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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Validation results for a single object.
 */
public class KlumValidationResult implements Serializable {
    private final List<KlumValidationProblem> validationProblems = new ArrayList<>();
    private final String breadcrumbPath;

    public KlumValidationResult(String breadcrumbPath) {
        this.breadcrumbPath = breadcrumbPath;
    }

    public void addProblem(KlumValidationProblem problem) {
        this.validationProblems.add(problem);
    }

    public void addProblems(List<KlumValidationProblem> problems) {
        validationProblems.addAll(problems);
    }

    public KlumValidationProblem.Level getMaxLevel() {
        return validationProblems.stream()
                .map(KlumValidationProblem::getLevel)
                .max(KlumValidationProblem.Level::compareTo)
                .orElse(KlumValidationProblem.Level.NONE);
    }

    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("at ").append(breadcrumbPath).append(":\n");
        validationProblems.forEach(e ->
                sb.append(" - ")
                        .append(e.getLocalMessage())
                        .append("\n"));
        return sb.toString();
    }

    public boolean has(KlumValidationProblem.Level level) {
        return getMaxLevel().equalOrWorseThan(level);
    }


    public void throwOn(KlumValidationProblem.Level level) throws KlumValidationException {
        if (getMaxLevel().equalOrWorseThan(level))
            throw new KlumValidationException(List.of(this));
    }
}
