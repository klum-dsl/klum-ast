/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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

import groovy.lang.*;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import org.codehaus.groovy.reflection.CachedField;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.*;
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.*;

/**
 * Helper class that can be used for (not only) cluster models. A cluster model provides access to
 */
public class ClusterModel {

    private ClusterModel() {}

    /**
     * Returns the default name for a field of the given type. This is the uncapitalized simple name of the type. I.e.
     * if a class Database had a single field of type UtilSchema, then the default name of such a field would be "utilSchema".
     * @param type The class
     * @return the uncapitalized named of the class.
     */
    public static String toDefaultFieldName(Class<?> type) {
        return StringGroovyMethods.uncapitalize(type.getSimpleName());
    }

    /**
     * Returns the default name for the type of the given object.
     * @see #toDefaultFieldName(Class)
     * @param object The object to determine the default name
     * @return The uncapitalized name of object's class
     */
    public static String toDefaultFieldName(Object object) {
        return toDefaultFieldName(object.getClass());
    }

    /**
     * Returns a map of all fields of the given container with the given type, using the given filter.
     * This includes null values.
     * @param container The object whose fields should be returned
     * @param fieldType The type of properties to be returned
     * @param filter Filter closure on the AnnotatedElement (Method or Field)
     * @return A Map of property names to property values containing all properties of the given type.
     */
    public static <T> Map<String, T> getPropertyMap(Object container, Class<T> fieldType, @ClosureParams(value = SimpleType.class, options = "java.lang.reflect.AnnotatedElement") Closure<Boolean> filter) {
        return getPropertyMap(container, fieldType, filter::call);
    }

    /**
     * Returns a map of all fields of the given container with the given type
     * This includes null values.
     * @param container The object whose fields should be returned
     * @param fieldType The type of properties to be returned
     * @return A Map of property names to property values containing all properties of the given type.
     */
    @SuppressWarnings("unchecked") // PropertyValue is not generic
    public static <T> Map<String, T> getPropertyMap(Object container, Class<T> fieldType) {
        return (Map<String, T>) getPropertiesStream(container, fieldType)
                .collect(toMap(PropertyValue::getName, PropertyValue::getValue));
    }

    /**
     * Returns a map of all fields of the given container with the given type, and the given annotation.
     * This includes null values.
     * @param container The object whose fields should be returned
     * @param fieldType The type of properties to be returned
     * @param filter Annotation type that must be present on the field
     * @return A Map of property names to property values containing all properties of the given type.
     */
    @SuppressWarnings("unchecked") // PropertyValue is not generic
    public static <T> Map<String, T> getPropertyMap(Object container, Class<T> fieldType, Class<? extends Annotation> filter) {
        return (Map<String, T>) getPropertiesStream(container, fieldType, it -> it.isAnnotationPresent(filter))
                .collect(toMap(PropertyValue::getName, PropertyValue::getValue));
    }

    /**
     * Returns a map of all fields of the given container with the given type, using the given filter.
     * This includes null values.
     * @param container The object whose fields should be returned
     * @param fieldType The type of properties to be returned
     * @param filter Filter closure on the AnnotatedElement (Method or Field)
     * @return A Map of property names to property values containing all properties of the given type.
     */
    @SuppressWarnings("unchecked") // PropertyValue is not generic
    public static <T> Map<String, T> getPropertyMap(Object container, Class<T> fieldType, Predicate<AnnotatedElement> filter) {
        return (Map<String, T>) getPropertiesStream(container, fieldType, filter)
                .collect(toMap(PropertyValue::getName, PropertyValue::getValue));
    }

    /**
     * Returns a map of all fields of the given container with the given type, using the given filter,
     * only including fields that are not null.
     * @param container The object whose fields should be returned
     * @param fieldType The type of properties to be returned
     * @param filter Filter closure on the AnnotatedElement (Method or Field)
     * @return A Map of property names to property values containing all properties of the given type.
     */
    public static <T> Map<String, T> getNonEmptyPropertyMap(Object container, Class<T> fieldType, @ClosureParams(value = SimpleType.class, options = "java.lang.reflect.AnnotatedElement") Closure<Boolean> filter) {
        return getNonEmptyPropertyMap(container,fieldType, filter::call);
    }

