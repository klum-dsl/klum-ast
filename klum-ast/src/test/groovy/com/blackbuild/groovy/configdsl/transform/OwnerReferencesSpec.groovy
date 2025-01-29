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

import com.blackbuild.klum.ast.process.DefaultKlumPhase
import com.blackbuild.klum.ast.process.KlumPhase
import com.blackbuild.klum.ast.process.PhaseDriver
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import spock.lang.Ignore
import spock.lang.Issue

@SuppressWarnings("GroovyAssignabilityCheck")
class OwnerReferencesSpec extends AbstractDSLSpec {

    def "if owners is specified, no owner accessor is created"() {
        when:
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

        then:
        rwClazz.metaClass.getMetaMethod("owner", clazz) == null
    }

    def "two different owners in hierarchy are allowed"() {
        when:
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

            @DSL
            class ChildBar extends Bar {
                @Owner Foo foo2
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "owner reference for single dsl object"() {
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
        def Bar = getClass("pk.Bar")

        when:
        instance = clazz.Create.With {
            bar {}
        }

        then:
        instance.bar.foo.is(instance)

        when:
        instance = clazz.Create.With {
            bar(Bar) {}
        }

        then:
        instance.bar.foo.is(instance)
    }

    def "reuse single dsl object sets owner reference if not set"() {
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

        when:
        def reuse = create("pk.Bar")

        then:
        reuse.foo == null

        when:
        instance = clazz.Create.With {
            bar reuse
        }

        then:
        reuse.foo.is(instance)

        when:
        def another = clazz.Create.With {
            bar reuse
        }

        then: "still"
        reuse.foo.is(instance)
    }

    def "using existing objects in list closure sets owner"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')
        def aBar = create("pk.Bar") {}

        when:
        instance = clazz.Create.With {
            bars {
                bar aBar
            }
        }

        then:
        instance.bars[0].foo.is(instance)
    }

    def "reusing of objects in list closure does not set owner"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')

        when:
        def aBar
        instance = clazz.Create.With {
            bars {
                aBar = bar {}
            }
        }

        def otherInstance = clazz.Create.With {
            bars {
                bar(aBar)
            }
        }

        then: "bar's owner should still be the first object"
        otherInstance.bars[0].foo.is(instance)
    }

