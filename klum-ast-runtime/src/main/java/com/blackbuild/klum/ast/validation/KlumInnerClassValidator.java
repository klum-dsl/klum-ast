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
import com.blackbuild.klum.ast.util.KlumSchemaException;
import com.blackbuild.klum.ast.util.LifecycleHelper;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

public class KlumInnerClassValidator extends KlumAnnotationsValidator {

    @Override
    protected void doValidateInstance() {
        LifecycleHelper.getLifecycleClasses(instance.getClass(), Validate.class).forEach(this::validateInnerClass);
    }

    private void validateInnerClass(Class<?> validatorClass) {
        try {
            Object validatorInstance = validatorClass.getConstructor(validatorClass.getDeclaringClass()).newInstance(instance);
            Arrays.stream(validatorClass.getMethods())
                    .filter(method -> LifecycleHelper.isValidLifecycleClassMethod(method, Validate.class))
                    .forEach(method -> validateMethod(validatorInstance, method));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new KlumSchemaException(e);
        }

    }

    private void validateMethod(Object validatorInstance, Method method) {
        Validate.Level level = getValidationLevelForMethod(method);
        Optional<KlumValidationIssue> issue = withExceptionCheck(
                validatorInstance.getClass().getSimpleName() + "#" + method.getName() + "()",
                level,
                () -> InvokerHelper.invokeMethod(validatorInstance, method.getName(), null)
        );

        issue.ifPresent(validationResult::addIssue);
    }

    private Validate.Level getValidationLevelForMethod(Method method) {
        Validate validate = method.getAnnotation(Validate.class);

        if (validate != null) return validate.level();

        return getValidateAnnotationOrDefault(method.getDeclaringClass()).level();
    }
}