    /**
     * Returns a map of all fields of the given container with the given type, and the given annotation,
     * only including fields that are not null.
     * @param container The object whose fields should be returned
     * @param fieldType The type of properties to be returned
     * @param filter Annotation type that must be present on the field
     * @return A Map of property names to property values containing all properties of the given type.
     */
    public static <T> Map<String, T> getNonEmptyPropertyMap(Object container, Class<T> fieldType, Class<? extends Annotation> filter) {
        return getNonEmptyPropertyMap(container,fieldType, it -> it.isAnnotationPresent(filter));
    }

    /**
     * Returns a map of all fields of the given container with the given type,
     * only including fields that are not null.
     * @param container The object whose fields should be returned
     * @param fieldType The type of properties to be returned
     * @return A Map of property names to property values containing all properties of the given type.
     */
    @SuppressWarnings("unchecked") // PropertyValue is not generic
    public static <T> Map<String, T> getNonEmptyPropertyMap(Object container, Class<T> fieldType) {
        return (Map<String, T>) getPropertiesStream(container, fieldType)
                .filter(it -> it.getValue() != null)
                .collect(toMap(PropertyValue::getName, PropertyValue::getValue));   }

    /**
     * Returns a map of all fields of the given container with the given type, using the given filter,
     * only including fields that are not null.
     * @param container The object whose fields should be returned
     * @param fieldType The type of properties to be returned
     * @param filter Filter closure on the AnnotatedElement (Method or Field)
     * @return A Map of property names to property values containing all properties of the given type.
     */
    @SuppressWarnings("unchecked") // PropertyValue is not generic
    public static <T> Map<String, T> getNonEmptyPropertyMap(Object container, Class<T> fieldType, Predicate<AnnotatedElement> filter) {
        return (Map<String, T>) getPropertiesStream(container, fieldType, filter)
                .filter(it -> it.getValue() != null)
                .collect(toMap(PropertyValue::getName, PropertyValue::getValue));
    }

    /**
     * Returns a Map of all collection fields with the given element type, using the given filter
     * @param container The object to search
     * @param fieldType The element type to look for
     * @param filter Filter closure on the AnnotatedElement (Method or Field)
     * @return A Map of property names to collections of values.
     */
    public static <T> Map<String, Collection<T>> getPropertyListMap(Object container, Class<T> fieldType, @ClosureParams(value = SimpleType.class, options = "java.lang.reflect.AnnotatedElement") Closure<Boolean> filter) {
        return getPropertyListMap(container, fieldType, filter::call);
    }

    /**
     * Returns a Map of all collection fields with the given element type, with the given annotation.
     * @param container The object to search
     * @param fieldType The element type to look for
     * @param filter Annotation type that must be present on the field
     * @return A Map of property names to collections of values.
     */
    public static <T> Map<String, Collection<T>> getPropertyListMap(Object container, Class<T> fieldType, Class<? extends Annotation> filter) {
        return getPropertyListMap(container, fieldType, it -> it.isAnnotationPresent(filter));
    }

    /**
     * Returns a Map of all collection fields with the given element type.
     * @param container The object to search
     * @param fieldType The element type to look for
     * @return A Map of property names to collections of values.
     */
    @SuppressWarnings("unchecked") // PropertyValue is not generic
    public static <T> Map<String, Collection<T>> getPropertyListMap(Object container, Class<T> fieldType) {
        return getPropertiesStream(container, Collection.class)
                .filter(it -> (boolean) InvokerHelper.invokeMethod(it.getValue(), "asBoolean", null))
                .filter(it -> isCollectionOf(container, it, fieldType))
                .collect(toMap(PropertyValue::getName, it -> (Collection<T>) it.getValue()));
    }

