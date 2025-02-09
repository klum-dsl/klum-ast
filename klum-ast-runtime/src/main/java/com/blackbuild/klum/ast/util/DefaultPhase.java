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

import com.blackbuild.groovy.configdsl.transform.Default;
import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.VisitingPhaseAction;
import com.blackbuild.klum.ast.util.layer3.ClusterModel;
import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Objects;

import static com.blackbuild.klum.ast.util.ClosureHelper.invokeClosureWithDelegateAsArgument;
import static com.blackbuild.klum.ast.util.ClosureHelper.isClosureType;
import static com.blackbuild.klum.ast.util.DslHelper.castTo;

public class DefaultPhase extends VisitingPhaseAction {

    public DefaultPhase() {
        super(DefaultKlumPhase.DEFAULT);
    }

    @Override
    public void visit(String path, Object element, Object container) {
        setFieldsAnnotatedWithDefaultAnnotation(element);
        setDefaultValuesFromDefaultValuesAnnotationOnOwnerField(element, container);
        setDefaultValuesFromDefaultValueAnnotationsOnType(element);
        executeDefaultLifecycleMethods(element);
    }

    private void setDefaultValuesFromDefaultValuesAnnotationOnOwnerField(Object element, Object container) {

    }

    private void setDefaultValuesFromDefaultValueAnnotationsOnType(Object element) {
        DslHelper.getDslHierarchyOf(element.getClass()).stream()
                .flatMap(layer -> AnnotationHelper.getMetaAnnotated(layer, DefaultValues.class))
                .filter(Objects::nonNull)
                .forEach(annotation -> setDefaultValuesFromAnnotation(element, annotation));
        }

    private void setDefaultValuesFromAnnotation(Object element, Annotation valuesAnnotation) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(element);
        AnnotationHelper.getNonDefaultMembers(valuesAnnotation)
                .forEach((field, value) -> setFieldToDefaultValue(field, value, proxy));
    }

    private static void setFieldToDefaultValue(String field, Object value, KlumInstanceProxy proxy) {
        if (!isEmpty(proxy.getInstanceAttribute(field))) return;
        Class<?> fieldType = proxy.getField(field).getType();

        if (isClosureType(value) && !Closure.class.isAssignableFrom(fieldType))
            value = invokeClosureWithDelegateAsArgument((Class<? extends Closure<Object>>) value, proxy.getDSLInstance());

        proxy.setInstanceAttribute(field, castTo(value, fieldType));
    }

    private static void executeDefaultLifecycleMethods(Object element) {
        LifecycleHelper.executeLifecycleMethods(KlumInstanceProxy.getProxyFor(element), Default.class);
    }

    private void setFieldsAnnotatedWithDefaultAnnotation(Object element) {
        ClusterModel.getFieldsAnnotatedWith(element, Default.class)
                .entrySet()
                .stream()
                .filter(this::isUnset)
                .forEach(entry -> applyDefaultValue(element, entry.getKey()));
    }

    private void applyDefaultValue(Object element, String fieldName) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(element);
        Object defaultValue = getDefaultValue(proxy, fieldName);
        proxy.setSingleField(fieldName, defaultValue);
    }

    @SuppressWarnings({"OptionalGetWithoutIsPresent", "java:S3655"})
    private Object getDefaultValue(KlumInstanceProxy proxy, String fieldName) {
        Field field = DslHelper.getField(proxy.getDSLInstance().getClass(), fieldName).get();
        Default defaultAnnotation = field.getAnnotation(Default.class);
        if (defaultAnnotation == null) return null;
        Class<?> fieldType = field.getType();

        if (!defaultAnnotation.field().isEmpty()) {
            Object defaultValue = proxy.getInstanceProperty(defaultAnnotation.field());
            if (defaultValue != null)
                return castTo(defaultValue, fieldType);
            return getDefaultValue(proxy, defaultAnnotation.field()); // special case: cascade defaults
        } else if (!defaultAnnotation.delegate().isEmpty()) {
            Object delegationTarget = proxy.getInstanceProperty(defaultAnnotation.delegate());
            return delegationTarget != null ? castTo(InvokerHelper.getProperty(delegationTarget, fieldName), fieldType) : null;
        } else {
            return castTo(invokeClosureWithDelegateAsArgument(defaultAnnotation.code(), proxy.getDSLInstance()), fieldType);
        }
    }

}
