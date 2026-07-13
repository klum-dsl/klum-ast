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

import com.blackbuild.klum.ast.util.KlumBuilder
import com.blackbuild.klum.ast.util.KlumModelException
import org.codehaus.groovy.control.MultipleCompilationErrorsException

import java.lang.reflect.Modifier

class BuilderFirstSpec extends AbstractDSLSpec {

    def "factory configures a Builder and returns a completed model"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                static int initializerCalls

                String value = initializeValue()

                @Field(FieldType.BUILDER)
                String scratch = "builder-only"

                private static String initializeValue() {
                    initializerCalls++
                    return "initialized"
                }

                @PostTree
                void finishValue() {
                    value = "$value:$scratch"
                }
            }
        '''

        when:
        instance = clazz.Create.With {
            assert delegate instanceof KlumBuilder
            value "configured"
        }

        then:
        rwClazz.superclass == KlumBuilder
        instance.value == "configured:builder-only"
        clazz.initializerCalls == 1
        clazz.declaredFields*.name.contains("scratch") == false
        clazz.methods*.name.contains("apply") == false
    }

    def "materialization resolves self and cyclic Builder relationships"() {
        given:
        createClass '''
            package pk

            @DSL
            class Node {
                String name
                Node child

                @Field(FieldType.LINK)
                Node peer
            }
        '''

        when:
        def rootBuilder
        def childBuilder
        instance = clazz.Create.With {
            rootBuilder = delegate
            name "root"
            peer = rootBuilder
            child {
                childBuilder = delegate
                name "child"
                peer = rootBuilder
            }
            rootBuilder.peer = rootBuilder
            childBuilder.child = rootBuilder
        }

        then:
        instance.peer.is(instance)
        instance.child.peer.is(instance)
        instance.child.child.is(instance)
        rootBuilder.completedModel.is(instance)
        childBuilder.completedModel.is(instance.child)
    }

    def "materialization publishes independent read only collection snapshots"() {
        given:
        createClass '''
            package pk

            @DSL
            class CollectionsModel {
                List<String> list
                Set<String> set
                SortedSet<String> sortedSet = new TreeSet<>(Comparator.reverseOrder())
                NavigableSet<String> navigableSet = new TreeSet<>(Comparator.reverseOrder())
                Map<String, String> map
                SortedMap<String, String> sortedMap = new TreeMap<>(Comparator.reverseOrder())
                NavigableMap<String, String> navigableMap = new TreeMap<>(Comparator.reverseOrder())
                EnumSet<Tone> tones = EnumSet.of(Tone.LIGHT)
            }

            enum Tone { LIGHT, DARK }
        '''

        when:
        def builder
        instance = clazz.Create.With {
            builder = delegate
            delegate.list.addAll(["first", "second"])
            delegate.set.addAll(["first", "second"])
            delegate.sortedSet.addAll(["first", "second"])
            delegate.navigableSet.addAll(["first", "second"])
            delegate.map.putAll(first: "one", second: "two")
            delegate.sortedMap.putAll(first: "one", second: "two")
            delegate.navigableMap.putAll(first: "one", second: "two")
        }

        then:
        instance.list == ["first", "second"]
        instance.set.toList() == ["first", "second"]
        instance.sortedSet.toList() == ["second", "first"]
        instance.navigableSet.toList() == ["second", "first"]
        instance.map.keySet().toList() == ["first", "second"]
        instance.sortedMap.keySet().toList() == ["second", "first"]
        instance.navigableMap.keySet().toList() == ["second", "first"]
        instance.sortedSet.comparator().compare("first", "second") > 0
        instance.sortedMap.comparator().compare("first", "second") > 0

        and: "the snapshot no longer shares storage with its Builder"
        builder.list.add("builder-only")
        builder.map.put("builder-only", "value") == null
        instance.list == ["first", "second"]
        !instance.map.containsKey("builder-only")

        when: "a published collection is mutated"
        instance.list.add("illegal")

        then:
        thrown(UnsupportedOperationException)

        when: "an EnumSet getter result is mutated"
        def enumSetCopy = instance.tones
        enumSetCopy.add(getClass("Tone").DARK)

        then: "the model keeps its defensive value"
        instance.tones == EnumSet.of(getClass("Tone").LIGHT)

        and:
        ["list", "set", "sortedSet", "navigableSet", "map", "sortedMap", "navigableMap", "tones"].every {
            Modifier.isFinal(clazz.getDeclaredField(it).modifiers)
        }
    }

    def "unsupported concrete collection declarations fail schema compilation"() {
        when:
        createClass '''
            package pk

            @DSL
            class UnsupportedCollections {
                ArrayList<String> values = []
            }
        '''

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains("Unsupported collection declaration 'java.util.ArrayList'")
        error.message.contains("Use List, Set, SortedSet/NavigableSet, Map, SortedMap/NavigableMap, or EnumSet")
    }

    def "generated Builders preserve DSL inheritance across compilation units"() {
        given:
        createClass '''
            package pk

            @DSL
            class Parent {
                static int initializerCalls
                String parentValue = initializeParent()

                private static String initializeParent() {
                    initializerCalls++
                    return "parent"
                }

                @PostTree
                void finishParent() {
                    parentValue += ":finished-parent"
                }
            }
        '''
        def parentClass = clazz
        def parentBuilderClass = rwClazz

        and:
        def childClass = createSecondaryClass '''
            package pk

            @DSL
            class Child extends Parent {
                static int initializerCalls
                String childValue = initializeChild()

                private static String initializeChild() {
                    initializerCalls++
                    return "child"
                }

                @PostTree
                void finishChild() {
                    childValue += ":finished-child"
                }
            }
        '''

        when:
        instance = childClass.Create.With {
            parentValue "configured-parent"
            childValue "configured-child"
        }

        then:
        getRwClass(childClass.name).superclass == parentBuilderClass
        instance.parentValue == "configured-parent:finished-parent"
        instance.childValue == "configured-child:finished-child"
        parentClass.initializerCalls == 1
        childClass.initializerCalls == 1
        Modifier.isFinal(parentClass.getDeclaredField("parentValue").modifiers)
        Modifier.isFinal(childClass.getDeclaredField("childValue").modifiers)
    }

    def "completed models are sealed aggregation LINK targets but not composition inputs"() {
        given:
        createClass '''
            package pk

            @DSL
            class Graph {
                Node child

                @Field(members = "node")
                List<Node> nodes

                @Field(FieldType.LINK)
                Node external

                @Field(value = FieldType.LINK, members = "externalNode")
                List<Node> externalNodes
            }

            @DSL
            class Node {
                static int postTreeCalls
                String name

                @PostTree
                void countLifecycle() {
                    postTreeCalls++
                }
            }
        '''
        def nodeClass = getClass("Node")
        def externalModel = nodeClass.Create.With { name "external" }

        when:
        def linkWrapper
        instance = clazz.Create.With {
            child { name "child" }
            node { name "list-child" }
            external externalModel
            externalNode externalModel
            linkWrapper = delegate.external
        }

        then:
        instance.child.name == "child"
        instance.nodes*.name == ["list-child"]
        instance.external.is(externalModel)
        instance.externalNodes[0].is(externalModel)
        linkWrapper.sealed
        linkWrapper.completedModel.is(externalModel)
        nodeClass.postTreeCalls == 3

        when: "a completed model is supplied to a composition field"
        clazz.Create.With {
            delegate.child(externalModel)
        }

        then:
        KlumModelException error = thrown()
        error.message.contains("only supported for LINK relationships")
    }
}
