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
package com.blackbuild.klum.ast.jackson;

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import com.blackbuild.groovy.configdsl.transform.Role;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.util.DslHelper;
import com.blackbuild.klum.ast.util.FactoryHelper;
import com.blackbuild.klum.ast.util.InternalKlumBuilder;
import com.blackbuild.klum.ast.util.KlumBuilder;
import com.blackbuild.klum.ast.util.KlumModelException;
import com.blackbuild.klum.ast.util.LifecycleHelper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.impl.ObjectIdReader;
import com.fasterxml.jackson.databind.deser.impl.ReadableObjectId;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Replays resolved Jackson configuration properties through the generated Builder lifecycle.
 */
final class KlumDeserializer extends StdDeserializer<Object> implements ContextualDeserializer {

    private static final ThreadLocal<ManagedRequest> MANAGED_REQUEST = new ThreadLocal<>();

    /**
     * Carries the replay session only across the synchronous re-entry caused by
     * {@link ReplaySession#discoverPolymorphicBuilder(ObjectNode, JavaType, TypeDeserializer, Object)} calling
     * {@link JsonDeserializer#deserializeWithType(JsonParser, DeserializationContext, TypeDeserializer)}. The
     * consumer must use the same {@link DeserializationContext}; the producer restores any previous request so nested
     * polymorphic discovery remains scoped correctly.
     */
    private static final ThreadLocal<OwnedBuilderRequest> OWNED_BUILDER_REQUEST = new ThreadLocal<>();

    private final Class<?> modelType;

    /** Jackson's standard delegate is serializable and must survive deserializer serialization. */
    @SuppressWarnings("java:S1948")
    private final JsonDeserializer<?> jacksonDelegate;