    static <T> boolean isCollectionOf(Object container, PropertyValue value, Class<T> type) {
        if (!Collection.class.isAssignableFrom(value.getType()))
            return false;

        AnnotatedElement element = getAnnotatedElementForProperty(container, value);
        if (!(element instanceof Field))
            return false;
        Field field = (Field) element;

        Type genericFieldType = field.getGenericType();
        if (!(genericFieldType instanceof ParameterizedType))
            return false;

        ParameterizedType aType = (ParameterizedType) genericFieldType;
        Type[] fieldArgTypes = aType.getActualTypeArguments();
        if (fieldArgTypes == null || fieldArgTypes.length == 0)
            return false;
        Type fieldArgType = fieldArgTypes[0];
        return type.isAssignableFrom((Class<?>) fieldArgType);
    }

    /**
     * Returns a Map of all collection fields with the given element type, using the given filter
     * @param container The object to search
     * @param fieldType The element type to look for
     * @param filter Filter closure on the AnnotatedElement (Method or Field)
     * @return A Map of property names to collections of values.
     */
    @SuppressWarnings("unchecked") // PropertyValue is not generic
    public static <T> Map<String, Collection<T>> getPropertyListMap(Object container, Class<T> fieldType, Predicate<AnnotatedElement> filter) {
        return getPropertiesStream(container, Collection.class, filter)
                .filter(it -> (boolean) InvokerHelper.invokeMethod(it.getValue(), "asBoolean", null))
                .filter(it -> isCollectionOf(container, it, fieldType))
                .collect(toMap(PropertyValue::getName, it -> (Collection<T>) it.getValue()));
    }

    @NotNull
    private static Stream<PropertyValue> getPropertiesStream(Object container, Class<?> fieldType, Predicate<AnnotatedElement> filter) {
        return getPropertiesStream(container, fieldType)
                .filter(it -> filter.test(getAnnotatedElementForProperty(container, it)));
    }

    @NotNull
    private static Stream<PropertyValue> getPropertiesStream(Object container, Class<?> fieldType) {
        return getMetaPropertyValues(container).stream()
                .filter(ClusterModel::isNoInternalProperty)
                .filter(it -> fieldType.isAssignableFrom(it.getType()))
                .filter(it -> hasField(container.getClass(), it.getName()));  // TODO Do we want to exclude getter only fields?
    }

    public static List<PropertyValue> getUnsetProperties(Object container, Class<?> fieldType) {
        return getPropertiesStream(container, fieldType)
                .filter(it -> it.getValue() == null)
                .collect(toList());
    }

    public static List<PropertyValue> getSetProperties(Object container, Class<?> fieldType) {
        return getPropertiesStream(container, fieldType)
                .filter(it -> it.getValue() != null)
                .collect(toList());
    }

    public static List<PropertyValue> getAllProperties(Object container, Class<?> fieldType) {
        return getPropertiesStream(container, fieldType)
                .collect(toList());
    }

    public static Optional<Field> getField(Class<?> containerType, String fieldName) {
        while (containerType != null) {
            Optional<Field> field = Arrays.stream(containerType.getDeclaredFields()).filter(it -> it.getName().equals(fieldName)).findFirst();
            if (field.isPresent())
                return field;
            containerType = containerType.getSuperclass();
        }
        return Optional.empty();
    }

    public static boolean hasField(Class<?> containerType, String fieldName) {
        return getField(containerType,fieldName).isPresent();
    }

    static AnnotatedElement getAnnotatedElementForProperty(Object container, PropertyValue propertyValue) {
        MetaBeanProperty property = (MetaBeanProperty) getMetaClass(container).getMetaProperty(propertyValue.getName());

        CachedField cachedField = property.getField();
        if (cachedField != null) {
            if (GroovySystem.getVersion().startsWith("2"))
                return (Field) InvokerHelper.getProperty(cachedField, "field");
            else
                return (Field) InvokerHelper.getProperty(cachedField, "cachedField");
        }
        return ((CachedMethod) property.getGetter()).getCachedMethod();
    }

