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

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.NoClosure;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.klum.ast.KlumModelObject;
import com.blackbuild.klum.ast.KlumRwObject;
import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import com.blackbuild.klum.ast.process.ConstructionSession;
import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.KlumPhase;
import com.blackbuild.klum.ast.process.PhaseDriver;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import groovy.lang.Reference;
import groovy.lang.Script;
import groovy.transform.Undefined;
import org.codehaus.groovy.reflection.CachedField;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.tools.Utilities;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.blackbuild.klum.ast.util.DslHelper.*;
import static java.lang.String.format;

/**
 * Mutable construction-time counterpart of a DSL Object.
 *
 * <p>Generated Builders inherit this class and own every mutable value until
 * the graph is materialized. A completed model never retains its Builder.</p>
 *
 * <p>The deprecated {@link KlumRwObject} marker is retained temporarily for
 * integrations migrating from the former RW layout and its
 * {@link KlumInstanceProxy} compatibility adapter. New code should identify
 * construction values through {@code KlumBuilder} directly.</p>
 */
@SuppressWarnings({"unused", "unchecked", "java:S100", "deprecation", "removal"}) // legacy marker plus generated-code ABI hooks
public abstract class KlumBuilder<M> extends GroovyObjectSupport implements KlumRwObject, Serializable {

    public static final String ADD_NEW_DSL_ELEMENT_TO_COLLECTION = "addNewDslElementToCollection";
    public static final String ADD_ELEMENTS_FROM_SCRIPTS_TO_COLLECTION = "addElementsFromScriptsToCollection";
    public static final String ADD_ELEMENTS_FROM_SCRIPTS_TO_MAP = "addElementsFromScriptsToMap";

    private final Class<M> modelType;
    @SuppressWarnings("java:S1948") // generated DSL model implementations are always Serializable
    private M completedModel;
    private boolean sealed;
    private boolean template;
    private transient ConstructionSession constructionSession;
    private transient boolean constructionSessionActive;

    private String breadcrumbPath;
    private String modelPath;
    private int breadcrumbQuantifier = 1;
    private transient Map<Class<?>, Object> currentTemplates = Collections.emptyMap();
    private final Map<String, Serializable> metadata = new HashMap<>();
    private final Map<Integer, List<Closure<?>>> applyLaterClosures = new TreeMap<>();
    private final List<KlumBuilder<?>> virtualChildren = new ArrayList<>();

    private static final MaterializationToken MATERIALIZATION_TOKEN = new MaterializationToken();

    /**
     * Unforgeable capability required by generated model constructors.
     * The type is public solely so generated classes in arbitrary packages can use it in an internal signature.
     */
    public static final class MaterializationToken {
        private MaterializationToken() {
        }
    }

    protected KlumBuilder(Class<M> modelType) {
        this.modelType = Objects.requireNonNull(modelType);
    }

    /** Internal hook used by {@link PhaseDriver}; the token remains opaque to clients. */
    public final void $attachConstructionSession(ConstructionSession session) {
        Objects.requireNonNull(session);
        if (constructionSession != null && constructionSession != session)
            throw crossSessionAdoptionError();
        constructionSession = session;
        constructionSessionActive = true;
    }

    /** Internal hook used by {@link PhaseDriver} when the owning root lifecycle ends. */
    public final void $completeConstructionSession(ConstructionSession session) {
        if (constructionSession == session)
            constructionSessionActive = false;
    }

    public final Class<M> getModelType() {
        return modelType;
    }

    public final boolean isSealed() {
        return sealed;
    }

    public final boolean isTemplate() {
        return template;
    }

    public final void markAsTemplate() {
        template = true;
    }

    public final M getCompletedModel() {
        return completedModel;
    }

    public final void sealTo(M existingModel) {
        if (!modelType.isInstance(existingModel))
            throw new KlumModelException(format("Cannot seal Builder for %s to %s", modelType.getName(), existingModel.getClass().getName()));
        completedModel = existingModel;
        sealed = true;
    }

    /** Identifies the generated model implementation. Allocation remains private to graph materialization. */
    protected abstract Class<? extends M> $modelImplementationType();

    /**
     * Validates the capability passed through a generated model constructor chain.
     * Client calls can name the token type but cannot obtain the required instance.
     */
    public static void $requireMaterializationToken(MaterializationToken token) {
        if (token != MATERIALIZATION_TOKEN)
            throw new KlumModelException("DSL Objects can only be constructed by internal materialization");
    }

    private M instantiateModel() {
        Class<? extends M> implementationType = Objects.requireNonNull(
                $modelImplementationType(), "Generated Builder returned no model implementation type");
        Constructor<?> constructor = Arrays.stream(implementationType.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 2)
                .filter(candidate -> candidate.getParameterTypes()[0].isInstance(this))
                .filter(candidate -> candidate.getParameterTypes()[1] == MaterializationToken.class)
                .findFirst()
                .orElseThrow(() -> new KlumModelException("No internal Builder constructor found for " + implementationType.getName()));
        try {
            if (!constructor.trySetAccessible())
                throw new KlumModelException("Cannot access internal Builder constructor for " + implementationType.getName());
            return (M) constructor.newInstance(this, MATERIALIZATION_TOKEN);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException)
                throw runtimeException;
            throw new KlumModelException("Could not instantiate internal model implementation " + implementationType.getName(), exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new KlumModelException("Could not instantiate internal model implementation " + implementationType.getName(), exception);
        }
    }

    /** Assigns relationship fields after every object in the graph was allocated. */
    protected void $assignRelationships() {
        // generated layers add their declared relationship assignments
    }

    final void allocateModel() {
        if (completedModel != null)
            return;
        completedModel = Objects.requireNonNull(instantiateModel(), "Generated Builder returned no model");
        sealed = true;
    }

    /**
     * Materializes a complete Builder graph in two passes so cycles and self links are preserved.
     */
    static Object materializeGraph(KlumBuilder<?> root) {
        List<KlumBuilder<?>> graph = collectGraph(root);
        graph.forEach(KlumBuilder::allocateModel);
        graph.forEach(KlumBuilder::$assignRelationships);
        return root.getCompletedModel();
    }

