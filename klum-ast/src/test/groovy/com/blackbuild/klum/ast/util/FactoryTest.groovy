/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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
//file:noinspection GrPackage
package com.blackbuild.klum.ast.util

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import spock.lang.Issue

@Issue("76")
class FactoryTest extends AbstractDSLSpec {

    def "Create field is created and initialized"() {
        when:
        createClass '''import com.blackbuild.groovy.configdsl.transform.DSL
@DSL abstract class AnAbstractClass {}

@DSL class NonAbstractSubclassOfAnAbstractClass extends AnAbstractClass {}

@DSL class AClass {}

@DSL class AKeyedClass {
    @Key String id
}

@DSL class ASubclassOfAKeyedClass extends AKeyedClass {}

@DSL(defaultImpl = DefaultImpl) 
abstract class AbstractWithDefaultImpl {}

@DSL class DefaultImpl extends AbstractWithDefaultImpl {}
'''
        def factoryField = getClass(className).getField("Create")

        then:
        factoryType.isAssignableFrom(factoryField.type)

        when:
        KlumFactory factory = getClass(className).Create

        then:
        factory.type.name == factoryTargetClassName

        where:
        className                              || factoryType           | factoryTargetClassName
        "AnAbstractClass"                      || KlumFactory           | "AnAbstractClass"
        "NonAbstractSubclassOfAnAbstractClass" || KlumFactory.Unkeyed   | "NonAbstractSubclassOfAnAbstractClass"
        "AClass"                               || KlumFactory.Unkeyed   | "AClass"
        "AKeyedClass"                          || KlumFactory.Keyed     | "AKeyedClass"
        "ASubclassOfAKeyedClass"               || KlumFactory.Keyed     | "ASubclassOfAKeyedClass"
        "AbstractWithDefaultImpl"              || KlumFactory.Unkeyed   | "DefaultImpl"
        "DefaultImpl"                          || KlumFactory.Unkeyed   | "DefaultImpl"
    }

    def "basic test"() {
        given:
        createClass '''
import com.blackbuild.groovy.configdsl.transform.DSL

@DSL class AClass {}
'''
        when:
        clazz.Create.One()

        then:
        noExceptionThrown()
    }

    def "allow overriding of factory base class with ungeneric factory"() {
        given:
        createClass '''
import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.util.KlumFactory

@DSL(factory = MyClassFactory)
class MyClass {
    String name
    String job
}

class MyClassFactory extends KlumFactory.Unkeyed<MyClass> {
    MyClassFactory() {
        super(MyClass)
    }
    
    public MyClass baker(String name) {
        return Create.With(name: name, job: "baker")
    }
}
'''
        expect:
        getClass('MyClassFactory').isInstance(clazz.Create)

        when:
        instance = clazz.Create.baker("Hans")

        then:
        instance.name == "Hans"
        instance.job == "baker"
    }

    def "allow overriding of factory base class with generic factory"() {
        given:
        createClass '''
import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.util.KlumFactory

@DSL(factory = MyClassFactory)
class MyClass {
    String name
    String job
}

class MyClassFactory<T> extends KlumFactory.Unkeyed<T> {
    MyClassFactory(Class<T> type) {
        super(type)
    }
    
    public T baker(String name) {
        return Create.With(name: name, job: "baker")
    }
}
'''
        expect:
        getClass('MyClassFactory').isInstance(clazz.Create)

        when:
        instance = clazz.Create.baker("Hans")

        then:
        instance.name == "Hans"
        instance.job == "baker"
    }

    def "allow overriding of factory base class with implicit factory"() {
        given:
        createClass '''
import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.util.KlumFactory

@DSL
class MyClass {
    String name
    String job
    
    static class Factory extends KlumFactory.Unkeyed<MyClass> {
        Factory() {
            super(MyClass)
        }
        
        public MyClass baker(String name) {
            return Create.With(name: name, job: "baker")
        }
    }
}

'''
        expect:
        getClass('MyClass$Factory').isInstance(clazz.Create)

        when:
        instance = clazz.Create.baker("Hans")

        then:
        instance.name == "Hans"
        instance.job == "baker"
    }

