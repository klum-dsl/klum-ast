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

import com.blackbuild.klum.ast.process.KlumPhase;
import com.blackbuild.klum.ast.process.VisitingPhaseAction;
import com.blackbuild.klum.ast.util.ClosureHelper;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.ast.util.layer3.annotations.AutoLink;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTarget;
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo;
import groovy.lang.Closure;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Set;

import static java.lang.String.format;

public class AutoLinkPhase extends VisitingPhaseAction {

    public AutoLinkPhase() {
        super(KlumPhase.AUTO_LINK);
    }

    @Override
    public void visit(String path, Object element) {
        ClusterModel.getFieldsAnnotatedWith(element, LinkTo.class)
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() == null)
                .forEach(entry -> autoLink(element, entry.getKey()));

        KlumInstanceProxy.getProxyFor(element).executeLifecycleMethods(AutoLink.class);
    }

    private void autoLink(Object container, String fieldName) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(container);
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Field field = ClusterModel.getField(container.getClass(), fieldName).get();
        LinkTo linkTo = field.getAnnotation(LinkTo.class);
        autoLink(proxy, field, linkTo);
    }

    private void autoLink(KlumInstanceProxy proxy, Field field, LinkTo linkTo) {
        Object value = determineLinkTarget(proxy, field, linkTo);
        if (value != null)
            proxy.setSingleField(field.getName(), value);
    }

    private Object determineLinkTarget(KlumInstanceProxy proxy, Field field, LinkTo linkTo) {
        Object ownerObject = determineOwnerObject(proxy, linkTo);

        if (!linkTo.field().equals(""))
            return InvokerHelper.getProperty(ownerObject, linkTo.field());

        if (!linkTo.fieldId().equals(""))
            throw new UnsupportedOperationException();

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

        return ClusterModel.getSingleValueOrFail(ownerObject, field.getType(), it -> !it.isAnnotationPresent(LinkTarget.class));
    }

    private static MetaProperty getFieldNameProperty(Field field, Object ownerObject, LinkTo linkTo) {
        return InvokerHelper.getMetaClass(ownerObject).getMetaProperty(field.getName() + linkTo.nameSuffix());
    }

    private static MetaProperty getInstanceNameProperty(KlumInstanceProxy proxy, Object ownerObject, LinkTo linkTo) {
        Set<Object> owners = proxy.getOwners();
        if (owners.size() != 1) return null;

        Object owner = owners.stream().findFirst().orElse(null);
        return StructureUtil.getPathOfSingleField(owner, proxy.getDSLInstance())
                .map(it -> it + linkTo.nameSuffix())
                .map(it -> InvokerHelper.getMetaClass(ownerObject).getMetaProperty(it))
                .orElse(null);
    }

    private Object determineOwnerObject(KlumInstanceProxy proxy, LinkTo linkTo) {
        if (linkTo.owner() == LinkTo.None.class) return proxy.getSingleOwner();

        Class<? extends Closure<Object>> ownerPath = linkTo.owner();
        return Objects.requireNonNull(ClosureHelper.invokeClosureWithDelegateAsArgument(ownerPath, proxy.getDSLInstance()));
    }

}