    /** Internal Builder hook that assigns one completed relationship without exposing a model mutator. */
    protected final void $assignMaterializedRelationship(String fieldName) {
        CachedField target = DslHelper.getCachedField(completedModel.getClass(), fieldName)
                .orElseThrow(() -> new MissingPropertyException(fieldName, completedModel.getClass()));
        target.setProperty(completedModel, $materializeRelationship(fieldName));
    }

    private static List<KlumBuilder<?>> collectGraph(KlumBuilder<?> root) {
        Set<KlumBuilder<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<KlumBuilder<?>> ordered = new ArrayList<>();
        collectGraph(root, visited, ordered);
        return ordered;
    }

    private static void collectGraph(KlumBuilder<?> builder, Set<KlumBuilder<?>> visited, List<KlumBuilder<?>> ordered) {
        if (builder == null || builder.isSealed() || !visited.add(builder))
            return;
        ordered.add(builder);
        builder.relationshipValues().forEach(value -> collectRelationshipValue(value, visited, ordered));
    }

    private static void collectRelationshipValue(Object value, Set<KlumBuilder<?>> visited, List<KlumBuilder<?>> ordered) {
        if (value instanceof KlumBuilder) {
            collectGraph((KlumBuilder<?>) value, visited, ordered);
        } else if (value instanceof Collection) {
            ((Collection<?>) value).forEach(member -> collectRelationshipValue(member, visited, ordered));
        } else if (value instanceof Map) {
            ((Map<?, ?>) value).values().forEach(member -> collectRelationshipValue(member, visited, ordered));
        }
    }

    private List<Object> relationshipValues() {
        List<Object> values = new ArrayList<>();
        for (Class<?> layer : DslHelper.getDslHierarchyOf(modelType)) {
            for (Field field : layer.getDeclaredFields()) {
                if (DslHelper.isRelationship(field))
                    values.add(getInstanceAttribute(field.getName()));
            }
        }
        values.addAll(virtualChildren);
        return values;
    }

    /** Called by generated model constructors for non-relationship fields. */
    public final Object $snapshotField(String fieldName) {
        Field schemaField = getModelField(fieldName);
        Object value = getInstanceAttribute(fieldName);
        return isMutableTransient(schemaField) ? value : snapshot(value, schemaField.getType());
    }

    /** Called by generated internal relationship assignment code. */
    public final Object $materializeRelationship(String fieldName) {
        Field schemaField = getModelField(fieldName);
        Object value = getInstanceAttribute(fieldName);
        if (value == null)
            return null;
        if (value instanceof KlumBuilder)
            return ((KlumBuilder<?>) value).getCompletedModel();
        if (value instanceof Map) {
            Map<Object, Object> mapped = newMapSnapshotSource((Map<?, ?>) value, schemaField.getType());
            ((Map<?, ?>) value).forEach((key, member) -> mapped.put(key, completedValue(member)));
            return isMutableTransient(schemaField) ? mapped : makeMapReadOnly(mapped, schemaField.getType());
        }
        if (value instanceof Collection) {
            Collection<Object> mapped = newCollectionSnapshotSource((Collection<?>) value, schemaField.getType());
            ((Collection<?>) value).forEach(member -> mapped.add(completedValue(member)));
            return isMutableTransient(schemaField) ? mapped : makeCollectionReadOnly(mapped, schemaField.getType());
        }
        throw new KlumModelException(format("Relationship field %s.%s contains unsupported value %s", modelType.getName(), fieldName, value.getClass().getName()));
    }

    private static Object completedValue(Object value) {
        return value instanceof KlumBuilder ? ((KlumBuilder<?>) value).getCompletedModel() : value;
    }

    private static boolean isMutableTransient(Field field) {
        return DslHelper.getKlumFieldType(field) == FieldType.TRANSIENT || Modifier.isTransient(field.getModifiers());
    }

    private static Object snapshot(Object value, Class<?> declaredType) {
        if (value == null)
            return null;
        if (value instanceof KlumBuilder)
            return ((KlumBuilder<?>) value).getCompletedModel();
        if (value instanceof EnumSet)
            return ((EnumSet<?>) value).clone();
        if (value instanceof Map) {
            Map<Object, Object> copy = newMapSnapshotSource((Map<?, ?>) value, declaredType);
            ((Map<?, ?>) value).forEach((key, member) -> copy.put(key, completedValue(member)));
            return makeMapReadOnly(copy, declaredType);
        }
        if (value instanceof Collection) {
            Collection<Object> copy = newCollectionSnapshotSource((Collection<?>) value, declaredType);
            ((Collection<?>) value).forEach(member -> copy.add(completedValue(member)));
            return makeCollectionReadOnly(copy, declaredType);
        }
        return value;
    }

    private static Collection<Object> newCollectionSnapshotSource(Collection<?> source, Class<?> declaredType) {
        if (NavigableSet.class.equals(declaredType) || SortedSet.class.equals(declaredType)) {
            Comparator<Object> comparator = source instanceof SortedSet ? (Comparator<Object>) ((SortedSet<?>) source).comparator() : null;
            return new TreeSet<>(comparator);
        }
        if (Set.class.equals(declaredType))
            return new LinkedHashSet<>();
        return new ArrayList<>();
    }

    private static Map<Object, Object> newMapSnapshotSource(Map<?, ?> source, Class<?> declaredType) {
        if (NavigableMap.class.equals(declaredType) || SortedMap.class.equals(declaredType)) {
            Comparator<Object> comparator = source instanceof SortedMap ? (Comparator<Object>) ((SortedMap<?, ?>) source).comparator() : null;
            return new TreeMap<>(comparator);
        }
        return new LinkedHashMap<>();
    }

    private static Object makeCollectionReadOnly(Collection<?> copy, Class<?> declaredType) {
        if (NavigableSet.class.equals(declaredType))
            return Collections.unmodifiableNavigableSet((NavigableSet<?>) copy);
        if (SortedSet.class.equals(declaredType))
            return Collections.unmodifiableSortedSet((SortedSet<?>) copy);
        if (Set.class.equals(declaredType))
            return Collections.unmodifiableSet((Set<?>) copy);
        return Collections.unmodifiableList((List<?>) copy);
    }