    KlumDeserializer(Class<?> modelType, JsonDeserializer<?> jacksonDelegate) {
        super(modelType);
        this.modelType = modelType;
        this.jacksonDelegate = jacksonDelegate;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
            throws JsonMappingException {
        JsonDeserializer<?> contextualDelegate = context.handleSecondaryContextualization(
                jacksonDelegate, property, context.constructType(modelType));
        if (contextualDelegate == jacksonDelegate)
            return this;
        return new KlumDeserializer(modelType, contextualDelegate);
    }

    @Override
    public ObjectIdReader getObjectIdReader() {
        return jacksonDelegate.getObjectIdReader();
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ManagedRequest managedRequest = MANAGED_REQUEST.get();
        if (managedRequest != null && !managedRequest.wasHandled() && managedRequest.matches(modelType))
            return deserializeManaged(parser, context, managedRequest);
        OwnedBuilderRequest request = OWNED_BUILDER_REQUEST.get();
        if (request != null && request.context() == context)
            return request.session().addOwnedBuilder(
                    modelType, readPolymorphicObject(parser, context), request.keyHint());
        if (parser.currentToken() == JsonToken.VALUE_NULL)
            return null;
        if (!parser.isExpectedStartObjectToken())
            return context.handleUnexpectedToken(modelType, parser);

        ObjectNode configuration = readObject(parser, context);
        try {
            return replay(configuration, parser, context);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        } catch (RuntimeException exception) {
            throw JsonMappingException.from(parser, "Could not replay configuration for " + modelType.getName()
                    + " through its Builder lifecycle", exception);
        }
    }

    static <T> T withManagedRequest(ManagedRequest request, ThrowingSupplier<T> action) throws IOException {
        ManagedRequest previous = MANAGED_REQUEST.get();
        MANAGED_REQUEST.set(request);
        try {
            return action.get();
        } finally {
            if (previous == null)
                MANAGED_REQUEST.remove();
            else
                MANAGED_REQUEST.set(previous);
        }
    }

    private Object deserializeManaged(JsonParser parser, DeserializationContext context, ManagedRequest request)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL)
            return null;
        if (!parser.isExpectedStartObjectToken())
            return context.handleUnexpectedToken(modelType, parser);
        ObjectNode configuration = readObject(parser, context);
        try {
            request.markHandled();
            return switch (request.mode()) {
                case ROOT -> replay(configuration, parser, context);
                case TEMPLATE -> replayTemplate(configuration, parser, context);
                case BUILDER, APPLY -> replayInto(internalBuilder(request.target()), configuration, parser, context);
            };
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    private static ObjectNode readObject(JsonParser parser, DeserializationContext context) throws IOException {
        return (ObjectNode) context.readTree(parser);
    }

    private static ObjectNode readPolymorphicObject(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (parser.isExpectedStartObjectToken())
            return readObject(parser, context);
        if (parser.currentToken() != JsonToken.FIELD_NAME)
            return (ObjectNode) context.handleUnexpectedToken(ObjectNode.class, parser);
        ObjectNode result = context.getNodeFactory().objectNode();
        while (parser.currentToken() == JsonToken.FIELD_NAME) {
            String name = parser.currentName();
            parser.nextToken();
            result.set(name, context.readTree(parser));
            parser.nextToken();
        }
        return result;
    }

    private Object replay(ObjectNode configuration, JsonParser source, DeserializationContext context) {
        ResolvedProperties properties = resolveProperties(modelType, context);
        String key = resolveKey(modelType, configuration, null, properties);
        ReplaySession replaySession = new ReplaySession(source, context);
        return PhaseDriver.withBuilderLifecycle(
                () -> FactoryHelper.createBuilder(modelType, key),
                builder -> replaySession.replay(builder, configuration, properties, true)
        );
    }

    private Object replayTemplate(ObjectNode configuration, JsonParser source, DeserializationContext context) {
        InternalKlumBuilder<?> builder = internalBuilder(FactoryHelper.createTemplateBuilderForImport(modelType));
        return replayInto(builder, configuration, source, context, true);
    }

    private Object replayInto(InternalKlumBuilder<?> builder, ObjectNode configuration, JsonParser source,
                              DeserializationContext context) {
        return replayInto(builder, configuration, source, context, false);
    }

    private Object replayInto(InternalKlumBuilder<?> builder, ObjectNode configuration, JsonParser source,
                              DeserializationContext context, boolean template) {
        if (builder == null)
            throw new KlumModelException("Managed Jackson import did not receive a Builder target");
        ResolvedProperties properties = resolveProperties(modelType, context);
        ReplaySession replaySession = new ReplaySession(source, context);
        replaySession.replay(builder, configuration, properties, !template);
        return template ? InternalKlumBuilder.materializeTemplateForImport(builder) : builder;
    }

    private static InternalKlumBuilder<?> internalBuilder(KlumBuilder<?> builder) {
        if (builder instanceof InternalKlumBuilder<?> internalBuilder)
            return internalBuilder;
        throw new KlumModelException("Managed Jackson import requires an active generated Builder implementation");
    }

    private static Collection<Object> createCollection(JavaType type) {
        Class<?> rawType = type.getRawClass();
        if (SortedSet.class.isAssignableFrom(rawType))
            return new TreeSet<>();
        if (Set.class.isAssignableFrom(rawType))
            return new LinkedHashSet<>();
        return new ArrayList<>();
    }

    private static JsonMappingException inlineLinkError(ResolvedProperty property, JsonParser source) {
        return JsonMappingException.from(source, "LINK property " + property.schemaField().getDeclaringClass().getName()
                + "." + property.internalName() + " must contain reference ids, not inline objects");
    }

    private static boolean hasCustomPropertyDeserializer(ResolvedProperty property, DeserializationContext context) {
        AnnotatedMember member = property.beanProperty().getMember();
        return member != null && context.getAnnotationIntrospector().findDeserializer(member) != null;
    }

    @SuppressWarnings("unchecked")
    private static JsonDeserializer<Object> findPropertyValueDeserializer(
            ResolvedProperty property, DeserializationContext context) throws JsonMappingException {
        AnnotatedMember member = property.beanProperty().getMember();
        Object definition = member == null ? null : context.getAnnotationIntrospector().findDeserializer(member);
        if (definition == null)
            return context.findContextualValueDeserializer(property.type(), property.beanProperty());
        JsonDeserializer<Object> deserializer = context.deserializerInstance(member, definition);
        return (JsonDeserializer<Object>) context.handleSecondaryContextualization(
                deserializer, property.beanProperty(), property.type());
    }

    private static boolean isAlwaysAsId(ResolvedProperty property, DeserializationContext context) {
        AnnotatedMember member = property.beanProperty().getMember();
        if (member == null)
            return false;
        var referenceInfo = context.getAnnotationIntrospector().findObjectReferenceInfo(member, null);
        return referenceInfo != null && referenceInfo.getAlwaysAsId();
    }

    private static Object readValue(JsonNode node, JsonParser source, JsonDeserializer<Object> valueDeserializer,
                                    DeserializationContext context) throws IOException {
        try (JsonParser valueParser = node.traverse(source.getCodec())) {
            valueParser.nextToken();
            return valueParser.currentToken() == JsonToken.VALUE_NULL
                    ? valueDeserializer.getNullValue(context)
                    : valueDeserializer.deserialize(valueParser, context);
        }
    }

    private static Object readSimpleValue(JsonNode node, JsonParser source, ResolvedProperty property,
                                          DeserializationContext context) throws IOException {
        JsonDeserializer<Object> valueDeserializer = findPropertyValueDeserializer(property, context);
        return readValue(node, source, valueDeserializer, context);
    }

    private ResolvedProperties resolveProperties(Class<?> currentType, DeserializationContext context) {
        JavaType type = context.constructType(currentType);
        BeanDescription description = context.getConfig().introspect(type);
        Map<String, ResolvedProperty> bindable = new LinkedHashMap<>();
        Set<String> ignored = new HashSet<>(description.getIgnoredPropertyNames());
        var objectIdInfo = context.getAnnotationIntrospector().findObjectIdInfo(description.getClassInfo());
        if (objectIdInfo != null && objectIdInfo.getGeneratorType() != ObjectIdGenerators.PropertyGenerator.class)
            ignored.add(objectIdInfo.getPropertyName().getSimpleName());
        description.getClassInfo().fields().forEach(field -> addIgnoredFieldName(field, ignored, context));
        description.findProperties().forEach(definition -> {
            Optional<ResolvedProperty> resolved = definition.couldDeserialize() && visibleInActiveView(definition, context)
                    ? toResolvedProperty(currentType, definition, context)
                    : Optional.empty();
            Stream<String> names = Stream.concat(
                    Stream.of(definition.getName()),
                    aliasesFor(definition, context).map(PropertyName::getSimpleName)
            );
            if (resolved.isPresent())
                names.forEach(name -> bindable.put(name, resolved.get()));
            else
                names.forEach(ignored::add);
        });
        JsonIgnoreProperties.Value ignorals = context.getConfig()
                .getDefaultPropertyIgnorals(currentType, description.getClassInfo());
        ignored.addAll(ignorals.findIgnoredForDeserialization());
        return new ResolvedProperties(bindable, ignored, ignorals.getIgnoreUnknown());
    }

    private static boolean visibleInActiveView(BeanPropertyDefinition property, DeserializationContext context) {
        Class<?> activeView = context.getActiveView();
        if (activeView == null)
            return true;
        Class<?>[] views = property.findViews();
        if (views == null)
            return context.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION);
        return Stream.of(views).anyMatch(view -> view.isAssignableFrom(activeView));
    }

