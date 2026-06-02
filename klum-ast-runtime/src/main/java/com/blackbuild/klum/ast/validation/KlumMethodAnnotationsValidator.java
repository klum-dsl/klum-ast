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
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Validator that validates {@link com.blackbuild.groovy.configdsl.transform.Validate} annotations on methods of the instance.
 */
public class KlumMethodAnnotationsValidator extends KlumAnnotationsValidator {

    @Override
    protected void doValidateLayer() {
        for (Method m : currentType.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Validate.class)) continue;
            validateCustomMethod(m).ifPresent(validationResult::addIssue);
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

}
