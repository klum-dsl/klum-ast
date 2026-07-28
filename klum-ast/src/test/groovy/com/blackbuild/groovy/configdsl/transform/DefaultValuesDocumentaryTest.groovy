/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast

import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class DefaultValuesDocumentaryTest extends AbstractDSLSpec {

    @Issue("318")
    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#other-fields-field")
    def "defaults a release identifier from its configured name"() {
        given:
        createClass '''
            package pk

            @DSL
            class Release {
                String name

                @Default(field = 'name')
                String identifier
            }
        '''

        when:
        def release = clazz.Create.With {
            name 'spring-catalog'
        }

        then:
        release.name == 'spring-catalog'
        release.identifier == 'spring-catalog'
    }

    @Issue("318")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#delegate-fields-delegate")
    def "defaults a component name from its owning container"() {
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

        when:
        def container = clazz.Create.With {
            name 'catalog'
            element {}
        }

        then:
        container.element.name == 'catalog'
    }

    @Issue("318")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#arbitrary-code-code")
    def "derives a normalized release identifier with default code"() {
        given:
        createClass '''
            package pk

            @DSL
            class Release {
                String name

                @Default(code = { name.toLowerCase() })
                String identifier
            }
        '''

        when:
        def release = clazz.Create.With {
            name 'Spring-Catalog'
        }

        then:
        release.identifier == 'spring-catalog'
    }

    @Issue("318")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#default-as-lifecycle-annotation")
    def "runs a default lifecycle method when a value is absent"() {
        given:
        createClass '''
            package pk

            @DSL
            class Release {
                String identifier

                @Default
                void defaultIdentifier() {
                    identifier = 'spring-catalog'
                }
            }
        '''

        when:
        def release = clazz.Create.One()

        then:
        release.identifier == 'spring-catalog'
    }

    @Issue("361")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#class-annotation")
    def "applies a default-values annotation to a configuration class"() {
        given:
        createSecondaryClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.DefaultValues

            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            @DefaultValues
            @interface ReleaseDefaults {
                String displayName() default ''
            }
        '''
        createClass '''
            package pk

            @ReleaseDefaults(displayName = 'Spring Catalog')
            @DSL
            class Release {
                String displayName
            }
        '''

        when:
        def release = clazz.Create.One()

        then:
        release.displayName == 'Spring Catalog'
    }

    @Issue("361")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#field-annotation")
    def "applies a default-values annotation to a child field"() {
        given:
        createSecondaryClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.DefaultValues

            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @DefaultValues
            @interface DisplayDefaults {
                String shortLabel() default ''
            }
        '''
        createClass '''
            package pk

            @DSL
            class FloorPlan {
                @DisplayDefaults(shortLabel = 'N')
                Window north
            }

            @DSL
            class Window {
                String shortLabel
            }
        '''

        when:
        def floorPlan = clazz.Create.With {
            north {}
        }

        then:
        floorPlan.north.shortLabel == 'N'
    }

    @Issue("361")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#closure-and-coercion")
    def "evaluates a default-values closure and coerces its result"() {
        given:
        createSecondaryClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.DefaultValues

            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            @DefaultValues
            @interface CapacityDefaults {
                Class<? extends Closure> capacity()
            }
        '''
        createClass '''
            package pk

            @CapacityDefaults(capacity = { '42' })
            @DSL
            class Release {
                int capacity
            }
        '''

        when:
        def release = clazz.Create.One()

        then:
        release.capacity == 42
    }

    @Issue("361")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#ignoreunknownfields")
    def "ignores unmatched default-values members when configured"() {
        given:
        createSecondaryClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.DefaultValues

            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            @DefaultValues(ignoreUnknownFields = true)
            @interface LenientDefaults {
                String displayName() default ''
                String retiredName() default ''
            }
        '''
        createClass '''
            package pk

            @LenientDefaults(displayName = 'Spring Catalog', retiredName = 'Legacy Catalog')
            @DSL
            class Release {
                String displayName
            }
        '''

        when:
        def release = clazz.Create.One()

        then:
        release.displayName == 'Spring Catalog'
    }

    @Issue("370")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#valuetarget")
    def "maps a concise annotation value to a default field"() {
        given:
        createSecondaryClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.DefaultValues

            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            @DefaultValues(valueTarget = 'displayName')
            @interface DisplayName {
                String value()
            }
        '''
        createClass '''
            package pk

            @DisplayName('Spring Catalog')
            @DSL
            class Release {
                String displayName
            }
        '''

        when:
        def release = clazz.Create.One()

        then:
        release.displayName == 'Spring Catalog'
    }

    @Issue("370")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Default-Values.md#defaultapply")
    def "applies default configuration to a child object"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.DefaultApply

            @DSL
            class Release {
                @DefaultApply({
                    name 'Spring Catalog'
                    capacity 42
                })
                Publication publication
            }

            @DSL
            class Publication {
                String name
                int capacity
            }
        '''

        when:
        def release = clazz.Create.With {
            publication {}
        }

        then:
        release.publication.name == 'Spring Catalog'
        release.publication.capacity == 42
    }
}
