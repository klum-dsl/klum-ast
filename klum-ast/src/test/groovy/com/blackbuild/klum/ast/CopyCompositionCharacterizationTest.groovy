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
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import spock.lang.Issue

/** Revised D5 compatibility baseline: copy identity is preserved; unavailable authority remains empty. */
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

    def '#kind donor copies #fieldName into fresh children without inventing an authoritative placement'() {
        given:
        schema()
        def nodeType = getClass('Node')
        def graphType = getClass('Graph')
        def source = kind == 'Map' ? [nodes: [[value: 'source']], unique: [[value: 'source']] as Set,
                                    indexed: [source: [value: 'source']]] :
            kind == 'Template' ? graphType.Create.Template.With {
                nodes = [nodeType.Create.AsBuilder().With { value 'source'; applyLater { replays++ } }]
                unique = [nodeType.Create.AsBuilder().With { value 'source' }]
                indexed = [source: nodeType.Create.AsBuilder().With { value 'source' }]
            } : kind == 'Model' ? graphType.Create.With {
                nodes = [nodeType.Create.AsBuilder().With { value 'source' }]
                unique = [nodeType.Create.AsBuilder().With { value 'source' }]
                indexed = [source: nodeType.Create.AsBuilder().With { value 'source' }]
            } : null
        def sourceEntry
        def liveEntry
        def liveDeclaration
        BuilderRelationshipLifetimeTest.observe(16) {
            liveDeclaration = KlumBuilderSupport.of(liveEntry).structure.owningRelationship
        }

        when:
        def result = graphType.Create.With {
            if (kind == 'Builder') {
                source = graphType.Create.AsBuilder().With {
                    nodes = [nodeType.Create.AsBuilder().With { value 'source'; applyLater { replays++ } }]
                    unique = [nodeType.Create.AsBuilder().With { value 'source' }]
                    indexed = [source: nodeType.Create.AsBuilder().With { value 'source' }]
                }
            }
            sourceEntry = fieldName == 'indexed' ? source.indexed.source : source."$fieldName".first()
            copyFrom(source)
            liveEntry = fieldName == 'indexed' ? delegate.indexed.source : delegate."$fieldName".first()
        }
        def child = fieldName == 'indexed' ? result.indexed.source : result."$fieldName".first()

        then:
        child.value == 'source'
        !child.is(sourceEntry)
        !TemplateManager.isTemplate(child)
        liveDeclaration.empty
        KlumObjectSupport.of(child).structure.owningRelationship.empty
        !(kind in ['Template', 'Builder']) || fieldName != 'nodes' || child.replays == 1
        getClass('Events').visited.count('source') == (kind == 'Model' ? 6 : 3)

        where:
        [kind, fieldName] << [['Model', 'Template', 'Map', 'Builder'], ['nodes', 'unique', 'indexed']].combinations()
    }

    def '#mode #fieldName copying preserves retention replacement and ordering without claims'() {
        given:
        schema(mode, 'FULL_REPLACE')
        def nodeType = getClass('Node')
        def copied

        when:
        def result = getClass('Graph').Create.With {
            def existing = nodeType.Create.AsBuilder().With { value 'existing' }
            delegate."$fieldName" = [existing]
            copyFrom([(fieldName): fieldName == 'unique' ? [[value: 'copied']] as Set : [[value: 'copied']]])
            copied = delegate."$fieldName".find { it.value == 'copied' }
        }

        then:
        result."$fieldName"*.value == expectedValues
        (copied == null) == (mode == 'SET_IF_EMPTY')
        result."$fieldName".find { it.value == 'copied' } == null ||
            KlumObjectSupport.of(result."$fieldName".find { it.value == 'copied' }).structure.owningRelationship.empty
        mode in ['REPLACE', 'ALWAYS_REPLACE'] ||
            KlumObjectSupport.of(result."$fieldName".first()).structure.owningRelationship.orElseThrow().name == fieldName

        where:
        mode             | fieldName | expectedValues
        'ADD'            | 'nodes'   | ['existing', 'copied']
        'REPLACE'        | 'nodes'   | ['copied']
        'ALWAYS_REPLACE' | 'nodes'   | ['copied']
        'SET_IF_EMPTY'   | 'nodes'   | ['existing']
        'ADD'            | 'unique'  | ['existing', 'copied']
        'REPLACE'        | 'unique'  | ['copied']
        'ALWAYS_REPLACE' | 'unique'  | ['copied']
        'SET_IF_EMPTY'   | 'unique'  | ['existing']
    }

    def '#mode Map copying retains existing accepted claims and leaves direct new placements absent'() {
        given:
        schema('REPLACE', mode)
        def nodeType = getClass('Node')
        def existing
        def observedIdentity
        BuilderRelationshipLifetimeTest.observe(16) {
            if (observedIdentity.is(existing))
                assert KlumBuilderSupport.of(existing).structure.owningRelationship.orElseThrow().name == 'indexed'
        }

        when:
        def result = getClass('Graph').Create.With {
            existing = nodeType.Create.AsBuilder().With { value 'initial' }
            indexed = [same: existing]
            copyFrom([indexed: [same: [value: 'incoming'], added: [value: 'added']]])
            observedIdentity = delegate.indexed.same
        }

        then:
        result.indexed.keySet().toList() == expectedKeys
        result.indexed.same.value == expectedSame
        observedIdentity.is(existing) == retainsSame
        KlumObjectSupport.of(result.indexed.same).structure.owningRelationship.map { it.name }.orElse(null) ==
            (retainsSame ? 'indexed' : null)
        result.indexed.added == null || KlumObjectSupport.of(result.indexed.added).structure.owningRelationship.empty

        where:
        mode             | expectedKeys      | expectedSame | retainsSame
        'FULL_REPLACE'   | ['same', 'added'] | 'incoming'   | false
        'ALWAYS_REPLACE' | ['same', 'added'] | 'incoming'   | false
        'SET_IF_EMPTY'   | ['same']          | 'initial'    | true
        'MERGE_KEYS'     | ['same', 'added'] | 'incoming'   | false
        'MERGE_VALUES'   | ['same', 'added'] | 'incoming'   | true
        'ADD_MISSING'    | ['same', 'added'] | 'initial'    | true
    }

    def 'repeated #kind recipes within and across containers preserve alias identity and independent copy calls'() {
        given:
        schema()
        def source = kind == 'Map' ? [value: 'shared'] : kind == 'Model' ?
            getClass('Node').Create.With { value 'shared' } :
            getClass('Node').Create.Template.With { value 'shared'; applyLater { replays++ } }

        when:
        def first = getClass('Graph').Create.With {
            copyFrom([nodes: [source, source], unique: [source] as Set, indexed: [one: source, two: source]])
        }
        def second = getClass('Graph').Create.With { copyFrom([nodes: [source]]) }

        then:
        first.nodes[0].is(first.nodes[1])
        first.nodes[0].is(first.unique.first())
        first.nodes[0].is(first.indexed.one)
        first.indexed.one.is(first.indexed.two)
        !first.nodes[0].is(second.nodes[0])
        !first.nodes[0].is(source)
        KlumObjectSupport.of(first.nodes[0]).structure.owningRelationship.empty
        KlumObjectSupport.of(second.nodes[0]).structure.owningRelationship.empty
        kind != 'Template' || (first.nodes[0].replays == 1 && second.nodes[0].replays == 1)

        where:
        kind << ['Map', 'Model', 'Template']
    }

    def 'single accepted claim remains authoritative despite copied container aliases in either donor order'() {
        given:
        schema()
        def shared = [value: 'shared']
        def donor = directFirst ? [direct: shared, nodes: [shared]] : [nodes: [shared], direct: shared]

        when:
        def result = getClass('Graph').Create.With { copyFrom donor }

        then:
        result.direct.is(result.nodes[0])
        KlumObjectSupport.of(result.nodes[0]).structure.owningRelationship.orElseThrow().name == 'direct'
        getClass('Events').visited == ['shared']

        where:
        directFirst << [true, false]
    }

    def 'copying the same recipe into two single composition fields preserves existing rejection'() {
        given:
        schema()
        def shared = [value: 'shared']

        when:
        getClass('Graph').Create.With { copyFrom([direct: shared, other: shared]) }

        then:
        def error = thrown(KlumModelException)
        error.message.contains('already claimed')
    }

    def '#claimed same-session Builder source is copied without transferring its original claim'() {
        given:
        schema()
        def nodeType = getClass('Node')
        def source
        def copied
        def sourceDeclaration
        BuilderRelationshipLifetimeTest.observe(16) {
            sourceDeclaration = KlumBuilderSupport.of(source).structure.owningRelationship
            assert KlumBuilderSupport.of(copied).structure.owningRelationship.empty
        }

        when:
        def result = getClass('Graph').Create.With {
            source = nodeType.Create.AsBuilder().With { value 'source'; applyLater { replays++ } }
            if (claimed) direct = source
            copyFrom([nodes: [source, source]])
            copied = delegate.nodes[0]
        }

        then:
        !copied.is(source)
        result.nodes[0].is(result.nodes[1])
        result.nodes[0].replays == 1
        sourceDeclaration.map { it.name }.orElse(null) == (claimed ? 'direct' : null)
        !claimed || !result.nodes[0].is(result.direct)
        KlumObjectSupport.of(result.nodes[0]).structure.owningRelationship.empty

        where:
        claimed << [false, true]
    }

    def 'LINK copies and generated OPTIONAL_LINK mixed attachments preserve original completed identity and claims'() {
        given:
        schema()
        def nodeType = getClass('Node')
        def graphType = getClass('Graph')
        def external = graphType.Create.With { direct { value 'external' } }
        def fresh
        def claimed

        when:
        def result = graphType.Create.With {
            copyFrom([links: [external.direct, external.direct], linkMap: [external: external.direct]])
            claimed = direct { value 'claimed' }
            fresh = nodeType.Create.AsBuilder().With { value 'fresh' }
            optionalNodes = [fresh, claimed, external.direct, fresh]
            optionalMap = [fresh: fresh, claimed: claimed, external: external.direct]
        }

        then:
        result.links.every { it.is(external.direct) }
        result.linkMap.external.is(external.direct)
        result.optionalNodes[0].is(result.optionalNodes[3])
        result.optionalMap.fresh.is(result.optionalNodes[0])
        result.optionalMap.claimed.is(result.direct)
        result.optionalMap.external.is(external.direct)
        KlumObjectSupport.of(result.optionalNodes[0]).structure.owningRelationship.orElseThrow().name == 'optionalNodes'
        KlumObjectSupport.of(result.optionalMap.claimed).structure.owningRelationship.orElseThrow().name == 'direct'
        KlumObjectSupport.of(result.optionalMap.external).structure.owningRelationship.orElseThrow().name == 'direct'
    }

    def 'OPTIONAL_LINK copied containers retain their existing unclaimed materialization behavior'() {
        given:
        schema()
        def nodeType = getClass('Node')
        def external = getClass('Graph').Create.With { direct { value 'external' } }
        def claimed
        def unclaimed
        def liveAbsence
        BuilderRelationshipLifetimeTest.observe(16) {
            liveAbsence = KlumBuilderSupport.of(unclaimed).structure.owningRelationship
        }

        when:
        def result = getClass('Graph').Create.With {
            claimed = direct { value 'claimed' }
            def fresh = nodeType.Create.AsBuilder().With { value 'fresh' }
            copyFrom([optionalNodes: [fresh, claimed, external.direct],
                      optionalMap: [fresh: fresh, claimed: claimed, external: external.direct]])
            unclaimed = delegate.optionalNodes[0]
        }

        then:
        !unclaimed.is(claimed)
        liveAbsence.empty
        result.optionalNodes == [null, null, null]
        result.optionalMap == [fresh: null, claimed: null, external: null]
    }

    def 'set-if-empty copies into empty #fieldName without turning placement into a claim'() {
        given:
        schema('SET_IF_EMPTY', 'SET_IF_EMPTY')

        when:
        def result = getClass('Graph').Create.With {
            copyFrom([(fieldName): fieldName == 'indexed' ? [entry: [value: 'new']] : [[value: 'new']]])
        }
        def child = fieldName == 'indexed' ? result.indexed.entry : result.nodes[0]

        then:
        child.value == 'new'
        KlumObjectSupport.of(child).structure.owningRelationship.empty

        where:
        fieldName << ['nodes', 'indexed']
    }

    def 'OPTIONAL_LINK copied aliases follow an existing recipient claim while unclaimed recipes stay unchanged'() {
        given:
        schema()
        def shared = [value: 'owned']
        def unclaimed = [value: 'unclaimed']

        when:
        def result = getClass('Graph').Create.With {
            copyFrom([direct: shared, optionalNodes: [shared, shared, unclaimed],
                      optionalMap: [owned: shared, unclaimed: unclaimed]])
        }

        then:
        result.optionalNodes[0].is(result.direct)
        result.optionalNodes[1].is(result.direct)
        result.optionalNodes[2] == null
        result.optionalMap.owned.is(result.direct)
        result.optionalMap.unclaimed == null
        KlumObjectSupport.of(result.optionalNodes[0]).structure.owningRelationship.orElseThrow().name == 'direct'
        getClass('Events').visited == ['owned', 'unclaimed']
    }

    def '#mode single copying preserves accepted adoption or nested merge authority'() {
        given:
        schema('REPLACE', 'FULL_REPLACE', mode)
        def existing
        def resulting

        when:
        def result = getClass('Graph').Create.With {
            existing = direct { value 'initial' }
            copyFrom([direct: [value: 'incoming']])
            resulting = delegate.direct
        }

        then:
        resulting.is(existing) == retained
        result.direct.value == expectedValue
        KlumObjectSupport.of(result.direct).structure.owningRelationship.orElseThrow().name == 'direct'

        where:
        mode             | retained | expectedValue
        'MERGE'          | true     | 'incoming'
        'REPLACE'        | false    | 'incoming'
        'ALWAYS_REPLACE' | false    | 'incoming'
        'SET_IF_NULL'    | true     | 'initial'
    }

    def 'generated LINK rejects fresh Builders and aggregates an already claimed Builder without recapture'() {
        given:
        schema()
        def nodeType = getClass('Node')
        def rejection

        when:
        def result = getClass('Graph').Create.With {
            def fresh = nodeType.Create.AsBuilder().With { value 'fresh' }
            try { links = [fresh] } catch (KlumModelException error) { rejection = error }
            def claimed = direct { value 'claimed' }
            links = [claimed, claimed]
        }

        then:
        rejection instanceof KlumModelException
        rejection.message.contains('Fresh Builder inputs')
        result.links[0].is(result.direct)
        result.links[1].is(result.direct)
        KlumObjectSupport.of(result.links[0]).structure.owningRelationship.orElseThrow().name == 'direct'
    }

    private void schema(String collectionMode = 'REPLACE', String mapMode = 'FULL_REPLACE', String singleMode = 'MERGE') {
        createNonDslClass """
            package characterization
            import com.blackbuild.klum.ast.copy.Overwrite
            import com.blackbuild.klum.ast.copy.OverwriteStrategy
            class Events { static List<String> visited = [] }
            @DSL class Node {
                String value
                int replays
                @PostTree void visit() { Events.visited << value }
            }
            @DSL class Graph {
                @Overwrite.Single(OverwriteStrategy.Single.$singleMode) Node direct
                Node other
                @Overwrite.Collection(OverwriteStrategy.Collection.$collectionMode) List<Node> nodes
                @Overwrite.Collection(OverwriteStrategy.Collection.$collectionMode) Set<Node> unique
                @Overwrite.Map(OverwriteStrategy.Map.$mapMode)
                @Field(keyMapping = { it.value }) Map<String, Node> indexed
                @Field(FieldType.LINK) List<Node> links
                @Field(value = FieldType.LINK, keyMapping = { it.value }) Map<String, Node> linkMap
                @Field(FieldType.OPTIONAL_LINK) List<Node> optionalNodes
                @Field(value = FieldType.OPTIONAL_LINK, keyMapping = { it.value }) Map<String, Node> optionalMap
            }
        """
    }
}