    private static void addIgnoredFieldName(AnnotatedField field, Set<String> ignored,
                                            DeserializationContext context) {
        if (!context.getAnnotationIntrospector().hasIgnoreMarker(field))
            return;
        PropertyName explicitName = context.getAnnotationIntrospector().findNameForDeserialization(field);
        if (explicitName != null && explicitName.hasSimpleName()) {
            ignored.add(explicitName.getSimpleName());
            return;
        }
        PropertyNamingStrategy namingStrategy = context.getConfig().getPropertyNamingStrategy();
        ignored.add(namingStrategy == null
                ? field.getName()
                : namingStrategy.nameForField(context.getConfig(), field, field.getName()));
    }

    private static Stream<PropertyName> aliasesFor(
            BeanPropertyDefinition property, DeserializationContext context) {
        return Stream.of(property.getPrimaryMember(), property.getField(), property.getGetter(), property.getSetter())
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(member -> Optional.ofNullable(context.getAnnotationIntrospector().findPropertyAliases(member)).stream())
                .flatMap(Collection::stream);
    }

    private Optional<ResolvedProperty> toResolvedProperty(Class<?> currentType, BeanPropertyDefinition property,
                                                           DeserializationContext context) {
        Optional<Field> schemaField = DslHelper.getField(currentType, property.getInternalName());
        if (schemaField.isEmpty() || !isConfigurable(schemaField.get()))
            return Optional.empty();
        AnnotatedMember contextualMember = Stream.of(
                        property.getField(), property.getSetter(), property.getGetter(), property.getPrimaryMember())
                .filter(Objects::nonNull)
                .distinct()
                .filter(member -> context.getAnnotationIntrospector().findDeserializer(member) != null)
                .findFirst()
                .orElse(property.getField() != null ? property.getField() : property.getPrimaryMember());
        BeanProperty beanProperty = new BeanProperty.Std(
                property.getFullName(),
                property.getPrimaryType(),
                property.getWrapperName(),
                contextualMember,
                property.getMetadata()
        );
        return Optional.of(new ResolvedProperty(
                property.getName(),
                schemaField.get().getName(),
                property.getPrimaryType(),
                beanProperty,
                schemaField.get()
        ));
    }

