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
import com.blackbuild.klum.ast.runtime.KlumModelException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('1026')
class StructuredKeyedSequenceTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String container = 'List', String keyAnnotations = '', String profile = '') {
        createClass """
            package keyedsequence
            import com.blackbuild.klum.ast.*
            import groovy.transform.CompileDynamic
            @DSL class Service {
                static List<String> events = []
                static List<Object> children = []
                static Object active
                static Collection<Object> activeRoutes
                $container<Route> routes ${container in ['SortedSet', 'NavigableSet'] ? '= new TreeSet(new RouteOrder())' : ''}
                String marker
                @PostCreate void created() { active = this; activeRoutes = (Collection<Object>) routes }
            }
            @DSL $profile class Route {
                @Key $keyAnnotations String routeName
                @Owner Service owner
                String path = '/default'
                @PostCreate void created() {
                    Service.children << this
                    Service.events << "create:\$routeName".toString()
                }
                @PostApply void applied() {
                    Service.events << "apply:\$routeName:\$path:\${Service.activeRoutes?.size()}".toString()
                }
                @PostTree void tree() { Service.events << "tree:\$routeName:\${owner != null}".toString() }
            }
            @CompileDynamic class RouteOrder implements Comparator<Object> {
                int compare(Object a, Object b) { a.routeName <=> b.routeName }
            }
        """
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('keyedsequence.Service', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    private Map run(Class service, boolean structured, List<Map> items) {
        service.events.clear()
        service.children.clear()
        service.active = null
        def model = structured ? service.Create.FromStruct([routes: items], 'routes.yaml') : service.Create.With {
            items.each { item -> route(item.routeName) { path item.path ?: '/default' } }
        }
        [model: model, events: service.events.toList(), children: service.children.toList()]
    }

    def 'ordinary #container creates fresh keyed children in call order before destination semantics'() {
        given:
        def service = schema(container)

        when:
        def ordinary = run(service, false, [[routeName: 'z'], [routeName: 'a'], [routeName: 'a']])

        then:
        ordinary.model.routes*.routeName == expected
        ordinary.events.take(6) == ['create:z', 'apply:z:/default:0', 'create:a', 'apply:a:/default:1', 'create:a', 'apply:a:/default:2']
        ordinary.children.size() == 3
        !ordinary.children[1].is(ordinary.children[2])
        ordinary.model.routes.every { it.owner.is(ordinary.model) }
        ordinary.events.findAll { it.startsWith('tree:') }.size() == treeCount

        where:
        container      | expected        | treeCount
        'List'         | ['z', 'a', 'a'] | 3
        'Set'          | ['z', 'a']      | 3
        'SortedSet'    | ['a', 'z']      | 2
        'NavigableSet' | ['a', 'z']      | 2
    }

    def 'current Struct rejects keyed sequence before child allocation'() {
        given:
        def service = schema()

        when:
        service.Create.FromStruct([routes: [[routeName: 'health', path: '/health']]], 'routes.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/routes')
        service.children.empty
    }

    def 'ordinary construction uses positional String identity independently of external key annotation #annotation'() {
        given:
        def service = schema('List', annotation)

        when:
        def ordinary = run(service, false, [[routeName: ''], [routeName: 'health']])

        then:
        ordinary.model.routes*.routeName == ['', 'health']
        ordinary.events.take(2) == ['create:', 'apply::/default:0']

        where:
        annotation << ['', '@Struct.Name("id/~")', '@Struct.Ignore']
    }

    def 'ordinary missing or malformed positional identity is characterized: #key'() {
        given:
        def service = schema()
        def result
        def failure

        when:
        try { result = service.Create.With { route(key) { } } }
        catch (Exception caught) { failure = caught }

        then:
        if (key == null) {
            assert failure == null
            assert result.routes*.routeName == [null]
            assert service.events == ['create:null', 'apply:null:/default:0', 'tree:null:true']
        } else {
            assert failure instanceof MissingMethodException
            assert result == null
            assert service.children.empty
        }

        where:
        key << [null, 42, true, [:]]
    }

    def 'ordinary Template stores child identities without callbacks and reowns fresh recipients'() {
        given:
        def service = schema()

        when:
        def recipe = service.Create.Template.With {
            route('health') { path '/health' }
            route('health') { path '/second' }
        }

        then:
        service.events.empty
        service.children.empty
        recipe.routes*.routeName == ['health', 'health']

        when:
        def recipients = []
        service.Template.With(recipe) {
            recipients << service.Create.With()
            recipients << service.Create.With()
        }

        then:
        recipients.every { it.routes*.routeName == ['health', 'health'] }
        recipients.every { root -> root.routes.every { it.owner.is(root) } }
        !recipients[0].routes.first().is(recipients[1].routes.first())
    }
}
