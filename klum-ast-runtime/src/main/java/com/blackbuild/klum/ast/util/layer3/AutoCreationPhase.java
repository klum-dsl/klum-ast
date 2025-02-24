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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.VisitingPhaseAction;
import com.blackbuild.klum.ast.util.*;
import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate;
import com.blackbuild.klum.ast.util.layer3.annotations.Cluster;
import groovy.lang.Closure;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static com.blackbuild.klum.ast.util.DslHelper.*;
import static java.lang.String.format;

public class AutoCreationPhase extends VisitingPhaseAction {

    public AutoCreationPhase() {
        super(DefaultKlumPhase.AUTO_CREATE);
    }

    @Override
    public void visit(String path, Object element, Object container, String nameOfFieldInContainer) {
        ClusterModel.getPropertiesStream(element, Object.class)
                .filter(entry -> entry.getValue() == null)
                .map(pv -> ClusterModel.getField(element, pv.getName()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(field -> field.isAnnotationPresent(AutoCreate.class))
                .forEach(field -> autoCreate(element, field));

        autoCreateClusterFields(element);

        LifecycleHelper.executeLifecycleMethods(KlumInstanceProxy.getProxyFor(element), AutoCreate.class);
    }

    private void autoCreate(Object element, Field field) {
        Optional<AutoCreate> autoCreate = AnnotationHelper.getAnnotation(field, AutoCreate.class);
        Map<String, Object> values = autoCreate.map(a -> ClosureHelper.invokeClosure(a.value())).orElse(Collections.emptyMap());

        String key = autoCreate.map(AutoCreate::key).orElse(null);
        if (AutoCreate.DEFAULT_KEY.equals(key))
            key = null;
        // TODO: Validation AST
        if (key == null && DslHelper.isKeyed(field.getType()))
            throw new KlumSchemaException(format("AutoCreate annotation for field '%s' is missing a 'key' field.", field.getName()));
        else if (key != null && !DslHelper.isKeyed(field.getType()))
            throw new KlumSchemaException(format("AutoCreate annotation for field '%s' has a key field, but annotated type '%s' is not keyed", field.getName(), field.getType().getName()));

        Class<?> type = autoCreate.isPresent() ? autoCreate.get().type() : Object.class;
        if (type.equals(Object.class)) {
            if (field.getType().equals(Closure.class)) return;
            if (!isInstantiable(field.getType()))
                throw new KlumSchemaException(format("AutoCreate annotation for abstract typed field '%s' is missing a 'type' field.", field.getName()));
            type = field.getType();
        } else if (!field.getType().isAssignableFrom(type)) {
            throw new KlumSchemaException(
                    format("AutoCreate annotation for field '%s' sets type '%s' which is no subtype of the field's type (%s)", field.getName(), type, field.getType()));
        }

        Object autoCreated = FactoryHelper.create(type, values, key, null);

        KlumInstanceProxy.getProxyFor(element).setSingleField(field.getName(), autoCreated);
    }

    private void autoCreateClusterFields(Object element) {
        Predicate<Method> autoCreateFilter = clusterField -> clusterField.getAnnotation(AutoCreate.class) != null;

        ClusterModel.getMethodsAnnotatedWithStream(element, Map.class, Cluster.class)
                .filter(autoCreateFilter)
                .forEach(clusterMethod -> autoCreateElementsForCluster(element, clusterMethod));
    }

    private void autoCreateElementsForCluster(Object element, Method clusterMethod) {
        Cluster cluster = clusterMethod.getAnnotation(Cluster.class);
        Class<? extends Annotation> filterAnnotation = cluster.value();
        Predicate<AnnotatedElement> clusterFilter = filterAnnotation != Cluster.Undefined.class ? elementToCheck -> elementToCheck.isAnnotationPresent(filterAnnotation) : elementToCheck -> true;

        Type elementType = getElementType(clusterMethod.getGenericReturnType());
        if (!(elementType instanceof Class))
            throw new KlumSchemaException(format("Cluster annotation on method '%s', whose element generics is no class (%s)", clusterMethod.getName(), elementType.getTypeName()));

        if (!isDslType(elementType))
            return;

        ClusterModel.getPropertiesStream(element, (Class<?>) elementType, clusterFilter)
                .filter(propertyValue -> propertyValue.getValue() == null)
                .map(pv -> ClusterModel.getField(element, pv.getName()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(property -> autoCreate(element, property));
    }


}
