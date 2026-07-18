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

import com.blackbuild.klum.ast.util.InternalKlumBuilder
import com.blackbuild.klum.ast.util.KlumBuilder
import com.blackbuild.klum.ast.util.FactoryHelper
import com.blackbuild.klum.ast.util.KlumModelException
import com.blackbuild.klum.ast.util.KlumObjectSupport
import com.blackbuild.klum.ast.validation.Validator
import org.codehaus.groovy.control.MultipleCompilationErrorsException

import java.lang.reflect.Modifier

class BuilderFirstSpec extends AbstractDSLSpec {

    private enum BuilderHolder {
        INSTANCE

        Object value
    }

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
        rwClazz.superclass == InternalKlumBuilder
        instance.value == "configured:builder-only"
        clazz.initializerCalls == 1
        clazz.declaredFields*.name.contains("scratch") == false
        clazz.methods*.name.contains("apply") == false
        clazz.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    }

    def "Builders expose only the public zero-operation capability"() {
        given:
        createClass '''
            package pk

            @DSL
            class CompatibilitySurface {
                String value
            }
        '''

        expect: "the generated public Builder contract carries the public capability"
        KlumBuilder.isAssignableFrom(rwClazz)
        getClass('pk.CompatibilitySurface_DSL$Builder').interfaces.contains(KlumBuilder)
        KlumBuilder.declaredMethods.length == 0

        and: "Builders do not carry legacy RW identity aliases"
        !rwClazz.methods*.name.contains("getDSLInstance")
        !rwClazz.methods*.name.contains("getRwInstance")

        and: "runtime operations stay on the internal support base"
        !InternalKlumBuilder.declaredMethods*.name.contains("invokeRwMethod")
        !Modifier.isPublic(InternalKlumBuilder.getDeclaredMethod("invokeBuilderMethod", String, Object[]).modifiers)
    }

    def "completed models reject source mutation and construction entrypoints"() {
        when: "the schema declares an apply method"
        createClass '''
            package pk

            @DSL
            class ApplyingModel {
                void apply(Closure body) {}
            }
        '''

        then:
        MultipleCompilationErrorsException applyError = thrown()
        applyError.message.contains("DSL Objects cannot declare apply methods")

        when: "the schema declares a client constructor"
        createClass '''
            package pk

            @DSL
            class ConstructedModel {
                ConstructedModel(String value) {}
            }
        '''

        then:
        MultipleCompilationErrorsException constructorError = thrown()
        constructorError.message.contains("DSL Objects cannot declare constructors")
    }

    def "Builder allocation and graph materialization are not public construction entrypoints"() {
        given:
        createClass '''
            package pk

            @DSL
            class FactoryOnlyModel {
                String value
            }
        '''

        expect:
        rwClazz.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
        !Modifier.isPublic(InternalKlumBuilder.getDeclaredMethod("materializeGraph", InternalKlumBuilder).modifiers)

        when: "same-package or subclass code tries to invoke the internal model constructor"
        def builder = FactoryHelper.createBuilder(clazz, null)
        def constructor = clazz.getDeclaredConstructor(rwClazz, InternalKlumBuilder.MaterializationToken)
        constructor.accessible = true
        constructor.newInstance(builder, null)

        then:
        def failure = thrown(ReflectiveOperationException)
        failure.cause instanceof KlumModelException
        failure.cause.message.contains("internal materialization")
    }

    def "completed models expose no generated relationship assignment method"() {
        given:
        createClass '''
            package pk

            @DSL
            class Parent {
                Child child
            }

            @DSL
            class Child {
                String name
            }
        '''

        when:
        instance = clazz.Create.With {
            child { name "child" }
        }

        then:
        instance.child.name == "child"
        clazz.declaredMethods*.name.every { !it.startsWith('$klum$assignRelationship$') }
    }

    def "completed Owner inputs cannot be introduced as aggregation targets"() {
        given:
        createClass '''
            package pk

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child {
                @Owner Parent parent
            }
        '''
        def Parent = clazz
        def Child = getClass("pk.Child")
        def completedParent = Parent.Create.With { name "completed" }

        when:
        Child.Create.With {
            ((InternalKlumBuilder) delegate).setInstanceAttribute("parent", completedParent)
        }

        then:
        KlumModelException error = thrown()
        error.message.contains("only supported for LINK relationships")
    }

    def "ordinary Model state contains no deferred actions"() {
        given:
        createClass '''
            package pk

            @DSL
            class Recipe {
                String name
                String result
            }
        '''

        when:
        instance = clazz.Create.With {
            name "model"
            applyLater {
                result name.toUpperCase()
            }
        }

        then:
        instance.result == "MODEL"
        KlumObjectSupport.of(instance).object.is(instance)
        !Class.forName('com.blackbuild.klum.ast.util.KlumModelProxy').declaredFields*.name.contains("applyLaterClosures")
        !Class.forName('com.blackbuild.klum.ast.util.KlumModelProxy').declaredMethods*.name.contains("getApplyLaterClosures")
        !InternalKlumBuilder.declaredClasses.find { it.simpleName == 'ModelState' }.declaredFields*.name.contains("applyLaterClosures")
        !InternalKlumBuilder.declaredClasses.find { it.simpleName == 'ModelState' }.declaredMethods*.name.contains("getApplyLaterClosures")
    }

    def "Template recipe actions replay into fresh Builders"() {
        given:
        createClass '''
            package pk

            @DSL
            class Recipe {
                String name
                String result
            }
        '''

        when:
        def suffix = "!"
        def template = clazz.Template.Create {
            applyLater {
                result name.toUpperCase() + suffix
            }
        }

        and: "the detached recipe is copied into a fresh Builder"
        instance = clazz.Template.With(template) {
            clazz.Create.With { name "fresh" }
        }

        then:
        instance.result == "FRESH!"
    }

    def "template recipes reject applyLater closures that capture a Builder"() {
        given:
        createClass '''
            package pk

            @DSL
            class CapturingRecipe {
                String value
            }
        '''

        when:
        clazz.Create.Template {
            def capturedBuilder = delegate
            applyLater {
                value capturedBuilder.modelType.simpleName
            }
        }

        then:
        KlumModelException error = thrown()
        error.message.contains("must not capture a Builder")
        error.message.contains("delegate")
    }

    def "template recipes reject Builders hidden in serializable closure captures"() {
        given:
        createClass '''
            package pk

            @DSL
            class IndirectCapturingRecipe {
                String value
            }
        '''

        when:
        clazz.Create.Template {
            def capturedBuilder = new java.util.concurrent.atomic.AtomicReference(delegate)
            applyLater {
                value capturedBuilder.get().modelType.simpleName
            }
        }

        then:
        KlumModelException error = thrown()
        error.message.contains("must not capture a Builder")
    }

    def "template recipes reject Builders hidden in enum singleton captures"() {
        given:
        createClass '''
            package pk

            @DSL
            class EnumCapturingRecipe {
                String value
            }
        '''

        when:
        clazz.Create.Template {
            BuilderHolder.INSTANCE.value = delegate
            def capturedHolder = BuilderHolder.INSTANCE
            applyLater {
                value capturedHolder.value.modelType.simpleName
            }
        }

        then:
        KlumModelException error = thrown()
        error.message.contains("must not capture a Builder")

        cleanup:
        BuilderHolder.INSTANCE.value = null
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

    def "ownership is established between Builders and materialized as model relationships"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.util.KlumBuilder

            @DSL
            class Root {
                String name
                Middle middle
            }

            @DSL
            class Middle {
                @Owner Root parent
                Leaf leaf
            }

            @DSL
            class Leaf {
                static boolean lifecycleSawBuilders

                @Owner Middle parent
                @Owner(transitive = true) Root root
                String ownerName

                @PostTree
                void readOwners() {
                    lifecycleSawBuilders = parent instanceof KlumBuilder && root instanceof KlumBuilder
                    ownerName = root.name
                }
            }
        '''

        when:
        instance = clazz.Create.With {
            name "root"
            middle {
                leaf()
            }
        }

        then:
        instance.middle.parent.is(instance)
        instance.middle.leaf.parent.is(instance.middle)
        instance.middle.leaf.root.is(instance)
        instance.middle.leaf.ownerName == "root"
        getClass("Leaf").lifecycleSawBuilders
    }

    def "only TRANSIENT model state remains publicly mutable"() {
        given:
        createClass '''
            package pk

            @DSL
            class MutableTransient {
                String stable = "stable"

                @Field(FieldType.TRANSIENT)
                String runtimeState = "initial"
            }
        '''

        when:
        instance = clazz.Create.One()
        instance.runtimeState = "changed"

        then:
        instance.runtimeState == "changed"
        Modifier.isFinal(clazz.getDeclaredField("stable").modifiers)
        !Modifier.isFinal(clazz.getDeclaredField("runtimeState").modifiers)
        clazz.methods*.name.contains("setStable") == false
    }

    def "provisional Builder issues transfer before completed model validators run once"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.util.KlumBuilder
            import com.blackbuild.klum.ast.validation.Validator

            @DSL
            class ValidatedModel {
                static Class validationReceiver
                static int validationCalls

                @Deprecated
                String legacy

                @PostTree
                void reportProvisionalIssue() {
                    Validator.addIssue("reported while building", Validate.Level.WARNING)
                }

                @Validate
                void validateCompletedModel() {
                    validationReceiver = this.class
                    validationCalls++
                    assert !(this instanceof KlumBuilder)
                }
            }
        '''

        when:
        instance = clazz.Create.With { legacy "used" }
        def result = Validator.getValidationResult(instance)

        then:
        clazz.validationReceiver == clazz
        clazz.validationCalls == 1
        result.issues*.message.contains("reported while building")
        result.issues*.message.any { it.contains("deprecated") }

        when: "the internal completed-model validation handler is invoked again"
        def handlerClass = Class.forName("com.blackbuild.klum.ast.validation.SingleObjectValidationHandler")
        def constructor = handlerClass.getDeclaredConstructor(Object)
        constructor.accessible = true
        def execute = handlerClass.getDeclaredMethod("execute")
        execute.accessible = true
        execute.invoke(constructor.newInstance(instance))

        then: "each InstanceValidator implementation remains memoized"
        clazz.validationCalls == 1
    }

    def "templates rehydrate nested DSL recipes into fresh Builder graphs"() {
        given:
        createClass '''
            package pk

            @DSL
            class RecipeRoot {
                RecipeChild child

                @Field(members = "item")
                List<RecipeChild> items
            }

            @DSL
            class RecipeChild {
                String name
                int postCreateCalls

                @PostCreate
                void initialize() {
                    postCreateCalls++
                }
            }
        '''
        def RecipeRoot = clazz

        and: "template construction records values without running lifecycle callbacks"
        def recipe = RecipeRoot.Create.Template {
            child { name "single" }
            item { name "listed" }
        }

        expect:
        recipe.child.postCreateCalls == 0
        recipe.items.first().postCreateCalls == 0

        when:
        def first
        def second
        RecipeRoot.Template.With(recipe) {
            first = RecipeRoot.Create.One()
            second = RecipeRoot.Create.One()
        }

        then: "each application owns an independent, fully initialized graph"
        first.child.name == "single"
        first.items*.name == ["listed"]
        first.child.postCreateCalls == 1
        first.items.first().postCreateCalls == 1
        !first.child.is(recipe.child)
        !first.items.first().is(recipe.items.first())
        !first.child.is(second.child)
        !first.items.first().is(second.items.first())
    }

    def "source factory converters create composition through child Builders"() {
        given:
        createClass '''
            package pk

            @DSL
            class FactoryRoot {
                FactoryChild child
            }

            @DSL
            class FactoryChild {
                String value

                static FactoryChild fromString(String value) {
                    return FactoryChild.Create.With(value: value)
                }
            }
        '''

        when:
        instance = clazz.Create.With { child "nested" }

        then:
        instance.child.value == "nested"
    }

    def "completed model companions survive Java serialization"() {
        given:
        createClass '''
            package pk

            @DSL
            class SerializableModel {
                String name
                List<String> values
            }
        '''
        instance = clazz.Create.With {
            name "serialized"
            values "one", "two"
        }
        when:
        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(instance) }
        def dynamicLoader = loader
        def restored = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass descriptor) {
                try {
                    return dynamicLoader.loadClass(descriptor.name)
                } catch (ClassNotFoundException ignored) {
                    return super.resolveClass(descriptor)
                }
            }
        }.withCloseable { it.readObject() }

        then:
        restored.name == "serialized"
        restored.values == ["one", "two"]
        def support = KlumObjectSupport.of(restored)
        support.object.is(restored)
        support.modelPath == "<root>"
        support.validation.result != null
    }
}
