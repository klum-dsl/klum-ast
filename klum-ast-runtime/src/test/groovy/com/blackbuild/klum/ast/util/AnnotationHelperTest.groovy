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

import spock.lang.Ignore

import java.lang.annotation.Annotation

class AnnotationHelperTest extends AbstractRuntimeTest {

    Class<? extends Annotation> MyAnnotation

    @Override
    def setup() {
        createClass '''
package annos
import java.lang.annotation.*
@Target([ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE])
@Retention(RetentionPolicy.RUNTIME)
@interface MyAnnotation {
    String value() default "inherit"
}

'''
        MyAnnotation = getClass("annos.MyAnnotation") as Class<? extends Annotation>

    }

    def "getMostSpecificAnnotation returns the annotation on the target itself"() {
        given:
        createClass '''
            package classes
            import annos.*

            @MyAnnotation("class")
            class Dummy {
                @MyAnnotation("field")
                String name

                @MyAnnotation("method")
                void name(String bla) {}
            }
        '''

        expect:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredField("name"), MyAnnotation).get().value() == "field"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredMethod("name", String), MyAnnotation).get().value() == "method"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz, MyAnnotation).get().value() == "class"
    }

    def "getMostSpecificAnnotation returns the one inherited from the class"() {
        given:
        createClass '''
            package classes
            import annos.*

            @MyAnnotation("class")
            class Dummy {
                String name
                void name(String bla) {}
            }
        '''

        expect:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredField("name"), MyAnnotation).get().value() == "class"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredMethod("name", String), MyAnnotation).get().value() == "class"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz, MyAnnotation).get().value() == "class"
    }

    def "getMostSpecificAnnotation returns the one inherited from the super class"() {
        given:
        createClass '''
            package classes
            import annos.*

            @MyAnnotation("parent-class")
            class Parent {
            }
            
            class Dummy extends Parent {
                String name
                void name(String bla) {}
            }
        '''
        clazz = getClass("classes.Dummy")
        Class<?> parent = getClass("classes.Parent")

        expect:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredField("name"), MyAnnotation).get().value() == "parent-class"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredMethod("name", String), MyAnnotation).get().value() == "parent-class"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz, MyAnnotation).get().value() == "parent-class"

        and:
        AnnotationHelper.getMostSpecificAnnotation(parent, MyAnnotation).get().value() == "parent-class"
    }

    def "getMostSpecificAnnotation with filter returns the one inherited from the super class"() {
        given:
        createClass '''
            package classes
            import annos.*

            @MyAnnotation("parent-class")
            class Parent {
            }
            
            @MyAnnotation("_class")
            class Dummy extends Parent {
                @MyAnnotation("_field")
                String name
                @MyAnnotation("_method")
                void name(String bla) {}
            }
        '''
        clazz = getClass("classes.Dummy")
        Class<?> parent = getClass("classes.Parent")

        expect:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredField("name"), MyAnnotation, { a -> !a.value().startsWith("_") }).get().value() == "parent-class"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredMethod("name", String), MyAnnotation, { a -> !a.value().startsWith("_") }).get().value() == "parent-class"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz, MyAnnotation, { a -> !a.value().startsWith("_") }).get().value() == "parent-class"

        and:
        AnnotationHelper.getMostSpecificAnnotation(parent, MyAnnotation, { a -> !a.value().startsWith("_") }).get().value() == "parent-class"
    }

    @Ignore("Need to find an elegant way to dynamically create a package-info.java file via groovy class loader")
    def "getMostSpecificAnnotation returns the inherited from the package"() {
        given:
        loader.parseClass('''
            @annos.MyAnnotation("package")
            package classes
        ''', "package-info.groovy")

        createClass '''
            @MyAnnotation("package")
            package classes
            import annos.*

            class Dummy {
                String name
                void name(String bla) {}
            }
        '''

        expect:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredField("name"), MyAnnotation).get().value() == "package"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz.getDeclaredMethod("name", String), MyAnnotation).get().value() == "package"

        and:
        AnnotationHelper.getMostSpecificAnnotation(clazz, MyAnnotation).get().value() == "package"
    }


}