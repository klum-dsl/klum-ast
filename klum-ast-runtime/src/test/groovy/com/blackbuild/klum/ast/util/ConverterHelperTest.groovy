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
//file:noinspection GroovyMissingReturnStatement
package com.blackbuild.klum.ast.util

import java.lang.reflect.Constructor

class ConverterHelperTest extends AbstractRuntimeTest {

    def "ConverterHelper returns constructors"() {
        when:
        def methods = ConverterHelper.getAllConverterMethods(URI)

        then:
        methods.size() == 1
        methods[0].name == "create"
    }

    def "ConverterHelper default converter methods"() {
        given:
        createClass '''
            class Test {
                Test() {}
                Test(String string) {}
            
                static Test create() {}
                static Test fromString(String string) {}
                static Test parseString(String string) {}
                static Test ofValues(List<String> strings) {}
                static String fromTest(Test test) {}
                static Test ignoredName() {}
            }
        '''

        when:
        def methods = ConverterHelper.getAllConverterMethods(clazz)
        def methodNames = methods*.name as Set

        then:
        methodNames == ["create", "fromString", "parseString", "ofValues"] as Set
    }
    def "ConverterHelper default converter methods and constructors"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.Converters
            
            @Converters(includeConstructors = true)
            class Test {
                Test() {}
                Test(String string) {}
            
                static Test create() {}
                static Test fromString(String string) {}
                static Test parseString(String string) {}
                static Test ofValues(List<String> strings) {}
                static String fromTest(Test test) {}
                static Test ignoredName() {}
            }
        '''

        when:
        def methods = ConverterHelper.getAllConverterMethods(clazz)
        def methodNames = methods*.name as Set

        then:
        methods.findAll {it instanceof Constructor}.size() == 2
        methodNames == ["create", "fromString", "parseString", "ofValues", "Test"] as Set
    }

    def "additionalIncludes"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.Converters
            
            @Converters(includeMethods = ["ignored"])
            class Test {
                static Test create() {}
                static Test fromString(String string) {}
                static Test parseString(String string) {}
                static Test ofValues(List<String> strings) {}
                static String fromTest(Test test) {}
                static Test ignoredName() {}
            }
        '''

        when:
        def methods = ConverterHelper.getAllConverterMethods(clazz)
        def methodNames = methods*.name as Set

        then:
        methodNames == ["create", "fromString", "parseString", "ofValues", "ignoredName"] as Set
    }

    def "additionalIncludes and excludes"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.Converters
            
            @Converters(includeMethods = ["ignored"], excludeMethods = ["from"])
            class Test {
                static Test create() {}
                static Test fromString(String string) {}
                static Test parseString(String string) {}
                static Test ofValues(List<String> strings) {}
                static String fromTest(Test test) {}
                static Test ignoredName() {}
            }
        '''

        when:
        def methods = ConverterHelper.getAllConverterMethods(clazz)
        def methodNames = methods*.name as Set

        then:
        methodNames == ["create", "parseString", "ofValues", "ignoredName"] as Set
    }

    def "additionalIncludes without default includes"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.Converters
            
            @Converters(includeMethods = ["ignored"], excludeDefaultPrefixes = true)
            class Test {
                static Test create() {}
                static Test fromString(String string) {}
                static Test parseString(String string) {}
                static Test ofValues(List<String> strings) {}
                static String fromTest(Test test) {}
                static Test ignoredName() {}
            }
        '''

        when:
        def methods = ConverterHelper.getAllConverterMethods(clazz)
        def methodNames = methods*.name as Set

        then:
        methodNames == ["ignoredName"] as Set
    }

    def "External Factories"() {
        given:
        createClass '''
            @Converters(TestFactory)
            class Test {
            }
            
            class TestFactory {
                static Test create() {}
                static Test fromString(String string) {}
                static Test parseString(String string) {}
                static Test ofValues(List<String> strings) {}
                static String fromTest(Test test) {}
                static Test ignoredName() {}
            }
        '''

        when:
        def methods = ConverterHelper.getAllConverterMethods(clazz)
        def methodNames = methods*.name as Set

        then:
        methodNames == ["create", "fromString", "parseString", "ofValues"] as Set
    }

    def "External Factories filtered by paramTypes"() {
        given:
        createClass '''
            @Converters(TestFactory)
            class Test {
            }
            
            class TestFactory {
                static Test create() {}
                static Test fromString(String string) {}
                static Test parseString(String string) {}
                static Test ofValues(List<String> strings) {}
                static String fromTest(Test test) {}
                static Test ignoredName() {}
            }
        '''

        when:
        def methods = ConverterHelper.getAllMatchingConverterMethods(clazz, String)
        def methodNames = methods*.name as Set

        then:
        methodNames == ["fromString", "parseString"] as Set
    }

    def "External Factories filtered by paramTypes with subtype"() {
        given:
        createClass '''
            @Converters(TestFactory)
            class Test {
            }
            
            class TestFactory {
                static Test fromString(CharSequence string) {}
                static Test parseString(String string) {}
            }
        '''

        when:
        def methods = ConverterHelper.getAllMatchingConverterMethods(clazz, StringBuilder)
        def methodNames = methods*.name as Set

        then:
        methodNames == ["fromString"] as Set
    }


}