    private static String resolveKey(Class<?> currentType, ObjectNode configuration, Object keyHint,
                                     ResolvedProperties properties) {
        Optional<Field> keyField = DslHelper.getKeyField(currentType);
        if (keyField.isEmpty())
            return null;
        Optional<String> externalKey = properties.bindable().entrySet().stream()
                .filter(entry -> entry.getValue().internalName().equals(keyField.get().getName()))
                .map(Map.Entry::getKey)
                .filter(configuration::has)
                .findFirst();
        if (externalKey.isPresent() && !configuration.get(externalKey.get()).isNull())
            return configuration.get(externalKey.get()).asText();
        return keyHint == null ? null : keyHint.toString();
    }

    private static boolean isConfigurable(Field field) {
        FieldType fieldType = DslHelper.getKlumFieldType(field);
        return !field.isSynthetic()
                && !field.isAnnotationPresent(Owner.class)
                && !field.isAnnotationPresent(Role.class)
                && fieldType != FieldType.PROTECTED
                && fieldType != FieldType.IGNORED
                && fieldType != FieldType.BUILDER;
    }

    private record ResolvedProperty(String externalName, String internalName, JavaType type,
                                    BeanProperty beanProperty, Field schemaField) {
    }

    private record ResolvedProperties(Map<String, ResolvedProperty> bindable, Set<String> ignored,
                                      boolean ignoreUnknown) {
    }

    private final class ReplaySession {
        private final JsonParser source;
        private final DeserializationContext context;
        private final List<PendingBuilder> pendingBuilders = new ArrayList<>();
        private final Map<ObjectNode, PendingBuilder> buildersByConfiguration = new IdentityHashMap<>();

        private ReplaySession(JsonParser source, DeserializationContext context) {
            this.source = source;
            this.context = context;
        }

