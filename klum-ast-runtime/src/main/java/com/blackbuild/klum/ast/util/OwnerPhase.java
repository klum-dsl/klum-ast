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
import com.blackbuild.groovy.configdsl.transform.NoClosure;
import com.blackbuild.groovy.configdsl.transform.Role;
import com.blackbuild.klum.ast.process.KlumPhase;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.process.VisitingPhaseAction;
import com.blackbuild.klum.ast.util.layer3.StructureUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public class OwnerPhase extends VisitingPhaseAction {
    public OwnerPhase() {
        super(KlumPhase.OWNER);
    }

    @Override
    public void visit(String path, Object element, Object container) {
        if (container == null) return;
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(element);
        setDirectOwners(proxy, container);
        setTransitiveOwners(proxy);
        setRootOwners(proxy);
        setRoles(proxy, container);
        LifecycleHelper.executeLifecycleClosures(proxy, Owner.class);
    }

    private void setDirectOwners(KlumInstanceProxy proxy, Object value) {
        DslHelper.getFieldsAnnotatedWith(proxy.getDSLInstance().getClass(), Owner.class)
                .filter(this::isNotTransitive)
                .filter(field -> getOwnerType(field).isInstance(value))
                .filter(field -> isUnset(proxy, field))
                .forEach(field -> setOwnerFieldValue(proxy, value, field));

        DslHelper.getMethodsAnnotatedWith(proxy.getRwInstance().getClass(), Owner.class)
                .filter(this::isNotTransitive)
                .filter(method -> getOwnerType(method).isInstance(value))
                .forEach(method -> callOwnerMethod(proxy, value, method));
    }

    private void setRoles(KlumInstanceProxy proxy, Object container) {
        DslHelper.getFieldsAnnotatedWith(proxy.getDSLInstance().getClass(), Role.class)
                .filter(field -> isUnset(proxy, field))
                .filter(field -> field.getAnnotation(Role.class).value().isInstance(container))
                .forEach(field -> setRole(
                        proxy,
                        container,
                        path -> proxy.setInstanceAttribute(field.getName(), path))
                );

        DslHelper.getMethodsAnnotatedWith(proxy.getRwInstance().getClass(), Role.class)
                .filter(method -> method.getAnnotation(Role.class).value().isInstance(container))
                .forEach(method -> setRole(
                        proxy,
                        container,
                        path -> proxy.getRwInstance().invokeMethod(method.getName(), path))
                );
    }

    private void setRole(KlumInstanceProxy proxy, Object container, Consumer<@NotNull String> action) {
        StructureUtil.getPathOfFieldContaining(container, proxy.getDSLInstance()).ifPresent(action);
    }

    private void setTransitiveOwners(KlumInstanceProxy proxy) {
        DslHelper.getFieldsAnnotatedWith(proxy.getDSLInstance().getClass(), Owner.class)
                .filter(this::isTransitive)
                .filter(field -> isUnset(proxy, field))
                .forEach(field -> setSingleTransitiveOwner(proxy, field));

        DslHelper.getMethodsAnnotatedWith(proxy.getRwInstance().getClass(), Owner.class)
                .filter(this::isTransitive)
                .forEach(method -> callTransitiveOwnerMethod(proxy, method));
    }

    private void setRootOwners(KlumInstanceProxy proxy) {
        Object root = PhaseDriver.getInstance().getRootObject();

        DslHelper.getFieldsAnnotatedWith(proxy.getDSLInstance().getClass(), Owner.class)
                .filter(this::isRoot)
                .filter(field -> isUnset(proxy, field))
                .forEach(field -> setOwnerFieldValue(proxy, root, field));

        DslHelper.getMethodsAnnotatedWith(proxy.getRwInstance().getClass(), Owner.class)
                .filter(this::isRoot)
                .forEach(method -> callOwnerMethod(proxy, root, method));
    }

    private static boolean isUnset(KlumInstanceProxy proxy, Field field) {
        return proxy.getInstanceAttribute(field.getName()) == null;
    }

    private static void callOwnerMethod(KlumInstanceProxy proxy, Object value, Method method) {
        proxy.getRwInstance().invokeMethod(method.getName(), convertValue(method.getAnnotation(Owner.class), value));
    }

    private static void setOwnerFieldValue(KlumInstanceProxy proxy, Object value, Field field) {
        proxy.setInstanceAttribute(field.getName(), convertValue(field.getAnnotation(Owner.class), value));
    }

    private static Object convertValue(Owner owner, Object originalValue) {
        if (owner.converter() != NoClosure.class)
            return ClosureHelper.invokeClosure(owner.converter(), originalValue);
        return originalValue;
    }

    private void setSingleTransitiveOwner(KlumInstanceProxy proxy, Field field) {
        StructureUtil.getAncestorOfType(proxy.getDSLInstance(), getOwnerType(field))
                .ifPresent(value -> setOwnerFieldValue(proxy, value, field));
    }

    private void callTransitiveOwnerMethod(KlumInstanceProxy proxy, Method method) {
        StructureUtil.getAncestorOfType(proxy.getDSLInstance(), getOwnerType(method))
                .ifPresent(value -> callOwnerMethod(proxy, value, method));
    }

    private boolean isTransitive(AnnotatedElement target) {
        return target.getAnnotation(Owner.class).transitive();
    }

    private boolean isNotTransitive(AnnotatedElement target) {
        return !isTransitive(target);
    }

    private boolean isRoot(AnnotatedElement target) {
        return target.getAnnotation(Owner.class).root();
    }

    private boolean isNotRoot(AnnotatedElement target) {
        return !isRoot(target);
    }

    private Class<?> getOwnerType(AnnotatedElement target) {
        Owner annotation = target.getAnnotation(Owner.class);
        if (annotation.converter() != NoClosure.class)
            return ClosureHelper.getFirstParameterType(annotation.converter());

        if (target instanceof Field)
            return ((Field) target).getType();
        else
            return ((Method) target).getParameterTypes()[0];
    }
}
