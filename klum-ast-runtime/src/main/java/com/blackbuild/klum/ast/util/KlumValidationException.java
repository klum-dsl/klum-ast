/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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

import com.blackbuild.klum.ast.util.layer3.KlumVisitorException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KlumValidationException extends KlumException {

    private final Map<String, List<KlumVisitorException>> validationErrors = new LinkedHashMap<>();

    public KlumValidationException(List<KlumVisitorException> validationErrors) {
        for (KlumVisitorException validationError : validationErrors) {
            addException(validationError);
        }
    }

    private void addException(KlumVisitorException validationError) {
        addSuppressed(validationError);
        this.validationErrors.computeIfAbsent(validationError.getBreadcrumbPath(), k -> new ArrayList<>()).add(validationError);
    }

    public KlumValidationException() {
    }

    public void merge(KlumValidationException other) {
        for (List<KlumVisitorException> errors : other.getValidationErrors().values()) {
            for (KlumVisitorException validationError : errors) {
                addException(validationError);
            }
        }
    }

    public Map<String, List<KlumVisitorException>> getValidationErrors() {
        return validationErrors;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation errors:\n");
        validationErrors.forEach((key, value) -> {
            sb.append("  at ").append(key).append(":\n");
            value.forEach(e -> sb.append("    - ").append(e.getUnlocalizedMessage()).append("\n"));
        });
        return sb.toString();
    }
}
