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

import com.blackbuild.annodocimal.annotations.InlineJavadocs;
import com.blackbuild.groovy.configdsl.transform.*;
import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import groovy.lang.*;
import groovy.transform.Undefined;
import org.codehaus.groovy.reflection.CachedField;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.Field;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.blackbuild.klum.ast.util.DslHelper.*;
import static groovyjarjarasm.asm.Opcodes.*;
import static java.lang.String.format;

/**
 * Implementations for generated instance methods.
 */
@SuppressWarnings("unused") // called from generated code
@InlineJavadocs
public class KlumInstanceProxy {

    public static final String NAME_OF_RW_FIELD_IN_MODEL_CLASS = "$rw";
    public static final String NAME_OF_PROXY_FIELD_IN_MODEL_CLASS = "$proxy";
    public static final Class<com.blackbuild.groovy.configdsl.transform.Field> FIELD_ANNOTATION = com.blackbuild.groovy.configdsl.transform.Field.class;

    private final GroovyObject instance;
    private boolean manualValidation;

    public KlumInstanceProxy(GroovyObject instance) {
        this.instance = instance;
    }

    /**
     * Returns the proxy for a given dsl object. Throws an illegalArgumentException if target is not a dsl object.
     * @param target the target object
     * @return the proxy instance of the given target.
     */
    public static KlumInstanceProxy getProxyFor(Object target) {
        if (!isDslObject(target))
            throw new IllegalArgumentException(format("Object of type %s is no dsl object", target.getClass()));
        return (KlumInstanceProxy) InvokerHelper.getAttribute(target, KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS);
    }

    protected GroovyObject getRwInstance() {
        return (GroovyObject) InvokerHelper.getAttribute(instance, KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS);
    }

    public Object getDSLInstance() {
        return instance;
    }

    // TODO: protected/private
    public <T> T getInstanceAttribute(String attributeName) {
        return (T) getCachedField(attributeName).getProperty(instance);
    }

    public <T> T getInstanceAttributeOrGetter(String attributeName) {
        Optional<CachedField> field = DslHelper.getCachedField(instance.getClass(), attributeName);

        if (field.isPresent())
            return (T) field.get().getProperty(instance);

        return (T) InvokerHelper.getProperty(instance, attributeName);
    }

    void setInstanceAttribute(String name, Object value) {
        getCachedField(name).setProperty(instance, value);
    }

    // TODO: private?
    public Object getInstanceProperty(String name){
        return makeReadOnly(getInstanceAttributeOrGetter(name));
    }

    private <T> T makeReadOnly(T value) {
        if (value instanceof EnumSet)
            return (T) EnumSet.copyOf((EnumSet<?>) value);
        if (value instanceof Collection || value instanceof Map)
            return (T) InvokerHelper.invokeMethod(DefaultGroovyMethods.class, "asImmutable", value);
        return value;
    }

    Field getField(String name) {
        return DslHelper.getField(instance.getClass(), name)
                .orElseThrow(() -> new MissingPropertyException(name, instance.getClass()));
    }

    CachedField getCachedField(String name) {
        return DslHelper.getCachedField(instance.getClass(), name)
                .orElseThrow(() -> new MissingPropertyException(name, instance.getClass()));
    }

    /**
     * Applies the given named params and the closure to this proxy's object.
     * Both params are optional. The map will be converted into a series of method calls, with the key being the method name and the value the single method argument.
     * The closure will be executed against the instance's RW object.
     * <p>Note that explicit calls to apply() are usually not necessary, as apply is part of the creation of an object.</p>
     * @param values Map of String to Object which will be translated into Method calls
     * @param body Closure to be executed against the instance.
     * @return the object itself
     */
    public Object apply(Map<String, ?> values, Closure<?> body) {
        applyOnly(values, body);
        LifecycleHelper.executeLifecycleMethods(this, PostApply.class);
        return instance;
    }

    void applyOnly(Map<String, ?> values, Closure<?> body) {
        Object rw = instance.getProperty(NAME_OF_RW_FIELD_IN_MODEL_CLASS);
        applyNamedParameters(rw, values);
        applyClosure(rw, body);
    }

