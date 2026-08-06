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

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.annodocimal.generator.ProjectionPolicy
import com.blackbuild.annodocimal.generator.SourceProjector
import com.blackbuild.klum.ast.runtime.internal.DslHelper
import com.blackbuild.klum.ast.runtime.KlumModelException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

class BuilderProjectionSpec extends AbstractDSLSpec {

    @Issue("662")
    def "qualified static recursive converters project scalar relationship overloads"() {
        given:
        createClass '''
            import java.net.URL

            @DSL class Root {
                Registry registry
            }

            @DSL class Registry {
                String source

                static Registry fromString(String value) {
                    return Registry.fromStrings(value)
                }

                static Registry fromStrings(String value) {
                    return Registry.Create.With(source: "string:${value}")
                }

                static Registry fromString(URL value) {
                    return Registry.fromStrings(value)
                }

                static Registry fromStrings(URL value) {
                    return Registry.Create.With(source: "url:${value}")
                }
            }
        '''

        when:
        instance = clazz.Create.With {
            registry 'one'
        }

        then:
        instance.registry.source == 'string:one'

        when:
        instance = clazz.Create.With {
            registry new URL('https://example.test/two')
        }

        then:
        instance.registry.source == 'url:https://example.test/two'

        and: 'direct root calls retain their completed-model contract'
        getClass('Registry').fromString('root').source == 'string:root'
    }

    @Issue("662")
    def "nested qualified converters preserve keyed single collection and map relationships"() {
        given:
        createClass '''
            import java.net.URI

            @DSL class Root {
                Storage primary
                List<Storage> secondaryStorages
                Map<String, Storage> storages
            }

            @DSL class Registry {
                URI uri

                static Registry fromString(String value) {
                    return Registry.Create.With(uri: new URI(value))
                }
            }

            @DSL class Storage {
                @Key String name
                Registry source
                Registry target

                static Storage fromStrings(String name, String source, String target) {
                    return Storage.Create.With(
                            name,
                            source: Registry.fromString(source),
                            target: Registry.fromString(target)
                    )
                }
            }
        '''

        when:
        instance = clazz.Create.With {
            primary 'primary', 'uri://primary/source', 'uri://primary/target'
            secondaryStorage 'secondary', 'uri://secondary/source', 'uri://secondary/target'
            storage 'named', 'uri://named/source', 'uri://named/target'
        }

        then:
        instance.primary.name == 'primary'
        instance.primary.source.uri == new URI('uri://primary/source')
        instance.secondaryStorages*.name == ['secondary']
        instance.secondaryStorages*.target.uri == [new URI('uri://secondary/target')]
        instance.storages.keySet() == ['named'] as Set
        instance.storages['named'].name == 'named'
        instance.storages['named'].source.uri == new URI('uri://named/source')
    }

    @Issue("662")
    def "qualified static converters project into relocated Builder methods"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.layer3.AutoCreate

            @DSL class Root {
                Registry docs
                Registry defaults
                Registry mutations

                @AutoCreate
                void createDocs() {
                    docs Registry.fromString('auto')
                }

                @Default
                void createDefaults() {
                    defaults Registry.fromString('default')
                }

                @Mutator
                void setMutation(String value) {
                    mutations Registry.fromString(value)
                }
            }

            @DSL class Registry {
                String source

                static Registry fromString(String value) {
                    return Registry.fromStrings(value)
                }

                static Registry fromStrings(String value) {
                    return Registry.Create.With(source: value)
                }
            }
        '''

        when:
        instance = clazz.Create.With {
            setMutation 'mutator'
        }

        then:
        instance.docs.source == 'auto'
        instance.defaults.source == 'default'
        instance.mutations.source == 'mutator'
    }

    @Issue("662")
    def "qualified static converters project through Builder method control flow"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.layer3.AutoCreate

            @DSL class Root {
                Registry docs
                Registry defaults

                @AutoCreate
                void createDocs() {
                    try {
                        docs Registry.fromString('try')
                    } catch (RuntimeException ignored) {
                        docs Registry.fromString('catch')
                    }
                }

                @Default
                void createDefaults() {
                    while (!defaults) {
                        defaults Registry.fromString('while')
                    }
                }
            }

            @DSL class Registry {
                String source

                static Registry fromString(String value) {
                    return Registry.Create.With(source: value)
                }
            }
        '''

        when:
        instance = clazz.Create.With()