    private static Object makeMapReadOnly(Map<?, ?> copy, Class<?> declaredType) {
        if (NavigableMap.class.equals(declaredType))
            return Collections.unmodifiableNavigableMap((NavigableMap<?, ?>) copy);
        if (SortedMap.class.equals(declaredType))
            return Collections.unmodifiableSortedMap((SortedMap<?, ?>) copy);
        return Collections.unmodifiableMap(copy);
    }

    final ModelState exportModelState() {
        return new ModelState(getBreadcrumbPath(), modelPath, metadata);
    }

    /**
     * Creates the internal companion retained by a generated DSL Object.
     * Public visibility exists only for generated constructors in arbitrary packages.
     */
    public final KlumObjectCompanion $createCompanion(GroovyObject model) {
        if (template)
            return new KlumTemplateProxy(
                    model,
                    getBreadcrumbPath(),
                    modelPath,
                    TemplateRecipeState.capture(applyLaterClosures)
            );
        return new KlumModelProxy(model, exportModelState());
    }

    static final class ModelState implements Serializable {
        private final String breadcrumbPath;
        private final String modelPath;
        private final Map<String, Serializable> metadata;

        private ModelState(String breadcrumbPath, String modelPath, Map<String, Serializable> metadata) {
            this.breadcrumbPath = breadcrumbPath;
            this.modelPath = modelPath;
            this.metadata = new HashMap<>(metadata);
        }

        String getBreadcrumbPath() {
            return breadcrumbPath;
        }

        String getModelPath() {
            return modelPath;
        }

        Map<String, Serializable> getMetadata() {
            return metadata;
        }

    }

    public <T> T getInstanceAttribute(String attributeName) {
        return (T) getCachedField(attributeName).getProperty(this);
    }

    public <T> T getInstanceAttributeOrGetter(String attributeName) {
        Optional<CachedField> field = DslHelper.getCachedField(getClass(), attributeName);
        if (field.isPresent())
            return (T) field.get().getProperty(this);
        return (T) InvokerHelper.getProperty(this, attributeName);
    }

    public Object getInstanceProperty(String name) {
        return getInstanceAttributeOrGetter(name);
    }

    public void setInstanceAttribute(String name, Object value) {
        assertMutable();
        Field schemaField = DslHelper.getField(modelType, name).orElse(null);
        Object normalized = schemaField != null ? normalizeForField(schemaField, value) : value;
        getCachedField(name).setProperty(this, normalized);
    }

    public Field getField(String name) {
        return DslHelper.getField(getClass(), name)
                .orElseThrow(() -> new MissingPropertyException(name, getClass()));
    }

    public Field getModelField(String name) {
        return DslHelper.getField(modelType, name)
                .orElseThrow(() -> new MissingPropertyException(name, modelType));
    }

    CachedField getCachedField(String name) {
        return DslHelper.getCachedField(getClass(), name)
                .orElseThrow(() -> new MissingPropertyException(name, getClass()));
    }

    private Object normalizeForField(Field schemaField, Object value) {
        if (!DslHelper.isRelationship(schemaField) || value == null)
            return value;
        if (value instanceof Map) {
            Map<Object, Object> result = newMutableMapLike((Map<?, ?>) value);
            ((Map<?, ?>) value).forEach((key, member) -> result.put(key, normalizeRelationshipValue(schemaField, member)));
            return result;
        }
        if (value instanceof Collection) {
            Collection<Object> result = newMutableCollectionLike((Collection<?>) value);
            ((Collection<?>) value).forEach(member -> result.add(normalizeRelationshipValue(schemaField, member)));
            return result;
        }
        return normalizeRelationshipValue(schemaField, value);
    }

    private Object normalizeRelationshipValue(Field schemaField, Object value) {
        if (value == null)
            return value;
        if (value instanceof KlumBuilder) {
            KlumBuilder<?> builderValue = (KlumBuilder<?>) value;
            if (builderValue.isSealed() && TemplateManager.isTemplate(builderValue.getCompletedModel()))
                throw templateRelationshipInputError(schemaField);
            if (builderValue.isSealed() && !acceptsCompletedRelationship(schemaField))
                throw completedRelationshipInputError(schemaField);
            if (!builderValue.isSealed())
                assertSameConstructionSession(builderValue);
            return value;
        }
        if (!(value instanceof KlumModelObject))
            throw new KlumModelException(format("Value for relationship %s.%s is neither a Builder nor a completed DSL Object", modelType.getName(), schemaField.getName()));
        if (TemplateManager.isTemplate(value))
            throw templateRelationshipInputError(schemaField);
        if (!acceptsCompletedRelationship(schemaField))
            throw completedRelationshipInputError(schemaField);
        return FactoryHelper.wrapCompletedModel(value);
    }

    private static boolean acceptsCompletedRelationship(Field schemaField) {
        return DslHelper.isLink(schemaField);
    }

    private KlumModelException completedRelationshipInputError(Field schemaField) {
        return new KlumModelException(format(
                "Completed DSL Object inputs are only supported for LINK relationships (%s.%s)",
                modelType.getName(),
                schemaField.getName()
        ));
    }

    private KlumModelException templateRelationshipInputError(Field schemaField) {
        return new KlumModelException(format(
                "Cannot use a Template as relationship value %s.%s. "
                        + "Rehydrate it with Template.With, copyFrom, or another Template/copy API so a fresh Builder graph is created.",
                modelType.getName(),
                schemaField.getName()
        ));
    }

    private void assertSameConstructionSession(KlumBuilder<?> child) {
        if (constructionSession == null && child.constructionSession == null)
            return;
        assertConstructionSessionActive();
        child.assertConstructionSessionActive();
        if (constructionSession != child.constructionSession)
            throw crossSessionAdoptionError();
    }

    private static KlumModelException crossSessionAdoptionError() {
        return new KlumModelException("Cannot adopt a Builder from a different Construction session. "
                + "Call Create.AsBuilder inside the owning root Builder lifecycle and attach that Builder there; "
                + "Builders cannot cross root lifecycles.");
    }

    private static Collection<Object> newMutableCollectionLike(Collection<?> source) {
        if (source instanceof SortedSet)
            return new TreeSet<>((Comparator<Object>) ((SortedSet<?>) source).comparator());
        if (source instanceof Set)
            return new LinkedHashSet<>();
        return new ArrayList<>();
    }

