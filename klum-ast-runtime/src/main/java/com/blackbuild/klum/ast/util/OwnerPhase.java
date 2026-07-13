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
package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.NoClosure;
import com.blackbuild.groovy.configdsl.transform.Role;
import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.process.BuilderVisitingPhaseAction;
import com.blackbuild.klum.ast.util.layer3.StructureUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public class OwnerPhase extends BuilderVisitingPhaseAction {
    public OwnerPhase() {
        super(DefaultKlumPhase.OWNER);
    }

    @Override
    protected void doVisit(@NotNull String path, @NotNull KlumBuilder<?> element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
        if (container == null) return;
        setDirectOwners(element, container);
        setTransitiveOwners(element);
        setRootOwners(element);
        setRoles(element, container);
        LifecycleHelper.executeLifecycleClosures(element, Owner.class);
    }

    private void setDirectOwners(KlumBuilder<?> builder, Object value) {
        DslHelper.getFieldsAnnotatedWith(builder.getClass(), Owner.class)
                .filter(this::isNotTransitive)
                .filter(field -> getOwnerType(field).isInstance(value))
                .filter(field -> isUnset(builder, field))
                .forEach(field -> setOwnerFieldValue(builder, value, field));

        DslHelper.getMethodsAnnotatedWith(builder.getClass(), Owner.class)
                .filter(this::isNotTransitive)
                .filter(method -> getOwnerType(method).isInstance(value))
                .forEach(method -> callOwnerMethod(builder, value, method));
    }

    private void setRoles(KlumBuilder<?> builder, Object container) {
        DslHelper.getFieldsAnnotatedWith(builder.getClass(), Role.class)
                .filter(field -> isUnset(builder, field))
                .filter(field -> field.getAnnotation(Role.class).value().isInstance(container))
                .forEach(field -> setRole(
                        builder,
                        container,
                        path -> builder.setInstanceAttribute(field.getName(), path))
                );

        DslHelper.getMethodsAnnotatedWith(builder.getClass(), Role.class)
                .filter(method -> method.getAnnotation(Role.class).value().isInstance(container))
                .forEach(method -> setRole(
                        builder,
                        container,
                        path -> builder.invokeMethod(method.getName(), path))
                );
    }

    private void setRole(KlumBuilder<?> builder, Object container, Consumer<@NotNull String> action) {
        StructureUtil.getPathOfFieldContaining(container, builder).ifPresent(action);
    }

    private void setTransitiveOwners(KlumBuilder<?> builder) {
        DslHelper.getFieldsAnnotatedWith(builder.getClass(), Owner.class)
                .filter(this::isTransitive)
                .filter(field -> isUnset(builder, field))
                .forEach(field -> setSingleTransitiveOwner(builder, field));

        DslHelper.getMethodsAnnotatedWith(builder.getClass(), Owner.class)
                .filter(this::isTransitive)
                .forEach(method -> callTransitiveOwnerMethod(builder, method));
    }

    private void setRootOwners(KlumBuilder<?> builder) {
        Object root = PhaseDriver.getInstance().getRootObject();

        DslHelper.getFieldsAnnotatedWith(builder.getClass(), Owner.class)
                .filter(this::isRoot)
                .filter(field -> isUnset(builder, field))
                .forEach(field -> setOwnerFieldValue(builder, root, field));

        DslHelper.getMethodsAnnotatedWith(builder.getClass(), Owner.class)
                .filter(this::isRoot)
                .forEach(method -> callOwnerMethod(builder, root, method));
    }

    private static boolean isUnset(KlumBuilder<?> builder, Field field) {
        return builder.getInstanceAttribute(field.getName()) == null;
    }

    private static void callOwnerMethod(KlumBuilder<?> builder, Object value, Method method) {
        builder.invokeMethod(method.getName(), convertValue(method.getAnnotation(Owner.class), value));
    }

    private static void setOwnerFieldValue(KlumBuilder<?> builder, Object value, Field field) {
        Object valueToSet = convertValue(field.getAnnotation(Owner.class), value);
        if (field.getType().isInstance(valueToSet))
            builder.setInstanceAttribute(field.getName(), valueToSet);
    }

    private static Object convertValue(Owner owner, Object originalValue) {
        if (owner.converter() != NoClosure.class)
            return ClosureHelper.invokeClosure(owner.converter(), originalValue);
        return originalValue;
    }

    private void setSingleTransitiveOwner(KlumBuilder<?> builder, Field field) {
        StructureUtil.getAncestorOfType(builder, getOwnerType(field))
                .ifPresent(value -> setOwnerFieldValue(builder, value, field));
    }

    private void callTransitiveOwnerMethod(KlumBuilder<?> builder, Method method) {
        StructureUtil.getAncestorOfType(builder, getOwnerType(method))
                .ifPresent(value -> callOwnerMethod(builder, value, method));
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