    private void applyClosure(Object rw, Closure<?> body) {
        if (body == null) return;
        body.setDelegate(rw);
        body.setResolveStrategy(Closure.DELEGATE_ONLY);
        body.call();
    }

    private void applyNamedParameters(Object rw, Map<String, ?> values) {
        if (values == null) return;
        values.forEach((key, value) -> InvokerHelper.invokeMethod(rw, key, value));
    }

    /**
     * Copies all non null / non empty elements from target to this.
     * @param template The template to apply
     */
    public void copyFrom(Object template) {
        DslHelper.getDslHierarchyOf(instance.getClass()).forEach(it -> copyFromLayer(it, template));
    }

    public Object cloneInstance() {
        Object key = isKeyed(instance.getClass()) ? getKey() : null;
        Object result = FactoryHelper.createInstance(instance.getClass(), (String) key);
        getProxyFor(result).copyFrom(instance);
        return result;
    }

    private void copyFromLayer(Class<?> layer, Object template) {
        if (layer.isInstance(template))
            Arrays.stream(layer.getDeclaredFields())
                    .filter(this::isNotIgnored)
                    .forEach(field -> copyFromField(field, template));
    }

    private boolean isIgnored(Field field) {
        if ((field.getModifiers() & (ACC_SYNTHETIC | ACC_FINAL | ACC_TRANSIENT)) != 0) return true;
        if (field.isAnnotationPresent(Key.class)) return true;
        if (field.isAnnotationPresent(Owner.class)) return true;
        if (field.getName().startsWith("$")) return true;
        if (DslHelper.getKlumFieldType(field) == FieldType.TRANSIENT) return true;
        return false;
    }

    private boolean isNotIgnored(Field field) {
        return !isIgnored(field);
    }

    private void copyFromField(Field field, Object template) {
        String fieldName = field.getName();
        Object templateValue = getProxyFor(template).getInstanceAttribute(fieldName);

        if (templateValue == null) return;

        if (templateValue instanceof Collection)
            copyFromCollectionField((Collection<?>) templateValue, fieldName);
        else if (templateValue instanceof Map)
            copyFromMapField((Map<?,?>) templateValue, fieldName);
        else
            setInstanceAttribute(fieldName, getCopiedValue(templateValue));
    }

    @SuppressWarnings("unchecked")
    private <T> T getCopiedValue(T templateValue) {
        if (isDslType(templateValue.getClass()))
            return (T) getProxyFor(templateValue).cloneInstance();
        else if (templateValue instanceof Collection)
            return (T) createCopyOfCollection((Collection<?>) templateValue);
        else if (templateValue instanceof Map)
            return (T) createCopyOfMap((Map<String, ?>) templateValue);
        else
            return templateValue;
    }

    private <T> Collection<T> createCopyOfCollection(Collection<T> templateValue) {
        Collection<T> result = createNewEmptyCollectionOrMapFrom(templateValue);
        templateValue.stream().map(this::getCopiedValue).forEach(result::add);
        return result;
    }

