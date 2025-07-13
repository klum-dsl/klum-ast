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
//file:noinspection GroovyVariableNotAssigned
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.util.KlumInstanceProxy
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Ignore
import spock.lang.Issue

/**
 * Copy of TemplatesSpec, but using the Template.With and Template.Create methods
 */
class BoundTemplatesSpec extends AbstractDSLSpec {

    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "empty template fields are not copied"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
        ''')

        when:
        def template = clazz.Template.Create {
            name "Welt"
            value null
        }

        instance = clazz.Create.With {
            name "toOverride"
            value "orig"
            copyFrom template
        }

        then:
        instance.name == "Welt"

        and: "empty values are not copied"
        instance.value == "orig"
    }

    def "create method should apply template"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
        ''')

        and:
        def template = clazz.Template.Create {
            name "Default"
            value "DefaultValue"
        }

        when:
        clazz.Template.With(template) {
            instance = clazz.Create.With {
                name "own"
            }
        }

        then:
        instance.name == "own"
        instance.value == "DefaultValue"
    }

    def "createAsTemplate should never call lifecycle methods"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                boolean postApplyCalled
                boolean postCreateCalled

                @PostApply
                void markPostApplyCalled() {
                    postApplyCalled = true
                }
                @PostCreate
                void markPostCreateCalled() {
                    postCreateCalled = true
                }

            }
        ''')

        when:
        def template = clazz.Template.Create {
            name "Default"
        }

        then:
        template.postApplyCalled == false
        template.postCreateCalled == false
    }

    def "create method should apply template for keyed objects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String value
                String value2
            }
        ''')

        and:
        def template = clazz.Template.Create {
            value "DefaultValue"
            value2 "DefaultValue2"
        }

        when:
        def currentTemplates = null
        clazz.Template.With(template) {
            instance = clazz.Create.With("Hallo") {
                currentTemplates = KlumInstanceProxy.getProxyFor(delegate).currentTemplates
                value "own"
            }
        }

        then:
        instance.name == "Hallo"
        instance.value == "own"
        instance.value2 == "DefaultValue2"
        currentTemplates == [(clazz): template]
    }

    def "Lists and Maps in template object should be cloned"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<String> names
            }
        ''')

        and:
        def template = Foo.Template.Create {
            names "a", "b"
        }

        when:
        Foo.Template.With(template) {
            instance = Foo.Create.With {}
        }

        then:
        !instance.names.is(template.names)
    }

    def "BUG applying a template with a List leads to UnsupportedOperationException"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                @Key String key
                List<String> names
            }
            @DSL
            class Child extends Parent {
                String value = "default"
            }
            @DSL
            class GrandChild extends Child {
                String value2
            }
        ''')
        def childClass = getClass("pk.Child")
        def grandChildClass = getClass("pk.GrandChild")

        and:
        def childTemplate
        def template = clazz.Template.Create {
            name "bli"
        }
        clazz.Template.With(template) {
            childTemplate = childClass.Template.Create {}
        }

        when:
        clazz.Template.With(childTemplate) {
            instance = grandChildClass.Create.With("Bla") {}
        }

        then:
        notThrown(UnsupportedOperationException)
        "bli" in instance.names
    }

    def "template for parent class affects child instances"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        def template = getClass("pk.Parent").Template.Create {
            name "default"
        }

        when:
        getClass("pk.Parent").Template.With(template) {
            instance = create("pk.Child") {}
        }

        then:
        instance.name == "default"
    }

    def "abstract class creates a artificial implementation"() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Parent {
              String name  
              abstract String calcName()
            }
            
            @DSL
            class Child extends Parent {
                String calcName() {
                  "$name-child"
                }
            }
        ''')

        expect:
        getClass('pk.Parent$Template') != null

        when:
        def template = clazz.Template.Create(name: 'Dieter')

        then:
        notThrown(InstantiationException)
        getClass('pk.Parent$Template').isInstance(template)
        template.name == 'Dieter'

        when:
        clazz.Template.With(template) {
            instance = getClass('pk.Child').Create.One()
        }

        then:
        instance.name == 'Dieter'

        when: 'Using copyFrom'
        instance = getClass('pk.Child').Create.With(copyFrom: template)

        then:
        instance.name == 'Dieter'
    }

    def "abstract class abstract methods with primitive return types "() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Parent {
              abstract int calcValue()
            }
        ''')

        expect:
        getClass('pk.Parent$Template') != null

        when:
        instance = getClass("pk.Parent").Template.Create()

        then:
        notThrown(InstantiationException)
        getClass('pk.Parent$Template').isInstance(instance)
    }

    def "abstract keyed class creates a artificial implementation"() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Parent {
              @Key String name
            }
        ''')

        expect:
        getClass('pk.Parent$Template') != null

        when:
        getClass("pk.Parent").Template.Create()

        then:
        notThrown(InstantiationException)
    }

    def "template for child class sets parent fields"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        def template = getClass("pk.Child").Template.Create {
            name "default"
        }

        when:
        getClass("pk.Child").Template.With(template) {
            instance = create("pk.Child") {}
        }

        then:
        instance.name == "default"
    }

    def "template for child class sets child fields"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        def template = getClass("pk.Child").Template.Create {
            name "default"
            value "defaultValue"
        }

        when:
        getClass("pk.Child").Template.With(template) {
            instance = create("pk.Child") {}
        }

        then:
        instance.name == "default"
        instance.value == "defaultValue"
    }

    def "child template overrides parent template"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        def template = getClass("pk.Parent").Template.Create {
            name "parent"
        }
        def childTemplate = getClass("pk.Child").Template.Create {
            name "child"
        }

        when:
        getClass("pk.Parent").Template.With(template) {
            getClass("pk.Child").Template.With(childTemplate) {
                instance = create("pk.Child") {}
            }
        }

        then:
        instance.name == "child"
    }

    def "Default value in sub closures"() {
        given:
        createClass('''
            package pk

            @DSL
            class Config {
                Child child
            }

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        def template = getClass("pk.Child").Template.Create {
            name "child"
        }

        when:
        getClass("pk.Child").Template.With(template) {
            instance = clazz.Create.With {
                child {}
            }
        }

        then:
        instance.child.name == "child"
    }

    def "Default value in sub closures with parent template"() {
        given:
        createClass('''
            package pk

            @DSL
            class Config {
                Child child
            }

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        def template = getClass("pk.Parent").Template.Create {
            name "parent"
        }

        when:
        getClass("pk.Parent").Template.With(template) {
            instance = clazz.Create.With {
                child {}
            }
        }

        then:
        instance.child.name == "parent"
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "Default value in list closures"() {
        given:
        createClass('''
            package pk

            @DSL
            class Config {
                @Field(members = "child")
                List<Child> children
            }

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        def template = getClass("pk.Child").Template.Create {
            name "child"
        }

        when:
        getClass("pk.Child").Template.With(template) {
            instance = clazz.Create.With {
                children {
                    child {}
                }
            }
        }

        then:
        instance.children[0].name == "child"
    }

    def "order of precedence for templates"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name = "default"
            }

            @DSL
            class Child extends Parent {
            }
        ''')

        expect:
        create("pk.Child") {}.name == "default"

        when:
        def template = getClass("pk.Parent").Template.Create {
            name "parent"
        }
        getClass("pk.Child").Template.With(template) {
            instance = create("pk.Child") {}
        }

        then:
        instance.name == "parent"

        when:
        def otherTemplate = getClass("pk.Child").Template.Create {
            name "child"
        }
        getClass("pk.Child").Template.With(otherTemplate) {
            instance = create("pk.Child") {}
        }

        then:
        instance.name == "child"

        when:
        getClass("pk.Child").Template.With(otherTemplate) {
            instance = create("pk.Child") {name "explicit"}
        }

        then:
        instance.name == "explicit"
    }

    def "templates add to parent templates collections"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                List<String> names = ["default"]
            }

            @DSL
            class Child extends Parent {
            }
        ''')
        def Child = getClass("pk.Child")
        def Parent = getClass("pk.Parent")

        expect:
        Child.Create.One().names == ["default"]

        when:
        def template = Parent.Template.Create {
            names "parent"
        }

        then:
        template.names == ["default", "parent"]

        when:
        Child.Template.With(template) {
            instance = Child.Create.One()
        }

        then:
        instance.names == ["default", "parent"]

        when:
        def childTemplate
        Parent.Template.With(template) {
            childTemplate = Child.Template.Create {
                names "child"
            }
        }
        Child.Template.With(childTemplate) {
            instance = Child.Create.One()
        }

        then:
        instance.names == ["default", "parent", "child"]

        when:
        Child.Template.With(childTemplate) {
            instance = Child.Create.With { name "explicit" }
        }

        then:
        instance.names == ["default", "parent", "child", "explicit"]
    }

    def "explicitly override parent templates collections"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                List<String> names = ["default"]
            }

            @DSL
            class Child extends Parent {
            }
        ''')
        def Child = getClass("pk.Child")
        def Parent = getClass("pk.Parent")

        def template = Parent.Template.Create {
            names "parent"
        }

        when:
        def childTemplate
        Parent.Template.With(template) {
            childTemplate = Child.Template.Create {
                names = ["child"]
            }
        }
        Child.Template.With(childTemplate) {
            instance = Child.Create.With { name "explicit"}
        }

        then:
        instance.names == ["child", "explicit"]
    }

    def "BUG: apply overrides overridden values again"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
            }
        ''')
        def template = clazz.Template.Create {
            value "default"
        }

        when:
        clazz.Template.With(template) {
            instance = clazz.Create.With {
                value "non-default"
            }
        }

        then:
        instance.value == "non-default"

        when:
        instance.apply {}

        then:
        instance.value == "non-default"

    }

    def "locally applied templates"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
        ''')

        and:
        def template = clazz.Create.With {
            name "Default"
            value "DefaultValue"
        }

        when:
        clazz.Template.With(template) {
            instance = clazz.Create.With {
                name "own"
            }
        }

        then:
        instance.name == "own"
        instance.value == "DefaultValue"
    }

    @Ignore
    def "locally applied templates using map"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
        ''')

        and:
        def template = clazz.Create.With {
            name "Default"
            value "DefaultValue"
        }

        when:
        clazz.Template.WithMultiple((clazz): template) {
            instance = clazz.Create.With {
                name "own"
            }
        }

        then:
        instance.name == "own"
        instance.value == "DefaultValue"
    }

    def "locally applied templates using empty map"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
        ''')

        when:
        clazz.Template.WithMultiple([:]) {
            instance = clazz.Create.With {
                name "own"
            }
        }

        then:
        instance.name == "own"
        instance.value == null
    }

    @Ignore
    def "locally applied templates with map"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
            
            @DSL
            class Bar {
                String token
            }
        ''')
        def Foo = getClass("pk.Foo")
        def fooTemplate = Foo.Create.With(name: 'DefaultName')
        def Bar = getClass("pk.Bar")
        def barTemplate = Bar.Create.With(token: 'DefaultToken')


        def foo, bar
        when:
        clazz.Template.WithMultiple((Foo) : fooTemplate, (Bar) : barTemplate) {
            foo = Foo.Create.With {
                value 'blub'
            }

            bar = Bar.Create.One()
        }

        then:
        foo.name == 'DefaultName'
        bar.token == 'DefaultToken'
    }

    def "locally applied templates with list"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
            
            @DSL
            class Bar {
                String token
            }
        ''')
        def fooClass = getClass("pk.Foo")
        def fooTemplate = fooClass.Create.With(name: 'DefaultName')
        def barClass = getClass("pk.Bar")
        def barTemplate = barClass.Create.With(token: 'DefaultToken')


        def foo, bar
        when:
        clazz.Template.WithMultiple([fooTemplate, barTemplate]) {
            foo = fooClass.Create.With {
                value 'blub'
            }

            bar = barClass.Create.One()
        }

        then:
        foo.name == 'DefaultName'
        bar.token == 'DefaultToken'
    }

    def "locally applied templates with list containing abstract templates"() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Foo {
                String name
                String value
            }
            
            @DSL
            class Bar extends Foo {
                String token
            }
        ''')
        def fooClass = getClass('pk.Foo')
        def fooTemplate = fooClass.Template.Create(name: 'DefaultName')

        def bar
        when:
        clazz.Template.WithMultiple([fooTemplate]) {
            bar = getClass('pk.Bar').Create.With {
                token "b"
            }
        }

        then:
        bar.name == 'DefaultName'
    }

    @Ignore
    def "parent child collections with map"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                List<String> names = ["default"]
            }

            @DSL
            class Child extends Parent {
            }
        ''')
        def parentClass = getClass("pk.Parent")
        def childClass = getClass('pk.Child')

        when:
        getClass("pk.Parent").Template.WithMultiple((parentClass) : parentClass.Create.With(name: "parent"), (childClass): childClass.Create.With(names: ["child"])) {
            instance = create("pk.Child") { name "explicit" }
        }

        then:
        instance.names == ["default", "child", "explicit"]
    }

    def "convenience template using named parameter"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
        ''')

        when:
        clazz.Template.With(name: "Default", value: "DefaultValue") {
            instance = clazz.Create.With {
                name "own"
            }
        }

        then:
        instance.name == "own"
        instance.value == "DefaultValue"
    }

    def "locally applied templates with convenience map"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
            
            @DSL
            class Bar {
                String token
            }
        ''')
        def fooClass = getClass("pk.Foo")
        def barClass = getClass("pk.Bar")


        def foo, bar
        when:
        clazz.Template.WithMultiple((fooClass) : [name: 'DefaultName'], (barClass) : [token: 'DefaultToken']) {
            foo = fooClass.Create.With {
                value 'blub'
            }

            bar = barClass.Create.One()
        }

        then:
        foo.name == 'DefaultName'
        bar.token == 'DefaultToken'
    }

    def "convenience template deactivates validation"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                @Validate String value
            }
        ''')

        when:
        clazz.Template.With(name: "Default") {
            instance = clazz.Create.With {
                value "bla"
            }
        }

        then:
        instance.name == "Default"
        instance.value == "bla"
    }

    def "use Template classes for Templates of abstract classes"() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Foo {
                String name
                abstract doIt()
            }
            
            @DSL
            class Bar extends Foo {
                String value
                
                def doIt() {}
            }
        ''')
        def template = getClass('pk.Foo').Template.Create {
            name "Default"
        }

        when:
        getClass("pk.Foo").Template.With(template) {
            instance = getClass("pk.Bar").Create.With {
                value "bla"
            }
        }

        then:
        instance.name == "Default"
        instance.value == "bla"
    }


    def "convenience template uses template class for abstract classes"() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Foo {
                String name
            }
            
            @DSL
            class Bar extends Foo {
                String value
            }
        ''')

        when:
        getClass("pk.Foo").Template.With(name: "Default") {
            instance = getClass("pk.Bar").Create.With {
                value "bla"
            }
        }

        then:
        instance.name == "Default"
        instance.value == "bla"
    }

    @Issue('https://github.com/klum-dsl/klum-ast/issues/82')
    def "collection Factories should not have a default value for closures"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                List<Inner> inners
            }
            @DSL
            class Inner {
                String name
            }
        ''')

        when:
        rwClazz.getDeclaredMethod("inners")

        then:
        thrown(NoSuchMethodException)
    }

    def "BUG: abstract class extends implements an interface which is fullfilled by covariant Field method fails in template"() {
        when:
        createSecondaryClass '''
            package pk
            
            interface MyFieldInterface {}
            
            interface MyModelInterface {
                MyFieldInterface getField()
            }
            
            @DSL
            class FieldImplementer implements MyFieldInterface {}
            
            @DSL
            abstract class Implementer implements MyModelInterface {
                FieldImplementer field
            } 
            '''
            then:
            notThrown(MultipleCompilationErrorsException)
    }

    @Issue("322")
    def "allow creating a template from a script text"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
        '''
        def scriptFile = temporaryFolder.newFile("template.groovy")

        when:
        scriptFile.text = '''
            name "Default"
            value "DefaultValue"
        '''
        def template = clazz.Template.CreateFrom(scriptFile)

        then:
        template.name == "Default"
        template.value == "DefaultValue"

        when:
        template = clazz.Template.CreateFrom(scriptFile.toURI().toURL())

        then:
        template.name == "Default"
        template.value == "DefaultValue"
    }

    @Issue("322")
    def "allow creating a template from a script text for keyed class"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                @Key String key
                String name
                String value
            }
        '''
        def scriptFile = temporaryFolder.newFile("template.groovy")

        when:
        scriptFile.text = '''
            name "Default"
            value "DefaultValue"
        '''
        def template = clazz.Template.CreateFrom(scriptFile)

        then:
        template.key == null
        template.name == "Default"
        template.value == "DefaultValue"

        when:
        template = clazz.Template.CreateFrom(scriptFile.toURI().toURL())

        then:
        template.key == null
        template.name == "Default"
        template.value == "DefaultValue"
    }

    @Issue("322")
    def "allow creating a template from a script text for abstract class"() {
        given:
        createClass '''
            package pk

            @DSL
            abstract class Foo {
                String name
                String value
            }
        '''
        def scriptFile = temporaryFolder.newFile("template.groovy")

        when:
        scriptFile.text = '''
            name "Default"
            value "DefaultValue"
        '''
        def template = clazz.Template.CreateFrom(scriptFile)

        then:
        template.name == "Default"
        template.value == "DefaultValue"

        when:
        template = clazz.Template.CreateFrom(scriptFile.toURI().toURL())

        then:
        template.name == "Default"
        template.value == "DefaultValue"
    }

    @Issue("322")
    def "allow creating a template from a script text for abstract keyed class"() {
        given:
        createClass '''
            package pk

            @DSL
            abstract class Foo {
                @Key String key
                String name
                String value
            }
        '''
        def scriptFile = temporaryFolder.newFile("template.groovy")

        when:
        scriptFile.text = '''
            name "Default"
            value "DefaultValue"
        '''
        def template = clazz.Template.CreateFrom(scriptFile)

        then:
        template.key == null
        template.name == "Default"
        template.value == "DefaultValue"

        when:
        template = clazz.Template.CreateFrom(scriptFile.toURI().toURL())

        then:
        template.key == null
        template.name == "Default"
        template.value == "DefaultValue"
    }

    @Issue("368")
    def "instance proxies store the currently active templates"() {
        given:
        createClass('''
            package pk

