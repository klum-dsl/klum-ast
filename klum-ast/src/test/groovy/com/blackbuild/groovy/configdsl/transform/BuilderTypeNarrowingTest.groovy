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
import com.blackbuild.klum.ast.runtime.KlumModelException
import spock.lang.Issue

@Issue('648')
class BuilderTypeNarrowingTest extends AbstractDSLSpec {

    def setup() {
        createClass '''
            package predicates

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Field
            import com.blackbuild.klum.ast.FieldType
            import com.blackbuild.klum.ast.PostCreate
            import com.blackbuild.klum.ast.PostTree
            import com.blackbuild.klum.ast.runtime.KlumModelException
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Deployment {
                Registry registry
                String observedUrl

                @PostTree
                void inspectRegistry() {
                    assert Registry.Create.isModelOrBuilder(registry)
                    assert SpecialRegistry.Create.isBuilder(registry)
                    def special = SpecialRegistry.Create.asBuilder(registry)
                    observedUrl = special.toUrl()
                }
            }

            @DSL abstract class Registry {
                String host

                @Builder.Query
                String toUrl() { "https://$host" }
            }

            @DSL class SpecialRegistry extends Registry {
                @Field(FieldType.PROTECTED)
                String kind

                @PostCreate
                void selectKind() { kind = 'special' }
            }

            @DSL class OrdinaryRegistry extends Registry {
            }

            @CompileStatic
            @DSL class LinkedDeployment {
                @Field(FieldType.LINK)
                Registry registry
                String observedUrl
                String mutationFailure

                @PostTree
                void inspectRegistry() {
                    def special = SpecialRegistry.Create.asBuilder(registry)
                    observedUrl = special.toUrl()
                    try {
                        special.host('changed.example.test')
                    } catch (KlumModelException exception) {
                        mutationFailure = exception.message
                    }
                }
            }
        '''
    }

    def "matches completed Models and Builders with ordinary hierarchy assignability"() {
        given:
        def Registry = getClass('predicates.Registry')
        def SpecialRegistry = getClass('predicates.SpecialRegistry')
        def OrdinaryRegistry = getClass('predicates.OrdinaryRegistry')
        Object capturedBuilder

        when:
        def special = SpecialRegistry.Create.With {
            capturedBuilder = delegate
            host 'packages.example.test'
        }

        then: 'completed Models satisfy only the combined predicate'
        Registry.Create.isModelOrBuilder(special)
        SpecialRegistry.Create.isModelOrBuilder(special)
        !OrdinaryRegistry.Create.isModelOrBuilder(special)
        !Registry.Create.isBuilder(special)
        !SpecialRegistry.Create.isBuilder(special)

        and: 'Builders use their declared Model type and retain identity'
        Registry.Create.isModelOrBuilder(capturedBuilder)
        SpecialRegistry.Create.isModelOrBuilder(capturedBuilder)
        !OrdinaryRegistry.Create.isModelOrBuilder(capturedBuilder)
        Registry.Create.isBuilder(capturedBuilder)
        SpecialRegistry.Create.isBuilder(capturedBuilder)
        !OrdinaryRegistry.Create.isBuilder(capturedBuilder)
        Registry.Create.asBuilder(capturedBuilder).is(capturedBuilder)
        SpecialRegistry.Create.asBuilder(capturedBuilder).is(capturedBuilder)

        and: 'null and foreign values never match'
        !Registry.Create.isModelOrBuilder(null)
        !Registry.Create.isBuilder(null)
        !Registry.Create.isModelOrBuilder('registry')
        !Registry.Create.isBuilder('registry')

        and: 'the protected discriminator remains absent from the public Builder contract'
        !getClass('predicates.SpecialRegistry_DSL$Builder').methods*.name.contains('getKind')
        !getClass('predicates.SpecialRegistry_DSL$Builder').methods*.name.contains('setKind')
    }

    def "narrows a related Builder under static checking during lifecycle execution"() {
        given:
        def specialFactory = getClass('predicates.SpecialRegistry').Create

        when:
        def deployment = clazz.Create.With {
            registry(specialFactory) {
                host 'packages.example.test'
            }
        }

        then:
        deployment.observedUrl == 'https://packages.example.test'
        deployment.registry.class.name == 'predicates.SpecialRegistry'
    }

