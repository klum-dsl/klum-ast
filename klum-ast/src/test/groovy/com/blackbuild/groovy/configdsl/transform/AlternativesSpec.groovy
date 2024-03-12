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
package com.blackbuild.groovy.configdsl.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import java.lang.annotation.Annotation

import static com.blackbuild.groovy.configdsl.transform.TestHelper.delegatesToPointsTo

class AlternativesSpec extends AbstractDSLSpec {

    def "Alternatives class is created"() {
        given:
        createClass('''
package pk
@DSL
class Config {

    String name

    Map<String, Element> elements
    List<Element> moreElements
}

@DSL
abstract class Element {

    @Key String name
}

@DSL
class SubElement extends Element {

    String role
}

@DSL
class ChildElement extends Element {

    String game
}''')


        when:
        getClass('pk.Config$_elements')

        then:
        notThrown(ClassNotFoundException)


    }

    def "elements delegates to collectionFactory"() {
        when:
        createClass('''
package pk
@DSL
class Config {

    String name

    Map<String, Element> elements
    List<Element> moreElements
}

@DSL
abstract class Element {

    @Key String name
}

@DSL
class SubElement extends Element {

    String role
}

@DSL
class ChildElement extends Element {

    String game
}''')


        then:
        clazz.Create.With {
            name "test"

            elements {
                assert delegate.class.name == 'pk.Config$_elements'
            }
        }
    }

    @Issue("77")
    def "collection factory allows implicit templates"() {
        given:
        createClass('''
package pk
@DSL
class Config {
    Map<String, Element> elements
}

@DSL
class Element {
    @Key String name
    String value
}
''')
        when:
        instance = clazz.Create.With {
            elements(value: 'fromTemplate') {
                delegate.element("first")
            }
        }

        then:
        instance.elements.first.value == 'fromTemplate'
    }

    @Issue("77")
    def "collection factory allows explicit templates"() {
        given:
        createClass('''
package pk
@DSL
class Config {
    Map<String, Element> elements
}

@DSL
class Element {
    @Key String name
    String value
}
''')
        def template = getClass("pk.Element").createAsTemplate(value: "fromTemplate")

        when:
        instance = clazz.Create.With {
            elements(template) {
                element("first")
            }
        }

        then:
        instance.elements.first.value == 'fromTemplate'
    }

