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
//file:noinspection GrDeprecatedAPIUsage
package com.blackbuild.groovy.configdsl.transform

import groovy.time.TimeCategory
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
        given:
        Date dateFromInstance

        when:
        createClass '''
            @DSL class Foo {
                @Field(converters = [
                    {long value -> new Date(value)},
                    {int day, int month, int year -> new Date(year, month, day)},
                    {new Date()},
                ])
                Date date
            }
            '''

        then:
        rwClazz.getMethod("date", Date)
        rwClazz.getMethod("date", long)
        rwClazz.getMethod("date", int, int, int)
        rwClazz.getMethod("date")

        when:
        instance = clazz.Create.With {
            date 123L
        }

        then:
        instance.date.time == 123L

        when: "method with multiple parameters"
        instance = clazz.Create.With {
            date 25, 5, 2018
        }

        and:
        dateFromInstance = instance.date

        then:
        dateFromInstance.date == 25
        dateFromInstance.month == 5
        dateFromInstance.year == 2018

        when: "empty method"
        instance = clazz.Create.With {
            date()
        }

        then:
        TimeCategory.minus(instance.date, new Date()).days == 0
    }

    def "generated converter for dsl field"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(converters = [{long value -> Bar.Create.With(value: new Date(value))}])
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
        instance = clazz.Create.With {
            bar 123L
        }

        then:
        instance.bar.value.time == 123L
    }

    def "generated converter for keyed dsl field"() {
        when:
        createClass '''
            @DSL class Foo {
                @Field(converters = [{String name, long value -> Bar.Create.With(name, value: new Date(value))}])
                Bar bar
            }
            
            @DSL class Bar {
                @Key String name
                Date value
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", String, long)

        when:
        instance = clazz.Create.With {
            bar "bla", 123L
        }

        then:
        instance.bar.value.time == 123L
    }

    def "allow converter methods for map fields"() {
        when:
        //noinspection GroovyInfiniteRecursion
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
        instance = clazz.Create.With {
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
        instance = clazz.Create.With {
            date "bla", 123L
        }

        then:
        instance.dates.bla.time == 123L
    }

    def "converter factory for dsl field"() {
        when:
        createClass '''
            @DSL class Foo {
                Bar bar
            }
            
            @DSL class Bar {
                Date birthday
                
                static Bar fromLong(long value) {
                    return create(birthday: new Date(value))
                }
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", long)

        when:
        instance = clazz.Create.With {
            bar 123L
        }

        then:
        instance.bar.birthday.time == 123L
    }

    def "converter factory for dsl field with default values"() {
        when:
        createClass '''
            @DSL class Foo {
                Bar bar
            }
            
            @DSL class Bar {
                Date birthday
                String token
                
                static Bar fromLong(long value, String token = "dummy") {
                    return create(birthday: new Date(value), token: token)
                }
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", long)
        rwClazz.getMethod("bar", long, String)

        when:
        instance = clazz.Create.With {
            bar 123L
        }

        then:
        instance.bar.birthday.time == 123L
        instance.bar.token == "dummy"

        when:
        instance = clazz.Create.With {
            bar 123L, "flummy"
        }

        then:
        instance.bar.token == "flummy"
    }

    def "converter factory for keyed dsl field"() {
        when:
        createClass '''
            @DSL class Foo {
                Bar bar
            }
            
            @DSL class Bar {
                @Key String name
                Date birthday
                
                static Bar fromLong(String name, long value) {
                    return create(name, birthday: new Date(value))
                }
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", String, long)

        when:
        instance = clazz.Create.With {
            bar "bla", 123L
        }

        then:
        instance.bar.birthday.time == 123L
    }

    def "converter factory for keyed dsl list"() {
        when:
        createClass '''
            @DSL class Foo {
                List<Bar> bars
            }
            
            @DSL class Bar {
                @Key String name
                Date birthday
                
                static Bar fromLong(String name, long value) {
                    return create(name, birthday: new Date(value))
                }
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", String, long)

        when:
        instance = clazz.Create.With {
            bar "bla", 123L
        }

        then:
        instance.bars.first().birthday.time == 123L
    }

    def "converter factory for keyed dsl map"() {
        when:
        createClass '''
            @DSL class Foo {
                Map<String, Bar> bars
            }
            
            @DSL class Bar {
                @Key String name
                Date birthday
                
                static Bar fromLong(String name, long value) {
                    return create(name, birthday: new Date(value))
                }
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", String, long)

        when:
        instance = clazz.Create.With {
            bar "bla", 123L
        }

        then:
        instance.bars.bla.birthday.time == 123L
    }

    def "converter classes in Converters annotation"() {
        when:
        createClass '''
            @Converters(BarUtil)
            @DSL class Foo {
                Bar bar
            }
            
            class Bar {
                Date birthday
            }

            class BarUtil {
                static Bar fromLong(long value) {
                    return new Bar(birthday: new Date(value))
                }
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", long)

        when:
        instance = clazz.Create.With {
            bar 123L
        }

        then:
        instance.bar.birthday.time == 123L
    }

    def "Implicit converters"() {
        when:
        createClass '''
            @DSL class Foo {
                URI bar
            }
            '''

        then:
        rwClazz.getMethod("bar", URI)
        rwClazz.getMethod("bar", String)
    }

    def "Constructor converters"() {
        when:
        createClass '''
            @Converters(includeConstructors = true)
            @DSL class Foo {
                URI bar
            }
            '''

        then:
        rwClazz.getMethod("bar", URI)
        rwClazz.getMethod("bar", String, String, String, String)
    }

    def "convention named factories are automatically included"() {
        when:
        createClass '''
            @DSL class Foo {
                Bar bar
            }
            
            class Bar {
                static Bar fromString(String value) {
                    return new Bar()
                }
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", String)
    }

    def "Custom named factories are automatically included if annotated with Converter"() {
        when:
        createClass '''
            @DSL class Foo {
                Bar bar
            }
            
            class Bar {
                @Converter 
                static Bar juhu(String value) {
                    return new Bar()
                }
            }
            '''

        then:
        rwClazz.getMethod("bar", getClass("Bar"))
        rwClazz.getMethod("bar", String)
    }

    def "Converter parameters are prepended with key parameter for simple type maps"() {
        when:
        createClass '''
            @DSL class Foo {
                Map<String, Bar> bars
            }
            
            class Bar {
                static Bar of(String value) {
                    return new Bar()
                }
            }
            '''

        then:
        rwClazz.getMethod("bar", String, getClass("Bar"))
        rwClazz.getMethod("bar", String, String)
    }

    def "Converters on sublass of target"() {
        given:
        createClass '''
            import java.util.function.Supplier
            @DSL class Foo {
                @Converters(StringSupplier)
                Supplier<String> name
            }
            
            class StringSupplier implements Supplier<String> {
                final String value
                private StringSupplier(String value) {
                    this.value = value
                }
            
                @Override
                String get() {
                    return value
                }
            
                static StringSupplier of(String value) {
                    return new StringSupplier(value)
                }
            }

        '''


        when:
        instance = create("Foo") {
            name "Bla"
        }

        then:
        instance.name.get() == "Bla"

    }

    @Issue("243")
    def "Special case with different placeholders for factories"() {
        given:
        createClass '''
@DSL
class Outer {
    Map<String, Other<String>> values
}

class Other<E> {
    E entry
    
    static <T> Other<T> fromGeneric(T anObject) {
        return new Other<T>(entry:  anObject) 
    }
}
'''
        when:
        rwClazz.getMethod("value", String, String)

        then:
        thrown(NoSuchMethodException)

        expect:
        rwClazz.getMethod("value", String, Object)

        when:
        create("Outer")

        then:
        noExceptionThrown()
    }




}
