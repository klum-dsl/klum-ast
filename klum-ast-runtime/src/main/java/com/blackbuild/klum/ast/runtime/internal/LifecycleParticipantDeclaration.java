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
package com.blackbuild.klum.ast.runtime.internal;

import com.blackbuild.klum.ast.Default;
import com.blackbuild.klum.ast.PostTree;
import com.blackbuild.klum.ast.layer3.AutoCreate;
import com.blackbuild.klum.ast.layer3.AutoLink;
import com.blackbuild.klum.ast.layer3.LinkTo;
import com.blackbuild.klum.ast.runtime.KlumSchemaException;
import com.blackbuild.klum.ast.runtime.LifecycleCreator;
import com.blackbuild.klum.ast.runtime.LifecycleMutator;
import com.blackbuild.klum.ast.runtime.LifecycleCreationHandler;
import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Runtime defense for separately compiled inputs, without constructing or caching handlers. */
final class LifecycleParticipantDeclaration {
    private LifecycleParticipantDeclaration() {}

    static void check(Field field) {
        Map<Class<? extends Annotation>, Integer> creators = new HashMap<>();
        for (Class<? extends Annotation> builtIn : Set.of(AutoCreate.class, LinkTo.class, Default.class)) {
            if (field.isAnnotationPresent(builtIn))
                creators.put(builtIn == LinkTo.class ? AutoLink.class : builtIn, 1);
        }
        for (Annotation annotation : field.getDeclaredAnnotations())
            checkParticipantAnnotation(field, annotation, creators);
    }

    private static void checkParticipantAnnotation(Field field, Annotation annotation,
                                                   Map<Class<? extends Annotation>, Integer> creators) {
        Class<? extends Annotation> domain = annotation.annotationType();
        LifecycleCreator[] creation = domain.getAnnotationsByType(LifecycleCreator.class);
        LifecycleMutator[] mutation = domain.getAnnotationsByType(LifecycleMutator.class);
        if (creation.length == 0 && mutation.length == 0) return;
        if (Modifier.isStatic(field.getModifiers()) || !DslHelper.isDslType(field.getType()))
            throw new KlumSchemaException("Lifecycle participant requires a non-static direct DSL field: " + field);
        for (LifecycleCreator creator : creation) {
            checkHandler(field, domain, creator.phase(), creator.handler(), LifecycleCreationHandler.class);
            if (creators.merge(creator.phase(), 1, Integer::sum) > 1)
                throw new KlumSchemaException("Competing lifecycle creators for " + creator.phase().getSimpleName() + " on " + field);
        }
        for (LifecycleMutator mutator : mutation)
            checkHandler(field, domain, mutator.phase(), mutator.handler(), LifecycleMutationHandler.class);
    }

    private static void checkHandler(Field field, Class<? extends Annotation> domain, Class<? extends Annotation> phase,
                                     Class<?> handler, Class<?> role) {
        String location = "Participant " + domain.getName() + " handler " + handler.getName() + " on " + field;
        if (!Set.of(AutoCreate.class, AutoLink.class, Default.class, PostTree.class).contains(phase))
            throw new KlumSchemaException(location + ": Lifecycle participants support only AutoCreate, AutoLink, Default and PostTree");
        if (!Modifier.isPublic(handler.getModifiers()) || Modifier.isAbstract(handler.getModifiers()) || handler.isInterface()
                || (handler.isMemberClass() && !Modifier.isStatic(handler.getModifiers())))
            throw new KlumSchemaException(location + ": requires a public concrete handler and public no-arg constructor");
        try {
            handler.getConstructor();
        } catch (NoSuchMethodException exception) {
            throw new KlumSchemaException(location + ": requires a public no-arg constructor", exception);
        }
        if (annotationParameter(handler, role, Map.of()) != domain)
            throw new KlumSchemaException(location + ": annotation parameter must resolve exactly to " + domain.getName()
                    + "; raw, wildcard, unresolved and mismatched parameters are unsupported");
    }

    private static Type annotationParameter(Type type, Class<?> role, Map<TypeVariable<?>, Type> incoming) {
        Class<?> raw;
        Map<TypeVariable<?>, Type> bindings = new HashMap<>();
        if (type instanceof ParameterizedType parameterized) {
            raw = (Class<?>) parameterized.getRawType();
            TypeVariable<?>[] variables = raw.getTypeParameters();
            Type[] arguments = parameterized.getActualTypeArguments();
            for (int i = 0; i < variables.length; i++)
                bindings.put(variables[i], resolve(arguments[i], incoming));
            if (raw == role) return resolve(arguments[0], incoming);
        } else if (type instanceof Class<?> clazz) {
            raw = clazz;
            if (raw == role) return null;
        } else return null;
        for (Type parent : raw.getGenericInterfaces()) {
            Type result = annotationParameter(parent, role, bindings);
            if (result != null) return result;
        }
        Type parent = raw.getGenericSuperclass();
        return parent == null ? null : annotationParameter(parent, role, bindings);
    }

    private static Type resolve(Type argument, Map<TypeVariable<?>, Type> bindings) {
        return argument instanceof TypeVariable<?> variable ? bindings.getOrDefault(variable, variable) : argument;
    }
}
