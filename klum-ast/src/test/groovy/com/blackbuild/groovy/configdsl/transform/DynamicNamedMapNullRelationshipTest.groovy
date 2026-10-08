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
package com.blackbuild.klum.ast

import groovy.lang.DelegatingMetaClass
import groovy.lang.MetaClass
import groovy.lang.GroovySystem
import groovy.lang.ProxyMetaClass
import groovy.transform.CompileStatic
import com.blackbuild.klum.ast.runtime.internal.InternalKlumBuilder
import org.codehaus.groovy.runtime.InvokerHelper
import org.codehaus.groovy.runtime.metaclass.MethodSelectionException
import org.codehaus.groovy.util.FastArray
import spock.lang.Issue

@Issue("846")
class DynamicNamedMapNullRelationshipTest extends AbstractDSLSpec {
    def setup() {
        createClass '''
            package pk
            @DSL class Graph { @Field(FieldType.LINK) Node linked }
            @DSL class Node { @Key String name }
        '''
    }

    def "dynamic relationship dispatch succeeds even when ordinary method lookup is ambiguous"() {
        given:
        def operations = []

        when:
        instance = clazz.Create.With {
            def builder = delegate
            def original = DynamicNamedMapNullRelationshipTest.originalMetaClass(builder)
            def dynamic = DynamicNamedMapNullRelationshipTest.relationshipMetaClass(original, operations, null, wrapped)
            builder.setMetaClass(dynamic)
            try {
                // Control: the unchanged named-map invocation can successfully resolve this operation.
                InvokerHelper.invokeMethod(builder, 'linked', (Object) null)
                DynamicNamedMapNullRelationshipTest.applyNamedValues(builder)
            } finally {
                builder.setMetaClass(original)
            }
        }

        then:
        operations == ['linked', 'linked']
        instance.linked == null

        where:
        wrapped << [false, true]
    }

    def "an exception inside a dynamically selected relationship handler remains the original exception"() {
        given:
        def operations = []
        def originalFailure = new MethodSelectionException('inside-handler', new FastArray(), new Class[0])

        when:
        clazz.Create.With {
            def builder = delegate
            def original = DynamicNamedMapNullRelationshipTest.originalMetaClass(builder)
            builder.setMetaClass(DynamicNamedMapNullRelationshipTest.relationshipMetaClass(original, operations, originalFailure, wrapped))
            try {
                DynamicNamedMapNullRelationshipTest.applyNamedValues(builder)
            } finally {
                builder.setMetaClass(original)
            }
        }

        then:
        MethodSelectionException failure = thrown()
        failure.is(originalFailure)
        operations == ['linked']

        where:
        wrapped << [false, true]
    }

    @CompileStatic
    private static MetaClass originalMetaClass(InternalKlumBuilder<?> builder) {
        return builder.getMetaClass()
    }

    @CompileStatic
    private static MetaClass relationshipMetaClass(MetaClass original, List<String> operations,
                                                   MethodSelectionException failure, boolean wrapped) {
        def handler = new RelationshipMetaClass(original, operations, failure)
        if (!wrapped)
            return handler
        def proxy = new ProxyMetaClass(GroovySystem.metaClassRegistry, original.getTheClass(), handler)
        proxy.setInterceptor(((ProxyMetaClass) original).getInterceptor())
        return proxy
    }

    // Factories enter this Java boundary directly; do not dispatch apply itself through the custom MetaClass.
    @CompileStatic
    private static void applyNamedValues(InternalKlumBuilder<?> builder) {
        builder.apply([linked: null])
    }

    private static class RelationshipMetaClass extends DelegatingMetaClass {
        private final List<String> operations
        private final MethodSelectionException failure

        RelationshipMetaClass(MetaClass delegate, List<String> operations, MethodSelectionException failure) {
            super(delegate)
            this.operations = operations
            this.failure = failure
        }

        @Override
        Object invokeMethod(Object object, String name, Object[] arguments) {
            if (name != 'linked')
                return super.invokeMethod(object, name, arguments)
            operations.add(name)
            assert arguments.length == 0 // preserve InvokerHelper's bare-null argument conversion
            if (failure != null)
                throw failure
            return null
        }
    }
}
