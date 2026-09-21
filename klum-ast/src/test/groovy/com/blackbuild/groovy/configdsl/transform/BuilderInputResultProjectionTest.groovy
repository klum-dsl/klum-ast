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
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.cast.KlumCastValidator
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.transform.GroovyASTTransformationClass
import spock.lang.Issue

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.METHOD
import static java.lang.annotation.ElementType.PARAMETER

@Issue('650')
class BuilderInputResultProjectionTest extends AbstractDSLSpec {

    def "Builder Input and Result are the canonical runtime projection facets"() {
        expect:
        Builder.Input.name == 'com.blackbuild.klum.ast.Builder$Input'
        Builder.Input.getAnnotation(Target).value().toList() == [PARAMETER]
        Builder.Input.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        Builder.Input.getAnnotation(GroovyASTTransformationClass).value().toList() ==
                ['com.blackbuild.klum.ast.compiler.internal.ast.BuilderInputTransformation']
        Builder.Result.name == 'com.blackbuild.klum.ast.Builder$Result'
        Builder.Result.getAnnotation(Target).value().toList() == [METHOD]
        Builder.Result.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        Builder.Result.getAnnotation(KlumCastValidator).value() ==
                'com.blackbuild.klum.ast.compiler.internal.ast.BuilderResultCheck'

        when:
        Class.forName('com.blackbuild.klum.ast.BuilderInput')

        then:
        thrown(ClassNotFoundException)

        when:
        Class.forName('com.blackbuild.klum.ast.BuilderResult')

        then:
        thrown(ClassNotFoundException)
    }

    def "projects explicit static Builder input and result through lifecycle attachment"() {
        given:
        createClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Deployment {
                Registry source
                Registry normalized

                @PostTree
                void normalizeRegistry() {
                    normalized Registry.normalized(source)
                }
            }

            @DSL class Registry {
                String host

                @Builder.Result
                static Registry normalized(@Builder.Input Registry source) {
                    Registry.Create.With(host: source.host.toLowerCase())
                }
            }
        '''

        when:
        def deployment = clazz.Create.With {
            source { host 'PACKAGES.EXAMPLE.TEST' }
        }

        then:
        deployment.source.host == 'PACKAGES.EXAMPLE.TEST'
        deployment.normalized.host == 'packages.example.test'

        and: 'the linked twin uses exact generated Builder types'
        def twin = getClass('Registry').declaredMethods.find { it.name == '$klum$asBuilder$normalized' }
        twin != null
        twin.returnType == getClass('Registry_DSL$Builder')
        twin.parameterTypes.toList() == [getClass('Registry_DSL$Builder')]
    }

    def "projects an explicit query input onto the public Builder and keeps the Model form"() {
        given:
        createClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Deployment {
                Registry source
                String observed

                @Builder.Query
                String normalize(@Builder.Input Registry donor) {
                    donor.host.toLowerCase()
                }

                @PostTree
                void normalizeRegistry() {
                    observed = normalize(source)
                }
            }

            @DSL class Registry { String host }
        '''

        when:
        def retainedBuilder
        def deployment = clazz.Create.With {
            retainedBuilder = delegate
            source { host 'PACKAGES.EXAMPLE.TEST' }
        }
        def completedResult = deployment.normalize(deployment.source)
        def sealedBuilderResult = retainedBuilder.normalize(retainedBuilder.source)

        then:
        deployment.observed == 'packages.example.test'
        completedResult == 'packages.example.test'
        sealedBuilderResult == 'packages.example.test'

        and:
        getClass('Deployment_DSL$Builder')
                .getMethod('normalize', getClass('Registry_DSL$Builder'))
                .returnType == String
    }