    private static Map<Object, Object> newMutableMapLike(Map<?, ?> source) {
        if (source instanceof SortedMap)
            return new TreeMap<>((Comparator<Object>) ((SortedMap<?, ?>) source).comparator());
        return new LinkedHashMap<>();
    }

    /**
     * Applies named values and a configuration closure to this Builder.
     * @param values named values translated into Builder method calls
     * @param body closure executed against this Builder
     * @return this Builder
     */
    public Object apply(Map<String, ?> values, Closure<?> body) {
        assertMutable();
        applyOnly(values, body);
        LifecycleHelper.executeLifecycleMethods(this, PostApply.class);
        return this;
    }

    public Object apply(Map<String, ?> values) {
        return apply(values, null);
    }

    public Object apply(Closure<?> body) {
        return apply(null, body);
    }

    public void applyOnly(Map<String, ?> values, Closure<?> body) {
        assertMutable();
        applyNamedParameters(values);
        applyClosure(body);
    }

    private void assertMutable() {
        assertConstructionSessionActive();
        if (sealed)
            throw new KlumModelException("A sealed Builder cannot be configured");
    }

    private void assertConstructionSessionActive() {
        if (constructionSession != null && !constructionSessionActive)
            throw new KlumModelException("Cannot use a Builder after its Construction session has completed. "
                    + "Create and attach a fresh child with Create.AsBuilder inside the owning root Builder lifecycle.");
    }

    private void applyClosure(Closure<?> body) {
        if (body == null)
            return;
        body.setDelegate(this);
        body.setResolveStrategy(Closure.DELEGATE_ONLY);
        body.call();
    }

    private void applyNamedParameters(Map<String, ?> values) {
        if (values != null)
            values.forEach((key, value) -> InvokerHelper.invokeMethod(this, key, value));
    }

    /**
     * Copies all non-null/non-empty recipe values from the template to this Builder.
     * @param template the recipe to apply
     */
    public void copyFrom(Object template) {
        if (template != null) {
            assertMutable();
            if (template instanceof KlumBuilder)
                assertValidBuilderCopySource((KlumBuilder<?>) template);
            CopyHandler.copyToFrom(this, template);
            copyApplyLaterClosuresFrom(template);
        }
    }

    private void assertValidBuilderCopySource(KlumBuilder<?> sourceBuilder) {
        if (sourceBuilder.sealed)
            throw new KlumModelException("Cannot copy from a sealed Builder. "
                    + "Use its completed model for a values-only copy, or copy from a marked Template to replay recipe actions.");
        if (constructionSession == null
                || sourceBuilder.constructionSession == null
                || constructionSession != sourceBuilder.constructionSession
                || !sourceBuilder.constructionSessionActive)
            throw new KlumModelException("Cannot copy from a Builder outside this active Construction session. "
                    + "Create the source with Create.AsBuilder inside the same root Builder lifecycle.");
    }

    /** Internal target used by generated typed copyFrom overloads. */
    public void copyFromRecipe(Object template) {
        copyFrom(template);
    }

    public Object getNullableKey() {
        return DslHelper.getKeyField(modelType)
                .map(Field::getName)
                .map(this::getInstanceProperty)
                .orElse(null);
    }

    Object getKey() {
        return DslHelper.getKeyField(modelType)
                .map(Field::getName)
                .map(this::getInstanceProperty)
                .orElseThrow(AssertionError::new);
    }

    public Object getSingleOwner() {
        Set<Object> owners = getOwners();
        if (owners.size() > 1)
            throw new KlumModelException("Object has more than one distinct owner");
        return owners.stream().findFirst().orElse(null);
    }

