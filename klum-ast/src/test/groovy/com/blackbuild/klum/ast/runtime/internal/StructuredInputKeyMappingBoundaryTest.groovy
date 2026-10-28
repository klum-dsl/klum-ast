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
package com.blackbuild.klum.ast.runtime.internal

import com.blackbuild.klum.ast.AbstractDSLSpec
import spock.lang.Issue

/** Keeps mapped relationships outside wire admission while preserving ordinary DSL key mapping. */
@Issue('932')
class StructuredInputKeyMappingBoundaryTest extends AbstractDSLSpec {

    def 'wire rejects mapped keyed ownership while ordinary Builder mapping and literal ownership remain intact'() {
        given:
        createClass '''
            package mappedboundary
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                @Field(keyMapping = { it.route })
                Map<String, Entry> entries
                Map<String, Entry> literalEntries
            }
            @DSL class Entry {
                @Key String name
                String route
                String value
            }
        '''
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def configuration = Class.forName('mappedboundary.Configuration', false, binaryLoader)

        when:
        def plan = new InputContractResolver().read(configuration)

        then:
        !plan.entries().containsKey('entries')
        plan.entries().get('literalEntries').role().name() == 'KEYED_OWNED_MAP'
        plan.rejections()*.source() == ['mappedboundary.Configuration#entries']
        plan.rejections()*.reason() == ['literal structured instance keys conflict with @Field.keyMapping-derived keys']

        when:
        def model = getClass('mappedboundary.Configuration').Create.With {
            entry('a/b~c') { route 'mapped'; value 'first' }
            entry('second') { route 'mapped'; value 'second' }
            literalEntry('a/b~c') { route 'mapped'; value 'first' }
            literalEntry('second') { route 'mapped'; value 'second' }
        }

        then:
        model.entries.keySet() == ['mapped'] as Set
        model.entries.mapped.name == 'second'
        model.entries.mapped.value == 'second'
        !model.entries.containsKey('a/b~c')
        !model.entries.containsKey('second')
        model.literalEntries.keySet() == ['a/b~c', 'second'] as Set
        model.literalEntries['a/b~c'].name == 'a/b~c'
        model.literalEntries.second.name == 'second'

        cleanup:
        binaryLoader?.close()
    }
}
