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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumBuilder
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
import com.blackbuild.klum.ast.runtime.KlumModelException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('868')
class BuilderModelTypeTest extends AbstractDSLSpec {
    def setup() {
        createClass """
            package discovery
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Field
            import com.blackbuild.klum.ast.FieldType
            import com.blackbuild.klum.ast.PostCreate
            import com.blackbuild.klum.ast.PostTree
            import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
            import groovy.transform.CompileStatic

            @DSL abstract class Registry { String host }
            @DSL class SpecialRegistry extends Registry {
                @PostCreate void inspectEarly() {
                    assert KlumBuilderSupport.of(this).modelType == SpecialRegistry
                }
            }
            @CompileStatic
            @DSL class Deployment {
                Registry registry
                @Field(FieldType.LINK) Registry linked
                Class<?> ownedType
                Class<?> linkedType
                @PostTree void inspect() {
                    ownedType = KlumBuilderSupport.of(registry).modelType
                    linkedType = KlumBuilderSupport.of(linked).modelType
                }
            }
        """
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Completed-Object-Support.md#discovering-the-model-type-of-a-builder')
    def 'discovers concrete Model types through base typed owned and LINK relationships'() {
        given:
        def SpecialRegistry = getClass('discovery.SpecialRegistry')
        def Deployment = getClass('discovery.Deployment')
        def completed = SpecialRegistry.Create.With(host: 'packages.example.test')
        KlumBuilder<?> owned
        KlumBuilder<?> capturedLink
        String sealedFailure

        when:
        def deployment = Deployment.Create.With {
            owned = registry(SpecialRegistry.Create) { host 'owned.example.test' }
            linked completed
            capturedLink = delegate.linked
            assert KlumBuilderSupport.of(owned).modelType == SpecialRegistry
            assert KlumBuilderSupport.of(capturedLink).modelType == SpecialRegistry
            try {
                capturedLink.host('changed')
            } catch (KlumModelException exception) {
                sealedFailure = exception.message
            }
        }

        then:
        sealedFailure.contains('sealed Builder')
        deployment.ownedType == SpecialRegistry
        deployment.linkedType == SpecialRegistry
        deployment.registry.host == 'owned.example.test'
        deployment.linked.is(completed)
        KlumBuilderSupport.of(owned).modelType == SpecialRegistry
        KlumBuilderSupport.of(capturedLink).modelType == SpecialRegistry
        SpecialRegistry.Create.narrowBuilder(owned).is(owned)
        SpecialRegistry.Create.narrowBuilder(capturedLink).is(capturedLink)

        when:
        capturedLink.host('changed')

        then:
        KlumModelException sealed = thrown()
        sealed.message.contains('Construction session has completed')

        when:
        owned.host('changed')

        then:
        KlumModelException inactive = thrown()
        inactive.message.contains('Construction session has completed')
    }

    def 'preserves Template defaults and materialization when reading recipient type'() {
        given:
        def SpecialRegistry = getClass('discovery.SpecialRegistry')
        def template = SpecialRegistry.Create.Template.With(host: 'template.example.test')
        KlumBuilder<?> recipient
        Object completed

        when:
        SpecialRegistry.Template.With(template) {
            completed = SpecialRegistry.Create.With {
                recipient = delegate
                assert KlumBuilderSupport.of(recipient).modelType == SpecialRegistry
            }
        }

        then:
        completed.class == SpecialRegistry
        completed.host == 'template.example.test'
        template.host == 'template.example.test'
        KlumBuilderSupport.of(recipient).modelType == SpecialRegistry
    }

    def 'keeps immutable type metadata readable after construction abort'() {
        given:
        def SpecialRegistry = getClass('discovery.SpecialRegistry')
        KlumBuilder<?> captured

        when:
        SpecialRegistry.Create.With {
            captured = delegate
            throw new IllegalStateException('abort construction')
        }

        then:
        thrown(IllegalStateException)
        KlumBuilderSupport.of(captured).modelType == SpecialRegistry

        when:
        captured.host('changed')

        then:
        thrown(KlumModelException)
    }

    def 'retains targeted null and unsupported marker diagnostics and an empty marker API'() {
        when:
        KlumBuilderSupport.of(null)

        then:
        NullPointerException missing = thrown()
        missing.message == 'builder'

        when:
        KlumBuilderSupport.of(new KlumBuilder<Object>() {})

        then:
        KlumModelException unsupported = thrown()
        unsupported.message.contains('requires a generated Builder')
        KlumBuilder.declaredMethods.length == 0
    }
}
