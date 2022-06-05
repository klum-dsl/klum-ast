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
package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.Default;
import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.Key;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import com.blackbuild.groovy.configdsl.transform.Validation;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import groovy.transform.Undefined;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.reflection.CachedField;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static com.blackbuild.klum.ast.util.DslHelper.castTo;
import static com.blackbuild.klum.ast.util.DslHelper.getElementType;
import static com.blackbuild.klum.ast.util.DslHelper.isDslType;
import static com.blackbuild.klum.ast.util.DslHelper.isKeyed;
import static groovyjarjarasm.asm.Opcodes.ACC_FINAL;
import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC;
import static groovyjarjarasm.asm.Opcodes.ACC_TRANSIENT;
import static java.lang.String.format;
import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Implementations for generated instance methods.
 */
public class KlumInstanceProxy {

    public static final String NAME_OF_RW_FIELD_IN_MODEL_CLASS = "$rw";
    public static final String NAME_OF_PROXY_FIELD_IN_MODEL_CLASS = "$proxy";
    public static final ClassNode POSTAPPLY_ANNOTATION = make(PostApply.class);
    public static final ClassNode POSTCREATE_ANNOTATION = make(PostCreate.class);
    public static final Class<com.blackbuild.groovy.configdsl.transform.Field> FIELD_ANNOTATION = com.blackbuild.groovy.configdsl.transform.Field.class;

