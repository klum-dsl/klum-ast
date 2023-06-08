/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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

import com.blackbuild.klum.ast.util.AbstractRuntimeTest
import org.codehaus.groovy.runtime.DefaultGroovyMethods

import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.lang.reflect.Method

class ClusterModelTest extends AbstractRuntimeTest {

    def "annotated element is correctly resolved in all groovy versions"() {
        given:
        createClass '''
            class Test {
                String field
                String getMethod() {
                    return "m"
                }
            }
'''
        instance = clazz.newInstance()

        when:
        def propertyValues = DefaultGroovyMethods.getMetaPropertyValues(instance)
        def fieldProp = propertyValues.find { it.name == "field" }
        def methodProp = propertyValues.find { it.name == "method" }

        then:
        ClusterModel.getAnnotatedElementForProperty(instance, fieldProp) instanceof Field
        ClusterModel.getAnnotatedElementForProperty(instance, methodProp) instanceof Method
    }

    def "getPropertyMap resolves correctly"() {
        given:
        createClass '''
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            @Retention(RetentionPolicy.RUNTIME)
            @interface Important {}

            class Person {
                @Important String firstname
                @Important String lastname
                String nickname
                int age
            }'''
        instance = newInstanceOf("Person", [firstname: "John", lastname: "Doe", nickname: "Johnny", age: 42])
        Class<Annotation> important = getClass("Important") as Class<Annotation>

        when:
        def props = ClusterModel.getPropertiesOfType(instance, String)

        then:
        props == [firstname: "John", lastname: "Doe", nickname: "Johnny"]

        when:
        props = ClusterModel.getPropertiesOfType(instance, String, {it.isAnnotationPresent(important) })

        then:
        props == [firstname: "John", lastname: "Doe"]

        when:
        props = ClusterModel.getPropertiesOfType(instance, String, important)

        then:
        props == [firstname: "John", lastname: "Doe"]
    }

    def "getCollectionsOfType returns only returns collections of correct type"() {
        given:
        createClass '''
            class Person {
                List<String> nicknames
                List<String> hobbies
                List<Integer> ages
                String name
            }
'''
        when:
        instance = newInstanceOf("Person", [nicknames: ["John", "Johnny"], hobbies: ["Soccer", "Tennis"], ages: [42, 43], name: "John Doe"])

        then:
        ClusterModel.getCollectionsOfType(instance, String) == [nicknames: ["John", "Johnny"], hobbies: ["Soccer", "Tennis"]]
        ClusterModel.getCollectionsOfType(instance, Integer) == [ages: [42, 43]]
    }

    def "isCollectionOf works"() {
        given:
        createInstance '''
           class Person {
                List<String> nicknames
                List<String> hobbies
                List<Integer> ages
                String name
            }
'''
        when:
        Map<String, PropertyValue> props = instance.getMetaPropertyValues().collectEntries { [it.name, it] }

        then:
        ClusterModel.isCollectionOf(instance, props.nicknames, String)
        ClusterModel.isCollectionOf(instance, props.nicknames, CharSequence)
        !ClusterModel.isCollectionOf(instance, props.nicknames, GString)

        and:
        ClusterModel.isCollectionOf(instance, props.ages, Integer)
        ClusterModel.isCollectionOf(instance, props.ages, Number)
        !ClusterModel.isCollectionOf(instance, props.ages, Float)
    }

    def "isMapOf works"() {
        given:
        createInstance '''
           class Person {
                Map<String, String> nicknames
                Map<String, String> hobbies
                Map<String, Integer> ages
                String name
            }
'''
        when:
        Map<String, PropertyValue> props = instance.getMetaPropertyValues().collectEntries { [it.name, it] }

        then:
        ClusterModel.isMapOf(instance, props.nicknames, String)
        ClusterModel.isMapOf(instance, props.nicknames, CharSequence)
        !ClusterModel.isMapOf(instance, props.nicknames, GString)

        and:
        ClusterModel.isMapOf(instance, props.ages, Integer)
        ClusterModel.isMapOf(instance, props.ages, Number)
        !ClusterModel.isMapOf(instance, props.ages, Float)
    }
}