    def "retargets only explicitly marked mutator inputs and results"() {
        given:
        createClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Deployment {
                Registry donor
                Registry owned

                @Builder.Method
                @Builder.Result
                Registry copyRegistry(@Builder.Input Registry source) {
                    Registry.Create.With(host: source.host.toLowerCase())
                }

                @Builder.Method
                Registry ordinaryResult(Registry source) {
                    source
                }

                @PostTree
                void attachCopy() {
                    owned copyRegistry(donor)
                }
            }

            @DSL class Registry { String host }
        '''

        when:
        def deployment = clazz.Create.With {
            donor { host 'PACKAGES.EXAMPLE.TEST' }
        }

        then:
        deployment.owned.host == 'packages.example.test'

        and:
        def publicBuilder = getClass('Deployment_DSL$Builder')
        publicBuilder.getMethod('copyRegistry', getClass('Registry_DSL$Builder')).returnType ==
                getClass('Registry_DSL$Builder')
        publicBuilder.getMethod('ordinaryResult', getClass('Registry')).returnType == getClass('Registry')
    }

    def "projects abstract DSL Object inputs with the bounded public Builder contract"() {
        given:
        createClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Deployment {
                Registry source
                Registry target
                String observed

                @PostTree
                void observe() { observed = target.hostOf(source) }
            }

            @DSL abstract class Registry {
                String host

                @Builder.Query
                String hostOf(@Builder.Input Registry donor) { donor.host }
            }

            @DSL class TenantRegistry extends Registry { }
        '''

        and:
        createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class AbstractInputConsumer {
                static String read(
                        Registry_DSL.Builder<? extends Registry> target,
                        Registry_DSL.Builder<? extends Registry> source) {
                    target.hostOf(source)
                }
            }
        '''

        when:
        def tenantType = getClass('TenantRegistry')
        def deployment = clazz.Create.With {
            source(tenantType) { host 'source.example.test' }
            target(tenantType) { host 'target.example.test' }
        }

        then:
        deployment.observed == 'source.example.test'
    }

    def "keeps the deprecated Mutator spelling compatible with explicit projection facets"() {
        given:
        createClass '''
            @DSL class Deployment {
                Registry donor
                Registry owned

                @Mutator
                @Builder.Result
                Registry copyRegistry(@Builder.Input Registry source) {
                    Registry.Create.With(host: source.host.toLowerCase())
                }

                @PostTree
                void attachCopy() {
                    owned copyRegistry(donor)
                }
            }

            @DSL class Registry { String host }
        '''

        expect:
        clazz.Create.With { donor { host 'PACKAGES.EXAMPLE.TEST' } }.owned.host == 'packages.example.test'
    }

    def "projects Collection inputs and results while preserving their outer type and order"() {
        given:
        createClass '''
            @DSL class Deployment {
                List<Registry> sources

                @Builder.Method
                @Builder.Result
                LinkedList<Registry> normalizedAll(@Builder.Input List<Registry> sources) {
                    new LinkedList<>(sources)
                }

                @Builder.Method
                @Builder.Result
                LinkedHashMap<String, Registry> indexed(@Builder.Input Map<String, Registry> sources) {
                    new LinkedHashMap<>(sources)
                }
            }

            @DSL class Registry { String host }
        '''

        and: 'a separately compiled static consumer sees the exact public container contract'
        createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticContainerConsumer {
                static LinkedList<Registry_DSL.Builder<Registry>> normalize(
                        Deployment_DSL.Builder<Deployment> deployment,
                        List<Registry_DSL.Builder<Registry>> sources) {
                    deployment.normalizedAll(sources)
                }

                static LinkedHashMap<String, Registry_DSL.Builder<Registry>> index(
                        Deployment_DSL.Builder<Deployment> deployment,
                        Map<String, Registry_DSL.Builder<Registry>> sources) {
                    deployment.indexed(sources)
                }
            }
        '''

        when:
        List<String> projectedHosts
        Map<String, String> indexedHosts
        def deployment = clazz.Create.With {
            source { host 'B.EXAMPLE.TEST' }
            source { host 'A.EXAMPLE.TEST' }
            projectedHosts = normalizedAll(sources)*.host
            indexedHosts = indexed([second: sources[1], first: sources[0]]).collectEntries { key, value ->
                [key, value.host]
            }
        }

        then:
        deployment.sources*.host == ['B.EXAMPLE.TEST', 'A.EXAMPLE.TEST']
        projectedHosts == ['B.EXAMPLE.TEST', 'A.EXAMPLE.TEST']
        indexedHosts == [second: 'A.EXAMPLE.TEST', first: 'B.EXAMPLE.TEST']

        and:
        def projected = getClass('Deployment_DSL$Builder').getMethod('normalizedAll', List)
        projected.returnType == LinkedList
        projected.genericReturnType.typeName.contains('Registry_DSL$Builder<Registry>')
        projected.genericParameterTypes[0].typeName.contains('Registry_DSL$Builder<Registry>')
        def projectedMap = getClass('Deployment_DSL$Builder').getMethod('indexed', Map)
        projectedMap.returnType == LinkedHashMap
        projectedMap.genericReturnType.typeName.contains('java.lang.String, Registry_DSL$Builder<Registry>')
        projectedMap.genericParameterTypes[0].typeName.contains('java.lang.String, Registry_DSL$Builder<Registry>')
    }