    def "alternative methods are working"() {
        given:
        createClass('''
package pk
@DSL
class Config {

    String name

    Map<String, Element> elements
    List<Element> moreElements
}

@DSL
abstract class Element {

    @Key String name
}

@DSL
class SubElement extends Element {

    String role
}

@DSL
class ChildElement extends Element {

    String game
}''')

        when:
        instance = clazz.Create.With {
            name "test"

            elements {
                subElement("blub")
                childElement("bli")
            }
        }

        then:
        instance.elements.size() == 2
        instance.elements.blub.class.simpleName == "SubElement"
        instance.elements.bli.class.simpleName == "ChildElement"
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    @Issue("270")
    def "factory methods are used for alternative methods"() {
        given:
        createClass('''
package pk
@DSL
class Config {

    String name

    Map<String, Element> elements
    List<Element> moreElements
}

@DSL
abstract class Element {

    @Key String name
}

@DSL
class SubElement extends Element {

    String role
    static SubElement fromString(String name, int role) {
        return SubElement.Create.With(name.toUpperCase(), role: name.toLowerCase() + role)
    } 
}
''')

        when:
        instance = clazz.Create.With {
            name "test"

            elements {
                subElement("Blub", 5)
            }
            moreElements {
                subElement("Bli", 3)
            }
        }

        then:
        instance.elements.size() == 1
        instance.elements.BLUB.class.simpleName == "SubElement"
        instance.elements.BLUB.name == "BLUB"
        instance.elements.BLUB.role == "blub5"

        and:
        instance.moreElements[0].class.simpleName == "SubElement"
        instance.moreElements[0].name == "BLI"
        instance.moreElements[0].role == "bli3"
    }

    def "alternative methods can get custom names"() {
        given:
        createClass('''
package pk
@DSL
class Config {

    String name

    Map<String, Element> elements
    List<Element> moreElements
}

@DSL
abstract class Element {

    @Key String name
}

@DSL(shortName = "subby")
class SubElement extends Element {

    String role
}

@DSL(shortName = "child")
class ChildElement extends Element {

    String game
}''')

        when:
        instance = clazz.Create.With {
            name "test"

            elements {
                subby("blub")
                child("bli")
            }
        }

        then:
        instance.elements.size() == 2
        instance.elements.blub.class.simpleName == "SubElement"
        instance.elements.bli.class.simpleName == "ChildElement"
    }

    def "common suffixes can be stripped from child alternatives"() {
        given:
        createClass('''
package pk
@DSL
class Config {

    String name

    Map<String, Element> elements
    List<Element> moreElements
}

@DSL(stripSuffix = "Element")
abstract class Element {

    @Key String name
}

@DSL
class SubElement extends Element {

    String role
}

@DSL
class ChildElement extends Element {

    String game
}''')

        when:
        instance = clazz.Create.With {
            name "test"

            elements {
                sub("blub")
                child("bli")
            }
        }

        then:
        instance.elements.size() == 2
        instance.elements.blub.class.simpleName == "SubElement"
        instance.elements.bli.class.simpleName == "ChildElement"
    }

    def "alternative names can also be configured for a given field"() {
        given:
        createClass('''
package pk
@DSL
class Config {
    String name
    @Field(alternatives = {[subby: SubElement, childy: ChildElement ]})
    Map<String, Element> elements
}

@DSL
abstract class Element {

    @Key String name
}

@DSL
class SubElement extends Element {

    String role
}

@DSL
class ChildElement extends Element {

    String game
}''')

        when:
        instance = clazz.Create.With {
            name "test"

            elements {
                subby("blub")
                childy("bli")
            }
        }

        then:
        instance.elements.size() == 2
        instance.elements.blub.class.simpleName == "SubElement"
        instance.elements.bli.class.simpleName == "ChildElement"
    }
    def "illegal alternatives"() {
        when:
        createClass("""
package pk
@DSL
class Config {
    String name
    @Field(alternatives = $value)
    Map<String, Element> elements
}

@DSL
abstract class Element {

    @Key String name
}

@DSL
class SubElement extends Element {

    String role
}

""")

        then:
        thrown(MultipleCompilationErrorsException)

        where:
        value               || scenario
        'String'            || 'no closure'
        '{ it -> true}'     || 'closure has parameters'
        '{[b: "b"]}'        || 'value is no Class'
        '{[("b"): "b"]}'    || 'key is no literal string'
        '{[b: String]}'     || 'value is no subclass of type'
    }

    def "DelegatesTo annotations for collection factory methods are created"() {
        given:
        createClass('''
package pk
@DSL
class Config {

    String name

    Map<String, Element> elements
}

@DSL
abstract class Element {

    @Key String name
}

@DSL
class SubElement extends Element {

    String role
}

@DSL
class ChildElement extends Element {

    String game
}''')

        def factory = getClass('pk.Config$_elements')

        when:
        Annotation[] elementAnnotations = factory.getMethod("subElement", String, Closure).parameterAnnotations[1]

        then:
        delegatesToPointsTo(elementAnnotations, 'pk.SubElement._RW')
    }

    def "common suffixes can be stripped from grand child alternatives as well"() {
        given:
        createClass('''
package pk
@DSL
class Config {

    String name
    List<Element> elements
}

@DSL(stripSuffix = "Element")
abstract class Element {
}

@DSL
class SubElement extends Element {
}

@DSL
class SubSubElement extends Element {
}''')

        when:
        instance = clazz.Create.With {
            name "test"

            elements {
                sub()
                subSub()
            }
        }

        then:
        instance.elements.size() == 2
        instance.elements[0].class.simpleName == "SubElement"
        instance.elements[1].class.simpleName == "SubSubElement"
    }

}
