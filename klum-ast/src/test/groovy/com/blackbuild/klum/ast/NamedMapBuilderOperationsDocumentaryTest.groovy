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

@Issue('792')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Basics.md#named-map-builder-calls')
class NamedMapBuilderOperationsDocumentaryTest extends AbstractDSLSpec {

    def "uses literal map entries as ordinary Builder operation calls"() {
        given:
        createClass '''
            package documentation

            import java.net.URI

            @DSL
            class Library {
                String name
                Publication featured

                @Field(members = 'publication')
                List<Publication> publications
            }

            @DSL
            class LocalizedContent {
                String locale

                @Builder.Method
                String inheritedLocale(String value) {
                    locale = value
                }
            }

            @DSL
            class Publication extends LocalizedContent {
                String title

                @Field(converters = [{ String value -> URI.create(value) }])
                URI source

                List<String> tags

                @Builder.Method
                String title(Integer edition) {
                    title = "Edition $edition"
                }
            }
        '''
        Class<?> publication = getClass('documentation.Publication')
        def baseline = publication.Create.With(title: 'Baseline', inheritedLocale: 'en')

        and:
        Class<?> model = createSecondaryClass '''
            package documentation

            import groovy.transform.CompileStatic

            @CompileStatic
            class LibraryModel {
                static Library create(Publication baseline) {
                    Library.Create.With(name: 'City Library') {
                        featured(copyFrom: baseline, setTitle: 'Featured') {
                            tag 'spotlight'
                        }
                        publication(
                                title: 2,
                                source: 'https://example.test/guide',
                                tag: 'guide',
                                inheritedLocale: 'de')
                    }
                }
            }
        '''

        when:
        def library = model.create(baseline)

        then:
        library.name == 'City Library'
        library.featured.title == 'Featured'
        library.featured.locale == 'en'
        library.featured.tags == ['spotlight']
        library.publications*.title == ['Edition 2']
        library.publications*.source == [new URI('https://example.test/guide')]
        library.publications*.tags == [['guide']]
        library.publications*.locale == ['de']
    }
}
