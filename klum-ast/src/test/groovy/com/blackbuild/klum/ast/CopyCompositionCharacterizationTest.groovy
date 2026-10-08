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

/** Current-master observations for the D5 decision gate, not desired ownership semantics. */
@Issue('856')
class CopyCompositionCharacterizationTest extends AbstractDSLSpec {
    def 'one Map recipe currently materializes as the same object in two composition fields without a declaration'() {
        given:
        createClass '''
            package characterization
            @DSL class Node { String value }
            @DSL class Recipient {
                List<Node> first
                @Field(keyMapping = { it.value }) Map<String, Node> second
            }
        '''
        def sharedRecipe = [value: 'shared']

        when:
        def result = getClass('Recipient').Create.With {
            copyFrom([first: [sharedRecipe], second: [shared: sharedRecipe]])
        }

        then:
        result.first[0].value == 'shared'
        result.first[0].is(result.second.shared)
        KlumObjectSupport.of(result.first[0]).structure.owningRelationship.empty
        KlumObjectSupport.of(result.second.shared).structure.owningRelationship.empty
    }
}
