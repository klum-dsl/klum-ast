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
import spock.lang.Issue

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.METHOD

@Issue('651')
class BuilderQueryTest extends AbstractDSLSpec {

    def "Builder Query is the canonical runtime query marker"() {
        when:
        Class<?> queryAnnotation = Builder.Query

        then:
        queryAnnotation.name == 'com.blackbuild.klum.ast.Builder$Query'
        queryAnnotation.annotation
        queryAnnotation.getAnnotation(Target).value().toList() == [METHOD]
        queryAnnotation.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        queryAnnotation.getAnnotation(KlumCastValidator).value() ==
                'com.blackbuild.klum.ast.compiler.internal.ast.BuilderQueryCheck'

        when: 'the provisional top-level name is queried'
        Class.forName('com.blackbuild.klum.ast.BuilderQuery')

        then:
        thrown(ClassNotFoundException)
    }

    def "projects a pure scalar query to Builder state and keeps the Model method"() {
        given:
        createClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Deployment {
                Registry registry
                String observedUrl
                String observedFactoryModel
                String observedEndpoint
                String observedNormalizedHost
                List<String> observedLabels

                @PostTree
                void observeRegistry() {
                    observedUrl = this.registry.toUrl()
                    observedFactoryModel = this.registry.factoryModelName()
                    observedEndpoint = this.registry.endpointName()
                    observedNormalizedHost = this.registry.normalizedHost()
                    observedLabels = this.registry.labels()
                }
            }

            @DSL class Registry {
                String host
                boolean secure
                Endpoint endpoint

                @Builder.Query
                String authority() { getHost().with { String value -> value.toLowerCase() } }

                @Builder.Query
                String scheme() { isSecure() ? 'https' : 'http' }

                @Builder.Query
                String toUrl() { "${scheme()}://${authority()}" }

                @Builder.Query
                String factoryModelName() { Registry.Create.getModelType().simpleName }

                @Builder.Query
                String endpointName() { getEndpoint().getName().toLowerCase() }

                @Builder.Query
                String normalizedHost() {
                    String value = host
                    value = value.trim()
                    value.toLowerCase()
                }

                @Builder.Query
                List<String> labels() { [host, scheme()] }
            }

            @DSL class Endpoint { String name }
        '''

        when:
        def deployment = clazz.Create.With {
            registry {
                host 'EXAMPLE.TEST'
                secure true
                endpoint { name 'EDGE' }
            }
        }

        then:
        deployment.observedUrl == 'https://example.test'
        deployment.observedFactoryModel == 'Registry'
        deployment.observedEndpoint == 'edge'
        deployment.observedNormalizedHost == 'example.test'
        deployment.observedLabels == ['EXAMPLE.TEST', 'https']
        deployment.registry.toUrl() == deployment.observedUrl
        hasMethod(getClass('Registry$Builder'), 'toUrl')
        hasMethod(getClass('Registry_DSL$Builder'), 'toUrl')
    }

    def "leaves an ordinary Model query off every Builder surface"() {
        given:
        createClass '''
            @DSL class Registry {
                String host
                String toUrl() { "https://$host" }
            }
        '''

        when:
        def registry = clazz.Create.With(host: 'example.test')

        then:
        hasNoMethod(getClass('Registry$Builder'), 'toUrl')
        hasNoMethod(getClass('Registry_DSL$Builder'), 'toUrl')
        registry.toUrl() == 'https://example.test'
    }

    def "inherits one projected query through the Builder hierarchy"() {
        given:
        createClass '''
            @DSL abstract class Registry {
                String host

                @Builder.Query
                String toUrl() { "https://$host" }
            }

            @DSL class TenantRegistry extends Registry {
                String tenant
            }
        '''

        when: 'a static consumer uses the inherited self-model Builder contract'
        createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class TenantRegistryQueryConsumer {
                static String read(TenantRegistry_DSL.Builder<TenantRegistry> builder) {
                    builder.toUrl()
                }

                static Registry_DSL.Builder<TenantRegistry> widen(
                        TenantRegistry_DSL.Builder<TenantRegistry> builder) {
                    builder
                }
            }
        '''

        then:
        getClass('TenantRegistry_DSL$Builder').methods.count { it.name == 'toUrl' } == 1
        getClass('TenantRegistry').Create.With(host: 'example.test', tenant: 'docs').toUrl() == 'https://example.test'
    }

