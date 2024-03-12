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
//file:noinspection GrMethodMayBeStatic
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.util.KlumInstanceProxy
import com.blackbuild.klum.ast.util.Validator
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Ignore
import spock.lang.Issue

class ValidationSpec extends AbstractDSLSpec {

    AssertionError error

    def "validation with Groovy Truth"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated
            }
        ''')

        when:
        clazz.Create.With {}

        then:
        thrown(AssertionError)
    }

    @Issue("25")
    def "validation of nested elements does not work"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated

                @Validate
                Inner inner
            }

            @DSL
            class Inner {
                @Validate
                String value
            }
        ''')

        when: 'missing inner instance'
        clazz.Create.With {
            validated "correct"
        }

        then:
        thrown(AssertionError)

        when: 'inner instance does not validate'
        clazz.Create.With {
            validated "correct"
            inner {}
        }

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With {
            validated "correct"
            inner {
                value "valid"
            }
        }

        then:
        notThrown(AssertionError)
    }

    @Issue("25")
    def "validation of nested list elements"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated

                @Validate
                List<Inner> inners
            }

            @DSL
            class Inner {
                @Validate
                String value
            }
        ''')

        when: 'inners is empty'
        clazz.Create.With {
            validated "correct"
        }

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With {
            validated "correct"
            inners {
                inner {}
            }
        }

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With {
            validated "correct"
            inners {
                inner {
                    value "valid"
                }
            }
        }

        then:
        notThrown(AssertionError)
    }

    @Issue("25")
    def "validation of nested map elements"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated

                @Validate
                Map<String, Inner> inners
            }

            @DSL
            class Inner {
                @Key
                String name
                @Validate
                String value
            }
        ''')

        when: 'inners is empty'
        clazz.Create.With {
            validated "correct"
        }

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With {
            validated "correct"
            inners {
                inner("bla") {}
            }
        }

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With {
            validated "correct"
            inners {
                inner("bla") {
                    value "valid"
                }
            }
        }

        then:
        notThrown(AssertionError)
    }

    def "validation with default message"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String name
            }
        ''')

        when:
        clazz.Create.With {}

        then:
        error = thrown(AssertionError)
        error.message.startsWith("'name' must be set.")
    }

    def "validation with message"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate(message = "We need a name")
                String name
            }
        ''')

        when:
        clazz.Create.With {}

        then:
        error = thrown(AssertionError)
        error.message.startsWith("We need a name.")
    }

    def "validation with explicit Groovy Truth"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate(Validate.GroovyTruth)
                String validated
            }
        ''')

        when:
        clazz.Create.With {}

        then:
        thrown(AssertionError)
    }

    def "validation with Ignore"() {
        given:
        createClass('''
            @DSL @Validation(option = Validation.Option.VALIDATE_UNMARKED)
            class Foo {
                @Validate(Validate.Ignore)
                String validated
            }
        ''')

        when:
        clazz.Create.With {}

        then:
        notThrown(AssertionError)
    }

    def "validation with Closure"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate({ it?.length() > 3 })
                String validated
            }
        ''')

        when:
        clazz.Create.With {}

        then:
        def e = thrown(AssertionError)
        e.message == "Field 'validated' (null) is invalid. Expression: (it?.length() > 3)"


        when:
        clazz.Create.With { validated "bla"}

        then:
        error = thrown(AssertionError)
        error.message == "Field 'validated' ('bla') is invalid. Expression: (it?.length() > 3)"

        when:
        clazz.Create.With { validated "valid"}

        then:
        notThrown(AssertionError)
    }

    def "validation with Closure and explicit assert"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate({ assert it?.length() > 3 })
                String validated
            }
        ''')

        when:
        clazz.Create.With {}

        then:
        error = thrown(AssertionError)
        // error.message == "Field 'validated' (null) is invalid. Expression: (it?.length() > 3)"

        when:
        clazz.Create.With { validated "bla"}

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With { validated "valid"}

        then:
        notThrown(AssertionError)
    }

    def "validation with Closure and message"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate(value = { it?.length() > 3 }, message = "It shall not be!")
                String validated
            }
        ''')

        when:
        clazz.Create.With {}

        then:
        error = thrown(AssertionError)
        error.message.startsWith "It shall not be!"
    }

    def "validation with named Closure"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate({ string -> string.length() > 3 })
                String validated
            }
        ''')

        when:
        clazz.Create.With {}

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With { validated "bla"}

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With { validated "valid"}

        then:
        notThrown(AssertionError)
    }

    @Ignore("class is now a warning, not a failure")
    def "validation only allows GroovyTruth, Ignore or literal closure"() {
        when:
        createClass('''
            @DSL
            class Foo {
                @Validate(String.class)
                String validated
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "defer validation via method"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated
            }
        ''')

        when:
        instance = clazz.Create.With {
            manualValidation(true)
        }

        then:
        notThrown(AssertionError)

        when:
        KlumInstanceProxy.getProxyFor(instance).validate()

        then:
        thrown(AssertionError)

        when:
        instance.apply {
            validated "bla"
        }
        KlumInstanceProxy.getProxyFor(instance).validate()

        then:
        notThrown(AssertionError)
    }

    def "validation is not performed on templates"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate String validated
                String nonValidated
            }
        ''')

        when:
        instance = clazz.createAsTemplate {
            nonValidated "bla"
        }

        then:
        notThrown(AssertionError)
    }

    def "non annotated fields are not validated"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated

                String notValidated
            }
        ''')

        when:
        clazz.Create.With {
            validated "bla"
        }

        then:
        notThrown(AssertionError)
    }

    def "Validate on class validates all unmarked fields"() {
        given:
        createClass('''
            @DSL
            @Validate
            class Foo {
                String validated
            }
        ''')

        when:
        instance = clazz.Create.With {}

        then:
        thrown(AssertionError)

        when:
        instance = clazz.Create.With {
            validated "bla"
        }

        then:
        notThrown(AssertionError)
    }

    @Issue("276")
    def "Validation on class is converted to validate"() {
        when:
        createClass('''
            @DSL
            @Validation(option = Validation.Option.VALIDATE_UNMARKED)
            class Foo {
                String validated
            }
        ''')

        then:
        clazz.getAnnotation(Validate) != null

        when:
        instance = clazz.Create.With {}

        then:
        thrown(AssertionError)

        when:
        instance = clazz.Create.With {
            validated "bla"
        }

        then:
        notThrown(AssertionError)
    }

    @Issue("276")
    def "Validation on class is converted to validate with static import"() {
        when:
        createClass('''
            import static com.blackbuild.groovy.configdsl.transform.Validation.Option.VALIDATE_UNMARKED
            @DSL
            @Validation(option = VALIDATE_UNMARKED)
            class Foo {
                String validated
            }
        ''')

        then:
        clazz.getAnnotation(Validate) != null
    }

    def "validation is inherited"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Validate
                String validated
            }

            @DSL
            class Bar extends Foo {
                String child
            }
        ''')

        when:
        instance = create("pk.Bar") {}

        then:
        thrown(AssertionError)
    }

    @Ignore("Legacy feature")
    def "explicit validation method"() {
        given:
        createClass('''
            @DSL
            class Foo {
                String value1
                String value2

                def doValidate() {
                    assert value1.length() < value2.length()
                }
            }
        ''')

        when:
        clazz.Create.With {
            value1 "bla"
            value2 "b"
        }

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With {
            value1 "b"
            value2 "bla"
        }

        then:
        notThrown(AssertionError)
    }

    def "validation methods must not have parameters"() {
        when:
        createClass('''
            @DSL
            class Foo {
                @Validate
                def doValidate(String test) {
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "validate annotation on methods must not have a value or message"() {
        when:
        createClass('''
            @DSL
            class Foo {
                @Validate({ it })
                def doValidate() {
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "validation method is deprecated"() {
        when:
        createClass('''
            @DSL
            class Foo {
                String value1
                String value2

                @Validate
                private def stringLength() {
                    assert value1.length() < value2.length()
                }
            }
        ''')

        then:
        isDeprecated(clazz.getMethod("validate"))
    }

    def "validation method"() {
        given:
        createClass('''
            @DSL
            class Foo {
                String value1
                String value2

                @Validate
                private def stringLength() {
                    assert value1.length() < value2.length()
                }
            }
        ''')

        when:
        clazz.Create.With {
            value1 "abc"
            value2 "bl"
        }

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With {
            value1 "b"
            value2 "bla"
        }

        then:
        notThrown(AssertionError)
    }

    def "exceptions in validation method are wrapped in AssertionErrors"() {
        given:
        createClass('''
            @DSL
            class Foo {
                String value1
                String value2

                @Validate
                private def stringLength() {
                    if (value1.length() > value2.length())
                        throw new IllegalStateException("value1 is too big")
                }
            }
        ''')

        when:
        clazz.Create.With {
            value1 "abc"
            value2 "bl"
        }

        then:
        error = thrown(AssertionError)
        error.message.contains "value1 is too big"

        when:
        clazz.Create.With {
            value1 "b"
            value2 "bla"
        }

        then:
        notThrown(AssertionError)
    }

    def "multiple validation methods"() {
        given:
        createClass('''
            @DSL
            class Foo {
                String value1
                String value2

                @Validate
                private def stringLength() {
                    assert value1.length() < value2.length()
                }

                @Validate
                private def contains() {
                    assert value2.contains(value1)
                }
            }
        ''')

        when:
        clazz.Create.With {
            value1 "ab"
            value2 "bla"
        }

        then:
        thrown(AssertionError)

        when:
        clazz.Create.With {
            value1 "b"
            value2 "bla"
        }

        then:
        notThrown(AssertionError)
    }

    def "validate method can be be defined"() {
        when:
        createClass('''
            @DSL
            class Foo {
                private def validate() {
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    @Issue("125")
    def "validation of inner objects is not done during their creation"() {
        given:
        createClass('''
            @DSL
            class Outer {
                boolean afterInnerObject
                Inner inner
            }
            
            @DSL class Inner {
                @Owner Outer outer
                @Validate def outerNameMustBeSet() {
                    assert outer.afterInnerObject
                } 
            }
        ''')

        when:
        clazz.Create.With {
            inner()
            afterInnerObject()
        }

        then:
        notThrown(AssertionError)
    }

    @Issue("125")
    def "validation of inner objects is done eventually"() {
        given:
        createClass('''
            @DSL
            class Outer {
                Inner inner
            }
            
            @DSL class Inner {
                @Owner Outer outer
                @Validate def fail() {
                    assert false
                } 
            }
        ''')

        when:
        clazz.Create.With {
            inner()
        }

        then: 'Validation of outer object fails'
        thrown(AssertionError)
    }

    @Issue("223")
    def "Validation on boolean is illegal"() {
        when:
        createClass('''
            @DSL
            class Outer {
                @Validate
                boolean myValue
            }
        ''')

        then: 'Compilation fails'
        thrown(MultipleCompilationErrorsException)
    }

    @Issue("221")
    void "Required as an alias for Validate"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Required
                String name
            }
        ''')
        instance = clazz.Create.Template() // skip validation, we call validatior explicitly

        when:
        instance.name = 'test'
        new Validator(instance).execute()

        then:
        noExceptionThrown()

        when:
        instance.name = null
        new Validator(instance).execute()

        then:
        thrown(AssertionError)
    }
}