import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate
import com.blackbuild.klum.ast.util.KlumInstanceProxy

            @DSL
            class Foo {
                String name
                @Field(FieldType.TRANSIENT)
                Map<Class, Object> activeTemplatesDuringAutoCreate
                
                @AutoCreate void storeTemplates() {
                    activeTemplatesDuringAutoCreate = KlumInstanceProxy.getProxyFor(this).currentTemplates
                }
            }
        ''')

        when:
        def template = clazz.Template.Create {
            name "Default"
        }

        then:
        template.name == "Default"

        when:
        def instance = clazz.Create.With {
            name "Instance"
        }
        def proxy = KlumInstanceProxy.getProxyFor(instance)

        then:
        instance.name == "Instance"
        proxy.currentTemplates == [:]

        when:
        def templatesDuringCreation = null
        clazz.Template.With(template) {
            instance = clazz.Create.With {
                name "Overridden"
                templatesDuringCreation = KlumInstanceProxy.getProxyFor(delegate).currentTemplates
            }
        }
        proxy = KlumInstanceProxy.getProxyFor(instance)

        then:
        instance.name == "Overridden"
        templatesDuringCreation == [(clazz): template]
        instance.activeTemplatesDuringAutoCreate == [(clazz): template]

        and: "templates are cleared after the instance is created"
        proxy.currentTemplates[clazz] == null
    }

    @Issue("376")
    def "applyLaterClosures are copied from template"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String fullName
            }
        ''')

        when:
        def template = Foo.Template.Create {
            applyLater {
                fullName name.toUpperCase()
            }
        }

        then: "applyLater is not run in templates"
        template.fullName == null

        when:
        def instance = Foo.Template.With(template) {
            Foo.Create.With {
                name "foo"
            }
        }

        then:
        instance.fullName == "FOO"
    }
}