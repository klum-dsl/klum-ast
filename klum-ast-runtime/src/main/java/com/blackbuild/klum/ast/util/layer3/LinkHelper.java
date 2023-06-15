/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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

import com.blackbuild.klum.ast.util.ClosureHelper;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkSource;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkToWrapper;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.Set;

import static java.lang.String.format;

public class LinkHelper {

    private LinkHelper() {}

    static void autoLink(Object container, String fieldName) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(container);
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Field field = ClusterModel.getField(container.getClass(), fieldName).get();
        LinkTo linkTo = new LinkToWrapper(field);
        autoLink(proxy, field, linkTo);
    }

    static void autoLink(KlumInstanceProxy proxy, Field field, LinkTo linkTo) {
        Object value = determineLinkTarget(proxy, field, linkTo);
        if (value != null)
            proxy.setSingleField(field.getName(), value);
    }

    static Object determineLinkTarget(KlumInstanceProxy proxy, Field field, LinkTo linkTo) {
        Object ownerObject = determineOwnerObject(proxy, linkTo);
        if (ownerObject == null) return null;

        if (!linkTo.field().equals(""))
            return InvokerHelper.getProperty(ownerObject, linkTo.field());

        if (!linkTo.fieldId().equals(""))
            return ClusterModel.getSingleValueOrFail(ownerObject, field.getType(), it -> isLinkSourceWithId(it, linkTo.fieldId()));

        MetaProperty metaPropertyForFieldName = getFieldNameProperty(field, ownerObject, linkTo);
        MetaProperty metaPropertyForInstanceName = getInstanceNameProperty(proxy, ownerObject, linkTo);

        if (metaPropertyForInstanceName != null && metaPropertyForFieldName != null && !metaPropertyForInstanceName.getName().equals(metaPropertyForFieldName.getName())) {
            switch (linkTo.strategy()) {
                case INSTANCE_NAME:
                    return metaPropertyForInstanceName.getProperty(ownerObject);
                case FIELD_NAME:
                    return metaPropertyForFieldName.getProperty(ownerObject);
                default:
                    throw new IllegalStateException(format("LinkTo annotation on %s#%s targeting %s would match both instance name (%s) and field name (%s). You need to explicitly set a strategy.",
                            field.getDeclaringClass().getName(), field.getName(), ownerObject.getClass().getName(), metaPropertyForInstanceName.getName(), metaPropertyForFieldName.getName()));
            }
        }

        if (metaPropertyForInstanceName != null)
            return metaPropertyForInstanceName.getProperty(ownerObject);
        else if (metaPropertyForFieldName != null)
            return metaPropertyForFieldName.getProperty(ownerObject);

        return ClusterModel.getSingleValueOrFail(ownerObject, field.getType(), it -> !it.isAnnotationPresent(LinkSource.class));
    }

    private static boolean isLinkSourceWithId(AnnotatedElement field, String id) {
        return field.isAnnotationPresent(LinkSource.class) && field.getAnnotation(LinkSource.class).value().equals(id);
    }

    static MetaProperty getFieldNameProperty(Field field, Object ownerObject, LinkTo linkTo) {
        return InvokerHelper.getMetaClass(ownerObject).getMetaProperty(field.getName() + linkTo.nameSuffix());
    }

    static MetaProperty getInstanceNameProperty(KlumInstanceProxy proxy, Object ownerObject, LinkTo linkTo) {
        Set<Object> owners = proxy.getOwners();
        if (owners.size() != 1) return null;

        Object owner = owners.stream().findFirst().orElseThrow(AssertionError::new);

        if (owner == ownerObject) return null;

        return StructureUtil.getPathOfSingleField(owner, proxy.getDSLInstance())
                .map(it -> it + linkTo.nameSuffix())
                .map(it -> InvokerHelper.getMetaClass(ownerObject).getMetaProperty(it))
                .orElse(null);
    }

    static Object determineOwnerObject(KlumInstanceProxy proxy, LinkTo linkTo) {
        if (linkTo.owner() != LinkTo.None.class)
            return ClosureHelper.invokeClosureWithDelegateAsArgument(linkTo.owner(), proxy.getDSLInstance());
        if (linkTo.ownerType() != Object.class)
            return StructureUtil.getAncestorOfType(proxy.getDSLInstance(), linkTo.ownerType())
                    .orElse(null);

        return proxy.getSingleOwner();
    }
}
