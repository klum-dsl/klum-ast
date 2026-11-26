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

import com.blackbuild.klum.ast.runtime.KlumModelException
import spock.lang.Issue

@Issue('1117')
class StandardContainerCopyIntegrationTest extends AbstractDSLSpec {
    def setup() {
        createClass '''
            package containers
            @DSL class Configuration {
                Map<String, Object> data
                List<Object> rows
                Object payload
            }
        '''
    }

    def '#kind copy preserves nested immutable value isolation through ordinary materialization'() {
        given:
        def leaf = ['initial']
        def tree = Map.of('items', List.of(Map.of('leaf', leaf)))
        def recipe = [data: [tree: tree], rows: [tree]]
        def type = clazz
        def donor = kind == 'Map' ? recipe : kind == 'Model' ? type.Create.With {
            data = recipe.data
            rows = recipe.rows
        } : type.Create.Template.With {
            data = recipe.data
            rows = recipe.rows
        }

        when:
        def first = type.Create.With { copyFrom donor }
        def second = type.Create.With { copyFrom donor }
        first.data.tree.items[0].leaf.add('recipient')
        leaf.add('donor')

        then:
        first.data.tree.items[0].leaf == ['initial', 'recipient']
        second.data.tree.items[0].leaf == ['initial']
        first.rows[0].items[0].leaf == ['initial']
        tree.items[0].leaf == ['initial', 'donor']
        !first.data.tree.is(second.data.tree)
        !first.data.tree.items.is(tree.items)
        !first.rows[0].is(tree)

        where:
        kind << ['Map', 'Model', 'Template']
    }

    def 'immutable value copied into a scalar Object field from a Map donor is isolated'() {
        given:
        def type = clazz
        def tree = Map.of('items', List.of(['initial']))

        when:
        def result = type.Create.With { copyFrom([payload: tree]) }
        result.payload.items[0].add('recipient')

        then:
        result.payload.items[0] == ['initial', 'recipient']
        tree.items[0] == ['initial']
    }

    def 'a materialized scalar container remains outside the qualified factory family'() {
        given:
        def type = clazz
        def donor = type.Create.With { payload = Map.of('items', List.of('initial')) }

        when:
        type.Create.With { copyFrom donor }

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Cannot copy container')
        failure.message.contains(donor.payload.getClass().name)
    }

    def 'scoped Template applies immutable nested values independently to each recipient'() {
        given:
        def tree = Map.of('items', List.of(['initial']))
        def type = clazz
        def template = type.Create.Template.With { data = [tree: tree] }
        def recipients = []

        when:
        type.Template.With(template) {
            recipients.add(type.Create.One())
            recipients.add(type.Create.One())
        }
        recipients[0].data.tree.items[0].add('recipient')

        then:
        recipients[0].data.tree.items[0] == ['initial', 'recipient']
        recipients[1].data.tree.items[0] == ['initial']
        template.data.tree.items[0] == ['initial']
    }
}
