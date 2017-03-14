package com.blackbuild.groovy.configdsl.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import java.lang.reflect.Method

import static groovy.lang.Closure.*

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
                String value
                int another
            }
        ''')

        when:
        instance = clazz.create() {}
        instance.apply(value: 'bla') {
            another 12
        }

        then:
        instance.value == 'bla'
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
        instance.value "Dieter"

        then:
        instance.value == "Dieter"
    }

    private List<Method> allMethodsNamed(String name) {
        clazz.methods.findAll { it.name == name }
    }

    def "@Ignore members are ignored"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                @Ignore
                String value
            }
        ''')

        when:
        instance.value "Dieter"

        then:
        thrown MissingMethodException
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
        instance.value "value"

        then:
        thrown MissingMethodException
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
        def inner = instance.inner {
            name "Dieter"
        }

        then:
        instance.inner.name == "Dieter"

        and: "object should be returned by closure"
        inner != null

        when: 'Allow named parameters'
        inner = instance.inner(name: 'Hans')

        then:
        inner.name == 'Hans'
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
        def inner = instance.inner("Dieter") {
            value 15
        }

        then:
        instance.inner.name == "Dieter"
        instance.inner.value == 15

        and: "object should be returned by closure"
        inner != null

        when: "Allow named arguments for simple objects"
        inner = instance.inner("Hans", value: 16)

        then:
        inner.name == "Hans"
        inner.value == 16

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
        instance.bars {
            bar { name "Dieter" }
            bar { name "Klaus"}
        }

        then:
        instance.bars[0].name == "Dieter"
        instance.bars[1].name == "Klaus"

        when: 'Allow named parameters'
        instance.bars {
            bar(name: "Kurt")
            bar(name: "Felix")
        }

        then:
        instance.bars[2].name == "Kurt"
        instance.bars[3].name == "Felix"
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
        instance.bars {
            bar { name "Dieter" }
            bar { name "Klaus"}
        }

        then:
        instance.bars*.name as Set == ["Dieter", "Klaus"] as Set

        when: 'Allow named parameters'
        instance.bars {
            bar(name: "Kurt")
            bar(name: "Felix")
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
        instance.bars {
            bar1 = bar { name "Dieter" }
            bar2 = bar { name "Klaus"}
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
        instance.bars {
            bar("Dieter") { url "1" }
            bar("Klaus") { url "2" }
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
        instance.bars {
            bar1 = bar("Dieter") { url "1" }
            bar2 = bar("Klaus") { url "2" }
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
        allMethodsNamed("value").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("bool").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("singleOther").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("singleKeyedOther").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("others").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("other").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("keyedOthers").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("keyedOther").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("mappedKeyedOthers").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("mappedKeyedOther").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("simpleValues").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("simpleValue").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("simpleMappedValues").every { it.getAnnotation(Deprecated) != null }
        allMethodsNamed("simpleMappedValue").every { it.getAnnotation(Deprecated) != null }

        allMethodsNamed("notDeprecated").every { it.getAnnotation(Deprecated) == null }
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
        instance.values "Dieter", "Klaus"

        then:
        instance.values == ["Dieter", "Klaus"]

        when:"add using list add again"
        instance.values "Heinz"

        then:"second call should add to previous values"
        instance.values == ["Dieter", "Klaus", "Heinz"]

        when:"add using single method"
        instance.value "singleadd"

        then:
        instance.values == ["Dieter", "Klaus", "Heinz", "singleadd"]

        when:
        instance.values(["asList"])

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
        instance.values "Dieter", "Klaus"

        then:
        instance.values == ["Dieter", "Klaus"] as Set

        when:"add using list add again"
        instance.values "Heinz"

        then:"second call should add to previous values"
        instance.values == ["Dieter", "Heinz", "Klaus" ] as Set

        when:"add using single method"
        instance.value "singleadd"

        then:
        instance.values == ["Dieter", "Heinz", "Klaus", "singleadd"] as Set

        when:
        instance.values(["asList"])

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
        instance.values "Dieter", "Klaus"
        instance.more "Heinz"

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
        instance.something "Dieter", "Klaus" // list adder
        instance.something "Heinz" // single added
        instance.something(["Franz"]) // List adder

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
        instance.values name:"Dieter", time:"Klaus", "val bri":"bri"

        then:
        instance.values == [name:"Dieter", time:"Klaus", "val bri":"bri"]

        when:
        instance.values name:"Maier", age:"15"

        then:
        instance.values == [name:"Maier", time:"Klaus", "val bri":"bri", age: "15"]

        when:
        instance.value("height", "14")

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
        instance.values name:"Dieter", time:"Klaus", "val bri":"bri"

        then:
        instance.values == [name:"Dieter", time:"Klaus", "val bri":"bri"]

        when:
        instance.values name:"Maier", age:"15"

        then:
        instance.values == [name:"Maier", time:"Klaus", "val bri":"bri", age: "15"]

        when:
        instance.value("height", "14")

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
        instance.bars {
            bar("Dieter") { url "1" }
            bar("Klaus") { url "2" }
        }

        then:
        instance.bars.Dieter.url == "1"
        instance.bars.Klaus.url == "2"

        when: "named parameters"
        instance.bars {
            bar("Kurt", url: "3")
            bar("Felix", url: "4")
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
        instance.bars {
            bar1 = bar("Dieter") { url "1" }
            bar2 = bar("Klaus") { url "2" }
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
        instance.bars {
            bar(aBar)
        }

        then:
        instance.bars[0].url == "welt"

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
        instance.bars {
            bar(aBar)
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
                @Owner Foo owner
            }
        ''')

        when:
        instance = clazz.create {
            bar {}
        }
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

    def "error: alternatives field of Field annotation is only allowed on collections"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Field(alternatives = [Foo]) String name
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
        clazz.interfaces.contains(Serializable)
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
        clazz.interfaces.contains(Serializable)
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
        def polymorphicMethod = clazz.getMethod(methodName, Class, Closure)
        def polymorphicMethodWithNamedParams = clazz.getMethod(methodName, Map, Class, Closure)
        def staticMethod = clazz.getMethod(methodName, Closure)
        def staticMethodWithNamedParams = clazz.getMethod(methodName, Map, Closure)

        then:
        polymorphicMethod.parameterAnnotations[0].find { it.annotationType() == DelegatesTo.Target }
        polymorphicMethod.parameterAnnotations[1].find { it.annotationType() == DelegatesTo && ((DelegatesTo) it).value() == DelegatesTo.Target && ((DelegatesTo) it).strategy() == DELEGATE_FIRST}

        and:
        polymorphicMethodWithNamedParams.parameterAnnotations[1].find { it.annotationType() == DelegatesTo.Target }
        polymorphicMethodWithNamedParams.parameterAnnotations[2].find { it.annotationType() == DelegatesTo && ((DelegatesTo) it).value() == DelegatesTo.Target && ((DelegatesTo) it).strategy() == DELEGATE_FIRST}

        and:
        staticMethod.parameterAnnotations[0].find { it.annotationType() == DelegatesTo && ((DelegatesTo) it).value().canonicalName == "pk.Inner" && ((DelegatesTo) it).strategy() == DELEGATE_FIRST}
        staticMethodWithNamedParams.parameterAnnotations[1].find { it.annotationType() == DelegatesTo && ((DelegatesTo) it).value().canonicalName == "pk.Inner" && ((DelegatesTo) it).strategy() == DELEGATE_FIRST}

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
        def polymorphicMethod = clazz.getMethod(methodName, Class, String, Closure)
        def polymorphicMethodWithNamedParams = clazz.getMethod(methodName, Map, Class, String, Closure)
        def staticMethod = clazz.getMethod(methodName, String, Closure)
        def staticMethodWithNamedParams = clazz.getMethod(methodName, Map, String, Closure)

        then:
        polymorphicMethod.parameterAnnotations[0].find { it.annotationType() == DelegatesTo.Target }
        polymorphicMethod.parameterAnnotations[2].find { it.annotationType() == DelegatesTo && ((DelegatesTo) it).value() == DelegatesTo.Target && ((DelegatesTo) it).strategy() == DELEGATE_FIRST}

        and:
        polymorphicMethodWithNamedParams.parameterAnnotations[1].find { it.annotationType() == DelegatesTo.Target }
        polymorphicMethodWithNamedParams.parameterAnnotations[3].find { it.annotationType() == DelegatesTo && ((DelegatesTo) it).value() == DelegatesTo.Target && ((DelegatesTo) it).strategy() == DELEGATE_FIRST}

        and:
        staticMethod.parameterAnnotations[1].find { it.annotationType() == DelegatesTo && ((DelegatesTo) it).value().canonicalName == "pk.Inner" && ((DelegatesTo) it).strategy() == DELEGATE_FIRST}
        staticMethodWithNamedParams.parameterAnnotations[2].find { it.annotationType() == DelegatesTo && ((DelegatesTo) it).value().canonicalName == "pk.Inner" && ((DelegatesTo) it).strategy() == DELEGATE_FIRST}

        where:
        methodName << ["inner", "listInner", "mapInner"]
    }
}
