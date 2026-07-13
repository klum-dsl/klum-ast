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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.groovy.configdsl.transform.NoClosure;
import com.blackbuild.klum.ast.util.ClosureHelper;
import com.blackbuild.klum.ast.util.DslHelper;
import com.blackbuild.klum.ast.util.KlumBuilder;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkSource;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkToWrapper;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.String.format;

public class LinkHelper {

    // TODO: Move to AutoLink Phase, convert static annotation checks to klumCast

    private LinkHelper() {
    }

    static void autoLink(KlumBuilder<?> container, String fieldName) {
        Field field = container.getField(fieldName);
        Field schemaField = DslHelper.getField(container.getModelType(), fieldName).orElse(field);
        LinkTo linkTo = new LinkToWrapper(schemaField);
        autoLink(container, field, linkTo);
    }

    static void autoLink(KlumBuilder<?> builder, Field field, LinkTo linkTo) {
        Object value = determineLinkTarget(builder, field, linkTo);
        if (value == null) return;

        if (!field.getType().isAssignableFrom(value.getClass()))
            throw new KlumVisitorException(String.format("LinkTo annotation on %s#%s targets %s, which is not compatible with the field type %s",
                    field.getDeclaringClass().getName(), field.getName(), value.getClass().getName(), field.getType().getName()), builder);

        if (value instanceof Collection)
            builder.addElementsToCollection(field.getName(), (Collection<?>) value);
        else if (value instanceof Map)
            builder.addElementsToMap(field.getName(), (Map<?, ?>) value);
        else
            builder.setSingleField(field.getName(), value);
    }

    static Object determineLinkTarget(KlumBuilder<?> builder, Field fieldToFill, LinkTo linkTo) {
        Object providerObject = determineProviderObject(builder, linkTo);
        if (providerObject == null) return null;

        if (!linkTo.field().isEmpty())
            return InvokerHelper.getProperty(providerObject, linkTo.field());

        if (!linkTo.fieldId().isEmpty())
            return getSingleValueOrFail(providerObject, fieldToFill.getType(), it -> isLinkSourceWithId(it, linkTo.fieldId()));

        String selector = linkTo.selector();
        if (!selector.isEmpty()) {
            Object selectorValue = builder.getInstanceProperty(selector);
            if (selectorValue == null) return null;

            if (selectorValue instanceof String)
                return InvokerHelper.getProperty(providerObject, (String) selectorValue);
            if (selectorValue instanceof Iterable)
                return StreamSupport.stream(((Iterable<?>) selectorValue).spliterator(), false)
                        .map(it -> InvokerHelper.getProperty(providerObject, it.toString()))
                        .collect(Collectors.toList());
            throw new KlumVisitorException("Selector value must be a String or Iterable, but is " + selectorValue.getClass().getName(), builder);
        }

        return inferLinkTarget(builder, fieldToFill, linkTo, providerObject);
    }

    private static @Nullable Object inferLinkTarget(KlumBuilder<?> builder, Field fieldToFill, LinkTo linkTo, Object providerObject) {
        MetaProperty metaPropertyForFieldName = getFieldNameProperty(fieldToFill, providerObject, linkTo);
        if (linkTo.strategy() == LinkTo.Strategy.FIELD_NAME)
            return metaPropertyForFieldName != null ? metaPropertyForFieldName.getProperty(providerObject) : null;

        MetaProperty metaPropertyForOwnerPath = getOwnerPathProperty(builder, providerObject, linkTo);
        if (linkTo.strategy() == LinkTo.Strategy.OWNER_PATH)
            return metaPropertyForOwnerPath != null ? metaPropertyForOwnerPath.getProperty(providerObject) : null;

        if (pointToDifferentProperties(metaPropertyForOwnerPath, metaPropertyForFieldName))
            throw new KlumVisitorException(format("LinkTo annotation on %s#%s targeting %s would match both instance name (%s) and field name (%s). You need to explicitly set a strategy.",
                    fieldToFill.getDeclaringClass().getName(), fieldToFill.getName(), providerObject.getClass().getName(), metaPropertyForOwnerPath.getName(), metaPropertyForFieldName.getName()), builder);

        if (metaPropertyForOwnerPath != null)
            return metaPropertyForOwnerPath.getProperty(providerObject);
        else if (metaPropertyForFieldName != null)
            return metaPropertyForFieldName.getProperty(providerObject);

        return getSingleValueOrFail(providerObject, fieldToFill.getType(), it -> !it.isAnnotationPresent(LinkSource.class));
    }

