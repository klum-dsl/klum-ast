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

import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag
import uk.org.webcompere.systemstubs.properties.SystemProperties

@Tag("documentary")
class ValidationDocumentaryTest extends AbstractDSLSpec {

    @AutoCleanup("teardown") SystemProperties systemProperties = new SystemProperties()

    @Override
    def setup() {
        systemProperties.setup()
    }

    @Issue("276")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#on-classes")
    def "validates unannotated fields when a class is marked for validation"() {
        given:
        createClass '''
            package pk

            @DSL
            @Validate
            class Release {
                String name

                @Optional
                String notes
            }
        '''

        when:
        clazz.Create.One()

        then:
        def exception = thrown(KlumValidationException)
        exception.message.endsWith("- ERROR #name: Field 'name' must be set")

        when:
        def release = clazz.Create.With {
            name 'spring-catalog'
        }

        then:
        release.name == 'spring-catalog'
        release.notes == null
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#on-fields")
    def "validates a numeric field with a closure"() {
        given:
        createClass '''
            package pk

            @DSL
            class Figure {
                @Validate({ it > 2 })
                int edges
            }
        '''

        when:
        clazz.Create.One()

        then:
        thrown(KlumValidationException)

        when:
        def figure = clazz.Create.With {
            edges 3
        }

        then:
        figure.edges == 3
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#on-fields")
    def "reports an explicit validation message for a field"() {
        given:
        createClass '''
            package pk

            @DSL
            class Release {
                @Validate(message = 'A release name is required')
                String name
            }
        '''

        when:
        clazz.Create.One()

        then:
        def exception = thrown(KlumValidationException)
        exception.message.endsWith("- ERROR #name: A release name is required")
    }

    @Issue("221")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#required-and-optional")
    def "uses Required as the concise Groovy-truth field rule"() {
        given:
        createClass '''
            package pk

            @DSL
            class Release {
                @Required
                String owner
            }
        '''

        when:
        clazz.Create.One()

        then:
        thrown(KlumValidationException)

        when:
        def release = clazz.Create.With {
            owner 'delivery-team'
        }

        then:
        release.owner == 'delivery-team'
    }

    @Issue("409")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#required-and-optional")
    def "excludes an optional field from class-wide validation"() {
        given:
        createClass '''
            package pk

            @DSL
            @Validate
            class Release {
                String name

                @Optional
                String notes
            }
        '''

        when:
        def release = clazz.Create.With {
            name 'spring-catalog'
        }

        then:
        release.name == 'spring-catalog'
        release.notes == null
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#on-methods")
    def "reports failed validation-method assertions at their configured levels"() {
        given:
        createClass '''
            package pk

            @DSL
            class Release {
                int replicas

                @Validate(level = Validate.Level.WARNING)
                void replicasShouldBeConfigured() {
                    assert replicas > 0 : 'Configure at least one replica'
                }

                @Validate
                void requiresTwoReplicas() {
                    assert replicas >= 2
                }
            }
        '''

        when:
        clazz.Create.With {
            replicas 0
        }

        then:
        def exception = thrown(KlumValidationException)
        exception.message.contains('- WARNING #replicasShouldBeConfigured(): java.lang.AssertionError: Configure at least one replica')
        exception.message.contains('- ERROR #requiresTwoReplicas(): Assertion failed:')
        exception.message.contains('assert replicas >= 2')
        exception.message.contains('replicas = 0')
    }

    @Issue("624")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#validation-of-nested-objects")
    def "validates a child against parent state configured after the child"() {
        given:
        createClass '''
            package pk

            @DSL
            class ReleasePlan {
                String releaseName
                ReleaseCheck check
            }

            @DSL
            class ReleaseCheck {
                @Owner ReleasePlan releasePlan

                @Validate
                void releaseNameWasConfigured() {
                    assert releasePlan.releaseName == '2026.2'
                }
            }
        '''

        when:
        def releasePlan = clazz.Create.With {
            check {}
            releaseName '2026.2'
        }

        then:
        releasePlan.check.releasePlan.is(releasePlan)
        releasePlan.releaseName == '2026.2'
    }

    @Issue("624")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#skipping-verification")
    def "verifies stored results without rerunning validators"() {
        given:
        systemProperties.set('klum.validation.skipVerify', 'true')
        createClass '''
            package pk

            @DSL
            class Release {
                static int validationRuns

                @Required
                String releaseName

                @Validate
                void countValidation() {
                    validationRuns++
                }
            }
        '''

        when:
        def model = clazz.Create.One()
        def validation = KlumObjectSupport.of(model).validation
        def storedResult = validation.result
        def storedIssues = storedResult.issues.toList()

        then:
        storedIssues.size() == 1
        clazz.validationRuns == 1

        when:
        validation.verify()

        then:
        def exception = thrown(KlumValidationException)
        exception.validationResults == [storedResult]
        clazz.validationRuns == 1
        validation.result.is(storedResult)
        storedResult.issues.toList() == storedIssues
    }
}
