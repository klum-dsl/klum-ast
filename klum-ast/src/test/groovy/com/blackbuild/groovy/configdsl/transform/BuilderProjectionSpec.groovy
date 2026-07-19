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

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.annodocimal.generator.AnnoDocGenerator
import com.blackbuild.klum.ast.util.DslHelper
import com.blackbuild.klum.ast.util.KlumModelException
import org.codehaus.groovy.control.MultipleCompilationErrorsException

class BuilderProjectionSpec extends AbstractDSLSpec {

    def "declared KlumBuilder generic projects to the concrete public Builder interface"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.util.KlumBuilder

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
            import com.blackbuild.klum.ast.util.KlumBuilder

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
            import com.blackbuild.klum.ast.util.KlumBuilder

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
        AnnoDocGenerator.generate(new File(compilerConfiguration.targetDirectory, 'sample/Root_DSL.class'), mirrorRoot)
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
            import com.blackbuild.klum.ast.util.KlumBuilder
            import com.blackbuild.klum.ast.util.KlumFactory

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
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

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
