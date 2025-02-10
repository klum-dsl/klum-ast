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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.VisitingPhaseAction;
import com.blackbuild.klum.ast.util.*;
import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate;
import groovy.lang.Closure;

import java.lang.reflect.Field;
import java.util.Map;

import static com.blackbuild.klum.ast.util.DslHelper.isInstantiable;
import static java.lang.String.format;

public class AutoCreationPhase extends VisitingPhaseAction {

    public AutoCreationPhase() {
        super(DefaultKlumPhase.AUTO_CREATE);
    }

    @Override
    public void visit(String path, Object element, Object container, String nameOfFieldInContainer) {
        ClusterModel.getFieldsAnnotatedWith(element, AutoCreate.class)
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() == null)
                .forEach(entry -> autoCreate(element, entry.getKey()));

        LifecycleHelper.executeLifecycleMethods(KlumInstanceProxy.getProxyFor(element), AutoCreate.class);
    }

    private void autoCreate(Object element, String fieldName) {
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Field field = DslHelper.getField(element.getClass(), fieldName).get();
        AutoCreate autoCreate = field.getAnnotation(AutoCreate.class);
        Map<String, Object> values = ClosureHelper.invokeClosure(autoCreate.value());

        String key = autoCreate.key();
        if (AutoCreate.DEFAULT_KEY.equals(key))
            key = null;
        // TODO: Validation AST
        if (key == null && DslHelper.isKeyed(field.getType()))
            throw new KlumSchemaException(format("AutoCreate annotation for field '%s' is missing a 'key' field.", fieldName));
        else if (key != null && !DslHelper.isKeyed(field.getType()))
            throw new KlumSchemaException(format("AutoCreate annotation for field '%s' has a key field, but annotated type '%s' is not keyed", fieldName, field.getType().getName()));

        Class<?> type = autoCreate.type();
        if (type.equals(Object.class)) {
            if (field.getType().equals(Closure.class)) return;
            if (!isInstantiable(field.getType()))
                throw new KlumSchemaException(format("AutoCreate annotation for abstract typed field '%s' is missing a 'type' field.", fieldName));
            type = field.getType();
        } else if (!field.getType().isAssignableFrom(type)) {
            throw new KlumSchemaException(
                    format("AutoCreate annotation for field '%s' sets type '%s' which is no subtype of the field's type (%s)", fieldName, type, field.getType()));
        }

        Object autoCreated = FactoryHelper.create(type, values, key, null);

        KlumInstanceProxy.getProxyFor(element).setSingleField(fieldName, autoCreated);
    }

}