    public Set<Object> getOwners() {
        return getFieldsAnnotatedWith(getClass(), Owner.class)
                .filter(field -> field.getAnnotation(Owner.class).converter() == NoClosure.class)
                .filter(field -> !field.getAnnotation(Owner.class).transitive())
                .filter(field -> !field.getAnnotation(Owner.class).root())
                .map(Field::getName)
                .map(this::getInstanceProperty)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Creates a new '{{fieldName}}' Builder {{param:type?with the given type}} or configures the existing Builder.
     * The resulting Builder is configured by the optional values and closure.
     * @param namedParams the optional parameters
     * @param fieldOrMethodName the name of the field to set or Builder method to call
     * @param type the model type of the new Builder
     * @param key the key to use for the new Builder
     * @param body the closure used to configure the Builder
     * @param <T> the generated Builder type
     * @return the newly created or existing Builder
     */
    public <T> T createSingleChild(Map<String, Object> namedParams, String fieldOrMethodName, Class<T> type, boolean explicitType, String key, Closure<T> body) {
        assertMutable();
        return BreadcrumbCollector.withBreadcrumb(null, explicitType ? shortNameFor(type) : null, key, () -> {
            ChildTarget target = resolveSingleChildTarget(fieldOrMethodName, type);
            String effectiveKey = resolveKeyForFieldFromAnnotation(fieldOrMethodName, target.member()).orElse(key);
            if (target.existingBuilder() != null)
                return configureExistingChild(target.existingBuilder(), type, explicitType, effectiveKey, namedParams, body);

            KlumBuilder<?> created = createNewBuilderFromParamsAndClosure(type, effectiveKey, namedParams, body);
            callSetterOrMethod(fieldOrMethodName, created);
            return (T) created;
        });
    }

    private ChildTarget resolveSingleChildTarget(String fieldOrMethodName, Class<?> type) {
        Optional<Field> field = DslHelper.getField(modelType, fieldOrMethodName);
        if (field.isPresent())
            return new ChildTarget(field.get(), getInstanceAttribute(fieldOrMethodName));

        AnnotatedElement virtualSetter = DslHelper.getVirtualSetter(getClass(), fieldOrMethodName, type)
                .orElseThrow(() -> new KlumModelException(format(
                        "Neither field nor single argument method named %s with type %s found in %s",
                        fieldOrMethodName, type, modelType)));
        return new ChildTarget(virtualSetter, null);
    }

    private <T> T configureExistingChild(KlumBuilder<?> existingBuilder, Class<T> type, boolean explicitType,
                                         String effectiveKey, Map<String, Object> namedParams, Closure<T> body) {
        if (explicitType && !type.isAssignableFrom(existingBuilder.getModelType()))
            throw new KlumModelException(format("Type mismatch: %s is not compatible with %s",
                    existingBuilder.getModelType().getName(), type.getName()));
        if (!Objects.equals(effectiveKey, existingBuilder.getNullableKey()))
            throw new KlumModelException(format("Key mismatch: %s != %s", effectiveKey, existingBuilder.getNullableKey()));
        existingBuilder.increaseBreadcrumbQuantifier();
        existingBuilder.apply(namedParams, body);
        return (T) existingBuilder;
    }

    private record ChildTarget(AnnotatedElement member, KlumBuilder<?> existingBuilder) {
    }

    /**
     * Sets '{{fieldName}}' on this Builder, or calls a Builder method with that name.
     * @param fieldOrMethodName the field or method name
     * @param value the value to set
     * @param <T> the value type
     * @return the supplied value
     */
    public <T> T setSingleField(String fieldOrMethodName, T value) {
        assertMutable();
        return BreadcrumbCollector.withBreadcrumb(() -> {
            callSetterOrMethod(fieldOrMethodName, value);
            return value;
        });
    }

    public <T> T setSingleFieldViaConverter(String fieldOrMethodName, Class<?> converterType, String converterMethod, Object... args) {
        return setSingleField(fieldOrMethodName, createObjectViaConverter(converterType, converterMethod, args));
    }

    private <T> T createObjectViaConverter(Class<?> converterType, String converterMethod, Object... args) {
        if (converterMethod == null)
            return (T) InvokerHelper.invokeConstructorOf(converterType, args);
        return (T) InvokerHelper.invokeMethod(converterType, converterMethod, args);
    }

    private void callSetterOrMethod(String fieldOrMethodName, Object value) {
        boolean hasStorageField = DslHelper.getCachedField(getClass(), fieldOrMethodName).isPresent();
        if (hasStorageField)
            setInstanceAttribute(fieldOrMethodName, value);
        else {
            InvokerHelper.invokeMethod(this, fieldOrMethodName, value);
            if (value instanceof KlumBuilder)
                virtualChildren.add((KlumBuilder<?>) value);
        }
        Object storedValue = hasStorageField
                ? getInstanceAttribute(fieldOrMethodName)
                : value;
        setModelPathOfInnerBuilder(storedValue, fieldOrMethodName);
    }

    /**
     * Adds an existing '{{singleElementName}}' to the Builder's '{{fieldName}}' collection.
     * @param fieldName the collection field name
     * @param element the element to add
     * @param <T> the element type
     * @return the added element
     */
    public <T> T addElementToCollection(String fieldName, T element) {
        Field schemaField = getModelField(fieldName);
        Object stored = DslHelper.isRelationship(schemaField) ? normalizeRelationshipValue(schemaField, element) : forceCastClosure(element, DslHelper.getElementType(schemaField));
        Collection<Object> target = getInstanceAttribute(fieldName);
        target.add(stored);
        setModelPathOfInnerBuilder(stored, fieldName + "[" + (target.size() - 1) + "]");
        return element;
    }

    public <T> T addElementToCollectionViaConverter(String fieldOrMethodName, Class<?> converterType, String converterMethod, Object... args) {
        return addElementToCollection(fieldOrMethodName, createObjectViaConverter(converterType, converterMethod, args));
    }

    /**
     * Creates a new '{{singleElementName}}' Builder {{param:type?with the given type}} and adds it to the Builder's '{{fieldName}}' collection.
     * The newly created Builder is configured by the optional values and closure.
     * @param namedParams the optional parameters
     * @param collectionName the collection field name
     * @param type the model type of the new Builder
     * @param key the key to use for the new Builder
     * @param body the closure used to configure the Builder
     * @param <T> the generated Builder type
     * @return the newly created Builder
     */
    public <T> T addNewDslElementToCollection(Map<String, Object> namedParams, String collectionName, Class<? extends T> type, boolean explicitType, String key, Closure<T> body) {
        return BreadcrumbCollector.withBreadcrumb(null, explicitType ? shortNameFor(type) : null, key, () -> {
            KlumBuilder<?> created = createNewBuilderFromParamsAndClosure(type, key, namedParams, body);
            addElementToCollection(collectionName, created);
            return (T) created;
        });
    }

    private KlumBuilder<?> createNewBuilderFromParamsAndClosure(Class<?> type, String key, Map<String, Object> namedParams, Closure<?> body) {
        return FactoryHelper.prepareNestedBuilder(type, key, template, builder -> builder.applyOnly(namedParams, body));
    }

    /**
     * Adds one or more existing '{{singleElementName}}' values to the Builder's '{{fieldName}}' collection.
     * @param fieldName the collection field name
     * @param elements the elements to add
     */
    public void addElementsToCollection(String fieldName, Object... elements) {
        Arrays.stream(elements).forEach(element -> addElementToCollection(fieldName, element));
    }

    /**
     * Adds one or more existing '{{singleElementName}}' values to the Builder's '{{fieldName}}' collection.
     * @param fieldName the collection field name
     * @param elements the elements to add
     */
    public void addElementsToCollection(String fieldName, Iterable<?> elements) {
        elements.forEach(element -> addElementToCollection(fieldName, element));
    }

    /**
     * Validates and attaches a projected batch of child Builders, then returns the producer's original container.
     * Validation happens before the first mutation so a rejected batch cannot be partially attached.
     */
    public <C extends Collection<?>> C addProjectedBuildersFromCollectionToCollection(String fieldName, C builders) {
        assertMutable();
        Field schemaField = getModelField(fieldName);
        builders.forEach(builder -> normalizeRelationshipValue(schemaField, builder));
        builders.forEach(builder -> addElementToCollection(fieldName, builder));
        return builders;
    }

    /** Attaches the values of a projected map to a collection relationship and returns the same map. */
    public <T extends Map<?, ?>> T addProjectedBuildersFromMapToCollection(String fieldName, T builders) {
        assertMutable();
        Field schemaField = getModelField(fieldName);
        builders.values().forEach(builder -> normalizeRelationshipValue(schemaField, builder));
        builders.values().forEach(builder -> addElementToCollection(fieldName, builder));
        return builders;
    }

    public <K, V> void addElementsToMap(String fieldName, Map<K, V> values) {
        values.forEach((key, value) -> addElementToMap(fieldName, key, value));
    }

    /**
     * Validates and attaches a projected map of child Builders, preserving its keys, then returns that same map.
     */
    public <T extends Map<?, ?>> T addProjectedBuildersFromMapToMap(String fieldName, T builders) {
        assertMutable();
        Field schemaField = getModelField(fieldName);
        builders.values().forEach(builder -> normalizeRelationshipValue(schemaField, builder));
        builders.forEach((key, builder) -> addElementToMap(fieldName, key, builder));
        return builders;
    }

    /** Attaches a projected collection to a keyed relationship and returns the producer's original collection. */
    public <C extends Collection<?>> C addProjectedBuildersFromCollectionToMap(String fieldName, C builders) {
        assertMutable();
        Field schemaField = getModelField(fieldName);
        builders.forEach(builder -> normalizeRelationshipValue(schemaField, builder));
        builders.forEach(builder -> addElementToMap(fieldName, null, builder));
        return builders;
    }

    public <V> void addElementsToMap(String fieldName, Iterable<V> values) {
        values.forEach(value -> addElementToMap(fieldName, null, value));
    }

    public void addElementsToMap(String fieldName, Object... values) {
        Arrays.stream(values).forEach(value -> addElementToMap(fieldName, null, value));
    }

    /**
     * Creates a new '{{singleElementName}}' Builder {{param:type?with the given type}} and adds it to the Builder's '{{fieldName}}' map.
     * The newly created Builder is configured by the optional values and closure.
     * @param namedParams the optional parameters
     * @param mapName the map field name
     * @param type the model type of the new Builder
     * @param key the key to use for the new Builder
     * @param body the closure used to configure the Builder
     * @param <T> the generated Builder type
     * @return the newly created Builder
     */
    public <T> T addNewDslElementToMap(Map<String, Object> namedParams, String mapName, Class<? extends T> type, boolean explicitType, String key, Closure<T> body) {
        return BreadcrumbCollector.withBreadcrumb(null, explicitType ? shortNameFor(type) : null, key, () -> {
            KlumBuilder<?> existing = ((Map<String, KlumBuilder<?>>) getInstanceAttributeOrGetter(mapName)).get(key);
            if (existing != null) {
                if (explicitType && !type.isAssignableFrom(existing.getModelType()))
                    throw new KlumModelException(format("Type mismatch: %s is not compatible with %s",
                            existing.getModelType().getName(), type.getName()));
                existing.apply(namedParams, body);
                return (T) existing;
            }
            KlumBuilder<?> created = createNewBuilderFromParamsAndClosure(type, key, namedParams, body);
            doAddElementToMap(mapName, key, created);
            return (T) created;
        });
    }

    public <K, V> V addElementToMap(String fieldName, K key, V value) {
        doAddElementToMap(fieldName, key, value);
        return value;
    }

    public <K, V> V addElementToMapViaConverter(String fieldOrMethodName, Class<?> converterType, String converterMethod, K key, Object... args) {
        return addElementToMap(fieldOrMethodName, key, createObjectViaConverter(converterType, converterMethod, args));
    }

    private <K, V> void doAddElementToMap(String fieldName, K key, V value) {
        Field schemaField = getModelField(fieldName);
        Object keySource = value instanceof KlumBuilder ? value : normalizeRelationshipValueIfNecessary(schemaField, value);
        key = determineKeyFromMappingClosure(fieldName, keySource, key);
        if (key == null && DslHelper.isKeyed(DslHelper.getClassFromType(DslHelper.getElementType(schemaField))))
            key = (K) ((KlumBuilder<?>) keySource).getKey();
        if (key == null)
            throw new IllegalArgumentException("Key is null");
        Object stored = DslHelper.isRelationship(schemaField) ? keySource : forceCastClosure(value, DslHelper.getElementType(schemaField));
        Map<K, Object> target = getInstanceAttribute(fieldName);
        target.put(key, stored);
        setModelPathOfInnerBuilder(stored, fieldName + "." + toGPath(key));
    }

    private Object normalizeRelationshipValueIfNecessary(Field schemaField, Object value) {
        return DslHelper.isRelationship(schemaField) ? normalizeRelationshipValue(schemaField, value) : value;
    }

    private <V> V forceCastClosure(Object value, Type elementType) {
        Class<V> effectiveType = (Class<V>) getClassFromType(elementType);
        if (value instanceof Closure)
            return castTo(value, effectiveType);
        if (effectiveType.isInstance(value))
            return (V) value;
        throw new KlumModelException(format("Value is not of type %s", elementType));
    }

    private <K, V> K determineKeyFromMappingClosure(String fieldName, V element, K defaultValue) {
        return DslHelper.getOptionalFieldAnnotation(modelType, fieldName, com.blackbuild.groovy.configdsl.transform.Field.class)
                .map(com.blackbuild.groovy.configdsl.transform.Field::keyMapping)
                .filter(DslHelper::isClosure)
                .map(value -> (K) normalizeMappedBuilderClass(
                        ClosureHelper.invokeClosure((Class<? extends Closure<Object>>) value, element), element))
                .orElse(defaultValue);
    }

    private Object normalizeMappedBuilderClass(Object mappedKey, Object element) {
        if (element instanceof KlumBuilder && mappedKey == element.getClass())
            return ((KlumBuilder<?>) element).getModelType();
        return mappedKey;
    }

    @SafeVarargs
    public final void addElementsFromScriptsToCollection(String fieldName, Class<? extends Script>... scripts) {
        Class<?> elementType = getClassFromType(DslHelper.getElementType(getModelField(fieldName)));
        Object builderFactory = InvokerHelper.getProperty(DslHelper.getFactoryOf(elementType), "AsBuilder");
        Arrays.stream(scripts).forEach(script -> addElementToCollection(fieldName, InvokerHelper.invokeMethod(builderFactory, "From", script)));
    }

    @SafeVarargs
    public final void addElementsFromScriptsToMap(String fieldName, Class<? extends Script>... scripts) {
        Class<?> elementType = getClassFromType(DslHelper.getElementType(getModelField(fieldName)));
        Object builderFactory = InvokerHelper.getProperty(DslHelper.getFactoryOf(elementType), "AsBuilder");
        Arrays.stream(scripts).forEach(script -> addElementToMap(fieldName, null, InvokerHelper.invokeMethod(builderFactory, "From", script)));
    }

    Object invokeBuilderMethod(String methodName, Object... args) {
        return InvokerHelper.invokeMethod(this, methodName, args);
    }

    public void copyFromTemplate() {
        DslHelper.getDslHierarchyOf(modelType).forEach(this::copyFromTemplateLayer);
    }

    private void copyFromTemplateLayer(Class<?> layer) {
        copyFrom(TemplateManager.getInstance().getTemplate(layer));
    }

    public Optional<String> resolveKeyForFieldFromAnnotation(String name, AnnotatedElement field) {
        com.blackbuild.groovy.configdsl.transform.Field annotation = field.getAnnotation(com.blackbuild.groovy.configdsl.transform.Field.class);
        if (annotation == null)
            return Optional.empty();
        Class<?> keyMember = annotation.key();
        if (keyMember == Undefined.class)
            return Optional.empty();
        if (keyMember == com.blackbuild.groovy.configdsl.transform.Field.FieldName.class)
            return Optional.of(name);
        String result = ClosureHelper.invokeClosureWithDelegateAsArgument((Class<? extends Closure<String>>) keyMember, this);
        return Optional.of(result);
    }

    public String getBreadcrumbPath() {
        if (breadcrumbQuantifier > 1)
            return breadcrumbPath + "." + breadcrumbQuantifier;
        return breadcrumbPath;
    }

    public void setBreadcrumbPath(String breadcrumbPath) {
        if (this.breadcrumbPath != null)
            throw new KlumModelException("Breadcrumb path already set to " + this.breadcrumbPath);
        this.breadcrumbPath = Objects.requireNonNull(breadcrumbPath);
    }

    public void increaseBreadcrumbQuantifier() {
        breadcrumbQuantifier++;
    }

    public void setCurrentTemplates(Map<Class<?>, Object> currentTemplates) {
        this.currentTemplates = currentTemplates;
    }

    public Map<Class<?>, Object> getCurrentTemplates() {
        return currentTemplates == null ? Collections.emptyMap() : currentTemplates;
    }

    public void applyLater(Closure<?> closure) {
        scheduleApplyLater(closure);
    }

    public void scheduleApplyLater(Closure<?> closure) {
        KlumPhase phase = PhaseDriver.getCurrentPhase();
        if (phase == null)
            phase = DefaultKlumPhase.APPLY_LATER;
        doScheduleApplyLater(phase.getNumber(), phase.getName(), closure);
    }

    public void applyLater(KlumPhase phase, Closure<?> closure) {
        scheduleApplyLater(phase, closure);
    }

    public void scheduleApplyLater(KlumPhase phase, Closure<?> closure) {
        doScheduleApplyLater(phase.getNumber(), phase.getName(), closure);
    }

    public void applyLater(Integer number, Closure<?> closure) {
        scheduleApplyLater(number, closure);
    }

    public void scheduleApplyLater(Integer number, Closure<?> closure) {
        doScheduleApplyLater(number, defaultPhaseName(number), closure);
    }

    private void doScheduleApplyLater(Integer number, String phaseName, Closure<?> closure) {
        if (number >= DefaultKlumPhase.INSTANTIATE.getNumber()) {
            String phaseDisplay = phaseName == null ? number.toString() : "'" + phaseName + "' (" + number + ")";
            throw new KlumModelException("Cannot schedule applyLater for phase " + phaseDisplay
                    + ": deferred Builder actions must run before materialization at phase 40. "
                    + "Use a phase below 40, or a ModelVisitingPhaseAction for completed-model work.");
        }
        applyLaterClosures.computeIfAbsent(number, ignore -> new ArrayList<>()).add(closure);
        if (!template)
            PhaseDriver.getInstance().registerApplyLaterPhase(number);
    }

    private static String defaultPhaseName(Integer number) {
        for (DefaultKlumPhase phase : DefaultKlumPhase.values())
            if (phase.getNumber() == number)
                return phase.getName();
        return null;
    }

    void copyApplyLaterClosuresFrom(Object recipe) {
        if (recipe instanceof KlumBuilder) {
            KlumBuilder<?> sourceBuilder = (KlumBuilder<?>) recipe;
            if (sourceBuilder.applyLaterClosures.isEmpty())
                return;
            assertValidBuilderCopySource(sourceBuilder);
            sourceBuilder.applyLaterClosures.forEach((phase, closures) ->
                    new ArrayList<>(closures).forEach(closure ->
                            scheduleApplyLater(phase, closure.dehydrate())));
            return;
        }
        if (recipe instanceof KlumModelObject
                && KlumTemplateProxy.companionFor(recipe) instanceof KlumTemplateProxy templateProxy)
            templateProxy.replayInto(this);
    }

    static Map<Integer, List<Closure<?>>> dehydrateApplyLaterClosures(Map<Integer, List<Closure<?>>> source) {
        Map<Integer, List<Closure<?>>> copy = new TreeMap<>();
        source.forEach((phase, closures) -> copy.put(phase, closures.stream()
                .map(KlumBuilder::dehydrateRecipeClosure)
                .toList()));
        return copy;
    }

    private static Closure<?> dehydrateRecipeClosure(Closure<?> closure) {
        Closure<?> dehydrated = closure.dehydrate();
        try {
            return copySerializableRecipeClosure(dehydrated);
        } catch (BuilderCaptureException exception) {
            throw new KlumModelException("Template applyLater closures must not capture a Builder; "
                    + "use the closure delegate so the recipe can run against each fresh Builder");
        } catch (IOException | ClassNotFoundException exception) {
            throw new KlumModelException("Template applyLater closures and their captured values must be serializable", exception);
        }
    }

    private static Closure<?> copySerializableRecipeClosure(Closure<?> closure)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new BuilderRejectingObjectOutputStream(serialized)) {
            output.writeObject(closure);
        }

