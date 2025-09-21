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

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;

/**
 * Denotes an exception that means a validation error occurred in the model.
 * This is used to collect multiple validation errors and provide a detailed message.
 * <p>
 *     Note that this is explicitly not a subclass of {@link KlumModelException}, since a KlumModelException
 *     points to a specific model element that is invalid, while this exception collects multiple validation errors.
 * </p>
 */
public class KlumValidationException extends KlumException {

    private final List<KlumValidationResult> validationResults;

    public KlumValidationException(List<KlumValidationResult> validationResults) {
        this.validationResults = new ArrayList<>(validationResults);
    }

    @Override
    public String getMessage() {
        return getMessage(Validate.Level.INFO);
    }

    public String getMessage(Validate.Level minimumLevel) {
        return validationResults.stream()
                .filter(klumValidationResult -> klumValidationResult.has(minimumLevel))
                .map(klumValidationResult -> klumValidationResult.getMessage(minimumLevel))
                .collect(joining("\n"));
    }

    public List<KlumValidationResult> getValidationResults() {
        return validationResults;
    }
}