    private <T> Map<String, T> createCopyOfMap(Map<String, T> templateValue) {
        Map<String, T> result = createNewEmptyCollectionOrMapFrom(templateValue);
        templateValue.forEach((key, value) -> result.put(key, getCopiedValue(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T createNewEmptyCollectionOrMapFrom(T source) {
        return (T) InvokerHelper.invokeConstructorOf(source.getClass(), null);
    }

    private <K,V> void copyFromMapField(Map<K,V> templateValue, String fieldName) {
        if (templateValue.isEmpty()) return;
        Map<K,V> instanceField = getInstanceAttribute(fieldName);
        instanceField.clear();
        templateValue.forEach((k, v) -> instanceField.put(k, getCopiedValue(v)));
    }

    private <T> void copyFromCollectionField(Collection<T> templateValue, String fieldName) {
        if (templateValue.isEmpty()) return;
        Collection<T> instanceField = getInstanceAttribute(fieldName);
        instanceField.clear();

        templateValue.stream().map(this::getCopiedValue).forEach(instanceField::add);
    }

    /**
     * Returns the key of this proxies instance. Illegal to call on an non keyed instance.
     * @return The key
     */
    Object getKey() {
        return DslHelper.getKeyField(instance.getClass())
                .map(Field::getName)
                .map(instance::getProperty)
                .orElseThrow(AssertionError::new);
    }

    /**
     * Executes validation for this instance
     *
     * @deprecated use {@link com.blackbuild.klum.ast.util.Validator#validate(Object)} instead
     */
    @Deprecated(forRemoval = true)
    public void validate() {
        Validator.validate(instance);
    }

    boolean getManualValidation() {
        return manualValidation;
    }

    void manualValidation() {
        manualValidation = true;
    }

    void manualValidation(boolean value) {
        manualValidation = value;
    }

    /**
     * Returns the owner of this object. If the object has more than one field annotated with {@link Owner},
     * all of them that are not null must point to the same object, otherwise an {@link IllegalStateException}
     * is thrown.
     * @return The found owner or null
     */
    public Object getSingleOwner() {
        Set<Object> owners = getOwners();
        if (owners.size() > 1)
            throw new IllegalStateException("Object has more that on distinct owner");
        return owners.stream().findFirst().orElse(null);
    }

    /**
     * Returns the unique values of all fields annotated with {@link Owner} that are not null, i.e.
     * if multiple owner fields point to the same object, it is included only once in the result.
     * Owner fields with {@link Owner#converter()} or {@link Owner#transitive()} set are ignored.
     * @return The set of owners
     */
    public Set<Object> getOwners() {
        return getFieldsAnnotatedWith(instance.getClass(), Owner.class)
                .filter(field -> field.getAnnotation(Owner.class).converter() == NoClosure.class)
                .filter(field -> !field.getAnnotation(Owner.class).transitive())
                .map(Field::getName)
                .map(instance::getProperty)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Creates a new '{{singleElementName}}' {{param:type?with the given type}} and adds it to the '{{fieldName}}' collection.
     * The newly created element will be configured by the optional parameters values and closure.
     * @param namedParams the optional parameters
     * @param fieldOrMethodName the name of the collection to add the new element to
     * @param type the type of the new element
     * @param key the key to use for the new element
     * @param body the closure to configure the new element
     * @param <T> the type of the newly created element
     * @return the newly created element
     */
    public <T> T createSingleChild(Map<String, Object> namedParams, String fieldOrMethodName, Class<T> type, String key, Closure<T> body) {
        try {
            BreadcrumbCollector.getInstance().enter(fieldOrMethodName, key);
            Optional<? extends AnnotatedElement> fieldOrMethod = DslHelper.getField(instance.getClass(), fieldOrMethodName);

            if (!fieldOrMethod.isPresent())
                fieldOrMethod = DslHelper.getVirtualSetter(getRwInstance().getClass(), fieldOrMethodName, type);

            if (!fieldOrMethod.isPresent())
                throw new GroovyRuntimeException(format("Neither field nor single argument method named %s with type %s found in %s", fieldOrMethodName, type, instance.getClass()));

            String effectiveKey = resolveKeyForFieldFromAnnotation(fieldOrMethodName, fieldOrMethod.get()).orElse(key);
            T created = createNewInstanceFromParamsAndClosure(type, effectiveKey, namedParams, body);
            return callSetterOrMethod(fieldOrMethodName, created);
        } finally {
            BreadcrumbCollector.getInstance().leave();
        }
    }

    /**
     * Sets the value of '{{fieldName}}'. This can call a setter like method.
     * @param fieldOrMethodName the name of the field or method to set
     * @param value the value to set
     * @param <T> the type of the value
     * @return the value
     */
    public <T> T setSingleField(String fieldOrMethodName, T value) {
        return callSetterOrMethod(fieldOrMethodName, value);
    }

    /**
     * Sets the value of the given field by using a converter method.
     * The converter is either a constructor or a static method of the given type.
     * @param fieldOrMethodName the name of the field or method to set
     * @param converterType the class containing the converter
     * @param converterMethod the name of the converter method, if null a constructor is used
     * @param args the arguments to pass to the converter
     * @return the created value
     * @param <T> the type of the value
     */
    public <T> T setSingleFieldViaConverter(String fieldOrMethodName, Class<?> converterType, String converterMethod, Object... args) {
        return setSingleField(fieldOrMethodName, createObjectViaConverter(converterType, converterMethod, args));
    }

    private <T> T createObjectViaConverter(Class<?> converterType, String converterMethod, Object... args) {
        if (converterMethod == null)
            return (T) InvokerHelper.invokeConstructorOf(converterType, args);
        return (T) InvokerHelper.invokeMethod(converterType, converterMethod, args);
    }

    private <T> T callSetterOrMethod(String fieldOrMethodName, T value) {
        if (DslHelper.getField(instance.getClass(), fieldOrMethodName).isPresent())
            setInstanceAttribute(fieldOrMethodName, value);
        else
            invokeRwMethod(fieldOrMethodName, value);
        return value;
    }

    /**
     * Adds an existing '{{singleElementName}}' to the '{{fieldName}}' collection.
     * @param fieldName the name of the collection to add the new element to
     * @param element the element to add
     * @param <T> the type of the element
     * @return the added element
     */
    public <T> T addElementToCollection(String fieldName, T element) {
        Type elementType = DslHelper.getElementType(instance.getClass(), fieldName);
        element = forceCastClosure(element, elementType);
        Collection<T> target = getInstanceAttribute(fieldName);
        target.add(element);
        return element;
    }

    /**
     * Adds new instance of the target type to a collection via a converter method.
     * @param fieldOrMethodName the name of the collection to add the new element to
     * @param converterType the class containing the converter
     * @param converterMethod the name of the converter method. If null, a constructor is used.
     * @param args the arguments to pass to the converter
     * @return the created value
     * @param <T> the type of the value
     */
    public <T> T addElementToCollectionViaConverter(String fieldOrMethodName, Class<?> converterType, String converterMethod, Object... args) {
        return addElementToCollection(fieldOrMethodName, createObjectViaConverter(converterType, converterMethod, args));
    }

    public static final String ADD_NEW_DSL_ELEMENT_TO_COLLECTION = "addNewDslElementToCollection";

    /**
     * Creates a new '{{singleElementName}}' {{param:type?with the given type}} and adds it to the '{{fieldName}}' collection.
     * The newly created element will be configured by the optional parameters values and closure.
     * @param namedParams the optional parameters
     * @param collectionName the name of the collection to add the new element to
     * @param type the type of the new element
     * @param key the key to use for the new element
     * @param body the closure to configure the new element
     * @param <T> the type of the newly created element
     * @return the newly created element
     */
    public <T> T addNewDslElementToCollection(Map<String, Object> namedParams, String collectionName, Class<? extends T> type, String key, Closure<T> body) {
        try {
            BreadcrumbCollector.getInstance().enter(collectionName, key);
            T created = createNewInstanceFromParamsAndClosure(type, key, namedParams, body);
            return addElementToCollection(collectionName, created);
        } finally {
            BreadcrumbCollector.getInstance().leave();
        }
    }

    private <T> T createNewInstanceFromParamsAndClosure(Class<? extends T> type, String key, Map<String, Object> namedParams, Closure<T> body) {
        T created = FactoryHelper.createInstance(type, key);
        KlumInstanceProxy createdProxy = getProxyFor(created);
        createdProxy.copyFromTemplate();
        LifecycleHelper.executeLifecycleMethods(createdProxy, PostCreate.class);
        createdProxy.apply(namedParams, body);
        return created;
    }

    /**
     * Adds one or more existing '{{singleElementName}}' to the '{{fieldName}}' collection.
     * @param fieldName the name of the collection to add the new elements to
     * @param elements the elements to add
     */
    public void addElementsToCollection(String fieldName, Object... elements) {
        Arrays.stream(elements).forEach(element -> addElementToCollection(fieldName, element));
    }

    /**
     * Adds one or more existing '{{singleElementName}}' to the '{{fieldName}}' collection.
     * @param fieldName the name of the collection to add the new elements to
     * @param elements the elements to add
     */
    public void addElementsToCollection(String fieldName, Iterable<?> elements) {
        elements.forEach(element -> addElementToCollection(fieldName, element));
    }

    /**
     * Adds one or more existing '{{singleElementName}}' to the '{{fieldName}}' map.
     * @param fieldName the name of the collection to add the new elements to
     * @param values map of values to add
     */
    public <K,V> void addElementsToMap(String fieldName, Map<K, V> values) {
        values.forEach((key, value) -> addElementToMap(fieldName, key, value));
    }

    /**
     * Adds one or more existing '{{singleElementName}}' to the '{{fieldName}}' map. The
     * key is determined by the keyMapping closure of the target field's
     * {@link com.blackbuild.groovy.configdsl.transform.Field} annotation or the natural key field
     * if the type is a keyed dsl class.
     * @param fieldName the name of the map to add the new elements to
     * @param values the values to add
     */
    public <V> void addElementsToMap(String fieldName, Iterable<V> values) {
        values.forEach(value -> addElementToMap(fieldName, null, value));
    }

    /**
     * Adds one or more existing '{{singleElementName}}' to the '{{fieldName}}' map. The
     * key is determined by the keyMapping closure of the target field's
     * {@link com.blackbuild.groovy.configdsl.transform.Field} annotation or the natural key field
     * if the type is a keyed dsl class.
     * @param fieldName the name of the map to add the new elements to
     * @param values the values to add
     */
    public void addElementsToMap(String fieldName, Object... values) {
        Arrays.stream(values).forEach(value -> addElementToMap(fieldName, null, value));
    }

    /**
     * Creates a new '{{singleElementName}}' {{param:type?with the given type}} and adds it to the '{{fieldName}}' collection.
     * The newly created element will be configured by the optional parameters values and closure.
     * @param namedParams the optional parameters
     * @param mapName the name of the collection to add the new element to
     * @param type the type of the new element
     * @param key the key to use for the new element
     * @param body the closure to configure the new element
     * @param <T> the type of the newly created element
     * @return the newly created element
     */
    public <T> T addNewDslElementToMap(Map<String, Object> namedParams, String mapName, Class<? extends T> type, String key, Closure<T> body) {
        try {
            BreadcrumbCollector.getInstance().enter(mapName, key);
            T created = createNewInstanceFromParamsAndClosure(type, key, namedParams, body);
            return doAddElementToMap(mapName, key, created);
        } finally {
            BreadcrumbCollector.getInstance().leave();
        }
    }

    /**
     * Adds a single existing '{{singleElementName}}' to the '{{fieldName}}' map.
     * @param fieldName the name of the map to add the new elements to
     * @param key the key to use for the new element
     * @param value the value to add
     * @return the added value
     */
    public <K,V> V addElementToMap(String fieldName, K key, V value) {
        return doAddElementToMap(fieldName, key, value);
    }

    /**
     * Adds new instance of the target type to a map via a converter method.
     * @param fieldOrMethodName the name of the map to add the new element to
     * @param converterType the class containing the converter
     * @param converterMethod the name of the converter method. If null, a constructor is used.
     * @param args the arguments to pass to the converter
     * @return the created value
     */
    public <K,V> V addElementToMapViaConverter(String fieldOrMethodName, Class<?> converterType, String converterMethod, K key, Object... args) {
        return addElementToMap(fieldOrMethodName, key, createObjectViaConverter(converterType, converterMethod, args));
    }

    private <K, V> V doAddElementToMap(String fieldName, K key, V value) {
        Type elementType = getElementType(instance.getClass(), fieldName);
        key = determineKeyFromMappingClosure(fieldName, value, key);
        if (key == null && isKeyed(getClassFromType(elementType)))
            key = (K) getProxyFor(value).getKey();
        Map<K, V> target = getInstanceAttribute(fieldName);
        value = forceCastClosure(value, elementType);
        if (key == null)
            throw new IllegalArgumentException("Key is null");
        target.put(key, value);
        return value;
    }

    private <V> V forceCastClosure(Object value, Type elementType) {
        Class<V> effectiveType = (Class<V>) getClassFromType(elementType);

        if (value instanceof Closure)
            return castTo(value, effectiveType);
        else if (effectiveType.isInstance(value))
            //noinspection unchecked
            return (V) value;
        else
            throw new IllegalArgumentException(format("Value is not of type %s", elementType));
    }

    private Class<?> getClassFromType(Type type) {
        if (type instanceof Class)
            return (Class<?>) type;
        if (type instanceof WildcardType)
            return (Class<?>) ((WildcardType) type).getUpperBounds()[0];
        if (type instanceof ParameterizedType)
            return (Class<?>) ((ParameterizedType) type).getRawType();
        throw new IllegalArgumentException("Unknown Type: " + type);
    }

    private <K, V> K determineKeyFromMappingClosure(String fieldName, V element, K defaultValue) {
        //noinspection unchecked
        return DslHelper.getOptionalFieldAnnotation(instance.getClass(), fieldName, FIELD_ANNOTATION)
                .map(com.blackbuild.groovy.configdsl.transform.Field::keyMapping)
                .filter(DslHelper::isClosure)
                .map(value -> (K) ClosureHelper.invokeClosure(value, element))
                .orElse(defaultValue);
    }

    public static final String ADD_ELEMENTS_FROM_SCRIPTS_TO_COLLECTION = "addElementsFromScriptsToCollection";

    /**
     * Adds one or more '{{fieldName}}' created by the given scripts.
     * Each scripts must return a single {{singleElementName}}.
     * @param fieldName the name of the collection to add the new elements to
     * @param scripts the scripts to create the new elements from
     */
    @SafeVarargs
    public final void addElementsFromScriptsToCollection(String fieldName, Class<? extends Script>... scripts) {
        Class<?> elementType = (Class<?>) getElementType(instance.getClass(), fieldName);
        Arrays.stream(scripts).forEach(script -> addElementToCollection(
                fieldName,
                InvokerHelper.invokeStaticMethod(elementType, "createFrom", script))
        );
    }

    public static final String ADD_ELEMENTS_FROM_SCRIPTS_TO_MAP = "addElementsFromScriptsToMap";

    /**
     * Adds one or more '{{fieldName}}' created by the given scripts.
     * Each scripts must return a single {{singleElementName}}.
     * @param fieldName the name of the collection to add the new elements to
     * @param scripts the scripts to create the new elements from
     */
    @SafeVarargs
    public final void addElementsFromScriptsToMap(String fieldName, Class<? extends Script>... scripts) {
        Class<?> elementType = (Class<?>) getElementType(instance.getClass(), fieldName);
        Arrays.stream(scripts).forEach(script -> addElementToMap(
                fieldName,
                null,
                InvokerHelper.invokeStaticMethod(elementType, "createFrom", script))
        );
    }

    Object invokeMethod(String methodName, Object... args) {
        return InvokerHelper.invokeMethod(instance, methodName, args);
    }

    @SuppressWarnings("UnusedReturnValue") // called from generated code
    Object invokeRwMethod(String methodName, Object... args) {
        return InvokerHelper.invokeMethod(getRwInstance(), methodName, args);
    }

    void copyFromTemplate() {
        DslHelper.getDslHierarchyOf(instance.getClass()).forEach(this::copyFromTemplateLayer);
    }

    private void copyFromTemplateLayer(Class<?> layer) {
        copyFrom(TemplateManager.getInstance().getTemplate(layer));
    }

    Optional<String> resolveKeyForFieldFromAnnotation(String name, AnnotatedElement field) {
        com.blackbuild.groovy.configdsl.transform.Field annotation = field.getAnnotation(com.blackbuild.groovy.configdsl.transform.Field.class);

        if (annotation == null)
            return Optional.empty();

        Class<?> keyMember = annotation.key();

        if (keyMember == Undefined.class)
            return Optional.empty();

        if (keyMember == com.blackbuild.groovy.configdsl.transform.Field.FieldName.class)
            return Optional.of(name);

        String result = ClosureHelper.invokeClosureWithDelegateAsArgument((Class<? extends Closure<String>>) keyMember, instance);
        return Optional.of(result);
    }

}