    private static Object getSingleValueOrFail(Object provider, Class<?> type, java.util.function.Predicate<AnnotatedElement> filter) {
        if (!(provider instanceof KlumBuilder))
            return ClusterModel.getSingleValueOrFail(provider, type, filter);

        KlumBuilder<?> builder = (KlumBuilder<?>) provider;
        List<Field> matches = new ArrayList<>();
        Class<?> layer = builder.getClass();
        while (layer != null && KlumBuilder.class.isAssignableFrom(layer)) {
            for (Field field : layer.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        && !field.getName().contains("$")
                        && type.isAssignableFrom(field.getType())
                        && filter.test(field))
                    matches.add(field);
            }
            layer = layer.getSuperclass();
        }
        if (matches.isEmpty())
            throw new IllegalArgumentException(format("Class %s has no field of type %s (possibly with filter)", builder.getClass().getName(), type.getName()));
        if (matches.size() > 1)
            throw new IllegalArgumentException(format("Class %s has more than one field of type %s (%s) (possibly with filter)",
                    builder.getClass().getName(), type.getName(), matches.stream().map(Field::getName).toList()));
        return builder.getInstanceAttribute(matches.get(0).getName());
    }

    static Object determineProviderObject(KlumBuilder<?> builder, LinkTo linkTo) {
        if (linkTo.provider() != NoClosure.class)
            return ClosureHelper.invokeClosureWithDelegateAsArgument(linkTo.provider(), builder);
        if (linkTo.providerType() != Object.class)
            return StructureUtil.getOwnerHierarchy(builder).stream()
                    .filter(KlumBuilder.class::isInstance)
                    .map(KlumBuilder.class::cast)
                    .filter(candidate -> linkTo.providerType().isAssignableFrom(candidate.getModelType()))
                    .findFirst()
                    .orElse(null);

        return builder.getSingleOwner();
    }

    private static boolean isLinkSourceWithId(AnnotatedElement field, String id) {
        return field.isAnnotationPresent(LinkSource.class) && field.getAnnotation(LinkSource.class).value().equals(id);
    }

    static MetaProperty getFieldNameProperty(Field field, Object providerObject, LinkTo linkTo) {
        return getMetaPropertyOrMapKey(providerObject, field.getName() + linkTo.nameSuffix());
    }

    static MetaProperty getOwnerPathProperty(KlumBuilder<?> builder, Object providerObject, LinkTo linkTo) {
        Set<Object> owners = builder.getOwners();
        if (owners.size() != 1) return null;

        Object owner = owners.stream().findFirst().orElseThrow(AssertionError::new);

        if (owner == providerObject) return null;

        return StructureUtil.getPathOfSingleField(owner, builder)
                .map(it -> it + linkTo.nameSuffix())
                .map(it -> getMetaPropertyOrMapKey(providerObject, it))
                .orElse(null);
    }

    private static boolean pointToDifferentProperties(MetaProperty metaPropertyForInstanceName, MetaProperty metaPropertyForFieldName) {
        if (metaPropertyForInstanceName == null || metaPropertyForFieldName == null) return false;
        return !metaPropertyForInstanceName.getName().equals(metaPropertyForFieldName.getName());
    }

    static MetaProperty getMetaPropertyOrMapKey(Object providerObject, String name) {
        MetaProperty result = InvokerHelper.getMetaClass(providerObject).getMetaProperty(name);
        if (result != null) return result;
        if (providerObject instanceof Map && ((Map<?, ?>) providerObject).containsKey(name))
            return new MapKeyMetaProperty(name);
        return null;
    }

    private static class MapKeyMetaProperty extends MetaProperty {
        MapKeyMetaProperty(String name) {
            super(name, Object.class);
        }

        @Override
        public Object getProperty(Object object) {
            return ((Map<String, Object>) object).get(name);
        }

        @Override
        public void setProperty(Object object, Object newValue) {
            ((Map<String, Object>) object).put(name, newValue);
        }
    }
}