    /**
     * Returns the name of the field of the container containing the given object. If the object is not
     * contained in a field, returns an empty Optional.
     * @param container The container object to search
     * @param child The child object to look for
     * @return The name of the field containing the child object, or an empty Optional if the object is not contained in a field.
     */
    public static Optional<String> getNameOfFieldContaining(Object container, @NotNull Object child) {
        Objects.requireNonNull(child);
        Optional<MetaProperty> field = getMetaClass(container).getProperties().stream()
                .filter(ClusterModel::isNoInternalProperty)
                .filter(MetaBeanProperty.class::isInstance)
                .filter(it -> ((MetaBeanProperty) it).getGetter() != null)
                .filter(it -> it.getType().isInstance(child))
                .filter(it -> child == it.getProperty(container))
                .findFirst();

        return field.map(MetaProperty::getName);
    }

    static boolean isNoInternalProperty(MetaProperty property) {
        return !property.getName().contains("$");
    }

    static boolean isNoInternalProperty(PropertyValue property) {
        return !property.getName().contains("$");
    }

    /**
     * Iterates through a data structure and returns all fields of the given type.
     * @param container The container from which to extract the types
     * @param type The target type to retrieve
     * @param ignoredTypes All types in this list are completely ignored, i.e. not visited
     * @param path The prefix to attach to the path
     * @return a map of strings to objects
     */
    public static <T> Map<String, T> deepFind(Object container, Class<T> type, List<Class<?>> ignoredTypes, String path) {
        return doDeepFind(container, type, ignoredTypes, path, new ArrayList<>());
    }

    protected static <T> Map<String, T> doDeepFind(Object container, Class<T> type, List<Class<?>> ignoredTypes, String path, List<Object> visited) {
        Map<String, T> result = new HashMap<>();
        if (container == null
                || ignoredTypes.stream().anyMatch(it -> it.isInstance(container))
                || visited.stream().anyMatch(it -> it == container))
            return result;

        visited.add(container);

        if (type.isInstance(container)) {
            //noinspection unchecked
            result.put(path, (T) container);
            return result;
        }

        if (container instanceof Collection) {
            AtomicInteger index = new AtomicInteger();
            ((Collection<?>) container).forEach(member -> result.putAll(doDeepFind(member, type, ignoredTypes, path + "[" + index.getAndIncrement() + "]", visited)));
        }
        else if (container instanceof Map) {
            ((Map<?, ?>) container).forEach((key, value) -> result.putAll(doDeepFind(value, type, ignoredTypes, path + "." + key, visited)));
        }
        else {
            getNonIgnoredProperties(container).forEach((name, value) -> result.putAll(doDeepFind(value, type, ignoredTypes, path + "." + name, visited)));
        }

        return result;
    }

    static Map<String, Object> getNonIgnoredProperties(Object container) {
        Map<String, Object> result = new HashMap<>();
        Class<?> type = container.getClass();

        while (type != null) {
            Arrays.stream(type.getDeclaredFields())
                    .filter(it -> !it.getName().contains("$"))
                    .forEach(it -> result.put(it.getName(), InvokerHelper.getProperty(container, it.getName())));
            type = type.getSuperclass();
        }

        return result;
    }

    public static <T> T getSingleValueOrFail(Object container, Class<T> type, @ClosureParams(value = SimpleType.class, options = "java.lang.reflect.AnnotatedElement") Closure<Boolean> filter) {
        //noinspection unchecked
        return (T) getSinglePropertyOrFail(container, type, filter).getValue();
    }

    public static <T> T getSingleValueOrFail(Object container, Class<T> type, Class<? extends Annotation> filter) {
        //noinspection unchecked
        return (T) getSinglePropertyOrFail(container, type, it -> it.isAnnotationPresent(filter)).getValue();
    }

    public static <T> T getSingleValueOrFail(Object container, Class<T> type, Predicate<AnnotatedElement> filter) {
        //noinspection unchecked
        return (T) getSinglePropertyOrFail(container, type, filter).getValue();
    }

    public static <T> T getSingleValueOrFail(Object container, Class<T> type) {
        //noinspection unchecked
        return (T) getSinglePropertyOrFail(container, type).getValue();
    }

