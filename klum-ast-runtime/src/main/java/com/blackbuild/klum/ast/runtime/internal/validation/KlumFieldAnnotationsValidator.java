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
package com.blackbuild.klum.ast.runtime.internal.validation;

import com.blackbuild.klum.ast.RelationshipConstraint;
import com.blackbuild.klum.ast.Owner;
import com.blackbuild.klum.ast.Validate;
import com.blackbuild.klum.ast.runtime.internal.AnnotationHelper;
import com.blackbuild.klum.ast.runtime.internal.ClosureHelper;
import com.blackbuild.klum.ast.runtime.internal.DslHelper;
import com.blackbuild.klum.ast.runtime.internal.process.PhaseDriver;
import com.blackbuild.klum.ast.runtime.validation.KlumValidationIssue;
import groovy.lang.Closure;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToBoolean;

/**
 * Validator that validates {@link Validate} annotations on fields of the instance.
 */
public class KlumFieldAnnotationsValidator extends KlumLayeredAnnotationsValidator {

    @Override
    protected void doValidateLayer() {
        for (Field field : currentLayer.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) validateRelationshipConstraints(field);
            if (!isNotExplicitlyIgnored(field)) continue;
            validateField(field).ifPresent(validationResult::addIssue);
        }
    }

    private void validateRelationshipConstraints(Field field) {
        List<Annotation> annotations = AnnotationHelper.getMetaAnnotated(field, RelationshipConstraint.class).toList();
        if (annotations.isEmpty()) return;
        Object value = DslHelper.getAttributeValue(field.getName(), instance);
        if (value == null) return;
        Map<Object, String> elementContexts = new IdentityHashMap<>();
        Set<String> usedContexts = new HashSet<>();
        for (Annotation annotation : annotations) {
            RelationshipConstraint marker = annotation.annotationType().getAnnotation(RelationshipConstraint.class);
            String label = "Relationship constraint @" + annotation.annotationType().getSimpleName();
            validateRelationshipEntries(field, value, annotation, marker, label, elementContexts, usedContexts);
        }
    }

    private void validateRelationshipEntries(Field field, Object value, Annotation annotation, RelationshipConstraint marker,
                                           String label, Map<Object, String> elementContexts, Set<String> usedContexts) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet())
                validateRelationshipTarget(field, annotation, marker, label + " at key '" + entry.getKey() + "'", entry.getValue());
        } else if (value instanceof List<?> list) {
            int index = 0;
            for (Object element : list)
                validateRelationshipTarget(field, annotation, marker, label + " at index " + index++, element);
        } else if (value instanceof Collection<?> collection) {
            validateNonPositionalCollection(field, annotation, marker, label, collection, elementContexts, usedContexts);
        } else {
            validateRelationshipTarget(field, annotation, marker, label, value);
        }
    }

    private void validateNonPositionalCollection(Field field, Annotation annotation, RelationshipConstraint marker,
                                                 String label, Collection<?> collection,
                                                 Map<Object, String> elementContexts, Set<String> usedContexts) {
        for (Object element : collection) {
            if (element == null) continue;
            String context = elementContexts.computeIfAbsent(element,
                    target -> newElementContext(target, usedContexts));
            validateRelationshipTarget(field, annotation, marker, label + " at " + context, element);
        }
    }

    private static String newElementContext(Object element, Set<String> usedContexts) {
        // An opaque identity token distinguishes Set entries without claiming an iteration position.
        String base = "element @" + Integer.toHexString(System.identityHashCode(element));
        String context = base;
        int collision = 2;
        while (!usedContexts.add(context)) context = base + "-" + collision++;
        return context;
    }

    @SuppressWarnings("unchecked")
    private void validateRelationshipTarget(Field field, Annotation annotation, RelationshipConstraint marker,
                                         String label, Object value) {
        if (value == null) return;
        try {
            PhaseDriver.getContext().setMember(field.getName());
            ClosureHelper.invokeClosureWithDelegate(
                    (Class<? extends Closure<Object>>) marker.value(), instance, annotation, value);
        } catch (AssertionError error) {
            validationResult.addIssue(new KlumValidationIssue(breadcrumbPath, field.getName(),
                    label + ": " + error.getMessage(), null, Validate.Level.ERROR));
        } catch (Exception error) {
            validationResult.addIssue(new KlumValidationIssue(breadcrumbPath, field.getName(),
                    label + ": " + error.getMessage(), error, Validate.Level.ERROR));
        } finally {
            PhaseDriver.getContext().setMember(null);
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
