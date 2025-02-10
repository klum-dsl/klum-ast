/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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

import com.blackbuild.klum.ast.util.layer3.KlumVisitorException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Ignore
import spock.lang.Issue

class DefaultValuesSpec extends AbstractDSLSpec {

    def "simple default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
            
                @Default(field = 'another')
                String value
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            another "a"
        }

        then:
        instance.value == "a"

        when:
        instance = create("pk.Foo") {
            value "b"
            another "a"
        }

        then:
        instance.value == "b"
    }

    @Ignore("Obsolete")
    def "undefaulted getter is created"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
            
                @Default(field = 'another')
                String value
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            another "a"
        }

        then:
        instance.value == "a"
        instance.$value == null

    }

    def "default value cascade"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                String base
            
                @Default(field = 'base')
                String value
                
                @Default(field = 'value')
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            base "a"
        }

        then:
        instance.another == "a"
    }

    def "closure default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                String name
            
                @Default(code ={ name.toLowerCase() })
                String lower
            }
'''
        when:
        instance = create("pk.Foo") {
            name "Hans"
        }

        then:
        instance.lower == "hans"
    }

    def "delegate default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Container {
                String name
                Element element
            }

            @DSL
            class Element {
                @Owner Container owner
            
                @Default(delegate = 'owner')
                String name
            }
'''
        when: "No name is set for the inner element"
        instance = create("pk.Container") {
            name "outer"
            element {

            }
        }

        then: "the name of the outer instance is used"
        instance.element.name == "outer"

        when:
        instance = create("pk.Container") {
            name "outer"
            element {
                name "inner"
            }
        }

        then:
        instance.element.name == "inner"
    }

    def "if delegate is null, delegate default returns null"() {
        given:
        createClass '''
            package pk

            @DSL
            class Container {
                String name
            
                Element element
            }


            @DSL
            class Element {
                @Owner Container owner
            
                @Default(delegate = 'owner')
                String name
            }
'''
        when: "No name is set for both instances"
        instance = create("pk.Container") {
            element {
            }
        }

        then:
        notThrown(NullPointerException)
        instance.element.name == null
    }

    def "It is illegal to use @Default without a member"() {
        when:
        createClass '''
            package pk

            @DSL
            class Element {
                @Default
                String name
            }
'''
        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "It is illegal to use more than one member of @Default"() {
        when:
        createClass '''
            package pk

            @DSL
            class Element {
                String other
            
                @Default(field = 'other', delegate = 'other')
                String name
            }
'''
        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "default values are coerced to target type"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                @Default(field = 'another')
                Integer value
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            another "10"
        }

        then:
        instance.value == 10
    }

    def "copyFrom should ignore default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                @Default(field = 'another')
                String value
                String another
            }
        '''

        when:
        def template = clazz.Create.Template {
            another "template"
        }

        def foo = clazz.Create.With {
            copyFrom template
            another = "model"
        }

        then:
        foo.another == "model"
        foo.value == "model"
    }

    @Issue("318")
    def "default methods should be lifecycle methods"() {
        when:
        createClass '''
            package pk

            @DSL
            class Foo {
                String value
                @Default
                void aDefaultMethod() {
                    value = "default"
                }
            }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
        hasNoMethod(clazz, "aDefaultMethod")
        hasNoMethod(rwClazz, "aDefaultMethod")

        when:
        def foo = create("pk.Foo")

        then:
        foo.value == "default"
    }

    @Issue("361")
    def "default values are taken from Default Values annotations"() {
        given:
        createSecondaryClass '''
            package pk

import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD])
            @DefaultValues
            @interface FooDefaults {
                String name() default ""
                int age() default 0
            }
'''

        createClass '''
            package pk

            @DSL
            abstract class Foo {
                String name
                int age
            }
            
            @FooDefaults(name = "defaultName", age = 42)
            @DSL class Bar extends Foo {
            }
        '''

        when:
        def bar = Bar.Create.One()

        then:
        bar.name == "defaultName"
        bar.age == 42
    }

    @Issue("361")
    def "Basic conversions do happen for Default Values annotations"() {
        given:
        createSecondaryClass '''
            package pk

import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD])
            @DefaultValues
            @interface FooDefaults {
                String[] names() default []
            }
'''

        createClass '''
            package pk

            @DSL
            abstract class Foo {
                List<String> names
            }
            
            @FooDefaults(names = ["bla", "blub"])
            @DSL class Bar extends Foo {
            }
        '''

        when:
        def bar = Bar.Create.One()

        then:
        bar.names == ["bla", "blub"]
    }

    @Issue("361")
    def "Closures for Default Values annotations are executed to determine value"() {
        given:
        createSecondaryClass '''
            package pk

import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD])
            @DefaultValues
            @interface FooDefaults {
                Class<? extends Closure> name() default NoClosure
            }
'''

        createClass '''
            package pk

            @DSL
            abstract class Foo {
                String name
            }
            
            @FooDefaults(name = { "bla" })
            @DSL class Bar extends Foo {
            }
        '''

        when:
        def bar = Bar.Create.One()

        then:
        bar.name == "bla"
    }

    @Issue("361")
    def "Closures for Default Values annotations are not executed if the field is a closure"() {
        given:
        createSecondaryClass '''
            package pk

import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD])
            @DefaultValues
            @interface FooDefaults {
                Class<? extends Closure> name() default NoClosure
            }
'''

        createClass '''
            package pk

            @DSL
            abstract class Foo {
                Closure<?> name
            }
            
            @FooDefaults(name = { "bla" })
            @DSL class Bar extends Foo {
            }
        '''

        when:
        def bar = Bar.Create.One()

        then:
        bar.name instanceof Closure
        bar.name.call() == "bla"
    }

    @Issue("361")
    def "default values are taken from annotation on owner field"() {
        given:
        createSecondaryClass '''
            package pk

import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD])
            @DefaultValues
            @interface BarDefaults {
                String name() default ""
            }
'''

        createClass '''
            package pk

            @DSL
            class Foo {
                @BarDefaults(name = "defaultName")
                Bar bar
            }
            
            @DSL class Bar {
                String name
            }
        '''

        when:
        def foo = Foo.Create.With {
            bar()
        }

        then:
        foo.bar.name == "defaultName"
    }

    @Issue("361")
    def "default values for collection elements are taken from annotation on owner field"() {
        given:
        createSecondaryClass '''
            package pk

import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD])
            @DefaultValues
            @interface BarDefaults {
                String name() default ""
            }
'''

        createClass '''
            package pk

            @DSL
            class Foo {
                @BarDefaults(name = "defaultName")
                List<Bar> bars
            }
            
            @DSL class Bar {
                String name
            }
        '''

        when:
        def foo = Foo.Create.With {
            bar()
            bar()
        }

        then:
        foo.bars[0].name == "defaultName"
        foo.bars[1].name == "defaultName"
    }

    @Issue("361")
    def "default values for map elements are taken from annotation on owner field"() {
        given:
        createSecondaryClass '''
            package pk

import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD])
            @DefaultValues
            @interface BarDefaults {
                String name() default ""
            }
'''

        createClass '''
            package pk

            @DSL
            class Foo {
                @BarDefaults(name = "defaultName")
                Map<String, Bar> bars
            }
            
            @DSL class Bar {
                @Key String key
                String name
            }
        '''

        when:
        def foo = Foo.Create.With {
            bar("Klaus")
            bar("Dieter")
            bar("Hans", name: "explicit")
        }

        then:
        foo.bars["Klaus"].name == "defaultName"
        foo.bars["Dieter"].name == "defaultName"
        foo.bars["Hans"].name == "explicit"
    }

    @Issue("361")
    def "default values with no matching fields fail unless strict is turned off"() {
        given:
        createSecondaryClass '''
            package pk

import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD])
            @DefaultValues
            @interface FooStrictDefaults {
                String name() default ""
                String game() default ""
            }

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE, ElementType.FIELD])
            @DefaultValues(ignoreUnknownFields = true)
            @interface FooLenientDefaults {
                String name() default ""
                String game() default ""
            }
'''

        createClass '''
            package pk

            @DSL
            abstract class Foo {
                String name
            }
            
            @FooStrictDefaults(name = "defaultName", game = "defaultGame")
            @DSL class StrictBar extends Foo {
            }
            
            @FooLenientDefaults(name = "defaultName", game = "defaultGame")
            @DSL class LenientBar extends Foo {
            }
        '''

        when:
        def bar = StrictBar.Create.One()

        then:
        thrown(KlumVisitorException)

        when:
        bar = LenientBar.Create.One()

        then:
        bar.name == "defaultName"
    }

}