    def "rejects an invalid Builder query declaration"() {
        when:
        createClass source

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains(expected)

        where:
        source << [
                '''
                    @DSL class Registry {
                        @Builder.Query private String hidden() { 'hidden' }
                    }
                ''',
                '''
                    @DSL class Registry {
                        @Builder.Query void empty() { }
                    }
                ''',
                '''
                    @DSL class Registry {
                        @Builder.Query static String staticQuery() { 'static' }
                    }
                ''',
                '''
                    class Registry {
                        @Builder.Query String misplaced() { 'misplaced' }
                    }
                ''',
                '''
                    @DSL abstract class Registry {
                        @Builder.Query abstract String missing()
                    }
                ''',
                '''
                    @DSL class Registry {
                        @Builder.Query @Builder.Method String mixed() { 'mixed' }
                    }
                ''',
                '''
                    @DSL class Registry {
                        @Builder.Query @Mutator String mixed() { 'mixed' }
                    }
                ''',
                '''
                    @DSL class Registry {
                        @Builder.Query @PostTree String mixed() { 'mixed' }
                    }
                ''',
        ]
        expected << [
                '@Builder.Query methods must be public',
                '@Builder.Query methods must return a value',
                '@Builder.Query methods must be instance methods',
                '@Builder.Query can only be used on methods declared by a DSL Object',
                '@Builder.Query methods must declare an implementation',
                '@Builder.Query and @Builder.Method are mutually exclusive method categories',
                '@Builder.Query and @Builder.Method are mutually exclusive method categories',
                '@Builder.Query cannot be combined with a lifecycle annotation',
        ]
    }

    def "rejects mutation and construction-only state in a Builder query"() {
        when:
        createClass source

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains(expected)

        where:
        source << [
                '''
                    @DSL class Registry {
                        String host
                        @Builder.Query String normalize() { host = host.trim() }
                    }
                ''',
                '''
                    @DSL class Registry {
                        String host
                        @Mutator void normalize() { host = host.trim() }
                        @Builder.Query String normalized() { normalize(); host }
                    }
                ''',
                '''
                    @DSL class Registry {
                        @Field(FieldType.BUILDER) String scratch
                        @Builder.Query String currentScratch() { this.scratch }
                    }
                ''',
                '''
                    @DSL class Child { String name }
                    @DSL class Registry {
                        @Builder.Query String childName() { Child.Create.FromMap(name: 'new').name }
                    }
                ''',
                '''
                    @DSL class Child {
                        @Mutator void rename() { }
                    }
                    @DSL class Registry {
                        Child child
                        @Builder.Query String childName() { this.child.rename(); 'renamed' }
                    }
                ''',
                '''
                    @DSL class Child {
                        @Mutator void rename() { }
                    }
                    @DSL class Registry {
                        @Builder.Query String childName(Child child) {
                            Child current = child
                            current.rename()
                            'renamed'
                        }
                    }
                ''',
                '''
                    @DSL class Child {
                        @Builder.Query String label(String value) { value }
                        @Mutator void label(Integer value) { }
                    }
                    @DSL class Registry {
                        @Builder.Query String childLabel(Child child, Integer value) {
                            child.label(value)
                            'labelled'
                        }
                    }
                ''',
                '''
                    @DSL class Registry {
                        int count
                        @Builder.Query int increment() { this.count++ }
                    }
                ''',
                '''
                    @DSL class Registry {
                        int count
                        @Builder.Query int increment() { ++this.count }
                    }
                ''',
                '''
                    @DSL class Registry {
                        List<String> names
                        @Builder.Query String replaceFirst() { names[0] = 'changed' }
                    }
                ''',
                '''
                    @DSL class Registry {
                        @Field(FieldType.BUILDER) boolean scratch
                        @Builder.Query boolean currentScratch() { isScratch() }
                    }
                ''',
                '''
                    @DSL class Child {
                        static String describe() { 'child' }
                    }
                    @DSL class Registry {
                        @Builder.Query String childDescription() { Child.describe() }
                    }
                ''',
                '''
                    @DSL class Child { String name }
                    @DSL class Registry {
                        @Builder.Query String childName() { Child.Create.Template.With(name: 'new').name }
                    }
                ''',
                '''
                    @DSL class Child {
                        @Builder.Query String label(Map<String, ?> value) { value.key }
                        @Mutator void label(Closure<?> value) { }
                    }
                    @DSL class Registry {
                        @Builder.Query String childLabel(Child child) {
                            child.label { 'value' }
                            'labelled'
                        }
                    }
                ''',
        ]
        expected << [
                '@Builder.Query normalize() must not assign DSL Object fields',
                '@Builder.Query normalized() must not call construction-time method normalize()',
                "@Builder.Query currentScratch() must not read construction-only field 'scratch'",
                '@Builder.Query childName() must not start or attach construction',
                '@Builder.Query childName() must not call construction-time method rename()',
                '@Builder.Query childName() must not call construction-time method rename()',
                '@Builder.Query childLabel() must not call construction-time method label()',
                '@Builder.Query increment() must not assign DSL Object fields',
                '@Builder.Query increment() must not assign DSL Object fields',
                '@Builder.Query replaceFirst() must not assign DSL Object fields',
                "@Builder.Query currentScratch() must not read construction-only field 'scratch'",
                "@Builder.Query childDescription() cannot call unprojected DSL method 'Child.describe()'",
                '@Builder.Query childName() must not start or attach construction',
                '@Builder.Query childLabel() must not call construction-time method label()',
        ]
    }