        private void replay(InternalKlumBuilder<?> root, ObjectNode configuration, ResolvedProperties properties,
                            boolean runLifecycle) {
            try {
                addBuilder(root, configuration, properties);
                if (runLifecycle)
                    for (PendingBuilder pending : pendingBuilders) {
                        pending.builder().setCurrentTemplates(Collections.emptyMap());
                        LifecycleHelper.executeLifecycleMethods(pending.builder(), PostCreate.class);
                    }
                for (PendingBuilder pending : pendingBuilders)
                    bindConfiguration(pending);
                if (runLifecycle)
                    for (PendingBuilder pending : pendingBuilders)
                        LifecycleHelper.executeLifecycleMethods(pending.builder(), PostApply.class);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private PendingBuilder addBuilder(InternalKlumBuilder<?> builder, ObjectNode configuration,
                                          ResolvedProperties properties) throws IOException {
            PendingBuilder pending = new PendingBuilder(builder, configuration, properties, new LinkedHashMap<>());
            pendingBuilders.add(pending);
            buildersByConfiguration.put(configuration, pending);
            registerIdentity(pending);
            discoverOwnedValues(pending);
            return pending;
        }

        private void registerIdentity(PendingBuilder pending) throws IOException {
            JsonDeserializer<Object> deserializer = context.findRootValueDeserializer(
                    context.constructType(pending.builder().getModelType()));
            ObjectIdReader reader = deserializer.getObjectIdReader();
            if (reader == null)
                return;
            JsonNode idNode = pending.configuration().get(reader.propertyName.getSimpleName());
            if (idNode == null || idNode.isNull())
                return;
            Object id;
            try (JsonParser idParser = idNode.traverse(source.getCodec())) {
                idParser.nextToken();
                id = reader.readObjectReference(idParser, context);
            }
            context.findObjectId(id, reader.generator, reader.resolver).bindItem(pending.builder());
        }

        private void discoverOwnedValues(PendingBuilder pending) throws IOException {
            var fields = pending.configuration().fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                ResolvedProperty property = pending.properties().bindable().get(entry.getKey());
                if (property == null || !DslHelper.isRelationship(property.schemaField())
                        || DslHelper.isLink(property.schemaField()))
                    continue;
                pending.ownedValues().put(property, discoverOwnedValue(entry.getValue(), property));
            }
        }

        private Object discoverOwnedValue(JsonNode node, ResolvedProperty property) throws IOException {
            if (node.isNull())
                return null;
            if (property.type().isMapLikeType())
                return discoverOwnedMap(node, property);
            if (property.type().isCollectionLikeType())
                return discoverOwnedCollection(node, property);
            return discoverOwnedBuilder(node, property.type().getRawClass(), null);
        }

        private Map<Object, Object> discoverOwnedMap(JsonNode node, ResolvedProperty property) throws IOException {
            if (!node.isObject())
                throw JsonMappingException.from(source, "Expected an object for owned map property "
                        + property.externalName());
            Map<Object, Object> result = SortedMap.class.isAssignableFrom(property.type().getRawClass())
                    ? new TreeMap<>()
                    : new LinkedHashMap<>();
            KeyDeserializer keyDeserializer = context.findKeyDeserializer(
                    property.type().getKeyType(), property.beanProperty());
            Class<?> childType = property.type().getContentType().getRawClass();
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                Object key = keyDeserializer.deserializeKey(entry.getKey(), context);
                result.put(key, discoverOwnedBuilder(entry.getValue(), childType, key));
            }
            return result;
        }

        private Collection<Object> discoverOwnedCollection(JsonNode node, ResolvedProperty property) throws IOException {
            if (!node.isArray())
                throw JsonMappingException.from(source, "Expected an array for owned collection property "
                        + property.externalName());
            Collection<Object> result = createCollection(property.type());
            Class<?> childType = property.type().getContentType().getRawClass();
            for (JsonNode element : node)
                result.add(discoverOwnedBuilder(element, childType, null));
            return result;
        }

        private InternalKlumBuilder<?> discoverOwnedBuilder(JsonNode node, Class<?> childType, Object keyHint)
                throws IOException {
            if (!node.isObject())
                throw JsonMappingException.from(source, "Expected an object for owned DSL value " + childType.getName());
            ObjectNode object = (ObjectNode) node;
            PendingBuilder existing = buildersByConfiguration.get(object);
            if (existing != null)
                return existing.builder();
            JavaType declaredType = context.constructType(childType);
            TypeDeserializer typeDeserializer = context.getConfig().findTypeDeserializer(declaredType);
            if (typeDeserializer != null)
                return discoverPolymorphicBuilder(object, declaredType, typeDeserializer, keyHint);
            return addOwnedBuilder(childType, object, keyHint);
        }