        ClassLoader closureLoader = closure.getClass().getClassLoader();
        try (ObjectInputStream input = new RecipeObjectInputStream(
                new ByteArrayInputStream(serialized.toByteArray()), closureLoader)) {
            Closure<?> detached = (Closure<?>) input.readObject();
            if (retainsBuilder(detached, Collections.newSetFromMap(new IdentityHashMap<>())))
                throw new BuilderCaptureException();
            return detached;
        }
    }

    private static boolean retainsBuilder(Object value, Set<Object> visited) {
        if (value == null)
            return false;
        if (value instanceof KlumBuilder)
            return true;
        if (isBuilderCaptureLeaf(value) || !visited.add(value))
            return false;
        if (value instanceof Reference<?> reference)
            return retainsBuilder(reference.get(), visited);
        if (value instanceof Map<?, ?> map)
            return map.entrySet().stream()
                    .anyMatch(entry -> retainsBuilder(entry.getKey(), visited) || retainsBuilder(entry.getValue(), visited));
        if (value instanceof Iterable<?> iterable)
            return iterableRetainsBuilder(iterable, visited);
        if (value.getClass().isArray())
            return arrayRetainsBuilder(value, visited);
        return fieldsRetainBuilder(value, visited);
    }

    private static boolean iterableRetainsBuilder(Iterable<?> values, Set<Object> visited) {
        for (Object member : values) {
            if (retainsBuilder(member, visited))
                return true;
        }
        return false;
    }

    private static boolean arrayRetainsBuilder(Object array, Set<Object> visited) {
        for (int index = 0; index < Array.getLength(array); index++) {
            if (retainsBuilder(Array.get(array, index), visited))
                return true;
        }
        return false;
    }

    private static boolean fieldsRetainBuilder(Object value, Set<Object> visited) {
        for (Class<?> layer = value.getClass(); layer != null && layer != Object.class; layer = layer.getSuperclass()) {
            for (Field field : layer.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !field.trySetAccessible())
                    continue;
                try {
                    if (retainsBuilder(field.get(value), visited))
                        return true;
                } catch (IllegalAccessException exception) {
                    throw new KlumModelException("Could not verify a detached template closure", exception);
                }
            }
        }
        return false;
    }

    private static boolean isBuilderCaptureLeaf(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Class;
    }

    private static final class BuilderRejectingObjectOutputStream extends ObjectOutputStream {
        private BuilderRejectingObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object instanceof KlumBuilder)
                throw new BuilderCaptureException();
            return object;
        }
    }

    private static final class RecipeObjectInputStream extends ObjectInputStream {
        private final ClassLoader classLoader;

        private RecipeObjectInputStream(ByteArrayInputStream input, ClassLoader classLoader) throws IOException {
            super(input);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
            try {
                return Class.forName(descriptor.getName(), false, classLoader);
            } catch (ClassNotFoundException ignored) {
                return super.resolveClass(descriptor);
            }
        }
    }

    private static final class BuilderCaptureException extends IOException {
    }

    public void executeApplyLaterClosures(int phase) {
        List<Closure<?>> closures = applyLaterClosures.get(phase);
        if (closures == null)
            return;
        int pendingAtPhaseStart = closures.size();
        for (int index = 0; index < pendingAtPhaseStart; index++) {
            Iterator<Closure<?>> iterator = closures.iterator();
            Closure<?> closure = iterator.next();
            iterator.remove();
            applyOnly(null, closure);
        }
        if (closures.isEmpty())
            applyLaterClosures.remove(phase);
    }

    public void cleanup() {
        currentTemplates = Collections.emptyMap();
    }

    boolean hasMetaData(String key) {
        return metadata.containsKey(key);
    }

    /**
     * Returns construction metadata that will be transferred to the completed model companion.
     * The complete metadata value graph is checked for Java serialization when stored. Callers
     * must not subsequently mutate an accepted value so it contains non-serializable state.
     *
     * @param key metadata key
     * @param type expected value type
     * @return the stored value, or {@code null} if no value is stored
     * @throws KlumException if the stored value is not of the requested type
     */
    <T> T getMetaData(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null)
            return null;
        if (!type.isInstance(value))
            throw new KlumException(format("Metadata value for key '%s' is not of type %s", key, type.getName()));
        return type.cast(value);
    }

    /**
     * Stores construction metadata for transfer to the completed model companion.
     *
     * @param key metadata key
     * @param value a value whose complete object graph is serializable, or {@code null}
     * @throws KlumException if {@code value} or anything reachable from it is not serializable
     */
    void setMetaData(String key, Object value) {
        metadata.put(key, KlumModelProxy.requireSerializableMetadataValue(key, value));
    }

    public void setModelPath(String path) {
        if (modelPath != null)
            return;
        modelPath = path;
        propagateModelPathToComposition();
    }

    public String getModelPath() {
        return modelPath;
    }

    private void setModelPathOfInnerBuilder(Object value, String pathSegment) {
        if (modelPath != null && value instanceof KlumBuilder)
            ((KlumBuilder<?>) value).setModelPath(modelPath + "." + pathSegment);
    }

    private void propagateModelPathToComposition() {
        for (Class<?> layer : DslHelper.getDslHierarchyOf(modelType)) {
            for (Field field : layer.getDeclaredFields()) {
                if (!DslHelper.isRelationship(field) || DslHelper.isOwner(field) || DslHelper.isLink(field))
                    continue;
                propagateModelPath(getInstanceAttribute(field.getName()), modelPath + "." + field.getName());
            }
        }
    }

    void refreshModelPaths() {
        if (modelPath != null)
            propagateModelPathToComposition();
    }

    private static void propagateModelPath(Object value, String path) {
        if (value instanceof KlumBuilder) {
            ((KlumBuilder<?>) value).setModelPath(path);
        } else if (value instanceof Collection) {
            int index = 0;
            for (Object member : (Collection<?>) value)
                propagateModelPath(member, path + "[" + index++ + "]");
        } else if (value instanceof Map) {
            ((Map<?, ?>) value).forEach((key, member) -> propagateModelPath(member, path + "." + toGPath(key)));
        }
    }

    private static String toGPath(Object value) {
        String text = String.valueOf(value);
        return Utilities.isJavaIdentifier(text) ? text : InvokerHelper.inspect(text);
    }
}
