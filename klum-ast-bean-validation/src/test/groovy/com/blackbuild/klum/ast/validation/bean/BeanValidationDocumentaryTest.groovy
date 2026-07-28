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
package com.blackbuild.klum.ast.validation.bean

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.Validate
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class BeanValidationDocumentaryTest extends AbstractDSLSpec {

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#jsr380-validation")
    def "accepts a release with a satisfied Jakarta validation constraint"() {
        given:
        createClass '''
            package pk

            import jakarta.validation.constraints.Size

            @DSL
            class Release {
                @Size(min = 2, max = 4, message = 'Choose between two and four approvers')
                List<String> approvers
            }
        '''

        when:
        def release = clazz.Create.With {
            approvers 'dana', 'lee'
        }

        then:
        release.approvers == ['dana', 'lee']
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#validation-levels-and-jsr380")
    def "records a Jakarta constraint violation at its payload-selected level"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.validation.bean.Level
            import jakarta.validation.constraints.Size

            @DSL
            class Release {
                @Size(min = 2, payload = Level.WARNING, message = 'Choose at least two approvers')
                List<String> approvers
            }
        '''

        when:
        def release = clazz.Create.With {
            approvers 'dana'
        }
        def result = KlumObjectSupport.of(release).validation.result

        then:
        result.has(Validate.Level.WARNING)
        result.issues*.member == ['approvers']
        result.issues*.level == [Validate.Level.WARNING]
        result.issues*.message == ['Choose at least two approvers']
    }
}
