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
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class ValidationPolicyDocumentaryTest extends AbstractDSLSpec {

    @Issue("145")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#validation-levels")
    def "records a warning-level validation result without failing construction"() {
        given:
        createClass '''
            package pk

            @DSL
            class Release {
                @Required(level = Validate.Level.WARNING)
                String releaseNotes
            }
        '''

        when:
        def release = clazz.Create.One()

        then:
        def result = KlumObjectSupport.of(release).validation.result
        result.maxLevel == Validate.Level.WARNING
        result.message.endsWith("- WARNING #releaseNotes: Field 'releaseNotes' must be set")
    }

    @Issue("145")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#deprecations")
    def "reports a documented deprecation only for a manually configured legacy field"() {
        given:
        createClass '''
            package pk

            @DSL
            class Release {
                /**
                 * @deprecated Use releaseChannel instead.
                 */
                @Deprecated
                String legacyChannel
            }
        '''

        when:
        def automaticallyConfiguredRelease = clazz.Create.One()

        then:
        KlumObjectSupport.of(automaticallyConfiguredRelease).validation.result.issues.empty

        when:
        def release = clazz.Create.With {
            legacyChannel 'nightly'
        }

        then:
        def result = KlumObjectSupport.of(release).validation.result
        result.maxLevel == Validate.Level.DEPRECATION
        result.message.endsWith("- DEPRECATION #legacyChannel: Use releaseChannel instead.")
    }

    @Issue("407")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#notify")
    def "reports a missing manually configured field"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.Notify

            @DSL
            class Release {
                @Notify(ifUnset = 'Choose a release channel before publishing.')
                String releaseChannel
            }
        '''

        when:
        def release = clazz.Create.One()

        then:
        def result = KlumObjectSupport.of(release).validation.result
        result.maxLevel == Validate.Level.WARNING
        result.message.endsWith("- WARNING #releaseChannel: Choose a release channel before publishing.")
    }

    @Issue("407")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#notify")
    def "uses Notify to replace the default deprecated-field policy"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.Notify

            @DSL
            class Release {
                @Notify(ifSet = 'Use releaseChannel instead.', level = Validate.Level.INFO)
                @Deprecated
                String legacyChannel
            }
        '''

        when:
        def release = clazz.Create.With {
            legacyChannel 'nightly'
        }

        then:
        def result = KlumObjectSupport.of(release).validation.result
        result.maxLevel == Validate.Level.INFO
        result.message.endsWith("- INFO #legacyChannel: Use releaseChannel instead.")
    }
}
