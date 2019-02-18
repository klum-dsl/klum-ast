/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform


import spock.lang.Issue

@SuppressWarnings("GroovyAssignabilityCheck")
@Issue("148")
class ConverterSpec extends AbstractDSLSpec {

    def "allow converter methods for simple fields"() {
        when:
        createClass '''
            @DSL class Foo {
                Closure closure
                
                @Mutator
                void closure(String literal) {
                    closure { literal }
                }
            }
            '''

        then:
        rwClazz.getMethod("closure", Closure)
        rwClazz.getMethod("closure", String)
    }

    def "generated converter for simple field"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(converters = [
                    {long value -> new Date(value)},
                    {int day, int month, int year -> new Date(year, month, day)}
                ])
                Date date
            }
            '''

        then:
        rwClazz.getMethod("date", Date)
        rwClazz.getMethod("date", long)
        rwClazz.getMethod("date", int, int, int)

        when:
        instance = clazz.create {
            date 123L
        }

        then:
        instance.date.time == 123L

        when:
        instance = clazz.create {
            date 25,5,2018
        }

        and:
        Date date = instance.date

        then:
        date.date == 25
        date.month == 5
        date.year == 2018
    }

    def "generated converter for dsl field"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(converters = [{long value -> Bar.create(value: new Date(value))}])
                Bar bar
            }
            
            @DSL class Bar {
                Date value
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", long)

        when:
        instance = clazz.create {
            bar 123L
        }

        then:
        instance.bar.value.time == 123L
    }

    def "allow converter methods for map fields"() {
        when:
        createClass '''
            @DSL class Foo {
                Map<String, Closure> closures
                
                @Mutator
                void closure(String key, String literal) {
                    closure(key) { literal }
                }
            }
            '''

        then:
        rwClazz.getMethod("closure", String, Closure)
        rwClazz.getMethod("closure", String, String)
    }

    def "generated converter for list fields"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(converters = [{long value -> new Date(value)}])
                List<Date> dates
            }
            '''

        then:
        rwClazz.getMethod("date", Date)
        rwClazz.getMethod("date", long)

        when:
        instance = clazz.create {
            date 123L
        }

        then:
        instance.dates.first().time == 123L
    }

    def "generated converter for map fields"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(converters = [{long value -> new Date(value)}])
                Map<String, Date> dates
            }
            '''

        then:
        rwClazz.getMethod("date", String, Date)
        rwClazz.getMethod("date", String, long)

        when:
        instance = clazz.create {
            date "bla", 123L
        }

        then:
        instance.dates.bla.time == 123L
    }

}
