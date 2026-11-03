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
import groovy.transform.CompileStatic
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

/** Public Struct parity for the C3 preparation/attachment characterization. */
@Issue('952')
class StructuredOwnedSequenceTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declaration, boolean comparable = false, boolean inertChild = false) {
        loader.parseClass '''
            package setprobe
            import com.blackbuild.klum.ast.*
            import groovy.transform.CompileDynamic
            @DSL class Service {
                static List<String> events = []
                static List<Object> children = []
                static Object active
                static Collection<Object> activeRoutes
                String marker
                ROUTES
                @PostCreate void created() { active = this; activeRoutes = (Collection<Object>) routes }
                @PostApply void applied() { events << 'root:apply' }
            }
            @DSL class Route COMPARABLE {
                INERT_CHILD
                @Owner Service owner
                String path = "/default"
                @PostCreate void created() {
                    Service.children << this
                    Service.events << 'create'
                }
                @PostApply void applied() {
                    Service.events << "apply:$path:${Service.activeRoutes?.size()}".toString()
                    if (path == 'lifecycle-fail') {
                        throw new IllegalStateException('apply failed')
                    }
                }
                @PostTree void tree() { Service.events << "tree:$path:${owner != null}".toString() }
                COMPARE
            }
            @CompileDynamic class PathOrder implements Comparator<Object> {
                int compare(Object a, Object b) { a.path <=> b.path }
            }
            class ModelOrder implements Comparator<Route> {
                int compare(Route a, Route b) { a.path <=> b.path }
            }
            @CompileDynamic class BuilderOnlyOrder implements Comparator<Object> {
                int compare(Object a, Object b) {
                    if (a instanceof Route || b instanceof Route) { throw new IllegalStateException('Model comparison failed') }
                    a.path <=> b.path
                }
            }
            @CompileDynamic class RejectingOrder implements Comparator<Object> {
                int compare(Object a, Object b) {
                    if (a.path == '/bad' || b.path == '/bad') { throw new IllegalArgumentException('incomparable route') }
                    a.path <=> b.path
                }
            }
        '''.replace('ROUTES', declaration)
                .replace('COMPARABLE', comparable ? 'implements Comparable<Route>' : '')
                .replace('COMPARE', comparable ? 'int compareTo(Route other) { path <=> other.path }' : '')
                .replace('INERT_CHILD', inertChild ? 'static { throw new AssertionError("Child initialized") }' : '')
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('setprobe.Service', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    private Map run(Class service, boolean stream, List<Map> items) {
        service.events.clear()
        service.children.clear()
        Class childType = artifacts.loadClass('setprobe.Route')
        def model = stream ? service.Create.FromStruct([routes: items], 'service.yaml') : service.Create.With {
            items.each { item -> route(item) }
        }
        [model: model, storageOrder: service.active.routes*.path, events: service.events.toList(), builders: service.children.toList(),
         comparator: service.active.routes instanceof SortedSet ? service.active.routes.comparator() : null,
         paths: service.children.collect { it.modelPath },
         crumbs: service.children.collect { it.breadcrumbPath.tokenize('/').last() }]
    }

    def 'plain Sets configure every new identity before attachment then deduplicate completed Models for #declaration'() {
        given:
        def service = schema(declaration)
        def items = [[path: '/z'], [path: '/a'], [path: '/a']]

        when:
        def ordinary = run(service, false, items)
        def stream = run(service, true, items)

        then:
        stream.events.findAll { !it.startsWith('tree:') } == ordinary.events.findAll { !it.startsWith('tree:') }
        stream.events.findAll { it.startsWith('tree:') }.sort() == ordinary.events.findAll { it.startsWith('tree:') }.sort()
        ordinary.events.take(3) == ['create', 'apply:/z:0', 'create']
        ordinary.events.findAll { it.startsWith('apply:') } == ['apply:/z:0', 'apply:/a:1', 'apply:/a:2']
        ordinary.builders.size() == 3
        !ordinary.builders[1].is(ordinary.builders[2])
        ordinary.builders[1] != ordinary.builders[2]
        ordinary.builders[1].completedModel == ordinary.builders[2].completedModel
        !ordinary.builders[1].completedModel.is(ordinary.builders[2].completedModel)
        ordinary.events.count('tree:/a:true') == 2
        ordinary.model.routes.size() == 2
        ordinary.model.routes*.path == ordinary.storageOrder.unique(false)
        stream.model.routes*.path == stream.storageOrder.unique(false)
        stream.model.routes*.path.toSet() == ordinary.model.routes*.path.toSet()
        ordinary.model.routes*.path.toSet() == ['/a', '/z'] as Set
        stream.paths == ordinary.paths
        ordinary.paths.toSet() == ['<root>.routes[0]', '<root>.routes[1]', '<root>.routes[2]'] as Set
        stream.crumbs == ordinary.crumbs
        ordinary.model.routes.every { it.owner.is(ordinary.model) }
        linkedOrder ? ordinary.model.routes*.path == ['/z', '/a'] : true

        where:
        declaration                                      | linkedOrder
        'Set<Route> routes'                               | true
        'Set<Route> routes = new LinkedHashSet<>()'        | true
        'Set<Route> routes = new HashSet<>()'              | false
    }

    def 'sorted comparator Sets execute in input order and retain comparator representatives for #kind'() {
        given:
        def service = schema("$kind<Route> routes = new TreeSet(new PathOrder())")
        def items = [[path: '/z'], [path: '/a'], [path: '/a']]

        when:
        def ordinary = run(service, false, items)
        def stream = run(service, true, items)

        then:
        stream.events == ordinary.events
        ordinary.events.findAll { it.startsWith('apply:') } == ['apply:/z:0', 'apply:/a:1', 'apply:/a:2']
        ordinary.events.findAll { it.startsWith('tree:') } == ['tree:/a:true', 'tree:/z:true']
        ordinary.builders.size() == 3
        !ordinary.builders[1].is(ordinary.builders[2])
        ordinary.model.routes*.path == ['/a', '/z']
        stream.model.routes*.path == ordinary.model.routes*.path
        ordinary.builders[1].completedModel.is(ordinary.model.routes.first())
        ordinary.paths == ['<root>.routes[0]', '<root>.routes[1]', '<root>.routes[1]']
        stream.paths == ordinary.paths
        stream.crumbs == ordinary.crumbs
        ordinary.model.routes.every { it.owner.is(ordinary.model) }
        ordinary.model.routes.comparator().is(ordinary.comparator)
        stream.model.routes.comparator().is(stream.comparator)
        owning(ordinary.builders[2]).name == 'routes'

        where:
        kind << ['SortedSet', 'NavigableSet']
    }

    def 'natural ordering fails at Builder insertion even for Comparable Models in #kind comparable #comparable'() {
        given:
        def service = schema("$kind<Route> routes", comparable)
        def results = []

        when:
        [false, true].each { stream ->
            try {
                run(service, stream, [[path: '/a'], [path: '/later']])
                assert false: 'ordinary insertion unexpectedly succeeded'
            } catch (Exception failure) {
                results << [cause: deepest(failure).class, size: service.active.routes.size(),
                            events: service.events.toList(), path: service.children.first().modelPath]
            }
        }

        then:
        results[0] == results[1]
        results[0].cause == ClassCastException
        results[0].size == 0
        results[0].events == ['create', 'apply:/a:0']
        results[0].path == null
        !Comparable.isAssignableFrom(service.children.first().class)
        Comparable.isAssignableFrom(artifacts.loadClass('setprobe.Route')) == comparable

        where:
        kind           | comparable
        'SortedSet'    | false
        'NavigableSet' | false
        'SortedSet'    | true
        'NavigableSet' | true
    }

    def 'Model typed comparator fails just as ordinary creation for #kind'() {
        given:
        def service = schema("$kind<Route> routes = new TreeSet(new ModelOrder())")
        def errors = []

        when:
        [false, true].each { stream ->
            try {
                run(service, stream, [[path: '/a']])
                assert false: 'typed comparator unexpectedly accepted a Builder'
            } catch (Exception failure) {
                errors << deepest(failure).class
            }
        }

        then:
        errors[0] == errors[1]
        service.active.routes.empty
        service.events == ['create', 'apply:/a:0']

        where:
        kind << ['SortedSet', 'NavigableSet']
    }

    def 'configuration and lifecycle failures precede attachment and preserve earlier operations for #badBody'() {
        given:
        def service = schema('Set<Route> routes')
        def outcomes = []

        when:
        [false, true].each { stream ->
            try {
                run(service, stream, [[path: '/first'], badBody, [path: '/later']])
                assert false: 'child configuration unexpectedly succeeded'
            } catch (Exception failure) {
                outcomes << [cause: deepest(failure).class, events: service.events.toList(),
                             values: service.active.routes*.path, paths: service.children.collect { it.modelPath }]
            }
        }

        then:
        outcomes[0].events == outcomes[1].events
        outcomes[0].values == outcomes[1].values
        outcomes[0].paths == outcomes[1].paths
        outcomes[0].values == ['/first']
        outcomes[0].paths == ['<root>.routes[0]', null]
        !outcomes[0].events.any { it.contains('/later') || it.startsWith('tree:') || it == 'root:apply' }

        when: 'a new root proves aborted-session cleanup'
        def fresh = run(service, true, [[path: '/fresh']])

        then:
        fresh.model.routes*.path == ['/fresh']

        where:
        badBody << [[path: 'lifecycle-fail'], [path: '/bad', missing: true]]
    }

    def 'comparator failure keeps prior child and claims the rejected child without assigning its placement for #kind'() {
        given:
        def service = schema("$kind<Route> routes = new TreeSet(new RejectingOrder())")
        def outcomes = []

        when:
        [false, true].each { stream ->
            try {
                run(service, stream, [[path: '/first'], [path: '/bad'], [path: '/later']])
                assert false: 'comparator unexpectedly succeeded'
            } catch (Exception failure) {
                if (stream) { assert failure.message.contains('/routes/1') }
                def children = service.children.toList()
                outcomes << [cause: deepest(failure).message, events: service.events.toList(),
                             values: service.active.routes*.path, paths: children.collect { it.modelPath },
                             claimed: owning(children[1]) != null]
            }
        }

        then:
        outcomes[0] == outcomes[1]
        outcomes[0].cause == 'incomparable route'
        outcomes[0].values == ['/first']
        outcomes[0].paths == ['<root>.routes[0]', null]
        outcomes[0].claimed
        outcomes[0].events == ['create', 'apply:/first:0', 'create', 'apply:/bad:1']

        where:
        kind << ['SortedSet', 'NavigableSet']
    }

    def 'comparator can accept Builders then fail during ordinary Model materialization for #kind'() {
        given:
        def service = schema("$kind<Route> routes = new TreeSet(new BuilderOnlyOrder())")
        def outcomes = []

        when:
        [false, true].each { stream ->
            try {
                run(service, stream, [[path: '/z'], [path: '/a']])
                assert false: 'Model comparator unexpectedly succeeded'
            } catch (Exception failure) {
                outcomes << [cause: deepest(failure).message, values: service.active.routes*.path,
                             events: service.events.toList()]
            }
        }

        then:
        outcomes[0] == outcomes[1]
        outcomes[0].cause == 'Model comparison failed'
        outcomes[0].values == ['/a', '/z']
        outcomes[0].events == ['create', 'apply:/z:0', 'create', 'apply:/a:1',
                               'root:apply', 'tree:/a:true', 'tree:/z:true']

        where:
        kind << ['SortedSet', 'NavigableSet']
    }

    def 'configuring a returned child is too late to preserve ordinary PostApply timing'() {
        given:
        def service = schema('Set<Route> routes')

        when:
        def ordinary = run(service, false, [[path: '/health']])
        service.events.clear()
        def late = service.Create.With {
            def child = route()
            child.applyStruct([path: '/health'], 'service.yaml')
        }

        then:
        ordinary.events == ['create', 'apply:/health:0', 'root:apply', 'tree:/health:true']
        service.events == ['create', 'apply:/default:0', 'apply:/health:1', 'root:apply', 'tree:/health:true']
        late.routes*.path == ['/health']
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#owned-child-sequences')
    def 'creates ordinary owned children from supplied body sequences'() {
        given:
        def service = schema('List<Route> routes')

        when:
        def ordinary = run(service, false, [[path: '/health'], [path: '/metrics'], [path: '/health']])
        def structured = run(service, true, [[path: '/health'], [path: '/metrics'], [path: '/health']])

        then:
        structured.model.routes*.path == ['/health', '/metrics', '/health']
        structured.events == ordinary.events
        structured.paths == ordinary.paths
        structured.model.routes.every { it.owner.is(structured.model) }
    }

    def 'Template definition and fresh recipients preserve ordinary recipe semantics for #declaration'() {
        given:
        def service = schema(declaration)
        def ordinary = service.Create.Template.With {
            route { path '/z' }
            route { path '/a' }
            route { path '/a' }
        }
        def structured = service.Create.Template.FromStruct([routes: [[path: '/z'], [path: '/a'], [path: '/a']]], 'template.yaml')

        expect:
        service.events.empty
        service.children.empty
        structured.routes*.path == ordinary.routes*.path

        when:
        def recipients = []
        [ordinary, structured, structured].each { recipe ->
            service.events.clear()
            service.activeRoutes = null
            service.Template.With(recipe) {
                recipients << [model: service.Create.With(), events: service.events.toList()]
            }
        }

        then:
        recipients[1].model.routes*.path == recipients[0].model.routes*.path
        recipients[1].events == recipients[0].events
        recipients[2].events == recipients[0].events
        recipients.every { result -> result.model.routes.every { it.owner.is(result.model) } }
        !recipients[1].model.routes.first().is(recipients[2].model.routes.first())

        where:
        declaration << ['List<Route> routes', 'Set<Route> routes',
                        'SortedSet<Route> routes = new TreeSet(new PathOrder())',
                        'NavigableSet<Route> routes = new TreeSet(new PathOrder())']
    }

    def 'empty sequences retain ordinary initial children and append preserves defaults'() {
        given:
        def service = schema('List<Route> routes; @PostCreate void seed() { route(path: "/initial") }')

        when:
        def empty = service.Create.FromStruct([routes: []])
        def appended = service.Create.FromStruct([routes: [[:], [path: '/last']]])

        then:
        empty.routes*.path == ['/initial']
        appended.routes*.path == ['/initial', '/default', '/last']
        appended.routes.every { it.owner.is(appended) }
    }

    def 'child Templates supply defaults inside normal creation before Struct configuration'() {
        given:
        def service = schema('List<Route> routes')
        def routeType = artifacts.loadClass('setprobe.Route')
        def recipe = routeType.Create.Template.With { path '/template' }
        def model

        when:
        routeType.Template.With(recipe) {
            model = service.Create.FromStruct([routes: [[:], [path: '/explicit']]])
        }

        then:
        model.routes*.path == ['/template', '/explicit']
        service.events.findAll { it.startsWith('apply:') } == ['apply:/template:0', 'apply:/explicit:1']
    }

    def 'malformed body at the reached input index retains earlier work: #body'() {
        given:
        def service = schema('List<Route> routes')

        when:
        service.Create.FromStruct([routes: [[path: '/first'], body, [path: '/later']], marker: 'later'], 'service.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('service.yaml')
        failure.message.contains('/routes/1')
        service.active.routes*.path == ['/first']
        service.active.marker == null
        !service.events.any { it.contains('/later') || it == 'root:apply' || it.startsWith('tree:') }

        where:
        body << [null, 'text', 42, true, [], [unknown: true], [path: 42], [owner: [:]], ['@type': 'setprobe.Route'], [key: 'invented']]
    }

    def 'rejects non-sequence container input without allocating a child: #input'() {
        given:
        def service = schema('Set<Route> routes')

        when:
        service.Create.FromStruct([routes: input], 'service.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/routes')
        service.children.empty

        where:
        input << [null, [:], 'text', 42, true, new LinkedHashSet()]
    }

    def 'attachment failure reports input index even after comparator-equivalent insertions for #kind'() {
        given:
        def service = schema('@Struct.Name("r/o~utes") ' + kind + '<Route> routes = new TreeSet(new RejectingOrder())')

        when:
        service.Create.FromStruct([('r/o~utes'): [[path: '/a'], [path: '/a'], [path: '/bad'], [path: '/later']]], 'opaque.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('opaque.yaml')
        failure.message.contains('/r~1o~0utes/2')
        deepest(failure) instanceof IllegalArgumentException
        deepest(failure).message == 'incomparable route'
        service.children.size() == 3
        service.active.routes*.path == ['/a']
        service.children*.modelPath == ['<root>.routes[0]', '<root>.routes[0]', null]
        owning(service.children[2]).name == 'routes'

        where:
        kind << ['SortedSet', 'NavigableSet']
    }

    def 'body failures retain the nested input pointer and cyclic containers stop when reached'() {
        given:
        def service = schema('@Struct.Name("r/o~utes") List<Route> routes')
        def items = [[path: '/first']]
        items << [path: items]

        when:
        service.Create.FromStruct([('r/o~utes'): items], 'service.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/r~1o~0utes/1')
        failure.cause.message.contains('/r~1o~0utes/1/path')
        service.active.routes*.path == ['/first']
    }

    def 'excluded relationship families never gain sequence admission: #declaration'() {
        given:
        def service = schema(declaration)

        when:
        def contract = new InputContractResolver().read(service)

        then:
        !contract.entries().containsKey('routes')

        where:
        declaration << ['@Field(FieldType.LINK) List<Route> routes',
                        '@Field(FieldType.OPTIONAL_LINK) Set<Route> routes',
                        'List<Keyed> routes; @DSL static class Keyed { @Key String name }',
                        'List<Abstract> routes; @DSL abstract static class Abstract { String value }']
    }

    def 'inspection reads source-free sequence contracts without initializing either Schema for #kind'() {
        given:
        def service = schema(kind + '<Route> routes; static { throw new AssertionError("Schema initialized") }', false, true)

        when:
        def contract = new InputContractResolver().read(service)

        then:
        contract.entries().routes.role() == CompiledBuilderSchema.Role.OWNED_SEQUENCE
        contract.entries().routes.payload().type().name == 'setprobe.Route'
        contract.entries().routes.operation() == 'route'
        contract.entries().routes.methods().size() == 1
        contract.entries().routes.methods().first().parameterTypes.toList() == [Map, Closure]

        where:
        kind << ['List', 'Set', 'SortedSet', 'NavigableSet']
    }

    def 'corrupt sequence projection fails before user initialization: #damage'() {
        given:
        def service = schema('List<Route> routes; static { throw new AssertionError("Schema initialized") }')
        damageContract(damage)

        when:
        new InputContractResolver().read(service)

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile')
        !(deepest(failure) instanceof AssertionError)

        where:
        damage << ['missing creator', 'missing selection', 'raw getter', 'wrong item', 'wrong role']
    }

    private void damageContract(String damage) {
        File file = new File(compilerConfiguration.targetDirectory, 'setprobe/Service_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (damage == 'missing creator' && name == 'route' && descriptor.startsWith('(Ljava/util/Map;Lgroovy/lang/Closure;)')) {
                    return null
                }
                if (name == 'getRoutes' && damage == 'raw getter') { signature = null }
                if (name == 'getRoutes' && damage == 'wrong item') { signature = '()Ljava/util/List<Ljava/lang/String;>;' }
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (damage == 'missing selection' && annotation.endsWith('GeneratedInput$Selection;')) { return null }
                        def downstream = super.visitAnnotation(annotation, visible)
                        if (damage == 'wrong role' && annotation.endsWith('GeneratedInput$Selection;')) {
                            return new AnnotationVisitor(Opcodes.ASM9, downstream) {
                                @Override void visit(String member, Object value) {
                                    super.visit(member, member == 'role' ? 'PROPERTY' : value)
                                }
                            }
                        }
                        downstream
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }

    @CompileStatic
    private static SchemaRelationshipDeclaration owning(InternalKlumBuilder child) {
        child.readOwningRelationship()
    }

    private static Throwable deepest(Throwable failure) {
        while (failure.cause != null && !failure.cause.is(failure)) {
            failure = failure.cause
        }
        failure
    }
}