    def "uses the emitted linked twin from a precompiled implementing Schema"() {
        given:
        createSecondaryClass '''
            package external

            @DSL class PrecompiledRegistry {
                String host

                @Builder.Result
                static PrecompiledRegistry normalized(@Builder.Input PrecompiledRegistry source) {
                    PrecompiledRegistry.Create.With(host: source.host.toLowerCase())
                }
            }
        '''

        createClass '''
            import external.PrecompiledRegistry
            import groovy.transform.TypeChecked
            import groovy.transform.TypeCheckingMode

            @DSL class Deployment {
                @PostTree
                @TypeChecked(TypeCheckingMode.SKIP)
                void normalizeRegistry() {
                    if (Boolean.FALSE) {
                        def source = PrecompiledRegistry.Create.AsBuilder().With()
                        PrecompiledRegistry.normalized(source)
                    }
                }
            }
        '''

        when:
        def deployment = clazz.Create.With()

        then:
        deployment != null
        getClass('external.PrecompiledRegistry').declaredMethods.any {
            it.name == '$klum$asBuilder$normalized'
        }
    }

    def "rejects unsupported explicit Builder input shapes"() {
        when:
        createClass source

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("Cannot project @Builder.Input parameter 'source'")
        error.message.contains(expected)

        where:
        source << [
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Query String inspect(@Builder.Input List source) { 'raw' }
                    }
                ''',
                '''
                    import com.blackbuild.klum.ast.runtime.KlumBuilder
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Query String inspect(@Builder.Input KlumBuilder source) { 'raw builder' }
                    }
                ''',
                '''
                    import com.blackbuild.klum.ast.runtime.KlumBuilder
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Query String inspect(@Builder.Input KlumBuilder<? extends Registry> source) { 'wildcard builder' }
                    }
                ''',
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Query String inspect(@Builder.Input List<? extends Registry> source) { 'wildcard' }
                    }
                ''',
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Query <T extends Registry> String inspect(@Builder.Input T source) { 'generic' }
                    }
                ''',
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Query String inspect(@Builder.Input List<List<Registry>> source) { 'nested' }
                    }
                ''',
                '''
                    @DSL class Deployment {
                        @Builder.Query String inspect(@Builder.Input String source) { source }
                    }
                ''',
        ]
        expected << [
                'Collection type is raw',
                'KlumBuilder type is raw',
                'KlumBuilder model type is a wildcard',
                'element type is a wildcard',
                'unresolved generic placeholder',
                'nested Collection/Map positions are not supported',
                'type does not resolve to a DSL Object',
        ]
    }

    def "rejects projected overloads that collapse to one Builder signature"() {
        when:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumBuilder

            @DSL class Registry { }

            @DSL class Deployment {
                @Builder.Query String inspect(@Builder.Input Registry source) { 'model' }
                @Builder.Query String inspect(@Builder.Input KlumBuilder<Registry> source) { 'builder' }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('@Builder.Query inspect(')
        error.message.contains('collides with an existing Builder method')
    }

    def "rejects Builder input annotations on external helpers"() {
        when:
        createClass '''
            @DSL class Registry { }

            class ExternalHelper {
                static String inspect(@Builder.Input Registry source) { 'external' }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('@Builder.Input can only be used on method parameters declared by a DSL Object')
    }

    def "rejects Builder input annotations on constructor parameters"() {
        when:
        createClass '''
            @DSL class Registry { }

            class ExternalHelper {
                ExternalHelper(@Builder.Input Registry source) { }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('@Builder.Input can only be used on method parameters declared by a DSL Object')
    }

    def "rejects Builder results on lifecycle methods"() {
        when:
        createClass '''
            @DSL class Registry {
                @PostTree
                @Builder.Result
                Registry invalid() { this }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('@Builder.Result cannot be combined with a lifecycle annotation')
    }

    def "requires an explicit method category for instance projection facets"() {
        when:
        createClass source

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains(expected)

        where:
        source << [
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        String inspect(@Builder.Input Registry source) { 'value' }
                    }
                ''',
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Result Registry create() { Registry.Create.With() }
                    }
                ''',
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Query @Builder.Result Registry create() { Registry.Create.With() }
                    }
                ''',
        ]
        expected << [
                '@Builder.Input on an instance method requires @Builder.Query or @Builder.Method',
                '@Builder.Result on an instance method requires @Builder.Method',
                '@Builder.Query cannot be combined with @Builder.Result',
        ]
    }

    def "rejects unsupported explicit Builder result shapes"() {
        when:
        createClass source

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Cannot project @Builder.Result')
        error.message.contains(expected)

        where:
        source << [
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Method @Builder.Result List projected() { [] }
                    }
                ''',
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Method @Builder.Result List<? extends Registry> projected() { [] }
                    }
                ''',
                '''
                    @DSL class Registry { }
                    @DSL class Deployment {
                        @Builder.Method @Builder.Result List<List<Registry>> projected() { [] }
                    }
                ''',
                '''
                    @DSL class Deployment {
                        @Builder.Method @Builder.Result String projected() { 'value' }
                    }
                ''',
        ]
        expected << [
                'Collection type is raw',
                'element type is a wildcard',
                'nested Collection/Map positions are not supported',
                'type does not resolve to a DSL Object',
        ]
    }
}
