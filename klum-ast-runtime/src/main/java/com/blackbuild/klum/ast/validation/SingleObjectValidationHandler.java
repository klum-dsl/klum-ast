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

import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.util.InternalKlumObjectSupport;

import java.util.ServiceLoader;

/**
 * Validates an instance of a DSL object, checking for the presence of required fields,
 * and custom validation methods.
 */
class SingleObjectValidationHandler {

    public static final String FAIL_ON_LEVEL_PROPERTY = "klum.validation.failOnLevel";
    public static final String ANY_MEMBER = "*";

    private final Object instance;


    protected SingleObjectValidationHandler(Object instance) {
        this.instance = instance;
    }

    public KlumValidationResult getOrCreateValidationResult() {
        return InternalKlumObjectSupport.getOrCreateValidationResult(instance);
    }

    public KlumValidationResult execute() {
        try {
            PhaseDriver.getContext().setInstance(instance);
            return validateInstance();
        } finally {
            PhaseDriver.getContext().setInstance(null);
        }
    }

    private KlumValidationResult validateInstance() {
        KlumValidationResult validationResult = getOrCreateValidationResult();
        ServiceLoader.load(InstanceValidator.class).forEach(handler -> {
            if (InternalKlumObjectSupport.markValidatorExecuted(instance, handler.getClass()))
                handler.validateInstance(instance, validationResult);
        });
        return validationResult;
    }

}
