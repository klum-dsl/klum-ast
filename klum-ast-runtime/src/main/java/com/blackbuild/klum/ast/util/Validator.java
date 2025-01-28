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

import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.util.layer3.KlumVisitorException;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToBoolean;

public class Validator {

    private final Object instance;
    private boolean classHasValidateAnnotation;
    private Class<?> currentType;
    private final List<KlumVisitorException> validationErrors = new ArrayList<>();

    public static void validate(Object instance) throws KlumValidationException {
        Validator validator = new Validator(instance);
        validator.execute();
        if (!validator.validationErrors.isEmpty()) {
            throw new KlumValidationException(validator.validationErrors);
        }
    }

    protected Validator(Object instance) {
        this.instance = instance;
    }

    public void execute() {
        DslHelper.getDslHierarchyOf(instance.getClass()).forEach(this::validateType);
    }

    private void validateType(Class<?> type) {
        currentType = type;
        classHasValidateAnnotation = type.isAnnotationPresent(Validate.class);
        validateFields();
        executeCustomValidationMethods();
    }

    private void executeCustomValidationMethods() {
        for (Method m : currentType.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Validate.class)) continue;

            try {
                validateCustomMethod(m);
            } catch (KlumVisitorException e) {
                validationErrors.add(e);
            }
        }
    }

    private void validateCustomMethod(Method method) {
        withExceptionCheck("Method '" + method.getName() + "'", () -> InvokerHelper.invokeMethod(instance, method.getName(), null));
    }

    private void withExceptionCheck(String message, Runnable runnable) throws KlumVisitorException {
        try {
            runnable.run();
        } catch (KlumVisitorException e) {
            throw e;
        } catch (Exception | AssertionError e) {
            throw new KlumVisitorException(message + ": " + e.getMessage(), instance, e);
        }
    }

    private void validateFields() {
        for (Field field : currentType.getDeclaredFields()) {
            if (!isNotExplicitlyIgnored(field)) continue;
            try {
                validateField(field);
            } catch (KlumVisitorException e) {
                validationErrors.add(e);
            }
        }
    }

    private boolean isNotExplicitlyIgnored(Field field) {
        if (Modifier.isStatic(field.getModifiers())) return false;
        Validate validate = field.getAnnotation(Validate.class);
        return validate == null || validate.value() != Validate.Ignore.class;
    }

    private boolean shouldValidate(Field field) {
        if (field.getName().startsWith("$")) return false;
        if (Modifier.isTransient(field.getModifiers())) return false;
        if (field.isAnnotationPresent(Owner.class)) return false;
        if (field.getType() == boolean.class) return false;

        return classHasValidateAnnotation || field.isAnnotationPresent(Validate.class);
    }

    private void validateField(Field field) {
        if (!shouldValidate(field))
            return;

        Object value = DslHelper.getAttributeValue(field.getName(), instance);

        Validate validate = field.getAnnotation(Validate.class);
        Class<? extends Closure> validationValue = validate != null ? validate.value() : Validate.GroovyTruth.class;

        if (validationValue == Validate.GroovyTruth.class)
            checkAgainstGroovyTruth(field, value, validate);
        else
            withExceptionCheck("Field '" + field.getName() + "'", () -> ClosureHelper.invokeClosureWithDelegate((Class<? extends Closure<Void>>) validationValue, instance, value));
    }

    private void checkAgainstGroovyTruth(Field field, Object value, Validate validate) {
        if (field.getType() == Boolean.class && value != null) return;
        if (castToBoolean(value)) return;

        String message = validate != null ? validate.message() : "";
        if (message.isEmpty())
            message = String.format("Field '%s' must be set.", field.getName());

        throw new KlumVisitorException(message, instance);
    }
}
