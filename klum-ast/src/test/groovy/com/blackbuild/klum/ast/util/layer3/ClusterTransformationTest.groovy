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
package com.blackbuild.klum.ast.util.layer3

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import java.lang.reflect.Modifier


class ClusterTransformationTest extends AbstractDSLSpec {

    def "Cluster annotation on Map resolves correctly"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

            @DSL abstract class Named {
                @Cluster abstract Map<String, String> getStrings()
            }
            
            @DSL class Person extends Named {
                String firstname
                String lastname
                String nickname
                int age
            }'''
        instance = Person.Create.With {
            firstname "John"
            lastname "Doe"
            nickname "Johnny"
            age 42
        }

        when:
        def props = instance.strings

        then:
        props == [firstname: "John", lastname: "Doe", nickname: "Johnny"]
    }

    @Issue("365")
    def "Cluster factories are created"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

            @DSL abstract class Named {
                @Cluster Map<String, String> names
            }
            
            @DSL class Person extends Named {
                String firstname
                String lastname
                String nickname
                int age
            }'''
        instance = Person.Create.With {
            names {
                firstname "John"
                lastname "Doe"
                nickname "Johnny"
            }
            age 42
        }

        when:
        def props = instance.names

        then:
        props == [firstname: "John", lastname: "Doe", nickname: "Johnny"]
    }

    @Issue("365")
    def "Cluster factories are filtered"() {
        given:
        createSecondaryClass '''
            
import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

            @Retention(RetentionPolicy.RUNTIME)
            @interface Service {}

            @DSL class User {
                String name
                String password
            }

            @DSL abstract class Database {
                @Cluster Map<String, User> users
                @Cluster(Service) Map<String, User> serviceUsers
            }
            
            @DSL class MyDatabase extends Database {
                String url
                User admin
                User guest
                @Service User service1
                @Service User service2
            }'''

        when:
        MyDatabase.Create.With {
            url "jdbc:postgresql://localhost:5432/mydb"
            admin {
                name "admin"
                password "admin"
            }
            guest {
                name "guest"
                password "guest"
            }
            service1 {
                name "service1"
                password "service1"
            }
            service2 {
                name "service2"
                password "service2"
            }
        }

        then:
        noExceptionThrown()

        when:
        MyDatabase.Create.With {
            url "jdbc:postgresql://localhost:5432/mydb"
            users {
                admin {
                    name "admin"
                    password "admin"
                }
                guest {
                    name "guest"
                    password "guest"
                }
                service1 {
                    name "service1"
                    password "service1"
                }
                service2 {
                    name "service2"
                    password "service2"
                }
            }
        }

        then:
        noExceptionThrown()

        when:
        MyDatabase.Create.With {
            url "jdbc:postgresql://localhost:5432/mydb"
            serviceUsers {
                service1 {
                    name "service1"
                    password "service1"
                }
                service2 {
                    name "service2"
                    password "service2"
                }
            }
        }

        then:
        noExceptionThrown()

        when:
        MyDatabase.Create.With {
            url "jdbc:postgresql://localhost:5432/mydb"
            serviceUsers {
                admin {
                    name "admin"
                    password "admin"
                }
            }
        }

        then:
        thrown(MissingMethodException)
    }

    @Issue("365")
    def "Cluster outside creators are protected"() {
        given:
        createSecondaryClass '''
            
import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

            @DSL class User {
                String name
                String password
            }

            @DSL abstract class Database {
                @Cluster(bounded = true) Map<String, User> users
            }
            
            @DSL class MyDatabase extends Database {
                String url
                User admin
                User guest
            }'''

        when:
        MyDatabase.Create.With {
            url "jdbc:postgresql://localhost:5432/mydb"
            users {
                admin {
                    name "admin"
                    password "admin"
                }
                guest {
                    name "guest"
                    password "guest"
                }
            }
        }

        then:
        noExceptionThrown()
        getClass('MyDatabase$_RW').getDeclaredMethods()
                .findAll { it.name == "admin" }
                .every { Modifier.isProtected(it.modifiers) }
    }

    @Issue("366")
    def "Cluster annotation on Map field is correctly resolved"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

            @DSL abstract class Named {
                @Cluster Map<String, String> strings
            }
            
            @DSL class Person extends Named {
                String firstname
                String lastname
                String nickname
                int age
            }'''

        when:
        getClass("Named").getDeclaredField("strings")

        then:
        thrown(NoSuchFieldException)
        getClass("Named").getDeclaredMethod("getStrings")

        when:
        instance = Person.Create.With {
            firstname "John"
            lastname "Doe"
            nickname "Johnny"
            age 42
        }
        def props = instance.strings

        then:
        props == [firstname: "John", lastname: "Doe", nickname: "Johnny"]
    }

    def "Cluster annotation with nonNull on Map resolves correctly"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

            @DSL abstract class Named {
                @Cluster(includeNulls = false) abstract Map<String, String> getStrings()
            }
            
            @DSL class Person extends Named {
                String firstname
                String lastname
                String nickname
                int age
            }'''

        when:
        instance = Person.Create.With {
            firstname "John"
            nickname "Johnny"
            age 42
        }
        def props = instance.strings

        then:
        props == [firstname: "John", nickname: "Johnny"]
    }

    def "Cluster annotation with null values on Map resolves correctly"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

            @DSL abstract class Named {
                @Cluster abstract Map<String, String> getStrings()
            }
            
            @DSL class Person extends Named {
                String firstname
                String lastname
                String nickname
                int age
            }'''

        when:
        instance = Person.Create.With {
            firstname "John"
            nickname "Johnny"
            age 42
        }
        def props = instance.strings

        then:
        props == [firstname: "John", nickname: "Johnny", lastname: null]
    }

    def "Cluster annotation on Map with filter resolves correctly"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

            import java.lang.annotation.*
           
            
            @DSL abstract class Named {
                @Cluster(Important) 
                abstract Map<String, String> getStrings()
            }

            @Target(ElementType.FIELD)
            @Retention(RetentionPolicy.RUNTIME)
            @interface Important {}
            
            @DSL class Person extends Named {
                @Important String firstname
                @Important String lastname
                String nickname
                int age
            }'''
        instance = Person.Create.With {
            firstname "John"
            lastname "Doe"
            nickname "Johnny"
            age 42
        }

        when:
        def props = instance.strings

        then:
        props == [firstname: "John", lastname: "Doe"]
    }

    @SuppressWarnings(['GroovyMissingReturnStatement', 'GrMethodMayBeStatic'])
    def "Cluster annotation on Map works with various return values"() {
        when:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster
            abstract class Named {
                @Cluster Map<String, String> getStringsWithEmpty() {}
                @Cluster Map<String, String> getStringsWithNull() {null}
                @Cluster Map<String, String> getStringsWithEmptyMap() {[:]}
                @Cluster Map<String, String> getStringsWithReturnNull() { return null }
                @Cluster Map<String, String> getStringsWithReturnEmptyMap() { return [:] }
            }'''

        then:
        noExceptionThrown()
    }

    def "Cluster annotation on Map fails with actual content"() {
        when:
        createClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster
            abstract class Named {
                @Cluster Map<String, String> getStringsWithEmpty() { [a: "b"] }
            }'''

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "Cluster annotation on Map of collections works"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster
            @DSL abstract class Named {
                @Cluster abstract Map<String, List<String>> getStringLists()
            }

            @DSL class Person extends Named {
                List<String> nicknames
                List<String> hobbies
                List<Integer> ages
                String name
            }
'''
        when:
        instance = Person.Create.With {
            nicknames "John", "Johnny"
            hobbies "Soccer", "Tennis"
            ages 42, 43
            name "John Doe"
        }

        then:
        instance.stringLists == [nicknames: ["John", "Johnny"], hobbies: ["Soccer", "Tennis"]]
    }

    def "Cluster annotation on Map of collections without generics works"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster
            @DSL abstract class Named {
                @Cluster abstract Map<String, List> getLists()
            }

            @DSL class Person extends Named {
                List<String> nicknames
                List<String> hobbies
                List<Integer> ages
                String name
            }
'''
        when:
        instance = Person.Create.With {
            nicknames "John", "Johnny"
            hobbies "Soccer", "Tennis"
            ages 42, 43
            name "John Doe"
        }

        then:
        instance.lists == [nicknames: ["John", "Johnny"], hobbies: ["Soccer", "Tennis"], ages: [42, 43]]
    }

    def "Corner Case: Cluster annotation on Map of Ungeneric sublass of collection works"() {
        given: // no DSL classes, because currently we do not support ungeneric subcollections
        createSecondaryClass '''
            import com.blackbuild.klum.ast.util.layer3.annotations.Cluster
            class StringList extends ArrayList<String> {
                StringList(Collection<String> elements) {
                    super(elements)
                }
            }
            
            abstract class Named {
                @Cluster abstract Map<String, StringList> getStringLists()
            }

            class Person extends Named {
                Person(Collection<String> nicknames, Collection<String> hobbies, List<Integer> ages, String name) {
                    this.nicknames = new StringList(nicknames)
                    this.hobbies = new StringList(hobbies)
                    this.ages = ages
                    this.name = name
                }
                StringList nicknames
                StringList hobbies
                List<Integer> ages
                String name
            }
'''
        when:
        instance = newInstanceOf("Person", [["John", "Johnny"], ["Soccer", "Tennis"], [42, 43], "John Doe"] as Object[])

        then:
        instance.stringLists == [nicknames: ["John", "Johnny"], hobbies: ["Soccer", "Tennis"]]
    }


}
