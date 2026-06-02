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

import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.util.ClosureHelper;
import com.blackbuild.klum.ast.util.DslHelper;
import groovy.lang.Closure;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToBoolean;

/**
 * Validator that validates {@link Validate} annotations on fields of the instance.
 */
public class KlumFieldAnnotationsValidator extends KlumAnnotationsValidator {

    @Override
    protected void doValidateLayer() {
        for (Field field : currentType.getDeclaredFields()) {
            if (!isNotExplicitlyIgnored(field)) continue;
            validateField(field).ifPresent(validationResult::addIssue);
        }
    }

    private boolean isNotExplicitlyIgnored(Field field) {
        if (Modifier.isStatic(field.getModifiers())) return false;
        return getValidateAnnotationOrDefault(field).value() != Validate.Ignore.class;
    }

    private boolean shouldValidate(Field field) {
        if (field.getName().startsWith("$")) return false;
        if (Modifier.isTransient(field.getModifiers())) return false;
        if (field.isAnnotationPresent(Owner.class)) return false;
        if (field.getType() == boolean.class) return false;

        return classHasValidateAnnotation || field.isAnnotationPresent(Validate.class);
    }

    private Optional<KlumValidationIssue> validateField(Field field) {
        if (!shouldValidate(field))
            return Optional.empty();

        Object value = DslHelper.getAttributeValue(field.getName(), instance);

        if (instance.getClass().isAnnotationPresent(Validate.class) && field.isAnnotationPresent(Deprecated.class) && !field.isAnnotationPresent(Validate.class))
            return Optional.empty();

        Validate validate = getValidateAnnotationOrDefault(field);

        if (validate.value() == Validate.GroovyTruth.class)
            return checkAgainstGroovyTruth(field, value, validate);
        else
            return withExceptionCheck(
                    field.getName(),
                    validate.level(),
                    () -> ClosureHelper.invokeClosureWithDelegate((Class<? extends Closure<Void>>) validate.value(), instance, value)
            );
    }

    private Optional<KlumValidationIssue> checkAgainstGroovyTruth(Field field, Object value, Validate validate) {
        if (isGroovyTruth(field, value)) return Optional.empty();

        String message = validate.message();

        if (message.isEmpty())
            message = String.format("Field '%s' must be set", field.getName());

        return Optional.of(new KlumValidationIssue(breadcrumbPath, field.getName(), message, null, validate.level()));
    }

    @SuppressWarnings("java:S1126")
    private boolean isGroovyTruth(Field field, Object value) {
        if (field.getType() == Boolean.class && value != null) return true;
        if (castToBoolean(value)) return true;
        return false;
    }

}
