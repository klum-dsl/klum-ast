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

import static com.blackbuild.klum.ast.util.DslHelper.getElementType;
import static com.blackbuild.klum.ast.util.DslHelper.isDslType;
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

    private final GroovyObject instance;
    private boolean skipPostCreate;
    private boolean skipPostApply;
    private boolean manualValidation;

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

    // TODO: in RW Proxy
    public void applyNamedParameters(Object rw, Map<String, Object> values) {
        if (values == null) return;
        values.forEach((key, value) -> InvokerHelper.invokeMethod(rw, key, value));
    }

    public void copyFrom(Object template) {
        DslHelper.getDslHierarchyOf(instance.getClass()).forEach(it -> copyFromLayer(it, template));
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
        else
            setInstanceAttribute(fieldName, templateValue);
    }

    private <K,V> void copyFromMapField(Map<K,V> templateValue, String fieldName) {
        if (templateValue.isEmpty()) return;
        Map<K,V> instanceField = (Map<K,V>) getInstanceAttribute(fieldName);
        instanceField.clear();
        instanceField.putAll(templateValue);
    }

    private <T> void copyFromCollectionField(Collection<T> templateValue, String fieldName) {
        if (templateValue.isEmpty()) return;
        Collection<T> instanceField = (Collection<T>) getInstanceAttribute(fieldName);
        instanceField.clear();
        instanceField.addAll(templateValue);
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

    public <T> T createSingleChild(Map<String, Object> namedParams, String fieldOrMethodName, Class<T> type, String key, Closure<T> body) {
        Optional<? extends AnnotatedElement> fieldOrMethod = DslHelper.getField(instance.getClass(), fieldOrMethodName);
        if (!fieldOrMethod.isPresent()) {
            fieldOrMethod = DslHelper.getMethod(getRwInstance().getClass(), fieldOrMethodName, type);
        }

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
        Class<?> elementType = DslHelper.getElementType(instance.getClass(), fieldName);
        // Closures need to explicitly be cast to target types
        if (element instanceof Closure)
            element = (T) InvokerHelper.invokeMethod(element, "asType", elementType);
        InvokerHelper.invokeMethod(getInstanceAttribute(fieldName), "add", element);
        return element;
    }

    public <T> T addElementToCollection(String fieldName, T element) {
        setInstanceAsOwnerFor(element);
        return doAddElementToCollection(fieldName, element);
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

    public static final String ADD_ELEMENTS_FROM_SCRIPTS_TO_COLLECTION = "addElementsFromScriptsToCollection";
    public void addElementsFromScriptsToCollection(String fieldName, Class<? extends Script>... scripts) {
        Class<?> elementType = getElementType(instance.getClass(), fieldName);
        Arrays.stream(scripts).forEach(script -> addElementToCollection(
                fieldName,
                InvokerHelper.invokeStaticMethod(elementType, "createFrom", script))
        );
    }

    public Object invokeMethod(String methodName, Object... args) {
        return InvokerHelper.invokeMethod(instance, methodName, args);
    }

    public Object invokeRwMethod(String methodName, Object... args) {
        return InvokerHelper.invokeMethod(getRwInstance(), methodName, args);
    }

    public void copyFromTemplate() {
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