    def "keeps sealed and inactive Builder identity without weakening mutation guards"() {
        given:
        def SpecialRegistry = getClass('predicates.SpecialRegistry')
        Object capturedBuilder
        SpecialRegistry.Create.With {
            capturedBuilder = delegate
            host 'packages.example.test'
        }

        when: 'the captured Builder is narrowed for read-only use'
        Object narrowed = SpecialRegistry.Create.asBuilder(capturedBuilder)

        then:
        SpecialRegistry.Create.isBuilder(capturedBuilder)
        narrowed.is(capturedBuilder)
        narrowed.toUrl() == 'https://packages.example.test'

        when: 'the same captured Builder is mutated after its lifecycle'
        capturedBuilder.host('changed.example.test')

        then:
        KlumModelException error = thrown()
        error.message.contains('Construction session has completed')
    }

    def "narrows a sealed completed-Model LINK wrapper for truthful read-only queries"() {
        given:
        def SpecialRegistry = getClass('predicates.SpecialRegistry')
        def LinkedDeployment = getClass('predicates.LinkedDeployment')
        def completedSpecial = SpecialRegistry.Create.With(host: 'packages.example.test')
        Object linkWrapper

        when:
        def deployment = LinkedDeployment.Create.With {
            registry completedSpecial
            linkWrapper = delegate.registry
        }

        then:
        SpecialRegistry.Create.isBuilder(linkWrapper)
        SpecialRegistry.Create.asBuilder(linkWrapper).is(linkWrapper)
        deployment.registry.is(completedSpecial)
        deployment.observedUrl == 'https://packages.example.test'
        deployment.mutationFailure.contains('sealed Builder')
    }

    def "reports stable diagnostics for values that cannot be narrowed"() {
        given:
        def SpecialRegistry = getClass('predicates.SpecialRegistry')
        def OrdinaryRegistry = getClass('predicates.OrdinaryRegistry')
        Object ordinaryBuilder
        def special = SpecialRegistry.Create.With(host: 'packages.example.test')
        OrdinaryRegistry.Create.With {
            ordinaryBuilder = delegate
        }

        when: 'a completed Model is supplied'
        SpecialRegistry.Create.asBuilder(special)

        then:
        KlumModelException completed = thrown()
        completed.message.contains('Cannot narrow completed Model')
        completed.message.contains('asBuilder accepts only an existing Builder')

        when: 'a Builder from a sibling Model type is supplied'
        SpecialRegistry.Create.asBuilder(ordinaryBuilder)

        then:
        KlumModelException mismatch = thrown()
        mismatch.message.contains('Cannot narrow the Builder for predicates.OrdinaryRegistry')
        mismatch.message.contains('declared Model types are not assignable')

        when: 'null is supplied'
        SpecialRegistry.Create.asBuilder(null)

        then:
        KlumModelException nullValue = thrown()
        nullValue.message.contains('Cannot narrow null to the Builder for predicates.SpecialRegistry')

        when: 'a foreign value is supplied'
        SpecialRegistry.Create.asBuilder('registry')

        then:
        KlumModelException foreign = thrown()
        foreign.message.contains('the value is neither a DSL Object nor a Builder')
    }

    def "returns the exact generated subtype Builder to a static Groovy consumer"() {
        given:
        Class<?> consumer = createSecondaryClass '''
            package predicates

            import groovy.transform.CompileStatic

            @CompileStatic
            final class StaticBuilderNarrowingConsumer {
                static SpecialRegistry_DSL.Builder<SpecialRegistry> narrow(Object value) {
                    SpecialRegistry.Create.asBuilder(value)
                }
            }
        ''', 'predicates/StaticBuilderNarrowingConsumer.groovy'
        Object capturedBuilder
        getClass('predicates.SpecialRegistry').Create.With {
            capturedBuilder = delegate
        }

        expect:
        consumer.narrow(capturedBuilder).is(capturedBuilder)
    }
}
