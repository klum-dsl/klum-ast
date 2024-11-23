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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.groovy.configdsl.transform.NoClosure;
import com.blackbuild.klum.ast.util.ClosureHelper;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkSource;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkToWrapper;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;

public class LinkHelper {

    private LinkHelper() {}

    static void autoLink(Object container, String fieldName) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(container);
        Field field = ClusterModel.getField(container.getClass(), fieldName).orElseThrow(AssertionError::new);
        LinkTo linkTo = new LinkToWrapper(field);
        autoLink(proxy, field, linkTo);
    }

    static void autoLink(KlumInstanceProxy proxy, Field field, LinkTo linkTo) {
        Object value = determineLinkTarget(proxy, field, linkTo);
        if (value != null)
            proxy.setSingleField(field.getName(), value);
    }

    static Object determineLinkTarget(KlumInstanceProxy proxy, Field field, LinkTo linkTo) {
        Object providerObject = determineProviderObject(proxy, linkTo);
        if (providerObject == null) return null;

        if (!linkTo.field().isEmpty())
            return InvokerHelper.getProperty(providerObject, linkTo.field());

        if (!linkTo.fieldId().isEmpty())
            return ClusterModel.getSingleValueOrFail(providerObject, field.getType(), it -> isLinkSourceWithId(it, linkTo.fieldId()));

        MetaProperty metaPropertyForFieldName = getFieldNameProperty(field, providerObject, linkTo);
        MetaProperty metaPropertyForInstanceName = getInstanceNameProperty(proxy, providerObject, linkTo);

        if (pointToDifferentProperties(metaPropertyForInstanceName, metaPropertyForFieldName)) {
            switch (linkTo.strategy()) {
                case INSTANCE_NAME:
                    return metaPropertyForInstanceName.getProperty(providerObject);
                case FIELD_NAME:
                    return metaPropertyForFieldName.getProperty(providerObject);
                default:
                    throw new IllegalStateException(format("LinkTo annotation on %s#%s targeting %s would match both instance name (%s) and field name (%s). You need to explicitly set a strategy.",
                            field.getDeclaringClass().getName(), field.getName(), providerObject.getClass().getName(), metaPropertyForInstanceName.getName(), metaPropertyForFieldName.getName()));
            }
        }

        if (metaPropertyForInstanceName != null)
            return metaPropertyForInstanceName.getProperty(providerObject);
        else if (metaPropertyForFieldName != null)
            return metaPropertyForFieldName.getProperty(providerObject);

        return ClusterModel.getSingleValueOrFail(providerObject, field.getType(), it -> !it.isAnnotationPresent(LinkSource.class));
    }

    private static boolean pointToDifferentProperties(MetaProperty metaPropertyForInstanceName, MetaProperty metaPropertyForFieldName) {
        if (metaPropertyForInstanceName == null || metaPropertyForFieldName == null) return false;
        return !metaPropertyForInstanceName.getName().equals(metaPropertyForFieldName.getName());
    }

    private static boolean isLinkSourceWithId(AnnotatedElement field, String id) {
        return field.isAnnotationPresent(LinkSource.class) && field.getAnnotation(LinkSource.class).value().equals(id);
    }

    static MetaProperty getFieldNameProperty(Field field, Object providerObject, LinkTo linkTo) {
        return getMetaPropertyOrMapKey(providerObject, field.getName() + linkTo.nameSuffix());
    }

    static MetaProperty getInstanceNameProperty(KlumInstanceProxy proxy, Object providerObject, LinkTo linkTo) {
        Set<Object> owners = proxy.getOwners();
        if (owners.size() != 1) return null;

        Object owner = owners.stream().findFirst().orElseThrow(AssertionError::new);

        if (owner == providerObject) return null;

        return StructureUtil.getPathOfSingleField(owner, proxy.getDSLInstance())
                .map(it -> it + linkTo.nameSuffix())
                .map(it -> getMetaPropertyOrMapKey(providerObject, it))
                .orElse(null);
    }

    static MetaProperty getMetaPropertyOrMapKey(Object providerObject, String name) {
        MetaProperty result = InvokerHelper.getMetaClass(providerObject).getMetaProperty(name);
        if (result != null) return result;
        if (providerObject instanceof Map && ((Map<?, ?>) providerObject).containsKey(name)) return new MapKeyMetaProperty(name);
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
            ((Map<String, Object>) object).put(name,newValue);
        }
    }

    static Object determineProviderObject(KlumInstanceProxy proxy, LinkTo linkTo) {
        if (linkTo.provider() != NoClosure.class)
            return ClosureHelper.invokeClosureWithDelegateAsArgument(linkTo.provider(), proxy.getDSLInstance());
        if (linkTo.providerType() != Object.class)
            return StructureUtil.getAncestorOfType(proxy.getDSLInstance(), linkTo.providerType())
                    .orElse(null);

        return proxy.getSingleOwner();
    }
}
