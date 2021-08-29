package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.Default;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MissingPropertyException;
import groovy.transform.Undefined;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static com.blackbuild.klum.ast.util.DslHelper.isDslType;
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

    private final GroovyObject instance;
    private boolean skipPostCreate;
    private boolean skipPostApply;

    public KlumInstanceProxy(GroovyObject instance) {
        this.instance = instance;
    }

    public static KlumInstanceProxy getProxyFor(Object target) {
        return (KlumInstanceProxy) InvokerHelper.getAttribute(target, KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS);
    }

    protected GroovyObject getRwInstance() {
        return (GroovyObject) InvokerHelper.getAttribute(instance, KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS);
    }

    public Object getInstanceAttribute(String attributeName) {
        try {
            return getField(attributeName).get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setInstanceAttribute(String name, Object value) {
        try {
            getField(name).set(instance, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public Object getInstanceProperty(String name){
        Object value = makeReadOnly(getInstanceAttribute(name));

        if (DefaultTypeTransformation.castToBoolean(value))
            return value;

        Default defaultAnnotation = getField(name).getAnnotation(Default.class);
        if (defaultAnnotation == null)
            return value;

        if (!defaultAnnotation.field().isEmpty())
            return getInstanceProperty(defaultAnnotation.field());

        if (!defaultAnnotation.delegate().isEmpty())
            return getProxyFor(getInstanceProperty(defaultAnnotation.delegate())).getInstanceProperty(name);

        return ClosureHelper.invokeClosureWithDelegate(defaultAnnotation.code(), instance, instance);
    }

    private <T> T makeReadOnly(T value) {
        if (value instanceof Collection || value instanceof Map)
            return (T) InvokerHelper.invokeMethod(DefaultGroovyMethods.class, "asImmutable", value);
        return value;
    }

    Field getField(String name) {
        return DslHelper.getField(instance.getClass(), name)
                .orElseThrow(() -> new MissingPropertyException(name, instance.getClass()));
    }

    public Object apply(Map<String, Object> values, Closure<?> body) {
        Object rw = instance.getProperty(NAME_OF_RW_FIELD_IN_MODEL_CLASS);
        applyNamedParameters(rw, values);

        body.setDelegate(rw);
        body.setResolveStrategy(Closure.DELEGATE_ONLY);
        body.call();
        postApply();

        return instance;
    }

    // TODO: in RW Proxy
    public void applyNamedParameters(Object rw, Map<String, Object> values) {
        values.forEach((key, value) -> InvokerHelper.invokeMethod(rw, key, value));
    }

    public Object getKey() {
        Optional<Field> keyField = DslHelper.getKeyField(instance.getClass());

        if (!keyField.isPresent())
            throw new AssertionError();

        return instance.getProperty(keyField.get().getName());
    }

    public void postCreate() {
        if (!skipPostCreate)
            executeLifecycleMethod(PostCreate.class);
    }

    public void postApply() {
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

    public void validate() {
        new Validator(instance).execute();
    }

    boolean getManualValidation() {
        return (boolean) InvokerHelper.getAttribute(instance, "$manualValidation");
    }

    public void setOwners(Object value) {
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

    public <T> T createSingleChild(String fieldOrMethodName, Class<T> type, String key, Map<String, Object> namedParams, Closure<?> body) {
        Optional<? extends AnnotatedElement> fieldOrMethod = DslHelper.getField(instance.getClass(), fieldOrMethodName);
        if (!fieldOrMethod.isPresent()) {
            fieldOrMethod = DslHelper.getMethod(getRwInstance().getClass(), fieldOrMethodName, type);
        }

        if (!fieldOrMethod.isPresent())
            throw new GroovyRuntimeException(format("Neither field nor single argument method named %s with type %s found in %s", fieldOrMethodName, type, instance.getClass()));

        String effectiveKey = resolveKeyForFieldFromAnnotation(fieldOrMethodName, fieldOrMethod.get()).orElse(key);
        T created = (T) InvokerHelper.invokeConstructorOf(type, effectiveKey);
        KlumInstanceProxy createdProxy = getProxyFor(created);
        createdProxy.copyFromTemplate();
        createdProxy.setOwners(instance);
        createdProxy.postCreate();
        createdProxy.apply(namedParams, body);
        callSetterOrMethod(fieldOrMethodName, created);
        return created;
    }

    public <T> T setSingleField(String fieldOrMethodName, T value) {
        setInstanceAsOwnerFor(value);
        callSetterOrMethod(fieldOrMethodName, value);
        return value;
    }

    private void setInstanceAsOwnerFor(Object value) {
        if (value != null && isDslType(value.getClass()))
            getProxyFor(value).setOwners(instance);
    }

    private void callSetterOrMethod(String fieldOrMethodName, Object value) {
        if (DslHelper.getField(instance.getClass(), fieldOrMethodName).isPresent())
            setInstanceAttribute(fieldOrMethodName, value);
        else
            invokeRwMethod(fieldOrMethodName, value);
    }

    public <T> T addElementToCollection(String fieldName, T element) {
        setInstanceAsOwnerFor(element);
        Class<?> elementType = DslHelper.getElementType(instance.getClass(), fieldName);
        // Closures need to explicitly be cast to target types
        if (element instanceof Closure)
            element = (T) InvokerHelper.invokeMethod(element, "asType", elementType);
        InvokerHelper.invokeMethod(getInstanceAttribute(fieldName), "add", element);
        return element;
    }

    public <T> T addDslElementToCollection(String collectionName, Class<? extends T> type, String key, Map<String, Object> namedParams, Closure<T> body) {
        T created = FactoryHelper.createInstance(type,key);
        KlumInstanceProxy createdProxy = getProxyFor(created);
        createdProxy.copyFromTemplate();
        addElementToCollection(collectionName, created);
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

    public Object invokeMethod(String methodName, Object... args) {
        return InvokerHelper.invokeMethod(instance, methodName, args);
    }

    public Object invokeRwMethod(String methodName, Object... args) {
        return InvokerHelper.invokeMethod(getRwInstance(), methodName, args);
    }

    public void copyFromTemplate() {
        getRwInstance().invokeMethod("copyFromTemplate", null);
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


}