    def "allow overriding of factory base class with for abstract classes"() {
        given:
        createClass '''
import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.util.KlumFactory

@DSL
abstract class MyClass {
    String name
    String job
    
    static class Factory extends KlumFactory<MyClass> {
        Factory() {
            super(MyClass)
        }
        
        public MyClass baker(String name) {
            return MyClassImpl.Create.With(name: name, job: "baker")
        }
    }
}

@DSL class MyClassImpl extends MyClass {}
'''
        expect:
        getClass('MyClass$Factory').isInstance(clazz.Create)

        when:
        instance = clazz.Create.baker("Hans")

        then:
        instance.name == "Hans"
        instance.job == "baker"
    }

    @Issue("359")
    def "complex structure with Create.FromMap"() {
        given:
        createClass '''
package pk

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Key

@DSL class Outer {
    String name
    String job
    Inner inner
    Map<String, OtherInner> others
}

@DSL class Inner {
    String name
    String job
}

@DSL class OtherInner {
    @Key String name
    String job
}
'''
        when:
        def instance = clazz.Create.FromMap([
            name: "Hans",
            job: "baker",
            inner: [
                name: "Peter",
                job: "baker"
            ],
            others: [
                "one": [
                    name: "Paul",
                    job: "baker"
                ],
                "two": [
                    name: "Mary",
                    job: "baker"
                ]
            ]
        ])

        then:
        instance.name == "Hans"
        instance.job == "baker"
        instance.inner.name == "Peter"
        instance.inner.job == "baker"
        instance.others.one.name == "Paul"
        instance.others.one.job == "baker"
        instance.others.two.name == "Mary"
        instance.others.two.job == "baker"
    }

    @Issue("359")
    def "complex structure with subclasses"() {
        given:
        createClass '''
package pk

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Key

@DSL class Outer {
    String name
    String job
    Inner inner
}

@DSL abstract class Inner {
    String name
    String job
}

@DSL class ConcreteInner extends Inner {}
'''
        when:
        def instance = clazz.Create.FromMap([
            name: "Hans",
            job: "baker",
            inner: [
                '@type': 'ConcreteInner',
                name: "Peter",
                job: "baker"
            ]
        ])

        then:
        instance.name == "Hans"
        instance.job == "baker"
        instance.inner.name == "Peter"
        instance.inner.job == "baker"
        instance.inner.getClass() == ConcreteInner
    }

    @Issue("359")
    def "convert map before creating instance"() {
        given:
        createClass '''
import com.blackbuild.klum.ast.util.KlumFactory

@DSL class Person {
    String firstName
    String lastName
 
    static class Factory extends KlumFactory.Unkeyed<Person> {
        protected Factory() { super(Person) }
        
        @Override
        Person FromMap(Map map) {
            def transformedMap = map.collectEntries { k, v ->
                // transform key from kebap to camel case
                [(k as String).tokenize('-').collect { it.capitalize() }.join('').uncapitalize(), v]
            }
            return super.FromMap(transformedMap)
        }
    }
} 
'''
        when:
        def person = Person.Create.FromMap(['first-name': 'Klaus', 'last-name': 'Müller'])

        then:
        person.firstName == 'Klaus'
        person.lastName == 'Müller'
    }

    @Issue("359")
    def "convert map before creating nested instance"() {
        given:
        createClass '''
import com.blackbuild.klum.ast.util.KlumFactory

@DSL class Person {
    String firstName
    String lastName
 
    Address address
} 

@DSL class Address {
    String street
    String city

    static class Factory extends KlumFactory.Unkeyed<Address> {
        protected Factory() { super(Address) }
        
        @Override
        Address FromMap(Map map) {
            def transformedMap = [
                street: map.number ? "$map.street $map.number" as String : map.street, 
                city: map.zip ? "$map.city $map.zip" as String : map.city
            ]
            return super.FromMap(transformedMap)
        }
    }
}

'''
        when:
        def person = Person.Create.FromMap(['firstName': 'Klaus', 'lastName': 'Müller', address: ['street': 'Hauptstraße', 'number': '12', 'city': 'München', 'zip': '80331']])

        then:
        person.firstName == 'Klaus'
        person.lastName == 'Müller'
        person.address.street == 'Hauptstraße 12'
        person.address.city == 'München 80331'
    }

}