        then:
        instance.docs.source == 'try'
        instance.defaults.source == 'while'
    }

    @Issue("642")
    def "unqualified static recursive converters project relationship overloads"() {
        given:
        createClass '''
            import java.io.File
            import java.net.URL

            @DSL class Root {
                Customer singleCustomer
                List<Customer> listCustomers
                Map<String, Customer> mapCustomers
            }

            @DSL class Customer {
                @Key String name
                String source

                static Customer fromFile(File file) {
                    return fromYaml(file)
                }

                static Customer fromUrl(URL source) {
                    return fromYaml(source)
                }

                static Customer fromYaml(URL source) {
                    return Customer.Create.With(source.file, source: "url:${source.file}")
                }

                static Customer fromYaml(File file) {
                    return Customer.Create.With(file.name, source: file.name)
                }
            }
        '''

        expect: 'the public Builder surface has the same source-visible converter on every relationship shape'
        getClass('Root_DSL$Builder').getMethod('singleCustomer', File).returnType == getClass('Customer_DSL$Builder')
        getClass('Root_DSL$Builder').getMethod('singleCustomer', URL).returnType == getClass('Customer_DSL$Builder')
        getClass('Root_DSL$Builder').getMethod('listCustomer', File).returnType == getClass('Customer_DSL$Builder')
        getClass('Root_DSL$Builder').getMethod('mapCustomer', File).returnType == getClass('Customer_DSL$Builder')

        and: 'the IDE mirror exposes the projected overloads without synthetic twins'
        File mirrorRoot = new File(tempFolder.root, 'mirrors')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(
                new File(compilerConfiguration.targetDirectory, 'Root_DSL.class').toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'Root_DSL.java').text
        mirror.contains('singleCustomer(File file)')
        mirror.contains('singleCustomer(URL source)')
        mirror.contains('listCustomer(File file)')
        mirror.contains('mapCustomer(File file)')
        !mirror.contains('$klum$asBuilder$')

        when:
        instance = clazz.Create.With {
            singleCustomer new File('single.yaml')
            listCustomer new File('list.yaml')
            mapCustomer new File('map.yaml')
        }

        then:
        instance.singleCustomer.source == 'single.yaml'
        instance.listCustomers*.source == ['list.yaml']
        instance.mapCustomers['map.yaml'].source == 'map.yaml'

        when: 'the later overload is selected for another recursive static call'
        instance = clazz.Create.With {
            singleCustomer new URL('file:/url.yaml')
        }

        then:
        instance.singleCustomer.source == 'url:/url.yaml'
    }

    def "declared KlumBuilder generic projects to the concrete public Builder interface"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumBuilder

            @DSL class Root {
                Child child
            }

            @DSL class Child {
                String value

                static KlumBuilder<Child> fromString(String value) {
                    return (KlumBuilder<Child>) Child.Create.AsBuilder.With(value: value)
                }
            }
        '''

        expect:
        rwClazz.getMethod('child', String).returnType == getClass('Child_DSL$Builder')
        getClass('Root_DSL$Builder').getMethod('child', String).returnType == getClass('Child_DSL$Builder')

        when:
        instance = clazz.Create.With { child 'projected' }

        then:
        instance.child.value == 'projected'
    }

    def "raw KlumBuilder element type produces a targeted compilation diagnostic"() {
        when:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumBuilder

            @DSL class Root {
                Child child
            }

            @DSL class Child {
                static KlumBuilder fromString(String value) {
                    return null
                }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Cannot project Builder-producing method')
        error.message.contains('KlumBuilder return type is raw')
        error.message.contains('Declare a concrete KlumBuilder<Foo> element type')
    }

    def "wildcard Builder container values produce a targeted compilation diagnostic"() {
        when:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumBuilder

            @DSL class Root {
                List<Child> children
            }

            @DSL class Child {
                static Collection<? extends KlumBuilder<Child>> fromValues(List<String> values) {
                    return null
                }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Cannot project Builder-producing method')
        error.message.contains('container Builder element type is a wildcard')
        error.message.contains('Declare a concrete KlumBuilder<Foo> element type')
    }

    def "the generated projection namespace is reserved for KlumAST"() {
        when:
        createClass '''
            @DSL class Root {
                void $klum$asBuilder$mine() {}
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('The \'$klum$\' namespace is reserved for generated KlumAST members')
        error.message.contains('$klum$asBuilder$mine')
    }

    def "bytecode mirror and AnnoDoc expose only the truthful composition contract"() {
        given:
        createClass '''
            package sample

            @DSL class Root {
                Child child
            }

            @DSL class Child {
                String value

                /**
                 * Materializes a standalone Child.
                 * @param value the value to configure
                 * @return an independently materialized Child
                 * @throws IllegalArgumentException when value is empty
                 */
                static Child fromString(String value) {
                    if (!value) throw new IllegalArgumentException('empty')
                    return Child.Create.With(value: value)
                }
            }
        '''

        when:
        def apiMethod = getClass('sample.Root_DSL$Builder').getMethod('child', String)
        def hiddenTwin = getClass('sample.Child').declaredMethods.find { it.name == '$klum$asBuilder$fromString' }
        File mirrorRoot = new File(tempFolder.root, 'mirrors')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(
                new File(compilerConfiguration.targetDirectory, 'sample/Root_DSL.class').toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'sample/Root_DSL.java').text

        then:
        apiMethod.returnType == getClass('sample.Child_DSL$Builder')
        apiMethod.getAnnotation(AnnoDoc).value().contains('unsealed Builder in the active construction session')
        apiMethod.getAnnotation(AnnoDoc).value().contains('attaches it to this relationship')
        apiMethod.getAnnotation(AnnoDoc).value().contains('cannot be independently materialized or validated')
        apiMethod.getAnnotation(AnnoDoc).value().contains('@param value the value to configure')
        apiMethod.getAnnotation(AnnoDoc).value().contains('@throws IllegalArgumentException when value is empty')
        apiMethod.getAnnotation(AnnoDoc).value().contains('@return the attached, unsealed Builder')
        apiMethod.getAnnotation(AnnoDoc).value().contains('Child#fromString')

        and:
        hiddenTwin != null
        hiddenTwin.synthetic
        java.lang.reflect.Modifier.isPublic(hiddenTwin.modifiers)
        hiddenTwin.getAnnotation(AnnoDoc) == null
        !getClass('sample.Child_DSL$Builder').declaredMethods*.name.any { it.startsWith('$klum$') }

        and:
        mirror.contains('Child_DSL.Builder<Child> child(String value)')
        mirror.contains('unsealed Builder in the active construction session')
        mirror.contains('@param value the value to configure')
        !mirror.contains('$klum$asBuilder$')
    }

    def "Collection and Map KlumBuilder values retain their declared outer types and map keys"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumBuilder
            import com.blackbuild.klum.ast.runtime.KlumFactory

            @DSL class Root {
                List<Child> children
                Map<String, Child> named
            }

            @DSL class Child {
                @Key String name
                @Owner Root owner
                String value

                static class Factory extends KlumFactory.Keyed<Child> {
                    protected Factory() { super(Child) }

                    LinkedList<KlumBuilder<Child>> fromValues(List<String> values) {
                        return new LinkedList<>(values.collect { value ->
                            (KlumBuilder<Child>) (Object) AsBuilder.With(value, value: value.toUpperCase())
                        })
                    }

                    TreeMap<String, KlumBuilder<Child>> fromNamed(Map<String, String> values) {
                        TreeMap<String, KlumBuilder<Child>> result = new TreeMap<>(Comparator.reverseOrder())
                        values.each { key, value ->
                            result[key] = (KlumBuilder<Child>) (Object) AsBuilder.With(key, value: value)
                        }
                        return result
                    }
                }
            }
        '''

        when:
        def projectedList
        def projectedMap
        instance = clazz.Create.With {
            children { projectedList = fromValues(['a', 'b']) }
            named { projectedMap = fromNamed([a: 'A', b: 'B']) }
        }
        def childPaths = instance.children.collect(DslHelper.&getBreadcrumbPath)

        then:
        rwClazz.getMethod('getChildren').returnType == List
        projectedList instanceof LinkedList
        instance.children*.value == ['A', 'B']
        instance.children.every { it.owner.is(instance) }
        childPaths.every { it.startsWith('$/Root.With/') }
        childPaths.any { it.contains('/children/fromValues') }

        and:
        projectedMap instanceof TreeMap
        projectedMap.keySet().toList() == ['b', 'a']
        projectedMap.comparator() != null
        instance.named.keySet().toList() == ['b', 'a']
        instance.named.a.value == 'A'
        instance.named.b.value == 'B'
        instance.named.values().every { it.owner.is(instance) }

        and: 'container projections document their exact return and key behavior'
        getClass('Root_DSL$Builder$CollectionFactory_children')
                .getMethod('fromValues', List)
                .getAnnotation(AnnoDoc).value().contains("returns the producer's original container")
        getClass('Root_DSL$Builder$CollectionFactory_named')
                .getMethod('fromNamed', Map)
                .getAnnotation(AnnoDoc).value().contains('original map keys')
    }

    def "Cluster delegates retain Builder-producing converter composition"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.layer3.Cluster

            @DSL class Root {
                Child first
                Child second
                @Cluster Map<String, Child> children
            }

            @DSL class Child {
                @Owner Root owner
                String value

                static Child fromString(String value) {
                    return Child.Create.With(value: value)
                }
            }
        '''

        when:
        instance = clazz.Create.With {
            children {
                first 'one'
                second 'two'
            }
        }

        then:
        instance.first.value == 'one'
        instance.second.value == 'two'
        instance.children == [first: instance.first, second: instance.second]
        instance.children.values().every { it.owner.is(instance) }
    }

    def "opaque source producer is omitted and a matching dynamic call gets migration guidance"() {
        given:
        createClass '''
            @DSL class Root {
                Child child
            }

            @DSL class Child {
                String value

                static Child fromString(String value) {
                    return materialize(value)
                }

                private static Child materialize(String value) {
                    return Child.Create.With(value: value)
                }
            }
        '''

        expect: 'the static and IDE-visible surfaces do not advertise the opaque projection'
        !rwClazz.methods.any { it.name == 'child' && it.parameterTypes.toList() == [String] }
        !getClass('Root_DSL$Builder').methods.any { it.name == 'child' && it.parameterTypes.toList() == [String] }

        and: 'the unchanged direct root producer still materializes normally'
        getClass('Child').fromString('root').value == 'root'

        when:
        clazz.Create.With { child 'nested' }

        then:
        def error = thrown(KlumModelException)
        error.message.contains('omitted Builder-producing projection child(java.lang.String)')
        error.message.contains('active-session Create.AsBuilder')
    }

    @Issue("662")
    def "qualified opaque converter calls in Builder methods retain the root factory rejection"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.layer3.AutoCreate

            @DSL class Root {
                Child child

                @AutoCreate
                void createChild() {
                    child Child.fromString('nested')
                }
            }

            @DSL class Child {
                String value

                static Child fromString(String value) {
                    return materialize(value)
                }

                private static Child materialize(String value) {
                    return Child.Create.With(value: value)
                }
            }
        '''

        expect: 'the opaque source converter remains a completed-model factory at the root'
        getClass('Child').fromString('root').value == 'root'

        when:
        clazz.Create.With()

        then: 'the unavailable twin cannot start an independent model factory during Builder execution'
        def error = thrown(RuntimeException)
        error.message.contains('Cannot start an independent DSL Object factory while a Builder lifecycle is active')
    }

    @Issue("662")
    def "qualified precompiled converter calls are omitted from Builder projection"() {
        given: 'the converter type is already compiled and has no active-session AST twin'
        createSecondaryClass '''
            package external

            class Converters {
                static Object fromString(String value) {
                    return Class.forName('Child').Create.With(value: value)
                }
            }
        '''

        createClass '''
            import external.Converters

            @DSL class Root {
                Child child
            }

            @DSL class Child {
                String value

                static Child fromString(String value) {
                    return (Child) Converters.fromString(value)
                }
            }
        '''

        expect: 'the precompiled converter remains an ordinary completed-model factory'
        getClass('Child').fromString('root').value == 'root'

        and: 'the relationship surface does not advertise an unavailable Builder-producing twin'
        !rwClazz.methods.any { it.name == 'child' && it.parameterTypes.toList() == [String] }

        when:
        clazz.Create.With { child 'nested' }

        then: 'the dynamic relationship call retains the established projection guidance'
        def error = thrown(KlumModelException)
        error.message.contains('omitted Builder-producing projection child(java.lang.String)')
        error.message.contains('active-session Create.AsBuilder')
    }

    def "unrelated unknown dynamic name remains an ordinary MissingMethodException"() {
        given:
        createClass '''
            @DSL class Root {
                Child child
            }

            @DSL class Child {
                static Child fromString(String value) {
                    return materialize(value)
                }

                private static Child materialize(String value) {
                    return Child.Create.With()
                }
            }
        '''

        when:
        clazz.Create.With { unrelated 'value' }

        then:
        def error = thrown(MissingMethodException)
        error.method == 'unrelated'
    }
}