        private InternalKlumBuilder<?> discoverPolymorphicBuilder(ObjectNode configuration, JavaType declaredType,
                                                          TypeDeserializer typeDeserializer, Object keyHint)
                throws IOException {
            JsonDeserializer<Object> valueDeserializer = context.findContextualValueDeserializer(declaredType, null);
            OwnedBuilderRequest previous = OWNED_BUILDER_REQUEST.get();
            OWNED_BUILDER_REQUEST.set(new OwnedBuilderRequest(this, context, keyHint));
            try (JsonParser parser = configuration.traverse(source.getCodec())) {
                parser.nextToken();
                Object result = valueDeserializer.deserializeWithType(parser, context, typeDeserializer);
                if (result instanceof InternalKlumBuilder<?> builder)
                    return builder;
                throw JsonMappingException.from(source, "Polymorphic owned DSL value " + declaredType
                        + " did not allocate a Builder in the active Construction session");
            } finally {
                if (previous == null)
                    OWNED_BUILDER_REQUEST.remove();
                else
                    OWNED_BUILDER_REQUEST.set(previous);
            }
        }

        private InternalKlumBuilder<?> addOwnedBuilder(Class<?> childType, ObjectNode object, Object keyHint)
                throws IOException {
            ResolvedProperties properties = resolveProperties(childType, context);
            String key = resolveKey(childType, object, keyHint, properties);
            InternalKlumBuilder<?> child = FactoryHelper.createBuilder(childType, key);
            addBuilder(child, object, properties);
            return child;
        }

        private Object readLinkValue(JsonNode node, ResolvedProperty property) throws IOException {
            if (node.isNull())
                return null;
            if (node.isObject() && !property.type().isMapLikeType())
                throw JsonMappingException.from(source, "LINK property "
                        + property.schemaField().getDeclaringClass().getName() + "." + property.internalName()
                        + " must contain a reference id, not an inline object");

            if (hasCustomPropertyDeserializer(property, context)) {
                JsonDeserializer<Object> valueDeserializer = findPropertyValueDeserializer(property, context);
                return readValue(node, source, valueDeserializer, context);
            }
            if (!isAlwaysAsId(property, context))
                throw JsonMappingException.from(source, KlumLinkDiagnostics.missingReferenceStrategy(
                        property.schemaField().getDeclaringClass().getName() + "." + property.internalName(),
                        "deserializer"));

            if (property.type().isMapLikeType())
                return readLinkMap(node, property);
            if (property.type().isCollectionLikeType())
                return readLinkCollection(node, property);
            return resolveLinkReference(node, property.type(), property);
        }

