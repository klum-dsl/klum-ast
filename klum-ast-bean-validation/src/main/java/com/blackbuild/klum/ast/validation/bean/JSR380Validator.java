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
package com.blackbuild.klum.ast.validation.bean;

import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.validation.InstanceValidator;
import com.blackbuild.klum.ast.validation.KlumValidationIssue;
import com.blackbuild.klum.ast.validation.KlumValidationResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import java.util.Set;

public class JSR380Validator implements InstanceValidator {
    private KlumValidationResult validationResult;

    @Override
    public void validateInstance(Object instance, KlumValidationResult validationResult) {
        this.validationResult = validationResult;
        try (ValidatorFactory factory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<Object>> validationResults = validator.validate(instance);

            validationResults.stream().map(this::mapViolationToResult)
                    .forEach(validationResult::addIssue);
        }

    }

    private KlumValidationIssue mapViolationToResult(ConstraintViolation<Object> objectConstraintViolation) {
        return new KlumValidationIssue(validationResult.getBreadcrumbPath(), null, objectConstraintViolation.getMessage(), null, Validate.Level.ERROR);
    }
}
