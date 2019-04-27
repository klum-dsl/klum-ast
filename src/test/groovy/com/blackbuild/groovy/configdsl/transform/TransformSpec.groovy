/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2019 Stephan Pauxberger
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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import java.lang.reflect.Method

import static com.blackbuild.groovy.configdsl.transform.TestHelper.*
import static groovyjarjarasm.asm.Opcodes.ACC_PROTECTED

@SuppressWarnings("GroovyAssignabilityCheck")
class TransformSpec extends AbstractDSLSpec {

    def "apply method is created"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
            }
        ''')

        then:
        clazz.metaClass.getMetaMethod("apply", Map, Closure) != null
    }

    def "apply method allows named parameters"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String field
                int another
            }
        ''')

        when:
        instance = clazz.create() {}
        instance.apply(field: 'bla') {
            another 12
        }

        then:
        instance.field == 'bla'
        instance.another == 12
    }

    def "named parameters also work for collection adders"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<String> values
            }
        ''')

        when:
        instance = clazz.create() {}
        instance.apply(value: 'bla') {}

        then:
        instance.values == ['bla']
    }

    def "named parameters also work for collection multi adders"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<String> values
            }
        ''')

        when:
        instance = clazz.create() {}
        instance.apply(values: ['bla', 'blub']) {}

        then:
        instance.values == ['bla', 'blub']
    }

    def "factory methods should be created"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
            }
        ''')

        when:
        instance = clazz.create() {}

        then:
        instance.class.name == "pk.Foo"

        when: 'Closure is optional'
        instance = clazz.create()

        then:
        noExceptionThrown()
    }

    def "factory methods with named parameters"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
            }
        ''')

        when:
        instance = clazz.create(value: 'bla') {}

        then:
        instance.class.name == "pk.Foo"
        instance.value == 'bla'

        when: 'Closure is optional'
        instance = clazz.create(value: 'blub')

        then:
        noExceptionThrown()
        instance.value == 'blub'
    }

    def "factory methods with key"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
            }
        ''')

        when:
        instance = clazz.create("Dieter") {}

        then:
        instance.name == "Dieter"

        and: "no name() accessor is created"
        instance.class.metaClass.getMetaMethod("name", String) == null

        when: 'Closure is optional'
        instance = clazz.create("Klaus")

        then:
        noExceptionThrown()
        instance.name == "Klaus"
    }

    def "factory methods with key and named parameters"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String value
            }
        ''')

        when:
        instance = clazz.create("Dieter", value: 'bla') {}

        then:
        instance.name == "Dieter"
        instance.value == 'bla'

        and: "no name() accessor is created"
        instance.class.metaClass.getMetaMethod("name", String) == null

        when: 'Closure is optional'
        instance = clazz.create("Klaus", value: 'blub')

        then:
        noExceptionThrown()
        instance.name == "Klaus"
        instance.value == 'blub'
    }

    def "constructor is created for keyed object"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
            }
        ''')

        when:
        instance = clazz.newInstance("Klaus")

        then:
        noExceptionThrown()
        instance.name == "Klaus"
    }

    def 'Key is reachable with get$Key()'() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
            }
        ''')

        when:
        instance = clazz.newInstance("Klaus")

        then:
        noExceptionThrown()
        instance.$key == "Klaus"
    }

    def "key field must be of type String"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key int name
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "simple member method"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                String value
            }
        ''')

        when:
        instance.apply { value "Dieter" }

        then:
        instance.value == "Dieter"
    }

    @Issue('#21')
    def "final fields are ignored"() {
        when:
        createInstance('''
            @DSL
            class Foo {
                final String value = "bla"
            }
        ''')

        then:
        notThrown MultipleCompilationErrorsException
    }

    def "transient fields are ignored"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                transient String value
            }
        ''')

        when:
        instance.apply { value "value" }

        then:
        thrown MissingMethodException
    }

    @Issue('80')
    def "adders for PROTECTED fields are protected"() {
        when:
        createClass('''
            @DSL
            class Foo {
                @Field(FieldType.PROTECTED) String value
                
                @Mutator void setAsLowerCase(String rawName) {
                    value = rawName.toLowerCase()   
                }
            }
        ''')

        then:
        rwClazz.getDeclaredMethod("value", String).getModifiers() & ACC_PROTECTED
        rwClazz.getDeclaredMethod("setValue", String).getModifiers() & ACC_PROTECTED

        when:
        instance = clazz.create {
            setAsLowerCase("HALLO")
        }

        then:
        instance.value == 'hallo'
    }

    @Issue('#22')
    def 'no helper methods are generated for $TEMPLATE field'() {
        when:
        createInstance('''
            @DSL
            class Foo {
            }
        ''')

        then:
        !clazz.metaClass.methods.find { it.name == '$TEMPLATE' }
    }

    def "simple boolean member setter should have 'true' as default"() {
        given:
        createClass('''
            @DSL
            class Foo {
                boolean helpful
            }
        ''')

        when:
        instance = clazz.create {
            helpful()
        }

        then:
        instance.helpful == true
    }

    def "simple member method for reusable config objects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar inner
            }

            @DSL
            class Bar {
                String name
            }
        ''')

        when:
        def bar = create("pk.Bar") {
            name = "Dieter"
        }
        instance = create("pk.Foo") {
            inner bar
        }

        then:
        instance.inner.name == "Dieter"
    }

    def "test existing method"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                String name, lastname
                def name(String value) {return "run"}
            }
        ''')

        expect: "Original method is called"
        instance.name("Dieter") == "run"
    }

    def "create inner object via closure"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Bar inner
            }

            @DSL
            class Bar {
                String name
            }
        ''')

        when:
        def innerInstance
        instance.apply {
            innerInstance = inner {
                name "Dieter"
            }
        }

        then:
        innerInstance.class.name == 'pk.Bar'
        instance.inner.name == "Dieter"

        and: "object should be returned by closure"
        innerInstance != null

        when: 'Allow named parameters'
        instance.apply {
            innerInstance = inner(name: 'Hans')
        }

        then:
        innerInstance.name == 'Hans'
    }

    def "create inner object via key and closure"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Bar inner
            }

            @DSL
            class Bar {
                @Key String name
                int value
            }
        ''')

        when:
        def innerInstance
        instance.apply {
            innerInstance = inner("Dieter") {
                value 15
            }
        }

        then:
        instance.inner.name == "Dieter"
        instance.inner.value == 15

        and: "object should be returned by closure"
        innerInstance != null

        when: "Allow named arguments for simple objects"
        instance.apply {
            innerInstance = inner("Hans", value: 16)
        }

        then:
        innerInstance.name == "Hans"
        innerInstance.value == 16
    }

    def "create list of inner objects"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                String name
            }
        ''')

        when:
        instance.apply {
            bars {
                bar { name "Dieter" }
                bar { name "Klaus"}
            }
        }

        then:
        instance.bars[0].name == "Dieter"
        instance.bars[1].name == "Klaus"

        when: 'Allow named parameters'
        instance.apply {
            bars {
                bar(name: "Kurt")
                bar(name: "Felix")
            }
        }

        then:
        instance.bars[2].name == "Kurt"
        instance.bars[3].name == "Felix"
    }

    @Issue('#72')
    def "Outer closures are hidden"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                String outerName
                List<Bar> bars
            }

            @DSL
            class Bar {
                String name
            }
        ''')

        when:
        instance.apply {
            bars {
                bar {
                    outerName "outer"
                    name "Dieter"
                }
            }
        }

        then:
        thrown(MissingMethodException)

        when:
        instance.apply {
            bars {
                outerName "outer"
                bar {
                    name "Dieter"
                }
            }
        }

        then:
        thrown(MissingMethodException)
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/58')
    def "create set of inner objects"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Set<Bar> bars
            }

            @DSL
            class Bar {
                String name
            }
        ''')

        when:
        instance.apply {
            bars {
                bar { name "Dieter" }
                bar { name "Klaus"}
            }
        }

        then:
        instance.bars*.name as Set == ["Dieter", "Klaus"] as Set

        when: 'Allow named parameters'
        instance.apply {
            bars {
                bar(name: "Kurt")
                bar(name: "Felix")
            }
        }

        then:
        instance.bars*.name as Set == ["Dieter", "Klaus", "Kurt", "Felix"] as Set
    }

    @SuppressWarnings("GroovyVariableNotAssigned")
    def "inner list objects closure should return the object"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                String name
            }
        ''')

        when:
        def bar1
        def bar2
        instance.apply {
            bars {
                bar1 = bar { name "Dieter" }
                bar2 = bar { name "Klaus"}
            }
        }

        then:
        bar1.name == "Dieter"
        bar2.name == "Klaus"
    }

    def "create list of named inner objects"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Key String name
                String url
            }
        ''')

        when:
        instance.apply {
            bars {
                bar("Dieter") { url "1" }
                bar("Klaus") { url "2" }
            }
        }

        then:
        instance.bars[0].name == "Dieter"
        instance.bars[0].url == "1"
        instance.bars[1].name == "Klaus"
        instance.bars[1].url == "2"
    }

    @SuppressWarnings("GroovyVariableNotAssigned")
    def "inner list objects closure with named objects should return the created object"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Key String name
                String url
            }
        ''')

        when:
        def bar1
        def bar2
        instance.apply {
            bars {
                bar1 = bar("Dieter") { url "1" }
                bar2 = bar("Klaus") { url "2" }
            }
        }

        then:
        bar1.name == "Dieter"
        bar2.name == "Klaus"
    }

    def "Bug: DSLField without value leads to NPE"() {
        when:
        createInstance('''
            package pk

            @DSL
            class Foo {
                @Field
                String name
            }
        ''')

        then:
        noExceptionThrown()
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/54')
    def 'methods for deprecated fields are deprecated as well'() {
        when:
        createClass('''
            @DSL
            class Foo {
                String notDeprecated
            
                @Deprecated
                String value
                
                @Deprecated
                boolean bool
                
                @Deprecated Other singleOther
                @Deprecated KeyedOther singleKeyedOther
                
                @Deprecated List<Other> others
                
                @Deprecated List<KeyedOther> keyedOthers
                @Deprecated Map<String, KeyedOther> mappedKeyedOthers
        
                @Deprecated List<String> simpleValues
                @Deprecated Map<String, String> simpleMappedValues
            }
            
            @DSL
            class Other {
            }
            
            @DSL
            class KeyedOther {
                @Key String name
            }
        ''')

        then:
        allMethodsNamed("value").every { isDeprecated(it) }
        allMethodsNamed("bool").every { isDeprecated(it) }
        allMethodsNamed("singleOther").every { isDeprecated(it) }
        allMethodsNamed("singleKeyedOther").every { isDeprecated(it) }
        allMethodsNamed("others").every { isDeprecated(it) }
        allMethodsNamed("other").every { isDeprecated(it) }
        allMethodsNamed("keyedOthers").every { isDeprecated(it) }
        allMethodsNamed("keyedOther").every { isDeprecated(it) }
        allMethodsNamed("mappedKeyedOthers").every { isDeprecated(it) }
        allMethodsNamed("mappedKeyedOther").every { isDeprecated(it) }
        allMethodsNamed("simpleValues").every { isDeprecated(it) }
        allMethodsNamed("simpleValue").every { isDeprecated(it) }
        allMethodsNamed("simpleMappedValues").every { isDeprecated(it) }
        allMethodsNamed("simpleMappedValue").every { isDeprecated(it) }

        allMethodsNamed("notDeprecated").every { !isDeprecated(it) }
    }

    def "collections gets initial values"() {
        when:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<String> values
                Set<String> setValues
                SortedSet<String> sortedSetValues
                Map<String, String> fields
                SortedMap<String, String> sortedFields
            }
        ''')

        then:
        instance.values instanceof List
        instance.values.isEmpty()

        and:
        instance.fields instanceof Map
        instance.fields.isEmpty()

        and:
        instance.sortedFields instanceof SortedMap
        instance.sortedFields.isEmpty()

        and:
        instance.setValues instanceof Set
        instance.setValues.isEmpty()

        and:
        instance.sortedSetValues instanceof SortedSet
        instance.sortedSetValues.isEmpty()
    }

    def "existing initial values are not overridden"() {
        when:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<String> values = ['Bla']
                Map<String, String> fields = [bla: "blub"]
            }
        ''')

        then:
        instance.values == ["Bla"]

        and:
        instance.fields == [bla: "blub"]
    }

    def "simple list element"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<String> values
            }
        ''')

        when:"add using list add"
        instance.apply {
            values "Dieter", "Klaus"
        }

        then:
        instance.values == ["Dieter", "Klaus"]

        when:"add using list add again"
        instance.apply {
            values "Heinz"
        }

        then:"second call should add to previous values"
        instance.values == ["Dieter", "Klaus", "Heinz"]

        when:"add using single method"
        instance.apply {
            value "singleadd"
        }

        then:
        instance.values == ["Dieter", "Klaus", "Heinz", "singleadd"]

        when:
        instance.apply {
            values(["asList"])
        }

        then:
        instance.values == ["Dieter", "Klaus", "Heinz", "singleadd", "asList"]
    }

    def "simple sorted set element"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                SortedSet<String> values
            }
        ''')

        when:"add using list add"
        instance.apply {
            values "Dieter", "Klaus"
        }

        then:
        instance.values == ["Dieter", "Klaus"] as Set

        when:"add using list add again"
        instance.apply {
            values "Heinz"
        }

        then:"second call should add to previous values"
        instance.values == ["Dieter", "Heinz", "Klaus" ] as Set

        when:"add using single method"
        instance.apply {
            value "singleadd"
        }

        then:
        instance.values == ["Dieter", "Heinz", "Klaus", "singleadd"] as Set

        when:
        instance.apply {
            values(["asList"])
        }

        then:
        instance.values == ["asList", "Dieter", "Heinz", "Klaus", "singleadd"] as Set
    }

    def "simple list element with different member name"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                @Field(members="more")
                List<String> values
            }
        ''')

        when:
        instance.apply {
            values "Dieter", "Klaus"
            more "Heinz"
        }

        then:
        instance.values == ["Dieter", "Klaus", "Heinz"]
    }

    def "with simple list element with singular name, element and group list methods have the same name"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<String> something
            }
        ''')

        when:
        instance.apply {
            something "Dieter", "Klaus" // vararg adder
            something "Heinz" // single added
            something(["Franz"]) // List adder
        }

        then:
        instance.something == ["Dieter", "Klaus", "Heinz", "Franz"]
    }

    def "List field without generics throws exception"() {
        when:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List values
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "simple map element"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Map<String, String> values
            }
        ''')

        when:
        instance.apply {
            values name:"Dieter", time:"Klaus", "val bri":"bri"
        }

        then:
        instance.values == [name:"Dieter", time:"Klaus", "val bri":"bri"]

        when:
        instance.apply {
            values name:"Maier", age:"15"
        }

        then:
        instance.values == [name:"Maier", time:"Klaus", "val bri":"bri", age: "15"]

        when:
        instance.apply {
            value("height", "14")
        }

        then:
        instance.values == [name:"Maier", time:"Klaus", "val bri":"bri", age: "15", height: "14"]
    }

    def "simple sorted map element"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                SortedMap<String, String> values
            }
        ''')

        when:
        instance.apply {
            values name:"Dieter", time:"Klaus", "val bri":"bri"
        }

        then:
        instance.values == [name:"Dieter", time:"Klaus", "val bri":"bri"]

        when:
        instance.apply {
            values name:"Maier", age:"15"
        }

        then:
        instance.values == [name:"Maier", time:"Klaus", "val bri":"bri", age: "15"]

        when:
        instance.apply {
            value("height", "14")
        }

        then:
        instance.values == [name:"Maier", time:"Klaus", "val bri":"bri", age: "15", height: "14"]
    }

    def "map of inner objects without keys throws exception"() {
        when:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                String name
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "create map of inner objects"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Key String name
                String url
            }
        ''')

        when:
        instance.apply {
            bars {
                bar("Dieter") { url "1" }
                bar("Klaus") { url "2" }
            }
        }

        then:
        instance.bars.Dieter.url == "1"
        instance.bars.Klaus.url == "2"

        when: "named parameters"
        instance.apply {
            bars {
                bar("Kurt", url: "3")
                bar("Felix", url: "4")
            }
        }

        then:
        instance.bars.Kurt.url == "3"
        instance.bars.Felix.url == "4"
    }

    @SuppressWarnings("GroovyVariableNotAssigned")
    def "creation of inner objects in map should return the create object"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Key String name
                String url
            }
        ''')

        when:
        def bar1
        def bar2
        instance.apply {
            bars {
                bar1 = bar("Dieter") { url "1" }
                bar2 = bar("Klaus") { url "2" }
            }
        }

        then:
        bar1.url == "1"
        bar2.url == "2"
    }

    def "reusing of objects in closure"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                String url
            }
        ''')
        def aBar = create("pk.Bar") {
            url "welt"
        }

        when:
        instance.apply {
            bars {
                bar(aBar)
            }
        }

        then:
        instance.bars[0].url == "welt"
    }

    def "reusing adders return the added object"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
                List<Bar> listBars
                List<KeyBar> listKeyBars
                Map<String, KeyBar> mapKeyBars
                List<Object> objects
                Map<String, Object> mapObjects
            }

            @DSL class Bar {}
            
            @DSL class KeyBar {
                @Key String id
            }
        ''')
        def aBar = create("pk.Bar")
        def keyBar = create("pk.KeyBar", "id")
        def basicObject = new Object()

        when:
        def added = []
        def addObjects = []

        clazz.create {
            added << bar(aBar)
            listBars {
                added << listBar(aBar)
            }
            added << listBar(aBar)
            listKeyBars {
                added << listKeyBar(keyBar)
            }
            added << listKeyBar(keyBar)
            mapKeyBars {
                added << mapKeyBar(keyBar)
            }
            added << mapKeyBar(keyBar)
            addObjects << object(basicObject)
            addObjects << mapObject("bl", basicObject)
        }

        then:
        added.size() == 7
        added.every { it.is(aBar) || it.is(keyBar) }

        and:
        addObjects.size() == 2
        addObjects.every { it.is(basicObject) }

    }

    def "reusing of map objects in closure"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Key String name
                String url
            }
        ''')
        def aBar = create("pk.Bar", "klaus") {
            url "welt"
        }

        when:
        instance.apply {
            bars {
                bar(aBar)
            }
        }

        then:
        instance.bars.klaus.url == "welt"
    }

    def "equals, hashcode and toString methods are created"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }
        ''')

        then:
        clazz.declaredMethods.find { Method method -> method.name == "toString"}
    }

    def "hashcode is 0 for non keyed objects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String other
            }
        ''')

        when:
        instance = clazz.create(name: 'bla', other: 'blub')

        then:
        instance.hashCode() == 0
    }

    def "hashcode is only derived from key for keyed objects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String other
            }
        ''')

        when:
        instance = clazz.create('bla', other: 'blub')

        then:
        instance.hashCode() == 'bla'.hashCode()
    }

    def "hashcode is not overridden"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String other
                
                int hashCode() {
                  return 5
                }
            }
        ''')

        when:
        instance = clazz.create(other: 'blub')

        then:
        instance.hashCode() == 5
    }

    def "equals works correctly"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }
        ''')

        when:
        def left = clazz.create(name: "a")
        def right = clazz.create(name: "a")
        def other = clazz.create(name: "b")

        then:
        left == right
        left != other
    }

    def "equals with inner objects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                List<Bar> bars
            }
            
            @DSL
            class Bar {
                String value
            }
        ''')

        when:
        def left = clazz.create(name: "a") { bar(value: 'a')}
        def right = clazz.create(name: "a") { bar(value: 'a')}
        def other = clazz.create(name: "a") { bar(value: 'b')}

        then:
        left == right
        left != other
    }


    def "Bug: toString() with owner field throws StackOverflowError"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')
        instance = clazz.create {
            bar {}
        }

        when:
        instance.toString()

        then:
        notThrown(StackOverflowError)
    }

    def "error: more than one key"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                @Key String name2
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "error: members field of Field annotation is only allowed on collections"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Field(members = "bla") String name
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    @Issue("35")
    def "Models are serializable by default"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }
        ''')

        then:
        Serializable.isAssignableFrom(clazz)
    }

    @Issue("35")
    def "RW classes are serializable as well"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }
        ''')

        then:
        Serializable.isAssignableFrom(rwClazz)
    }

    @Issue("35")
    def "if model is already serializable, do nothing"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo implements Serializable{
                String name
            }
        ''')

        then:
        noExceptionThrown()
        Serializable.isAssignableFrom(clazz)
    }

    def "DelegatesTo annotations for unkeyed inner models are created"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Inner inner
                List<Inner> listInners
            }

            @DSL
            class Inner {
                String name
            }
        ''')

        when:
        def polymorphicMethodParams = rwClazz.getMethod(methodName, Class, Closure).parameterAnnotations

        then:
        hasDelegatesToTargetAnnotation(polymorphicMethodParams[0])
        delegatesToPointsToDelegateTarget(polymorphicMethodParams[1])

        when:
        def polymorphicMethodWithNamesParams = rwClazz.getMethod(methodName, Map, Class, Closure).parameterAnnotations

        then:
        hasDelegatesToTargetAnnotation(polymorphicMethodWithNamesParams[1])
        delegatesToPointsToDelegateTarget(polymorphicMethodWithNamesParams[2])

        and:
        delegatesToPointsTo(rwClazz.getMethod(methodName, Closure).parameterAnnotations[0], 'pk.Inner._RW')
        delegatesToPointsTo(rwClazz.getMethod(methodName, Map, Closure).parameterAnnotations[1], 'pk.Inner._RW')

        where:
        methodName << ["inner", "listInner"]
    }

    def "DelegatesTo annotations for keyed inner models are created"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Inner inner
                List<Inner> listInners
                Map<String, Inner> mapInners
            }

            @DSL
            class Inner {
                @Key String name
            }
        ''')

        when:
        def polymorphicMethodParams = rwClazz.getMethod(methodName, Class, String, Closure).parameterAnnotations

        then:
        hasDelegatesToTargetAnnotation(polymorphicMethodParams[0])
        delegatesToPointsToDelegateTarget(polymorphicMethodParams[2])

        when:
        def polymorphicMethodWithNamesParams = rwClazz.getMethod(methodName, Map, Class, String, Closure).parameterAnnotations

        then:
        hasDelegatesToTargetAnnotation(polymorphicMethodWithNamesParams[1])
        delegatesToPointsToDelegateTarget(polymorphicMethodWithNamesParams[3])

        and:
        delegatesToPointsTo(rwClazz.getMethod(methodName, String, Closure).parameterAnnotations[1], 'pk.Inner._RW')
        delegatesToPointsTo(rwClazz.getMethod(methodName, Map, String, Closure).parameterAnnotations[2], 'pk.Inner._RW')

        where:
        methodName << ["inner", "listInner", "mapInner"]
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/56')
    def 'mutator methods are not created in the model anymore'() {
        when:
        createClass('''
            @DSL
            class Foo {
                String notDeprecated
            
                String value
                
                boolean bool
                
                Other singleOther
                KeyedOther singleKeyedOther
                
                List<Other> others
                
                List<KeyedOther> keyedOthers
                Map<String, KeyedOther> mappedKeyedOthers
        
                List<String> simpleValues
                Map<String, String> simpleMappedValues
            }
            
            @DSL
            class Other {
            }
            
            @DSL
            class KeyedOther {
                @Key String name
            }
        ''')

        then:
        hasNoPublicMethodsNamed("value")
        hasNoPublicMethodsNamed("setValue")

        hasNoPublicMethodsNamed("bool")
        hasNoPublicMethodsNamed("setBool")

        hasNoPublicMethodsNamed("singleOther")
        hasNoPublicMethodsNamed("setSingleOther")

        hasNoPublicMethodsNamed("singleKeyedOther")
        hasNoPublicMethodsNamed("setSingleKeyedOther")

        hasNoPublicMethodsNamed("others")
        hasNoPublicMethodsNamed("setOthers")
        hasNoPublicMethodsNamed("other")

        hasNoPublicMethodsNamed("keyedOthers")
        hasNoPublicMethodsNamed("setKeyedOthers")
        hasNoPublicMethodsNamed("keyedOther")

        hasNoPublicMethodsNamed("mappedKeyedOthers")
        hasNoPublicMethodsNamed("setMappedKeyedOthers")
        hasNoPublicMethodsNamed("mappedKeyedOther")

        hasNoPublicMethodsNamed("simpleValues")
        hasNoPublicMethodsNamed("setSimpleValues")
        hasNoPublicMethodsNamed("simpleValue")

        hasNoPublicMethodsNamed("simpleMappedValues")
        hasNoPublicMethodsNamed("setSimpleMappedValues")
        hasNoPublicMethodsNamed("simpleMappedValue")
    }

    def hasNoPublicMethodsNamed(String name) {
        assert !allMethodsNamed(name).findAll { it.modifiers && Opcodes.ACC_PUBLIC }
        return true
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/56')
    def 'created collections are read only'() {
        given:
        createInstance('''
            @DSL
            class Foo {
                List<Other> others
                List<KeyedOther> keyedOthers
                Map<String, KeyedOther> mappedKeyedOthers
                List<String> simpleValues
                Map<String, String> simpleMappedValues
            }
            
            @DSL
            class Other {
            }
            
            @DSL
            class KeyedOther {
                @Key String name
            }
        ''')

        when:
        instance.others.add(null)

        then:
        thrown(UnsupportedOperationException)

        when:
        instance.keyedOthers.add(null)

        then:
        thrown(UnsupportedOperationException)

        when:
        instance.simpleValues.add("bla")

        then:
        thrown(UnsupportedOperationException)

        when:
        instance.mappedKeyedOthers.put("bla", null)

        then:
        thrown(UnsupportedOperationException)

        when:
        instance.simpleMappedValues.put("bla", "blub")

        then:
        thrown(UnsupportedOperationException)

    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/121')
    def 'interfaces can be annotated'() {
        when:
        createNonDslClass('''
            @DSL interface Foo {
                String getValue()
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/121')
    def 'DSL methods for DSL-interface fields are generated'() {
        when:
        createClass('''
            @DSL class Outer {
                Foo foo
            }

            @DSL class FooImpl implements Foo {
                String value
                String name
            }

            @DSL interface Foo {
                String getValue()
                String getName()
            }
        ''')

        then:
        rwClazz.metaClass.getMetaMethod("foo", Class, Closure) != null

        and: "interface fields must get a Class argument"
        rwClazz.metaClass.getMetaMethod("foo", Closure) == null
    }

    def 'classloader is injectable for copyFrom'() {
        given:
        def loader = GroovySpy(GroovyClassLoader)

        createClass '''
            @DSL class Dummy {
                String name
            }
        '''
        def script = '''getClass().classLoader.getResourceAsStream("mock")'''

        when:
        clazz.createFrom(script, loader)

        then: 'method is called from within script'
        1 * loader.getResource("mock")
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/126')
    def "no methods are generated for ignored fields, but setters are part of rw class"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(FieldType.IGNORED)
                Map<Class<? extends Hint>, ? extends Hint> hints
            }
            
            @DSL class Hint {}
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
        clazz.metaClass.getMetaMethod("hints", Closure) == null
        clazz.metaClass.getMetaMethod("hint", Closure) == null

        and:
        rwClazz.metaClass.getMetaMethod("hints", Closure) == null
        rwClazz.metaClass.getMetaMethod("hint", Closure) == null

        and:
        clazz.metaClass.getMetaMethod("setHints", Map) == null
        rwClazz.metaClass.getMetaMethod("setHints", Map) != null
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/126')
    def "Use case for IGNORED field"() {
        given:
        createClass '''
            @DSL class Foo {
                @Field(FieldType.IGNORED)
                Map<Class<? extends Hint>, ? extends Hint> hints = [:]
                
                void hint(Hint hint) {
                    hints.put(hint.class, hint)
                }
                
                def <T extends Hint> T getHint(Class<T> type) {
                    return hints[type] as T
                }
            }
            
            @DSL abstract class Hint {}
            
            @DSL class AHint extends Hint {
                String value
            }
            @DSL class BHint extends Hint {
                String otherValue
            }
        '''

        when:
        def a = getClass("AHint").create(value: "blub")
        def b = getClass("BHint").create(otherValue: "bli")
        instance = clazz.create {
            hint(a)
            hint(b)
        }

        then:
        instance.hints.size() == 2
        instance.getHint(getClass("AHint")).value == "blub"
        instance.getHint(getClass("BHint")).otherValue == "bli"

    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/172')
    def "LINK fields only get reuse setters"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(FieldType.LINK)
                Bar singleBar

                @Field(FieldType.LINK)
                List<Bar> bars
                
                @Field(FieldType.LINK)
                Map<String, KeyBar> keyBars
            }
            
            @DSL class Bar {}
            
            @DSL class KeyBar {
                @Key String key
            }
        '''

        then:
        rwClassHasNoMethod("singleBar", Map, Closure)
        rwClassHasNoMethod("singleBar", Closure)
        rwClassHasNoMethod("singleBar", Map)
        rwClassHasNoMethod("singleBar")

        and:
        rwClassHasMethod("singleBar", getClass("Bar"))

        then:
        rwClassHasNoMethod("bar", Map, Closure)
        rwClassHasNoMethod("bar", Closure)
        rwClassHasNoMethod("bar", Map)
        rwClassHasNoMethod("bar")

        and:
        rwClassHasNoMethod("bar", Map, Class, Closure)
        rwClassHasNoMethod("bar", Class, Closure)
        rwClassHasNoMethod("bar", Map, Class)
        rwClassHasNoMethod("bar", Class)

        and:
        rwClassHasMethod("bar", getClass("Bar"))

        then:
        rwClassHasNoMethod("keyBar", Map, String, Closure)
        rwClassHasNoMethod("keyBar", String, Closure)
        rwClassHasNoMethod("keyBar", Map, String)
        rwClassHasNoMethod("keyBar", String)

        and:
        rwClassHasNoMethod("keyBar", Map, Class, String, Closure)
        rwClassHasNoMethod("keyBar", Class, String, Closure)
        rwClassHasNoMethod("keyBar", Map, Class, String)
        rwClassHasNoMethod("keyBar", Class, String)

        and:
        rwClassHasMethod("keyBar", getClass("KeyBar"))
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/127')
    def "custom key mappings"() {
        given:
        createClass '''
            @DSL class Foo {
                @Field(keyMapping = { it.secondary })
                Map<String, Bar> bars
                @Field(keyMapping = { it.secondary })
                Map<String, TwoBar> twobars
            }
            
            @DSL class Bar {
                String secondary
            }
            @DSL class TwoBar {
                @Key String key
                String secondary
            }
        '''

        when:
        instance = clazz.create {
            bar {
                secondary "blub"
            }
            bar {
                secondary "bli"
            }
            twobar("boink") {
                secondary "blub"
            }
            twobar("bunk") {
                secondary "bli"
            }
        }

        then:
        instance.bars.size() == 2
        instance.bars.blub
        instance.bars.bli

        and:
        instance.twobars.blub.key == "boink"
        instance.twobars.bli.key == "bunk"
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/127')
    def "custom key mappings are validated"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(keyMapping = { it.tertiary })
                Map<String, Bar> bars
            }
            
            @DSL class Bar {
                String secondary
            }
        '''

        then:
        thrown(MultipleCompilationErrorsException)
    }


    @Issue('https://github.com/klum-dsl/klum-ast/issues/127')
    def "Use case: Allow custom key mappings for DSL maps"() {
        given:
        createClass '''
            @DSL class Foo {
                @Field(keyMapping = { it.class })
                Map<Class<? extends Hint>, ? extends Hint> hints
                
                def <T extends Hint> T getHint(Class<T> type) {
                    return hints[type] as T
                }
            }
            
            @DSL abstract class Hint {}
            
            @DSL class AHint extends Hint {
                String value
            }
            @DSL class BHint extends Hint {
                String otherValue
            }
        '''

        when:
        def a = getClass("AHint").create(value: "blub")
        def b = getClass("BHint").create(otherValue: "bli")
        instance = clazz.create {
            hint(a)
            hint(b)
        }

        then:
        instance.hints.size() == 2
        instance.getHint(getClass("AHint")).value == "blub"
        instance.getHint(getClass("BHint")).otherValue == "bli"
    }


    @Issue('https://github.com/klum-dsl/klum-ast/issues/128')
    def "custom key mappings for simple fields"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(keyMapping = { it.toUpperCase() })
                Map<String, String> values
            }
        '''

        then:
        rwClassHasMethod("value", String)

        when:
        instance = clazz.create {
            value "Beer"
            value "BLUB"
            value "beer"
            value "small"
        }

        then:
        instance.values.size() == 3
        instance.values.BEER == "beer"
        instance.values.BLUB == "BLUB"
        instance.values.SMALL == "small"
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/128')
    def "Collection adder for keyMapping field accepts Collection or vararg, not Map"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(keyMapping = { it.toUpperCase() })
                Map<String, String> values
            }
        '''

        then:
        rwClassHasNoMethod("values", Map)
        rwClassHasMethod("values", Collection)
        rwClassHasMethod("values", String[])

        when:
        instance = clazz.create {
            value "single"
            values "var1", "var2"
            values(["coll1", "coll2"])
        }

        then:
        instance.values.size() == 5
        instance.values == [SINGLE: "single", VAR1: "var1", VAR2: "var2", COLL1: "coll1", COLL2: "coll2"]
    }

    def "Annotated setters create dsl methods"() {
        given:
        createClass '''
            @DSL class Foo {
                Date date
                
                @Field
                void setDate(long value) {
                    this.date = new Date(value)
                }
            }
            '''
        when:
        instance = clazz.create {
            date 0L
        }

        then:
        instance.date != null
    }

    def "Annotated setters work for dsl types"() {
        given:
        createClass '''
            @DSL class Foo {
                String name
                
                @Field
                void setBar(Bar bar) {
                    this.name = bar.name
                }
            }
            
            @DSL class Bar {
                String name
            }
            '''
        when:
        instance = clazz.create {
            bar {
                name "Hans"
            }
        }

        then:
        instance.name == "Hans"
    }

    def "Annotated non setter methods work for dsl types"() {
        given:
        createClass '''
            @DSL class Foo {
                String name
                
                @Field
                void addBar(Bar bar) {
                    this.name = bar.name
                }
            }
            
            @DSL class Bar {
                String name
            }
            '''
        when:
        instance = clazz.create {
            bar {
                name "Hans"
            }
        }

        then:
        instance.name == "Hans"
    }

    def "it is illegal to annotate nonSetter like methods with @Field"() {
        when:
        createClass '''
            @DSL class Foo {
                Date date
                
                @Field
                void setDate(long value, long anotherValue) {}
            }
            '''
        then:
        thrown(MultipleCompilationErrorsException)
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/128')
    def "allow to inject DelegatesTo on parameter"() {
        given:
        createClass '''
            @DSL class Foo {
                @ParameterAnnotation.ClosureHint(delegate = @DelegatesTo(Map))
                Closure<String> converter
            }
            '''

        when:
        def annotations = rwClazz.getMethod("converter", Closure).getParameterAnnotations()[0]
        DelegatesTo delegatesTo = annotations.find { it instanceof DelegatesTo }

        then:
        delegatesTo != null
        delegatesTo.value() == Map
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/128')
    def "allow to inject ClosureParams on parameter"() {
        given:
        createClass '''
            import groovy.transform.stc.ClosureParams
            import groovy.transform.stc.FromString
            @DSL class Foo {
                @ParameterAnnotation.ClosureHint(params = @ClosureParams(value=FromString, options="Map<String,Object>"))
                Closure<String> converter
            }
            '''

        when:
        def annotations = rwClazz.getMethod("converter", Closure).getParameterAnnotations()[0]
        ClosureParams closureParams = annotations.find { it instanceof ClosureParams }

        then:
        closureParams != null
        closureParams.value() == FromString
        closureParams.options() as List == ["Map<String,Object>"]
    }
}
