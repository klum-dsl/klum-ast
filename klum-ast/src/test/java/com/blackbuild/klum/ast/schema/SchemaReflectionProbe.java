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
package com.blackbuild.klum.ast.schema;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Disposable SCHEMA-R observation only: no admission, carrier reader or schema generation. */
public final class SchemaReflectionProbe {
    private SchemaReflectionProbe() { }

    public static final class IsolatedLoader extends URLClassLoader {
        public final List<String> resolved = new ArrayList<>();
        private final String denied;

        public IsolatedLoader(URL[] urls, String denied) {
            super(urls, ClassLoader.getPlatformClassLoader());
            this.denied = denied;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(denied)) throw new ClassNotFoundException("SCHEMA-R missing dependency: " + name);
            Class<?> result = super.findClass(name);
            resolved.add(name);
            return result;
        }
    }

    public static Map<String, Object> inspect(ClassLoader loader, String name) throws ReflectiveOperationException {
        try {
            return inspectResolved(loader, name);
        } catch (LinkageError | ClassNotFoundException failure) {
            throw new IllegalStateException("SCHEMA-R root " + name
                    + ": supply the complete resolved API/Schema/runtime/annotation classpath", failure);
        }
    }

    private static Map<String, Object> inspectResolved(ClassLoader loader, String name) throws ReflectiveOperationException {
        Class<?> contract = Class.forName(name, false, loader);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", contract.getName());
        result.put("module", contract.getModule().getName() == null ? "unnamed" : contract.getModule().getName());
        result.put("parents", Arrays.stream(contract.getGenericInterfaces()).map(Type::getTypeName).toList());
        result.put("bounds", Arrays.stream(contract.getTypeParameters())
                .flatMap(variable -> Arrays.stream(variable.getBounds())).map(Type::getTypeName).toList());
        result.put("annotations", annotations(contract));
        List<Map<String, Object>> methods = new ArrayList<>();
        // getMethods includes inherited public declarations; retain the actual declaring owner.
        for (Method method : contract.getMethods()) {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("owner", method.getDeclaringClass().getName());
            fact.put("name", method.getName());
            fact.put("return", method.getGenericReturnType().getTypeName());
            fact.put("parameters", Arrays.stream(method.getGenericParameterTypes()).map(Type::getTypeName).toList());
            fact.put("descriptor", descriptor(method));
            fact.put("annotations", annotations(method));
            fact.put("parameterAnnotations", Arrays.stream(method.getParameters()).map(SchemaReflectionProbe::annotationNames).toList());
            methods.add(fact);
        }
        result.put("methods", methods);
        return result;
    }

    private static String descriptor(Method method) {
        return method.getName() + "(" + String.join(", ", Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).toList())
                + "):" + method.getReturnType().getTypeName();
    }

    private static List<String> annotationNames(AnnotatedElement element) {
        return Arrays.stream(element.getDeclaredAnnotations()).map(annotation -> annotation.annotationType().getName()).toList();
    }

    private static Map<String, Object> annotations(AnnotatedElement element) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Annotation annotation : element.getDeclaredAnnotations()) {
            String name = annotation.annotationType().getName();
            Map<String, Object> values = new LinkedHashMap<>();
            // Read actual AnnoDoc.value and JDK-valued annotation members; never access enum values explicitly.
            for (Method member : annotation.annotationType().getDeclaredMethods()) {
                Class<?> type = member.getReturnType();
                if (type != String.class && type != String[].class && type != boolean.class && type != Class.class) continue;
                try {
                    Object value = member.invoke(annotation);
                    values.put(member.getName(), value instanceof Class<?> clazz ? clazz.getName()
                            : value instanceof String[] array ? List.of(array) : value);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("SCHEMA-R annotation member " + name + "." + member.getName(), failure);
                }
            }
            result.put(name, values);
        }
        return result;
    }

    public static void requireRootIdentity(ClassLoader loader, Class<?> root) throws ClassNotFoundException {
        if (Class.forName(root.getName(), false, loader) != root) {
            throw new IllegalArgumentException("SCHEMA-R root " + root.getName()
                    + ": loader identity mismatch; reload the root through the inspected resolved classpath");
        }
    }

    public static List<String> enumNames(ClassLoader loader, String name) throws ClassNotFoundException {
        Class<?> enumeration = Class.forName(name, false, loader);
        return Arrays.stream(enumeration.getDeclaredFields()).filter(field -> field.isEnumConstant()).map(field -> field.getName()).toList();
    }

    /** Deliberate execution phase only; never called during extraction. */
    public static void constructControl(ClassLoader loader, boolean converter) throws ReflectiveOperationException {
        Class<?> model = Class.forName("schemar.schema.Application", true, loader);
        Object factory = model.getField("Create").get(null);
        Class<?> contract = Class.forName("schemar.schema.Application_DSL$Factory", false, loader);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            if (converter) contract.getMethod("With", Map.class).invoke(factory, Map.of("uri", "https://example.invalid"));
            else contract.getMethod("One").invoke(factory);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public static String exactDoc(ClassLoader loader, String contract, String method) throws ReflectiveOperationException {
        Class<?> type = Class.forName(contract, false, loader);
        Class<? extends Annotation> doc;
        try {
            doc = Class.forName("com.blackbuild.annodocimal.annotations.AnnoDoc", false, loader).asSubclass(Annotation.class);
        } catch (ClassNotFoundException failure) {
            throw new ClassNotFoundException("SCHEMA-R root " + contract + ": supply the AnnoDoc annotation protocol dependency", failure);
        }
        Annotation annotation = type.getMethod(method, String.class).getAnnotation(doc);
        return annotation == null ? null : (String) doc.getMethod("value").invoke(annotation);
    }
}
