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
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
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
                    if (path == 'fail') { throw new IllegalStateException('child failed') }
                }
                @PostTree void tree() { Service.events << "tree:\$routeName:\${owner != null}".toString() }
            }
            @CompileDynamic class RouteOrder implements Comparator<Object> {
                int compare(Object a, Object b) {
                    if (a.routeName == 'bad' || b.routeName == 'bad') { throw new IllegalArgumentException('comparison failed') }
                    a.routeName <=> b.routeName
                }
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

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#keyed-child-sequences')
    def 'creates keyed routes from structured sequence items'() {
        given:
        def service = schema()
        def input = [routes: [[routeName: 'health', path: '/health'], [routeName: 'metrics', path: '/metrics']]]

        when:
        def model = service.Create.FromStruct(input, 'routes.yaml')

        then:
        model.routes*.routeName == ['health', 'metrics']
        model.routes*.path == ['/health', '/metrics']
        service.events.take(4) == ['create:health', 'apply:health:/health:0', 'create:metrics', 'apply:metrics:/metrics:1']
        input.routes[0] == [routeName: 'health', path: '/health']
    }

    def 'Struct matches ordinary fresh identities lifecycle and destination semantics for #container'() {
        given:
        def service = schema(container)
        def items = [[routeName: 'z', path: '/z'], [routeName: 'a', path: '/a'],
                     [routeName: 'a', path: '/a'], [routeName: 'a', path: '/other']]

        when:
        def ordinary = run(service, false, items)
        def structured = run(service, true, items)

        then:
        structured.model == ordinary.model
        structured.events == ordinary.events
        structured.children.size() == 4
        !structured.children[1].is(structured.children[2])
        structured.children*.modelPath == ordinary.children*.modelPath
        structured.model.routes.every { it.owner.is(structured.model) }
        structured.model.routes*.path == expected

        where:
        container      | expected
        'List'         | ['/z', '/a', '/a', '/other']
        'Set'          | ['/z', '/a', '/other']
        'SortedSet'    | ['/a', '/z']
        'NavigableSet' | ['/a', '/z']
    }

    def 'sequence identities reuse external key naming: #annotation #profile'() {
        given:
        def service = schema('List', annotation, profile)

        when:
        def model = service.Create.FromStruct([routes: [[(spelling): '', path: '/empty'], [(spelling): 'health']]])

        then:
        model.routes*.routeName == ['', 'health']
        service.events.take(2) == ['create:', 'apply::/empty:0']

        where:
        annotation                 | profile                                            | spelling
        ''                         | ''                                                 | 'routeName'
        '@Struct.Name("id/~")'     | ''                                                 | 'id/~'
        ''                         | '@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)'   | 'route-name'
        '@Struct.Name("id/~")'     | '@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)'   | 'id/~'
    }

    def 'missing malformed or native renamed keys reject at input index before allocating reached child: #body'() {
        given:
        def service = schema('List', '@Struct.Name("id/~")')

        when:
        service.Create.FromStruct([routes: [[('id/~'): 'first'], body, [('id/~'): 'later']], marker: 'later'], 'bad.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('bad.yaml')
        failure.message.contains('/routes/1/id~1~0')
        failure.message.contains('keyedsequence.Route#routeName')
        service.children*.routeName == ['first']
        service.active.routes*.routeName == ['first']
        service.active.marker == null

        where:
        body << [[:], [routeName: 'native'], [('id/~'): null], [('id/~'): 42],
                 [('id/~'): true], [('id/~'): []], [('id/~'): [:]], [('id/~'): "${'gstring'}"]]
    }

    def 'excluded or colliding external child key cannot establish sequence identity: #annotation'() {
        given:
        def service = schema('List', annotation)

        when:
        service.Create.FromStruct([routes: [[(spelling): 'health']]], 'key.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/routes/0/' + spelling)
        failure.message.contains(reason)
        service.children.empty

        where:
        annotation             | spelling    | reason
        '@Struct.Ignore'       | 'routeName' | '@Struct.Ignore'
        '@Struct.Name("path")' | 'path'      | 'external-name collision'
    }

    def 'reached body or lifecycle failure preserves earlier work and source index: #body'() {
        given:
        def service = schema()

        when:
        service.Create.FromStruct([routes: [[routeName: 'first'], body, [routeName: 'later']]], 'body.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('body.yaml')
        failure.message.contains('/routes/1')
        service.active.routes*.routeName == ['first']
        !service.events.any { it.startsWith('tree:') || it.contains('later') }
        service.children.size() == allocated

        where:
        body                                         | allocated
        [routeName: 'bad', unknown: true]              | 2
        [routeName: 'bad', path: 'fail']               | 2
        null                                         | 1
        'text'                                       | 1
        []                                           | 1
    }

    def 'structured Template recipes and child Template defaults preserve ordinary behavior for #container'() {
        given:
        def service = schema(container)
        def routeType = artifacts.loadClass('keyedsequence.Route')
        def childRecipe = routeType.Create.Template.With { path '/template' }
        def ordinary
        def structured

        when:
        routeType.Template.With(childRecipe) {
            ordinary = service.Create.Template.With { route('z'); route('a'); route('a') }
            structured = service.Create.Template.FromStruct([routes: [[routeName: 'z'], [routeName: 'a'], [routeName: 'a']]])
        }

        then:
        service.events.empty
        service.children.empty
        structured.routes*.routeName == ordinary.routes*.routeName
        structured.routes*.path == ordinary.routes*.path

        when:
        def outcomes = []
        [ordinary, structured, structured].each { recipe ->
            service.events.clear()
            service.activeRoutes = null
            service.Template.With(recipe) {
                outcomes << [model: service.Create.With(), events: service.events.toList()]
            }
        }

        then:
        outcomes[1].model == outcomes[0].model
        outcomes[1].events == outcomes[0].events
        outcomes[2].events == outcomes[0].events
        outcomes.every { result -> result.model.routes.every { it.owner.is(result.model) && it.path == '/template' } }
        !outcomes[1].model.routes.first().is(outcomes[2].model.routes.first())

        where:
        container << ['List', 'Set', 'SortedSet', 'NavigableSet']
    }

    def 'sorted attachment failures retain supplied index after comparator duplicates for #container'() {
        given:
        def service = schema(container)
        def outcomes = []

        when:
        [false, true].each { structured ->
            try {
                run(service, structured, [[routeName: 'a'], [routeName: 'a'], [routeName: 'bad'], [routeName: 'later']])
                assert false: 'Comparator must reject the reached child'
            } catch (Exception failure) {
                if (structured) {
                    assert failure.message.contains('routes.yaml')
                    assert failure.message.contains('/routes/2')
                }
                outcomes << [events: service.events.toList(), values: service.active.routes*.routeName]
            }
        }

        then:
        outcomes[0] == outcomes[1]
        outcomes[0].values == ['a']
        outcomes[0].events == ['create:a', 'apply:a:/default:0', 'create:a', 'apply:a:/default:1', 'create:bad', 'apply:bad:/default:1']

        where:
        container << ['SortedSet', 'NavigableSet']
    }

    def 'missing child key metadata fails before root allocation'() {
        given:
        def service = schema()
        def file = new File(compilerConfiguration.targetDirectory, 'keyedsequence/Route_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        annotation.endsWith('GeneratedInput$Key;') ? null : super.visitAnnotation(annotation, visible)
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()

        when:
        service.Create.FromStruct([routes: [[routeName: 'health']]], 'metadata.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('metadata.yaml')
        failure.message.contains('recompile')
        service.active == null
        service.children.empty
    }

    def 'generic child contract corruption rejects before root allocation'() {
        given:
        def service = schema()
        def file = new File(compilerConfiguration.targetDirectory, 'keyedsequence/Route.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                // Preserve the valid relationship facts while making its concrete Model unexpectedly generic.
                String hierarchy = signature ?: 'L' + parent + ';' + interfaces.collect { 'L' + it + ';' }.join()
                super.visit(version, access, name, '<T:Ljava/lang/Object;>' + hierarchy, parent, interfaces)
            }
        }, 0)
        file.bytes = writer.toByteArray()

        when:
        service.Create.FromStruct([routes: [[routeName: 'health']]], 'generic-contract.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('generic-contract.yaml')
        failure.message.contains('Owned sequence requires a nongeneric concrete child: keyedsequence.Service#routes')
        failure.message.contains('recompile')
        service.active == null
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