    public static PropertyValue getSinglePropertyOrFail(Object container, Class<?> type, @ClosureParams(value = SimpleType.class, options = "java.lang.reflect.AnnotatedElement") Closure<Boolean> filter) {
        return getSinglePropertyOrFail(container, type, filter::call);
    }

    public static PropertyValue getSinglePropertyOrFail(Object container, Class<?> type, Class<? extends Annotation> filter) {
        return getSinglePropertyOrFail(container, type, it -> it.isAnnotationPresent(filter));
    }

    public static PropertyValue getSinglePropertyOrFail(Object container, Class<?> type) {
        return getSinglePropertyOrFail(container, type, x -> true);
    }

    public static PropertyValue getSinglePropertyOrFail(Object container, Class<?> type, Predicate<AnnotatedElement> filter) {
        List<PropertyValue> result = getPropertiesStream(container, type, filter).collect(toList());

        if (result.isEmpty())
            throw new IllegalArgumentException(format("Class %s has no field of type %s (possibly with filter)", container.getClass().getName(), type.getName()));

        if (result.size() > 1)
            throw new IllegalArgumentException(format("Class %s has more than one field of type %s (%s) (possibly with filter)",
                    container.getClass().getName(), type.getName(), result.stream().map(PropertyValue::getName).collect(toList())));

        return result.get(0);
    }

    public static <T> T getFieldOfType(Object container, Class<T> type, String name) {
        Map<String, T> properties = getPropertyMap(container, type);

        if (!properties.containsKey(name))
            throw new IllegalArgumentException("Class ${container.getClass().name} does not have a field '$name' with type '$type.name'");

        return properties.get(name);
    }

    public static Map<String, Object> getAnnotatedValues(Object object, Class<? extends Annotation> annotation) {
        Map<String, Object> result = getFieldsValues(object, annotation);
        result.putAll(getMethodBasedValues(object, annotation));
        return result;
    }

    public static Map<String, Object> getFieldsValues(Object object, Class<? extends Annotation> annotation) {
        return getPropertyMap(object, Object.class, annotation);
    }

    /**
     * Executes all parameterless, non-void methods with the given annotation and returns the result.
     * @param object The object whose methods should be executed
     * @param annotation The annotation to look for
     * @return A map of method names and their return values
     */
    public static Map<String, Object> getMethodBasedValues(Object object, Class<? extends Annotation> annotation) {
        return getMethodBasedValues(object, annotation, Object.class);
    }

    /**
     * Executes all parameterless, non-void methods with the given annotation and returns the result.
     * @param object The object whose methods should be executed
     * @param annotation The annotation to look for
     * @param returnType only methods with this return type will be executed
     * @return A map of method names and their return values
     */
    public static Map<String, Object> getMethodBasedValues(Object object, Class<? extends Annotation> annotation, Class<?> returnType) {
        return getMethodsAnnotatedWithStream(object, returnType, annotation)
                .collect(toMap(Method::getName, it -> InvokerHelper.invokeMethod(object, it.getName(), null)));
    }

    /**
     * Returns all parameterless methods of the given Return type (possibly filtered).
     * @param object The object whose methods should be returned
     * @return A list of matching methods
     */
    static List<Method> getMethodsAnnotatedWith(Object object, Class<? extends Annotation> annotation) {
        return getMethodsAnnotatedWithStream(object, Object.class, annotation)
                .collect(toList());
    }

    @NotNull
    private static Stream<Method> getMethodsAnnotatedWithStream(Object object, Class<?> returnType, Class<? extends Annotation> annotation) {
        return Arrays.stream(object.getClass().getMethods())
                .filter(it -> it.getParameterCount() == 0)
                .filter(it -> returnType.isAssignableFrom(it.getReturnType()))
                .filter(it -> !it.getName().startsWith("get"))
                .filter(it -> !it.getName().startsWith("is"))
                .filter(it -> it.isAnnotationPresent(annotation));
    }

}
