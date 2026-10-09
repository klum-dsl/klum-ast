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
class VirtualFieldsDocumentaryTest extends AbstractDSLSpec {

    @Issue("250")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#virtual-fields")
    def "configures a virtual field with a concrete article type"() {
        given:
        createClass '''
            @DSL
            class Feed {
                String headline

                @Field
                void article(Article article) {
                    headline = article.headline
                }
            }

            @DSL
            abstract class Article {
                String headline
            }

            @DSL
            class ReleaseNote extends Article {
            }
        '''

        when:
        def ReleaseNote = getClass('ReleaseNote')
        def feed = clazz.Create.With {
            article(ReleaseNote) {
                headline 'KlumAST 4.0 is available'
            }
        }

        then:
        feed.headline == 'KlumAST 4.0 is available'
    }
}