    def "reusing of existing objects in map closure does not set owner"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                @Key String name
                @Owner Object outer
            }

            @DSL
            class Fum {
                Map<String, Bar> bars
            }
        ''')

        when:
        def aBar
        instance = create("pk.Foo") {
            aBar = bar("Klaus") {}
        }

        create("pk.Fum") {
            bars {
                bar aBar
            }
        }

        then:
        aBar.outer.is(instance)
    }


    def "using existing objects in map closure sets owner"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Key String name
                @Owner Foo foo
            }
        ''')
        def aBar = create("pk.Bar", "Klaus") {}

        when:
        instance = clazz.Create.With {
            bars {
                bar(aBar)
            }
        }

        then:
        instance.bars.Klaus.foo.is(instance)
    }

    def "owner reference for dsl object list"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')
        def Bar = getClass("pk.Bar")

        when:
        instance = clazz.Create.With {
            bars {
                bar {}
                bar(Bar) {}
            }
        }

        then:
        instance.bars[0].foo.is(instance)
        instance.bars[1].foo.is(instance)
    }

    def "owner reference for dsl object map"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
                @Key String name
            }
        ''')
        def Bar = getClass("pk.Bar")

        when:
        instance = clazz.Create.With {
            bars {
                bar("Klaus") {}
                bar(Bar, "Dieter") {}
            }
        }

        then:
        instance.bars.Klaus.foo.is(instance)
        instance.bars.Dieter.foo.is(instance)
    }

    def "owner will not be overridden"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')

        def aBar = create("pk.Bar") {}

        when:
        instance = clazz.Create.With {
            bar(aBar)
        }

        then:
        aBar.foo.is(instance)


        when:
        def anotherInstance = clazz.Create.With {
            bar(aBar)
        }

        then: "bar.owner is not replaced"
        aBar.foo.is(instance)
    }

    def "owner is not overridden in Maps"() {
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
                @Owner Foo foo
            }
        ''')
        def aBar = create("pk.Bar", "Klaus") {}
        def otherFoo = create("pk.Foo") {
            bar(aBar)
        }

        when:
        instance.apply {
            bars {
                bar(aBar)
            }
        }

        then:
        instance.bars.Klaus.foo.is(otherFoo)
    }

    def "owner is not overridden in Lists"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')
        def aBar = create("pk.Bar") {}
        def otherFoo = create("pk.Foo") {
            bar(aBar)
        }

        when:
        instance.apply {
            bars {
                bar(aBar)
            }
        }

        then:
        instance.bars[0].foo.is(otherFoo)
    }


    def "bug: Reusing an object in a different structure throws ClassCastException"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class OtherOwner {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')

        when:
        def aFoo = create("pk.Foo") {
            bars {
                bar {}
            }
        }

        def secondFoo = create("pk.OtherOwner") {
            bars {
                bar aFoo.bars[0]
            }
        }

        then:
        notThrown(GroovyCastException)
        secondFoo.bars[0].foo == aFoo
    }

    @Ignore("Obsolete with owner phases")
    def "owner must be set before calling the closure"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
                List<Bar> listBars
                Map<String, Keyed> keyeds
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }

            @DSL
            class Keyed {
                @Owner Foo foo
                @Key String name
            }
        ''')

        when:
        instance = clazz.Create.With {
            bar {
                assert owner.delegate.getClass().name == "pk.Foo$_RW"
            }
            listBars {
                bar {
                    assert foo != null
                }
            }
            keyeds {
                keyed("bla") {
                    assert foo != null
                }
            }

        }

        then:
        noExceptionThrown()
    }

    @Issue("https://github.com/klum-dsl/klum-ast/issues/171")
    def "Allow multiple owners"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Moo {
                Bar linkedBar
            }

            @DSL
            class Bar {
                @Owner Foo foo
                @Owner Moo moo
            }
        ''')

        when:
        def barInstance
        instance = clazz.Create.With {
            barInstance = bar {}
        }

        then:
        barInstance.foo.is(instance)
        barInstance.moo == null

        when:
        def instance2 = create("pk.Moo") {
            linkedBar barInstance
        }

        then:
        barInstance.foo.is(instance)
        barInstance.moo.is(instance2)
    }

    @Issue("https://github.com/klum-dsl/klum-ast/issues/171")
    def "Owners of parent class are set"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Moo {
                Bar linkedBar
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }

            @DSL
            class SubBar extends Bar {
                @Owner Moo moo
            }
        ''')

        when:
        def barInstance = create("pk.SubBar")
        instance = clazz.Create.With {
            bar barInstance
        }

        then:
        barInstance.foo.is(instance)
        barInstance.moo == null

        when:
        def instance2 = create("pk.Moo") {
            linkedBar barInstance
        }

        then:
        barInstance.foo.is(instance)
        barInstance.moo.is(instance2)
    }

    @Issue("https://github.com/klum-dsl/klum-ast/issues/176")
    def "Owner methods are called"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                
                @Field(FieldType.IGNORED)
                @Validate(Validate.Ignore)
                Foo foo
            
                @Owner 
                void setFooAsOwner(Foo foo) {
                    this.foo = foo
                }
            }
        ''')

        when:
        instance = clazz.Create.With {
            bar {}
        }

        then:
        instance.bar.foo.is(instance)

        when:
        Class Bar = getClass("pk.Bar")
        instance = clazz.Create.With {
            bar(Bar) {}
        }

        then:
        instance.bar.foo.is(instance)
    }

    @Issue("https://github.com/klum-dsl/klum-ast/issues/176")
    def "overridden Owner methods are called only once"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                boolean barCalled
            
                @Owner 
                void setFooAsOwner(Foo foo) {
                    barCalled = true
                }
            }
            @DSL
            class Boo extends Bar {
                boolean booCalled
            
                @Owner 
                void setFooAsOwner(Foo foo) {
                    booCalled = true
                }
            }
        ''')

        when:
        def Boo = getClass("pk.Boo")
        instance = clazz.Create.With {
            bar(Boo) {}
        }

        then: 'overridden method not called'
        !instance.bar.barCalled

        and:
        instance.bar.booCalled
    }

    def "Owner methods must have exactly one argument"() {
        when:
        createClass '''
            @DSL
            class Foo {
                @Owner 
                void setFooAsOwner() {}
            }'''

        then:
        thrown(MultipleCompilationErrorsException)

        when:
        createClass '''
            @DSL
            class Bar {
                @Owner 
                void setFooAsOwner(String name, String blame) {}
            }'''

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "Key Field must not have an Owner annotation"() {
        when:
        createClass '''
            @DSL
            class Foo {
                @Owner @Key String name
            }'''

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "Key Field must not have a Field annotation"() {
        when:
        createClass '''
            @DSL
            class Foo {
                @Field @Key String name
            }'''

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "Owner closures are executed as part of the owner phase"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
                String name
            }

            @DSL
            class Bar {
                @Owner Foo foo
            
                @Owner Closure ownerClosure
            }
        ''')

        when:
        KlumPhase closurePhase = null
        instance = clazz.Create.With {
            name "Klaus"
            bar {
                ownerClosure {
                    closurePhase = PhaseDriver.currentPhase
                    assert foo.name == "Klaus"
                }
            }
        }

        then:
        noExceptionThrown()
        closurePhase == DefaultKlumPhase.OWNER
    }

    @Issue("49")
    def "Transitive owners are set after normal owners"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                Child child
                String name
            }

            @DSL
            class Child {
                @Owner Parent parent
                GrandChild child
                String name
            }
            
            @DSL
            class GrandChild {
                @Owner Child parent
                @Owner(transitive = true) Parent grandParent
                String name
            }
        ''')

        when:
        instance = clazz.Create.With {
            name "Klaus"
            child {
                name "Child Level 1"
                child {
                    name "Child Level 2"
                }
            }
        }

        then:
        noExceptionThrown()
        instance.child.parent.is(instance)
        instance.child.child.parent.is(instance.child)
        instance.child.child.grandParent.is(instance)
    }

    def "Root owners are set without an explicit chain"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                Child child
                String name
            }

            @DSL
            class Child {
                GrandChild child
                String name
            }
            
            @DSL
            class GrandChild {
                @Owner(root = true) Parent grandParent
                String name
            }
        ''')

        when:
        instance = clazz.Create.With {
            name "Klaus"
            child {
                name "Child Level 1"
                child {
                    name "Child Level 2"
                }
            }
        }

        then:
        noExceptionThrown()
        instance.child.child.grandParent.is(instance)
    }

    @Issue("49")
    def "Transitive owners methods are called after normal owners"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                Child child
                String name
            }

            @DSL
            class Child {
                @Owner Parent parent
                GrandChild child
                String name
            }
            
            @DSL
            class GrandChild {
                @Owner Child parent
                String name
                String grandParentName
                
                @Owner(transitive = true) 
                void grandParent(Parent grandParent) {
                    grandParentName = grandParent.name
                }
            }
        ''')

        when:
        instance = clazz.Create.With {
            name "Klaus"
            child {
                name "Child Level 1"
                child {
                    name "Child Level 2"
                }
            }
        }

        then:
        noExceptionThrown()
        instance.child.child.grandParentName == "Klaus"
    }

    def "Root owners methods are called without explicit chain"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                Child child
                String name
            }

            @DSL
            class Child {
                GrandChild child
                String name
            }
            
            @DSL
            class GrandChild {
                String name
                String grandParentName
                
                @Owner(root = true) 
                void grandParent(Parent grandParent) {
                    grandParentName = grandParent.name
                }
            }
        ''')

        when:
        instance = clazz.Create.With {
            name "Klaus"
            child {
                name "Child Level 1"
                child {
                    name "Child Level 2"
                }
            }
        }

        then:
        noExceptionThrown()
        instance.child.child.grandParentName == "Klaus"
    }

    @Issue("189")
    def "Owner converters"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                Child child
                String name
            }

            @DSL
            class Child {
                @Owner Parent parent
                @Owner(converter = { Parent parent -> parent.name }) String parentName
                String name
                String upperCaseParentName
                
                @Owner(converter = { Parent parent -> parent.name.toUpperCase() })
                void setUCParentName(String name) {
                    upperCaseParentName = name.toUpperCase()
                }
            }
        ''')

        when:
        instance = clazz.Create.With {
            name "Klaus"
            child {
                name "Child"
            }
        }

        then:
        noExceptionThrown()
        instance.child.parent.is(instance)
        instance.child.parentName == "Klaus"
        instance.child.upperCaseParentName == "KLAUS"
    }

    @Issue("189")
    def "Combine Transitive owners and converters"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                Child child
                String name
            }

            @DSL
            class Child {
                @Owner Parent parent
                GrandChild child
                String name
            }
            
            @DSL
            class GrandChild {
                @Owner Child parent
                @Owner(transitive = true, converter = { Parent parent -> parent.name }) String grandParentName
                String name
                String upperCaseGrandParentName
                
                @Owner(transitive = true, converter = { Parent parent -> parent.name.toUpperCase() })
                void setUCGrandParentName(String name) {
                    upperCaseGrandParentName = name.toUpperCase()
                }
            }
        ''')

        when:
        instance = clazz.Create.With {
            name "Klaus"
            child {
                name "Child Level 1"
                child {
                    name "Child Level 2"
                }
            }
        }

        then:
        noExceptionThrown()
        instance.child.child.grandParentName == "Klaus"
        instance.child.child.upperCaseGrandParentName == "KLAUS"
    }

    @Issue("86")
    def "Role fields are set during Owner phase"() {
        given:
        createClass '''
            package pk

            @DSL
            class Database {
                DatabaseUser resourceUser
                DatabaseUser connectUser
                DatabaseUser monitoringUser
            }

            @DSL
            class DatabaseUser {
                @Owner Database database
                @Role String role
                
                String name
            }
        '''

        when:
        instance = clazz.Create.With {
            resourceUser {
                name "user1"
            }
            connectUser {
                name "user2"
            }
            monitoringUser {
                name "user3"
            }
        }

        then:
        noExceptionThrown()
        instance.resourceUser.role == "resourceUser"
        instance.connectUser.role == "connectUser"
        instance.monitoringUser.role == "monitoringUser"
    }

    @Issue("86")
    def "Role fields ca be filtered by type"() {
        given:
        createClass '''
            package pk

            @DSL
            class Database {
                User ddl
                User dml
            }

            @DSL
            class Service {
                User admin
                User access
            }

            @DSL
            class User {
                @Owner Owner container
                @Role(Database) String dbRole
                @Role(Service) String serviceRole
                
                String name
            }
        '''

        when:
        instance = create("pk.Database") {
            ddl {
                name "ddl"
            }
            dml {
                name "dml"
            }
        }

        then:
        instance.ddl.dbRole == "ddl"
        instance.ddl.serviceRole == null
        instance.dml.dbRole == "dml"
        instance.dml.serviceRole == null

        when:
        instance = create("pk.Service") {
            admin {
                name "admin"
            }
            access {
                name "access"
            }
        }

        then:
        instance.admin.dbRole == null
        instance.admin.serviceRole == "admin"
        instance.access.dbRole == null
        instance.access.serviceRole == "access"
    }

}