    def "rejects nested access through builder-only state"() {
        when:
        createClass '''
            @DSL class Child { String name }
            @DSL class Registry {
                @Field(FieldType.BUILDER) Child child
                @Builder.Query String childName() { this.child.name }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("@Builder.Query childName() must not read construction-only field 'child'")
    }

    def "rejects generic builder-only state"() {
        when:
        createClass '''
            @DSL class Child { String name }
            @DSL class Registry {
                @Field(FieldType.BUILDER) List<Child> children
                @Builder.Query List<String> childNames() { children.name }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("@Builder.Query childNames() must not read construction-only field 'children'")
    }

    def "rejects an ambiguous overloaded DSL call in a Builder query"() {
        when:
        createClass '''
            @DSL class Child {
                @Builder.Query String label(String value) { value }
                @Builder.Query String label(Integer value) { "$value" }
            }

            @DSL class Registry {
                @Builder.Query String label(Child child, Object value) { child.label(value) }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains(
                "@Builder.Query label() cannot resolve overloaded DSL method 'Child.label()' unambiguously")
    }

    def "rejects DSL Object and Builder-bearing query results"() {
        when:
        createClass source

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('@Builder.Query result must not contain a DSL Object or Builder type')

        where:
        source << [
                '''
                    @DSL class Child { }
                    @DSL class Parent {
                        Child child
                        @Builder.Query Child selected() { child }
                    }
                ''',
                '''
                    @DSL class Child { }
                    @DSL class Parent {
                        List<Child> children
                        @Builder.Query List<Child> selected() { children }
                    }
                ''',
                '''
                    @DSL class Child { }
                    @DSL class Parent {
                        @Builder.Query Child[] selected() { [] as Child[] }
                    }
                ''',
                '''
                    @DSL class Child { }
                    @DSL class Parent {
                        @Builder.Query List<? extends Child> selected() { [] }
                    }
                ''',
                '''
                    @DSL class Child { }
                    @DSL class Parent {
                        @Builder.Query List<? super Child> selected() { [] }
                    }
                ''',
        ]
    }

    def "selects a projected overload from a map argument"() {
        given:
        createClass '''
            @DSL class Child {
                @Builder.Query String label(Map<String, ?> value) { value.key }
                @Mutator void label(Closure<?> value) { }
            }

            @DSL class Registry {
                Child child
                @Builder.Query String childLabel() { child.label(key: 'mapped') }
            }
        '''

        expect:
        hasMethod(getClass('Registry_DSL$Builder'), 'childLabel')
    }

    def "rejects calls to an opaque unprojected DSL query"() {
        given: 'a separately compiled DSL Object without an emitted query projection'
        createClass '''
            package external
            @DSL class Registry {
                String host
                String toUrl() { "https://$host" }
            }
        '''

        when:
        createSecondaryClass '''
            import external.Registry

            @DSL class Deployment extends Registry {
                @Builder.Query
                String registryUrl() { toUrl() }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("@Builder.Query registryUrl() cannot call unprojected DSL method 'Registry.toUrl()'")
        error.message.contains('annotate the source query with @Builder.Query and recompile that DSL Object')
    }

    def "does not mistake a custom getter for a generated field read"() {
        when:
        createClass '''
            @DSL class Registry {
                String host

                String getHost() { host.toLowerCase() }

                @Builder.Query
                String authority() { getHost() }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains(
                "@Builder.Query authority() cannot call unprojected DSL method 'Registry.getHost()'")
    }

    def "rejects a projected signature collision with an existing Builder operation"() {
        when:
        createClass '''
            @DSL class Registry {
                String host

                @Builder.Query
                String setHost(String value) { value }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('@Builder.Query setHost(java.lang.String) collides with an existing Builder method')
    }
}