    private final GroovyObject instance;
    private boolean skipPostCreate;
    private boolean skipPostApply;
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
        if (!isDslType(target.getClass()))
            throw new IllegalArgumentException(format("Object of type %s is no dsl object", target.getClass()));
        return (KlumInstanceProxy) InvokerHelper.getAttribute(target, KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS);
    }

    protected GroovyObject getRwInstance() {
        return (GroovyObject) InvokerHelper.getAttribute(instance, KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS);
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

    private void setInstanceAttribute(String name, Object value) {
        getCachedField(name).setProperty(instance, value);
    }

    // TODO: private?
    public Object getInstanceProperty(String name){
        Object value = makeReadOnly(getInstanceAttributeOrGetter(name));

        if (DefaultTypeTransformation.castToBoolean(value))
            return value;

        Default defaultAnnotation = getField(name).getAnnotation(Default.class);
        if (defaultAnnotation == null)
            return value;

        Class<?> fieldType = getField(name).getType();

        if (!defaultAnnotation.field().isEmpty())
            return castTo(getInstanceProperty(defaultAnnotation.field()), fieldType);

        if (!defaultAnnotation.delegate().isEmpty())
            return castTo(InvokerHelper.getProperty(getInstanceProperty(defaultAnnotation.delegate()), name), fieldType);

        return castTo(ClosureHelper.invokeClosureWithDelegate(defaultAnnotation.code(), instance, instance), fieldType);
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
     * Applies the given named params and the closure to this proxy's object. Both params can be null.
     * The map will be converted into a series of method calls, with the key being the method name and the
     * value the single method argument.
     * @param values Map of String to Object which will be translated into Method calls
     * @param body Closure to be executed against the instance.
     * @return the object itself
     */
    public Object apply(Map<String, Object> values, Closure<?> body) {
        Object rw = instance.getProperty(NAME_OF_RW_FIELD_IN_MODEL_CLASS);
        applyNamedParameters(rw, values);
        applyClosure(rw, body);
        postApply();

        return instance;
    }

    private void applyClosure(Object rw, Closure<?> body) {
        if (body == null) return;
        body.setDelegate(rw);
        body.setResolveStrategy(Closure.DELEGATE_ONLY);
        body.call();
    }

    private void applyNamedParameters(Object rw, Map<String, Object> values) {
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
            Arrays.stream(layer.getDeclaredFields()).filter(this::isNotIgnored).forEach(field -> copyFromField(field, template));
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
        else if (isDslType(templateValue.getClass()))
            setInstanceAttribute(fieldName, getProxyFor(templateValue).cloneInstance());
        else
            setInstanceAttribute(fieldName, templateValue);
    }

    private <T> T getCopiedValue(T templateValue) {
        if (isDslType(templateValue.getClass()))
            return (T) getProxyFor(templateValue).cloneInstance();
        else
            return templateValue;

    }

    private <K,V> void copyFromMapField(Map<K,V> templateValue, String fieldName) {
        if (templateValue.isEmpty()) return;
        Map<K,V> instanceField = getInstanceAttribute(fieldName);
        instanceField.clear();
        instanceField.putAll(templateValue);
    }

    private <T> void copyFromCollectionField(Collection<T> templateValue, String fieldName) {
        if (templateValue.isEmpty()) return;
        Collection<T> instanceField = getInstanceAttribute(fieldName);
        instanceField.clear();
        instanceField.addAll(templateValue);
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
     * Runs the postcreate lifecycle methods for this instance
     */
    void postCreate() {
        if (!skipPostCreate)
            executeLifecycleMethod(PostCreate.class);
    }

    /**
     * Runs the postapply lifecycle methods for this instance
     */
    void postApply() {
        if (!skipPostApply)
            executeLifecycleMethod(PostApply.class);
    }

    private void executeLifecycleMethod(Class<? extends Annotation> annotation) {
        Object rw = getRwInstance();
        DslHelper.getMethodsAnnotatedWith(rw.getClass(), annotation)
                .stream()
                .map(Method::getName)
                .distinct()
                .forEach(method -> InvokerHelper.invokeMethod(rw, method, null));
    }

    /**
     * Executes validation for this instance
     */
    public void validate() {
        new Validator(instance).execute();
    }

    boolean getManualValidation() {
        if (manualValidation) return true;
        Validation annotation = instance.getClass().getAnnotation(Validation.class);
        if (annotation == null) return false;
        return (annotation.mode() == Validation.Mode.MANUAL);
    }

    void manualValidation() {
        manualValidation = true;
    }

    void manualValidation(boolean value) {
        manualValidation = value;
    }

    void setOwners(Object value) {
        DslHelper.getFieldsAnnotatedWith(instance.getClass(), Owner.class)
                .stream()
                .filter(field -> field.getType().isInstance(value))
                .filter(field -> getInstanceAttribute(field.getName()) == null)
                .forEach(field -> setInstanceAttribute(field.getName(), value));

        DslHelper.getMethodsAnnotatedWith(getRwInstance().getClass(), Owner.class)
                .stream()
                .filter(method -> method.getParameterTypes()[0].isInstance(value))
                .map(Method::getName)
                .distinct()
                .forEach(method -> getRwInstance().invokeMethod(method, value));
    }

    public <T> T createSingleChild(Map<String, Object> namedParams, String fieldOrMethodName, Class<T> type, String key, Closure<T> body) {
        Optional<? extends AnnotatedElement> fieldOrMethod = DslHelper.getField(instance.getClass(), fieldOrMethodName);

        if (!fieldOrMethod.isPresent())
            fieldOrMethod = DslHelper.getVirtualSetter(getRwInstance().getClass(), fieldOrMethodName, type);

        if (!fieldOrMethod.isPresent())
            throw new GroovyRuntimeException(format("Neither field nor single argument method named %s with type %s found in %s", fieldOrMethodName, type, instance.getClass()));

        String effectiveKey = resolveKeyForFieldFromAnnotation(fieldOrMethodName, fieldOrMethod.get()).orElse(key);
        T created = createNewInstanceFromParamsAndClosure(type, effectiveKey, namedParams, body);
        return callSetterOrMethod(fieldOrMethodName, created);
    }

    public <T> T setSingleField(String fieldOrMethodName, T value) {
        setInstanceAsOwnerFor(value);
        return callSetterOrMethod(fieldOrMethodName, value);
    }

    public <T> T setSingleFieldViaConverter(String fieldOrMethodName, Class<?> converterType, String converterMethod, Object... args) {
        return setSingleField(fieldOrMethodName, createObjectViaConverter(converterType, converterMethod, args));
    }

    private <T> T createObjectViaConverter(Class<?> converterType, String converterMethod, Object... args) {
        if (converterMethod == null)
            return (T) InvokerHelper.invokeConstructorOf(converterType, args);
        return (T) InvokerHelper.invokeMethod(converterType, converterMethod, args);
    }

    private void setInstanceAsOwnerFor(Object value) {
        if (value != null && isDslType(value.getClass()))
            getProxyFor(value).setOwners(instance);
    }

    private <T> T callSetterOrMethod(String fieldOrMethodName, T value) {
        if (DslHelper.getField(instance.getClass(), fieldOrMethodName).isPresent())
            setInstanceAttribute(fieldOrMethodName, value);
        else
            invokeRwMethod(fieldOrMethodName, value);
        return value;
    }

    private <T> T doAddElementToCollection(String fieldName, T element) {
        Type elementType = DslHelper.getElementType(instance.getClass(), fieldName);
        element = forceCastClosure(element, elementType);
        Collection<T> target = getInstanceAttribute(fieldName);
        target.add(element);
        return element;
    }

    public <T> T addElementToCollection(String fieldName, T element) {
        setInstanceAsOwnerFor(element);
        return doAddElementToCollection(fieldName, element);
    }

    public <T> T addElementToCollectionViaConverter(String fieldOrMethodName, Class<?> converterType, String converterMethod, Object... args) {
        return addElementToCollection(fieldOrMethodName, createObjectViaConverter(converterType, converterMethod, args));
    }

    public static final String ADD_NEW_DSL_ELEMENT_TO_COLLECTION = "addNewDslElementToCollection";

    public <T> T addNewDslElementToCollection(Map<String, Object> namedParams, String collectionName, Class<? extends T> type, String key, Closure<T> body) {
        T created = createNewInstanceFromParamsAndClosure(type, key, namedParams, body);
        return doAddElementToCollection(collectionName, created);
    }

    private <T> T createNewInstanceFromParamsAndClosure(Class<? extends T> type, String key, Map<String, Object> namedParams, Closure<T> body) {
        T created = FactoryHelper.createInstance(type, key);
        KlumInstanceProxy createdProxy = getProxyFor(created);
        createdProxy.copyFromTemplate();
        createdProxy.setOwners(instance);
        createdProxy.postCreate();
        createdProxy.apply(namedParams, body);
        return created;
    }

    public void addElementsToCollection(String fieldName, Object... elements) {
        Arrays.stream(elements).forEach(element -> addElementToCollection(fieldName, element));
    }

    public void addElementsToCollection(String fieldName, Iterable<?> elements) {
        elements.forEach(element -> addElementToCollection(fieldName, element));
    }

    public <K,V> void addElementsToMap(String fieldName, Map<K, V> values) {
        values.forEach((key, value) -> addElementToMap(fieldName, key, value));
    }

    public <V> void addElementsToMap(String fieldName, Iterable<V> values) {
        values.forEach(value -> addElementToMap(fieldName, null, value));
    }

    public void addElementsToMap(String fieldName, Object... values) {
        Arrays.stream(values).forEach(value -> addElementToMap(fieldName, null, value));
    }

    public <T> T addNewDslElementToMap(Map<String, Object> namedParams, String mapName, Class<? extends T> type, String key, Closure<T> body) {
        T created = createNewInstanceFromParamsAndClosure(type, key, namedParams, body);
        return doAddElementToMap(mapName, key, created);
    }

    public <K,V> V addElementToMap(String fieldName, K key, V value) {
        setInstanceAsOwnerFor(value);
        return doAddElementToMap(fieldName, key, value);
    }

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
        return (K) DslHelper.getOptionalFieldAnnotation(instance.getClass(), fieldName, FIELD_ANNOTATION)
                .map(com.blackbuild.groovy.configdsl.transform.Field::keyMapping)
                .filter(DslHelper::isClosure)
                .map(value -> ClosureHelper.invokeClosure(value, element))
                .orElse(defaultValue);
    }


    public static final String ADD_ELEMENTS_FROM_SCRIPTS_TO_COLLECTION = "addElementsFromScriptsToCollection";
    @SafeVarargs
    public final void addElementsFromScriptsToCollection(String fieldName, Class<? extends Script>... scripts) {
        Class<?> elementType = (Class<?>) getElementType(instance.getClass(), fieldName);
        Arrays.stream(scripts).forEach(script -> addElementToCollection(
                fieldName,
                InvokerHelper.invokeStaticMethod(elementType, "createFrom", script))
        );
    }

    public static final String ADD_ELEMENTS_FROM_SCRIPTS_TO_MAP = "addElementsFromScriptsToMap";
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

        return Optional.of(ClosureHelper.invokeClosureWithDelegate((Class<? extends Closure<String>>) keyMember, instance, instance));
    }

    void skipPostCreate() {
        this.skipPostCreate = true;
    }

    void skipPostApply() {
        this.skipPostApply = true;
    }




}
