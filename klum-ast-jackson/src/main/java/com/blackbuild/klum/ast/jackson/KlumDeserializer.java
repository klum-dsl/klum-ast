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
import com.blackbuild.klum.ast.util.KlumBuilder;
import com.blackbuild.klum.ast.util.LifecycleHelper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    private final Class<?> modelType;
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

    private static ObjectNode readObject(JsonParser parser, DeserializationContext context) throws IOException {
        return (ObjectNode) context.readTree(parser);
    }

    private Object replay(ObjectNode configuration, JsonParser source, DeserializationContext context) {
        ResolvedProperties properties = resolveProperties(modelType, context);
        String key = resolveKey(modelType, configuration, null, properties);
        ReplaySession replaySession = new ReplaySession(source, context);
        return PhaseDriver.withBuilderLifecycle(
                () -> FactoryHelper.createBuilder(modelType, key),
                builder -> replaySession.replay(builder, configuration, properties)
        );
    }

    private void bind(KlumBuilder<?> builder, String externalName, JsonNode node,
                      ResolvedProperties properties, Map<ResolvedProperty, Object> ownedValues,
                      JsonParser source, DeserializationContext context) {
        ResolvedProperty property = properties.bindable().get(externalName);
        if (property == null) {
            if (properties.ignored().contains(externalName) || properties.ignoreUnknown())
                return;
            handleUnknown(builder, externalName, node, source, context);
            return;
        }
        try {
            Object value;
            if (DslHelper.isLink(property.schemaField()))
                value = readLinkValue(node, property, source, context);
            else if (DslHelper.isRelationship(property.schemaField()))
                value = ownedValues.get(property);
            else
                value = readSimpleValue(node, source, property, context);
            builder.setSingleField(property.internalName(), value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Object readLinkValue(JsonNode node, ResolvedProperty property,
                                 JsonParser source, DeserializationContext context) throws IOException {
        if (node.isNull())
            return null;
        if (node.isObject())
            throw JsonMappingException.from(source, "LINK property " + property.schemaField().getDeclaringClass().getName()
                    + "." + property.internalName() + " must contain a reference id, not an inline object");

        JsonDeserializer<Object> valueDeserializer = context.findContextualValueDeserializer(
                property.type(), property.beanProperty());
        if (hasCustomPropertyDeserializer(property, context))
            return readValue(node, source, valueDeserializer, context);
        if (!isAlwaysAsId(property, context) || valueDeserializer.getObjectIdReader() == null)
            throw JsonMappingException.from(source, "Non-null LINK property "
                    + property.schemaField().getDeclaringClass().getName() + "." + property.internalName()
                    + " requires @JsonIdentityInfo on the target type and "
                    + "@JsonIdentityReference(alwaysAsId = true) on the LINK property, or an explicit custom property deserializer");

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
                    + " reference id '" + id + "' to a completed DSL Object or Builder in this Construction session");
        return resolved;
    }

    private static boolean hasCustomPropertyDeserializer(ResolvedProperty property, DeserializationContext context) {
        AnnotatedMember member = property.beanProperty().getMember();
        return member != null && context.getAnnotationIntrospector().findDeserializer(member) != null;
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
        try (JsonParser valueParser = node.traverse(source.getCodec())) {
            valueParser.nextToken();
            JsonDeserializer<Object> valueDeserializer = context.findContextualValueDeserializer(
                    property.type(), property.beanProperty());
            return valueParser.currentToken() == JsonToken.VALUE_NULL
                    ? valueDeserializer.getNullValue(context)
                    : valueDeserializer.deserialize(valueParser, context);
        }
    }

    private void handleUnknown(KlumBuilder<?> builder, String externalName, JsonNode node,
                               JsonParser source, DeserializationContext context) {
        try (JsonParser valueParser = node.traverse(source.getCodec())) {
            valueParser.nextToken();
            context.handleUnknownProperty(valueParser, this, builder, externalName);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private ResolvedProperties resolveProperties(Class<?> currentType, DeserializationContext context) {
        JavaType type = context.constructType(currentType);
        BeanDescription description = context.getConfig().introspect(type);
        Map<String, ResolvedProperty> bindable = new LinkedHashMap<>();
        Set<String> ignored = new HashSet<>(description.getIgnoredPropertyNames());
        description.getClassInfo().fields().forEach(field -> addIgnoredFieldName(field, ignored, context));
        description.findProperties().forEach(definition -> {
            Optional<ResolvedProperty> resolved = definition.couldDeserialize() && visibleInActiveView(definition, context)
                    ? toResolvedProperty(currentType, definition)
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

    private Optional<ResolvedProperty> toResolvedProperty(Class<?> currentType, BeanPropertyDefinition property) {
        Optional<Field> schemaField = DslHelper.getField(currentType, property.getInternalName());
        if (schemaField.isEmpty() || !isConfigurable(schemaField.get()))
            return Optional.empty();
        BeanProperty beanProperty = new BeanProperty.Std(
                property.getFullName(),
                property.getPrimaryType(),
                property.getWrapperName(),
                property.getPrimaryMember(),
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

        private void replay(KlumBuilder<?> root, ObjectNode configuration, ResolvedProperties properties) {
            try {
                addBuilder(root, configuration, properties);
                for (PendingBuilder pending : pendingBuilders) {
                    pending.builder().setCurrentTemplates(Collections.emptyMap());
                    LifecycleHelper.executeLifecycleMethods(pending.builder(), PostCreate.class);
                }
                for (PendingBuilder pending : pendingBuilders)
                    bindConfiguration(pending);
                for (PendingBuilder pending : pendingBuilders)
                    LifecycleHelper.executeLifecycleMethods(pending.builder(), PostApply.class);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private PendingBuilder addBuilder(KlumBuilder<?> builder, ObjectNode configuration,
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
            Collection<Object> result = SortedSet.class.isAssignableFrom(property.type().getRawClass())
                    ? new TreeSet<>()
                    : Set.class.isAssignableFrom(property.type().getRawClass())
                    ? new LinkedHashSet<>()
                    : new ArrayList<>();
            Class<?> childType = property.type().getContentType().getRawClass();
            for (JsonNode element : node)
                result.add(discoverOwnedBuilder(element, childType, null));
            return result;
        }

        private KlumBuilder<?> discoverOwnedBuilder(JsonNode node, Class<?> childType, Object keyHint)
                throws IOException {
            if (!node.isObject())
                throw JsonMappingException.from(source, "Expected an object for owned DSL value " + childType.getName());
            ObjectNode object = (ObjectNode) node;
            PendingBuilder existing = buildersByConfiguration.get(object);
            if (existing != null)
                return existing.builder();
            ResolvedProperties properties = resolveProperties(childType, context);
            String key = resolveKey(childType, object, keyHint, properties);
            KlumBuilder<?> child = FactoryHelper.createBuilder(childType, key);
            addBuilder(child, object, properties);
            return child;
        }

        private void bindConfiguration(PendingBuilder pending) {
            pending.configuration().fields().forEachRemaining(entry -> bind(
                    pending.builder(), entry.getKey(), entry.getValue(), pending.properties(), pending.ownedValues(),
                    source, context));
        }
    }

    private record PendingBuilder(KlumBuilder<?> builder, ObjectNode configuration, ResolvedProperties properties,
                                  Map<ResolvedProperty, Object> ownedValues) {
    }

}