        private Map<Object, Object> readLinkMap(JsonNode node, ResolvedProperty property) throws IOException {
            if (!node.isObject())
                throw JsonMappingException.from(source, "Expected an object of reference ids for LINK map property "
                        + property.externalName());
            Map<Object, Object> result = SortedMap.class.isAssignableFrom(property.type().getRawClass())
                    ? new TreeMap<>()
                    : new LinkedHashMap<>();
            KeyDeserializer keyDeserializer = context.findKeyDeserializer(
                    property.type().getKeyType(), property.beanProperty());
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getValue().isObject())
                    throw inlineLinkError(property, source);
                Object key = keyDeserializer.deserializeKey(entry.getKey(), context);
                result.put(key, resolveLinkReference(
                        entry.getValue(), property.type().getContentType(), property));
            }
            return result;
        }

        private Collection<Object> readLinkCollection(JsonNode node, ResolvedProperty property) throws IOException {
            if (!node.isArray())
                throw JsonMappingException.from(source,
                        "Expected an array of reference ids for LINK collection property " + property.externalName());
            Collection<Object> result = createCollection(property.type());
            for (JsonNode element : node) {
                if (element.isObject())
                    throw inlineLinkError(property, source);
                result.add(resolveLinkReference(element, property.type().getContentType(), property));
            }
            return result;
        }

        private Object resolveLinkReference(JsonNode node, JavaType targetType, ResolvedProperty property)
                throws IOException {
            if (node.isNull())
                return null;
            JsonDeserializer<Object> valueDeserializer = context.findContextualValueDeserializer(
                    targetType, property.beanProperty());
            if (valueDeserializer.getObjectIdReader() == null)
                throw JsonMappingException.from(source, "Non-null LINK property "
                        + property.schemaField().getDeclaringClass().getName() + "." + property.internalName()
                        + " requires @JsonIdentityInfo on its target type");

            ObjectIdReader reader = valueDeserializer.getObjectIdReader();
            Object id;
            try (JsonParser valueParser = node.traverse(source.getCodec())) {
                valueParser.nextToken();
                id = reader.readObjectReference(valueParser, context);
            }
            ReadableObjectId readableObjectId = context.findObjectId(id, reader.generator, reader.resolver);
            Object resolved = readableObjectId.resolve();
            if (resolved == null)
                throw JsonMappingException.from(source, "Could not resolve LINK property "
                        + property.schemaField().getDeclaringClass().getName() + "." + property.internalName()
                        + " reference id '" + id
                        + "' to a completed DSL Object or Builder in this Construction session");
            return resolved;
        }

        private void handleUnknown(InternalKlumBuilder<?> builder, String externalName, JsonNode node) {
            try (JsonParser valueParser = node.traverse(source.getCodec())) {
                valueParser.nextToken();
                context.handleUnknownProperty(valueParser, KlumDeserializer.this, builder, externalName);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private void bind(InternalKlumBuilder<?> builder, String externalName, JsonNode node,
                          ResolvedProperties properties, Map<ResolvedProperty, Object> ownedValues) {
            ResolvedProperty property = properties.bindable().get(externalName);
            if (property == null) {
                if (properties.ignored().contains(externalName) || properties.ignoreUnknown())
                    return;
                handleUnknown(builder, externalName, node);
                return;
            }
            try {
                Object value;
                if (DslHelper.isLink(property.schemaField()))
                    value = readLinkValue(node, property);
                else if (DslHelper.isRelationship(property.schemaField()))
                    value = ownedValues.get(property);
                else
                    value = readSimpleValue(node, source, property, context);
                builder.setSingleField(property.internalName(), value);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private void bindConfiguration(PendingBuilder pending) {
            pending.configuration().fields().forEachRemaining(entry -> bind(
                    pending.builder(), entry.getKey(), entry.getValue(), pending.properties(), pending.ownedValues()));
        }
    }

    enum ManagedMode {
        ROOT("readRoot"), TEMPLATE("readTemplate"), BUILDER("readBuilder"), APPLY("applyToBuilder");

        private final String operation;

        ManagedMode(String operation) {
            this.operation = operation;
        }

        String operation() {
            return operation;
        }
    }

    static final class ManagedRequest {
        private final ManagedMode mode;
        private final KlumBuilder<?> target;
        private boolean handled;

        ManagedRequest(ManagedMode mode, KlumBuilder<?> target) {
            this.mode = mode;
            this.target = target;
        }

        private boolean matches(Class<?> type) {
            return target == null || target instanceof InternalKlumBuilder<?> builder
                    && builder.getModelType().equals(type);
        }

        ManagedMode mode() {
            return mode;
        }

        KlumBuilder<?> target() {
            return target;
        }

        boolean wasHandled() {
            return handled;
        }

        void markHandled() {
            handled = true;
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws IOException;
    }

    private record PendingBuilder(InternalKlumBuilder<?> builder, ObjectNode configuration, ResolvedProperties properties,
                                  Map<ResolvedProperty, Object> ownedValues) {
    }

    private record OwnedBuilderRequest(ReplaySession session, DeserializationContext context, Object keyHint) {
    }

}
