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
package com.blackbuild.klum.ast.util;

import com.blackbuild.annodocimal.annotations.InlineJavadocs;
import com.blackbuild.groovy.configdsl.transform.NoClosure;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import com.blackbuild.klum.ast.KlumModelObject;
import com.blackbuild.klum.ast.KlumRwObject;
import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.KlumPhase;
import com.blackbuild.klum.ast.process.PhaseDriver;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import groovy.transform.Undefined;
import org.codehaus.groovy.reflection.CachedField;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static com.blackbuild.klum.ast.util.DslHelper.*;
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
    public static final String NAME_OF_MODEL_FIELD_IN_RW_CLASS = "this$0";

    private final GroovyObject instance;
    private boolean manualValidation;
    private String breadcrumbPath;
    private int breadCrumbQuantifier = 1;
    private Map<Class<?>, Object> currentTemplates = Collections.emptyMap();

    private final Map<String, Object> metadata = new HashMap<>();

    private final Map<Integer, List<Closure<?>>> applyLaterClosures = new TreeMap<>();

    public KlumInstanceProxy(GroovyObject instance) {
        this.instance = instance;
    }

    /**
     * Returns the proxy for a given dsl object. Throws an illegalArgumentException if target is not a dsl object.
     * @param target the target object
     * @return the proxy instance of the given target.
     */
    public static KlumInstanceProxy getProxyFor(Object target) {
        if (target instanceof KlumInstanceProxy)
            return (KlumInstanceProxy) target;
        if (target instanceof KlumModelObject)
            return (KlumInstanceProxy) InvokerHelper.getAttribute(target, KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS);
        if (target instanceof KlumRwObject) {
            Object modelInstance = InvokerHelper.getAttribute(target, KlumInstanceProxy.NAME_OF_MODEL_FIELD_IN_RW_CLASS);
            return (KlumInstanceProxy) InvokerHelper.getAttribute(modelInstance, KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS);
        }
        throw new KlumException(format("Object of type %s is no dsl object", target.getClass()));
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
        if (template == null) return;
        CopyHandler.copyToFrom(instance, template);
        if (isDslObject(template))
            getProxyFor(template).applyLaterClosures
                    .forEach((phase, actions) ->
                            applyLaterClosures.computeIfAbsent(phase, ignore -> new ArrayList<>()).addAll(actions));
    }

    public <T> T cloneInstance() {
        Object result = FactoryHelper.createInstance(instance.getClass(), (String) getNullableKey(), "{" + getLocalBreadcrumbPath() + "}");
        KlumInstanceProxy cloneProxy = getProxyFor(result);
        cloneProxy.setCurrentTemplates(currentTemplates);
        cloneProxy.copyFrom(instance);
        return (T) result;
    }

    private @NotNull String getLocalBreadcrumbPath() {
        if (breadcrumbPath == null || breadcrumbPath.length() < 2)
            return "";
        return breadcrumbPath.substring(breadcrumbPath.indexOf("/", 2) + 1);
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
     * Returns the key of this proxies instance or null if the instance is not keyed (or the key is null in case of a template).
     * @return The key or null
     */
    Object getNullableKey() {
        return DslHelper.getKeyField(instance.getClass())
                .map(Field::getName)
                .map(instance::getProperty)
                .orElse(null);
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
            throw new KlumModelException("Object has more that on distinct owner");
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
                .filter(field -> !field.getAnnotation(Owner.class).root())
                .map(Field::getName)
                .map(instance::getProperty)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Creates a new '{{fieldName}}' {{param:type?with the given type}} or adds to the existing member if existant.
     * The newly created (or existing) element will be configured by the optional parameters values and closure.
     * @param namedParams the optional parameters
     * @param fieldOrMethodName the name of the field to set or setter method to call
     * @param type the type of the new element
     * @param key the key to use for the new element
     * @param body the closure to configure the new element
     * @param <T> the type of the newly created element
     * @return the newly created element
     */
    public <T> T createSingleChild(Map<String, Object> namedParams, String fieldOrMethodName, Class<T> type, boolean explicitType, String key, Closure<T> body) {
        return BreadcrumbCollector.withBreadcrumb(null, explicitType ? shortNameFor(type) : null, key, () -> {
            T existingValue = null;

            Optional<? extends AnnotatedElement> fieldOrMethod = DslHelper.getField(instance.getClass(), fieldOrMethodName);

            if (fieldOrMethod.isEmpty()) {
                fieldOrMethod = DslHelper.getVirtualSetter(getRwInstance().getClass(), fieldOrMethodName, type);
                if (fieldOrMethod.isEmpty())
                    throw new KlumModelException(format("Neither field nor single argument method named %s with type %s found in %s", fieldOrMethodName, type, instance.getClass()));
            } else {
                existingValue = getInstanceAttribute(fieldOrMethodName);
            }

            String effectiveKey = resolveKeyForFieldFromAnnotation(fieldOrMethodName, fieldOrMethod.get()).orElse(key);

            if (existingValue != null) {
                KlumInstanceProxy existingValueProxy = getProxyFor(existingValue);
                if (!Objects.equals(effectiveKey, existingValueProxy.getNullableKey()))
                    throw new KlumModelException(
                            format("Key mismatch: %s != %s, either use '%s.apply()' to keep existing object or explicitly create and assign a new object.",
                                    effectiveKey, existingValueProxy.getNullableKey(), fieldOrMethodName));

                if (type != existingValue.getClass() && type != ((Field) fieldOrMethod.get()).getType())
                    throw new KlumModelException(
                            format("Type mismatch: %s != %s, either use '%s.apply()' to keep existing object or explicitly create and assign a new object.",
                                    type, existingValue.getClass(), fieldOrMethodName));

                existingValueProxy.increaseBreadcrumbQuantifier();
                return (T) existingValueProxy.apply(namedParams, body);
            }

            T created = createNewInstanceFromParamsAndClosure(type, effectiveKey, namedParams, body);
            return callSetterOrMethod(fieldOrMethodName, created);
        });
    }

    /**
     * Sets the value of '{{fieldName}}'. This can call a setter like method.
     * @param fieldOrMethodName the name of the field or method to set
     * @param value the value to set
     * @param <T> the type of the value
     * @return the value
     */
    public <T> T setSingleField(String fieldOrMethodName, T value) {
        return BreadcrumbCollector.withBreadcrumb(() -> callSetterOrMethod(fieldOrMethodName, value));
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
        Type elementType = DslHelper.getElementTypeOfField(instance.getClass(), fieldName);
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
    public <T> T addNewDslElementToCollection(Map<String, Object> namedParams, String collectionName, Class<? extends T> type, boolean explicitType, String key, Closure<T> body) {
        return BreadcrumbCollector.withBreadcrumb(null, explicitType ? shortNameFor(type) : null, key, () -> {
            T created = createNewInstanceFromParamsAndClosure(type, key, namedParams, body);
            return addElementToCollection(collectionName, created);
        });
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
    public <T> T addNewDslElementToMap(Map<String, Object> namedParams, String mapName, Class<? extends T> type, boolean explicitType, String key, Closure<T> body) {
        return BreadcrumbCollector.withBreadcrumb(null, explicitType ? shortNameFor(type) : null, key, () -> {
            T existing = ((Map<String, T>) getInstanceAttributeOrGetter(mapName)).get(key);
            if (existing != null) {
                if (type != null && type != existing.getClass() && type != getElementTypeOfField(instance.getClass(), mapName))
                    throw new KlumModelException(
                            format("Type mismatch: %s != %s, either use 'apply()' to keep existing object or explicitly create and assign a new object.",
                                    type, existing.getClass()));
                return (T) getProxyFor(existing).apply(namedParams, body);
            }

            T created = createNewInstanceFromParamsAndClosure(type, key, namedParams, body);
            return doAddElementToMap(mapName, key, created);
        });
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
        Type elementType = getElementTypeOfField(instance.getClass(), fieldName);
        key = determineKeyFromMappingClosure(fieldName, value, key);
        if (key == null && isKeyed(getClassFromType(elementType)))
            key = (K) getProxyFor(value).getKey();
        if (key == null)
            throw new IllegalArgumentException("Key is null");
        Map<K, V> target = getInstanceAttribute(fieldName);
        value = forceCastClosure(value, elementType);
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
            throw new KlumModelException(format("Value is not of type %s", elementType));
    }

    private <K, V> K determineKeyFromMappingClosure(String fieldName, V element, K defaultValue) {
        //noinspection unchecked
        return DslHelper.getOptionalFieldAnnotation(instance.getClass(), fieldName, FIELD_ANNOTATION)
                .map(com.blackbuild.groovy.configdsl.transform.Field::keyMapping)
                .filter(DslHelper::isClosure)
                .map(value -> ClosureHelper.invokeClosure((Class<Closure<K>>) value, element))
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
        Class<?> elementType = (Class<?>) getElementTypeOfField(instance.getClass(), fieldName);
        Arrays.stream(scripts).forEach(script -> addElementToCollection(
                fieldName,
                InvokerHelper.invokeMethod(DslHelper.getFactoryOf(elementType), "From", script))
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
        Class<?> elementType = (Class<?>) getElementTypeOfField(instance.getClass(), fieldName);
        Arrays.stream(scripts).forEach(script -> addElementToMap(
                fieldName,
                null,
                InvokerHelper.invokeMethod(DslHelper.getFactoryOf(elementType), "From", script))
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

    public String getBreadcrumbPath() {
        if (breadCrumbQuantifier > 1)
            return breadcrumbPath + "." + breadCrumbQuantifier;
        return breadcrumbPath;
    }

    public void setBreadcrumbPath(String breadcrumbPath) {
        if (this.breadcrumbPath != null)
            throw new KlumModelException("Breadcrumb path already set to " + this.breadcrumbPath);
        this.breadcrumbPath = Objects.requireNonNull(breadcrumbPath);
    }

    public void increaseBreadcrumbQuantifier() {
        breadCrumbQuantifier++;
    }

    void setCurrentTemplates(Map<Class<?>, Object> currentTemplates) {
        this.currentTemplates = currentTemplates;
    }

    void removeCurrentTemplates() {
        this.currentTemplates = Collections.emptyMap();
    }

    public Map<Class<?>, Object> getCurrentTemplates() {
        return currentTemplates;
    }

    /**
     * Schedules the given closure to be executed in the PostApply phase or after the current phase when called from a lifecycle method.
     * @param closure The closure to be executed later
     */
    public void applyLater(Closure<?> closure) {
        KlumPhase phase = PhaseDriver.getCurrentPhase();
        if (phase == null) phase = DefaultKlumPhase.APPLY_LATER;
        applyLater(phase, closure);
    }

    /**
     * Schedules the given closure to be executed in the given phase.
     * @param defaultKlumPhase The phase in which the closure should be executed
     * @param closure The closure to be executed later
     */
    public void applyLater(KlumPhase defaultKlumPhase, Closure<?> closure) {
        applyLater(defaultKlumPhase.getNumber(), closure);
    }

    /**
     * Schedules the given closure to be executed in the given phase.
     * @param number The phase number in which the closure should be executed
     * @param closure The closure to be executed later
     */
    public void applyLater(Integer number, Closure<?> closure) {
        applyLaterClosures.computeIfAbsent(number, ignore -> new ArrayList<>()).add(closure);
        PhaseDriver.getInstance().registerApplyLaterPhase(number);
    }

    /**
     * Executes all closures that were scheduled for the given phase.
     * @param phase The phase number for which to execute the closures
     */
    public void executeApplyLaterClosures(int phase) {
        applyLaterClosures.getOrDefault(phase, Collections.emptyList())
                .forEach(closure -> applyOnly(null, closure));
    }

    /**
     * Cleans up any unnecessary resources or references held by this instance.
     * Currently, this only cleans up stored templates.
     */
    public void cleanup() {
        removeCurrentTemplates();
    }

    public boolean hasMetaData(String key) {
        return metadata.containsKey(key);
    }

    public <T> T getMetaData(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null)
            return null;
        if (!type.isInstance(value))
            throw new KlumException(format("Metadata value for key '%s' is not of type %s", key, type.getName()));
        return (T) value;
    }

    public void setMetaData(String key, Object value) {
        metadata.put(key, value);
    }
}
