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

import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import spock.lang.Issue

@Issue('856')
class RelationshipCompositionTest extends AbstractDSLSpec {
    def 'normalized #fieldName entries retain their inherited containing declaration through sealing'() {
        given:
        schema()
        def nodeType = getClass('Node')
        def receivers
        def views
        [16, 50].each { phase -> BuilderRelationshipLifetimeTest.observe(phase) {
            views.each { view ->
                assert view.owningRelationship.orElseThrow().declaringClass.is(getClass('GraphBase'))
                assert view.owningRelationship.orElseThrow().name == fieldName
                assert view.getOwningRelationshipAnnotation(getClass('Slot')).orElseThrow().value() == fieldName
            }
            assert receivers.every { it.sealed == (phase == 50) }
        } }

        when:
        instance = create('Graph') {
            receivers = ['first', 'second'].collect { label -> nodeType.Create.AsBuilder().With { name label } }
            views = receivers.collect { KlumBuilderSupport.of(it).structure }
            delegate."$fieldName" = fieldName == 'indexed' ? [('a.b[0]'): receivers[0], second: receivers[1]] : receivers
            related = receivers + receivers
        }
        def children = fieldName == 'indexed' ? instance.indexed.values().toList() : instance."$fieldName".toList()

        then:
        children*.name == ['first', 'second']
        children.every { KlumObjectSupport.of(it).structure.owningRelationship.orElseThrow().name == fieldName }
        instance.related[0].is(children[0])
        instance.related[2].is(children[0])
        KlumObjectSupport.of(instance).structure.owningRelationship.empty
        views.every { view -> failureOf { view.owningRelationship } instanceof KlumModelException }

        where:
        fieldName << ['nodes', 'unique', 'indexed']
    }

    def 'optional single list and map entries retain per-entry ownership and external originals'() {
        given:
        schema()
        def externalGraph = create('Graph') { direct { name 'external' } }
        def externalRoot = create('Node') { name 'root' }
        def nodeType = getClass('Node')
        def views
        BuilderRelationshipLifetimeTest.observe(20) {
            assert views.collect { it.owningRelationship.orElseThrow().name } == ['direct', 'optionalNodes', 'optionalNodes', 'optionalMap']
        }

        when:
        instance = create('Graph') {
            def claimed = direct { name 'claimed' }
            def fresh = nodeType.Create.AsBuilder().With { name 'list-owned' }
            optional = claimed
            optionalNodes = [fresh, claimed, externalGraph.direct, externalRoot, fresh]
            def mapOwned = nodeType.Create.AsBuilder().With { name 'map-owned' }
            optionalMap = [owned: mapOwned, again: fresh, claimed: claimed, external: externalGraph.direct]
            views = [claimed, fresh, delegate.optionalNodes[0], mapOwned].collect { KlumBuilderSupport.of(it).structure }
        }

        then:
        instance.optional.is(instance.direct)
        instance.optionalNodes[0].is(instance.optionalNodes[4])
        instance.optionalNodes[1].is(instance.direct)
        instance.optionalNodes[2].is(externalGraph.direct)
        instance.optionalNodes[3].is(externalRoot)
        instance.optionalMap.again.is(instance.optionalNodes[0])
        instance.optionalMap.external.is(externalGraph.direct)
        declaration(instance.optionalMap.owned) == 'optionalMap'
        declaration(instance.optionalNodes[0]) == 'optionalNodes'
        declaration(instance.optionalNodes[2]) == 'direct'
        KlumObjectSupport.of(instance.optionalNodes[3]).structure.owningRelationship.empty
    }

    def 'accepted self optional claim transfer replaces the declaration while a rejected claim leaves it intact'() {
        given:
        schema()
        def nodeType = getClass('Node')
        def view
        def rejection
        BuilderRelationshipLifetimeTest.observe(16) { assert view.owningRelationship.orElseThrow().name == 'direct' }

        when:
        instance = create('Graph') {
            def child = nodeType.Create.AsBuilder().With { name 'transfer'; optional = delegate }
            direct = child
            view = KlumBuilderSupport.of(child).structure
            rejection = RelationshipCompositionTest.failureOf { delegate.other = child }
        }

        then:
        rejection instanceof KlumModelException
        rejection.message.contains('already claimed')
        instance.direct.optional.is(instance.direct)
        instance.other == null
        declaration(instance.direct) == 'direct'
    }

    def 'self and cyclic LINK identities retain their declarations during completed validation'() {
        given:
        createClass '''
            package metadata
            import com.blackbuild.klum.ast.runtime.KlumObjectSupport
            @DSL class Graph { Node first; Node second }
            @DSL class Node {
                static List<String> validated = []
                @Field(FieldType.LINK) Node peer
                @Field(FieldType.LINK) Node self
                @Validate void inspect() {
                    assert self.is(this)
                    validated << KlumObjectSupport.of(this).structure.owningRelationship.orElseThrow().name
                }
            }
        '''

        when:
        instance = clazz.Create.With {
            def a = first {}
            def b = second {}
            a.peer = b; b.peer = a
            a.self = a; b.self = b
        }

        then:
        instance.first.peer.is(instance.second)
        instance.second.peer.is(instance.first)
        declaration(instance.first.peer) == 'second'
        getClass('Node').validated.toSet() == ['first', 'second'].toSet()
    }

    def 'normal container attachment after OWNER captures metadata immediately'() {
        given:
        schema()
        def root
        def nodeType = getClass('Node')
        def observed
        BuilderRelationshipLifetimeTest.observe(16) {
            def child = nodeType.Create.AsBuilder().With { name 'late' }
            root.nodes = [child]
            observed = KlumBuilderSupport.of(child).structure.owningRelationship.orElseThrow()
        }

        when:
        instance = create('Graph') { root = delegate }

        then:
        instance.nodes*.name == ['late']
        observed == KlumObjectSupport.of(instance.nodes[0]).structure.owningRelationship.orElseThrow()
    }

    private void schema() {
        createNonDslClass '''
            package metadata
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Slot { String value() }
            @DSL class Node { String name; @Field(FieldType.OPTIONAL_LINK) Node optional }
            @DSL class GraphBase {
                @Slot('nodes') List<Node> nodes
                @Slot('unique') Set<Node> unique
                @Slot('indexed') @Field(keyMapping = { it.name }) Map<String, Node> indexed
                Node direct
                Node other
                @Field(FieldType.LINK) List<Node> related
                @Field(FieldType.OPTIONAL_LINK) Node optional
                @Field(FieldType.OPTIONAL_LINK) List<Node> optionalNodes
                @Field(value = FieldType.OPTIONAL_LINK, keyMapping = { it.name }) Map<String, Node> optionalMap
            }
            @DSL class Graph extends GraphBase {}
        '''
    }

    private static String declaration(Object model) {
        KlumObjectSupport.of(model).structure.owningRelationship.orElseThrow().name
    }

    private static Throwable failureOf(Closure operation) {
        try { operation.call(); null } catch (Throwable error) { error }
    }
}
