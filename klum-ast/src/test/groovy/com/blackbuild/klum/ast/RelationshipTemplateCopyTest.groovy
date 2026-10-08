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
import com.blackbuild.klum.ast.runtime.KlumException
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.internal.DslHelper
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('856')
class RelationshipTemplateCopyTest extends AbstractDSLSpec {
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Completed-Object-Support.md#templates-copies-and-imports')
    def 'a Template child is recaptured under the recipient field on each application'() {
        given:
        schema()
        def liveView
        def recipe = getClass('Definition').Create.Template.With {
            child { value 'configured'; applyLater { result = value.toUpperCase() } }
            liveView = KlumBuilderSupport.of(delegate.child).structure
        }
        def recipientType = getClass('Recipient')

        when:
        def first = recipientType.Create.With { actual { copyFrom recipe.child } }
        def second = recipientType.Create.With { actual { copyFrom recipe.child } }

        then:
        retained(recipe) == null
        retained(recipe.child).declaringClass.is(getClass('DefinitionBase'))
        retained(recipe.child).name == 'child'
        first.actual.value == 'configured'
        first.actual.result == 'CONFIGURED'
        !first.actual.is(second.actual)
        !first.actual.is(recipe.child)
        !TemplateManager.isTemplate(first.actual)
        KlumObjectSupport.of(first.actual).structure.owningRelationship.orElseThrow().declaringClass.is(recipientType)
        KlumObjectSupport.of(first.actual).structure.getOwningRelationshipAnnotation(getClass('Slot')).orElseThrow().value() == 'recipient'
        failureOf { liveView.owningRelationship } instanceof KlumModelException
        [recipe, recipe.child].every { value -> failureOf { KlumObjectSupport.of(value) } instanceof KlumException }
        failureOf { recipientType.Create.With { linked recipe.child } } instanceof KlumModelException
        failureOf { recipientType.Create.With { actual recipe.child } } instanceof KlumModelException
    }

    def 'definition list set and map attachments retain only their accepted containing declaration'() {
        given:
        schema()
        def nodeType = getClass('Node')

        when:
        def recipe = getClass('Definition').Create.Template.With {
            nodes = [nodeType.Create.AsBuilder().With { value 'list' }]
            unique = [nodeType.Create.AsBuilder().With { value 'set' }]
            indexed = [('a.b'): nodeType.Create.AsBuilder().With { value 'map' }]
        }

        then:
        retained(recipe) == null
        retained(recipe.nodes[0]).name == 'nodes'
        retained(recipe.unique.first()).name == 'unique'
        retained(recipe.indexed['a.b']).name == 'indexed'
        (recipe.nodes + recipe.unique + recipe.indexed.values()).every { TemplateManager.isTemplate(it) }
    }

    def '#donorKind copy sources recapture single children and preserve ordinary LINK identity'() {
        given:
        schema()
        def definitionType = getClass('Definition')
        def external = getClass('Node').Create.With { value 'external' }
        def source = donorKind == 'Template' ? definitionType.Create.Template.With {
            child { value 'source' }
            linked external
        } : donorKind == 'Model' ? definitionType.Create.With {
            child { value 'source' }
            linked external
        } : [child: [value: 'source'], linked: external]

        when:
        def result = definitionType.Create.With { copyFrom source }

        then:
        result.child.value == 'source'
        result.linked.is(external)
        KlumObjectSupport.of(result.child).structure.owningRelationship.orElseThrow().declaringClass.is(getClass('DefinitionBase'))
        KlumObjectSupport.of(result.child).structure.owningRelationship.orElseThrow().name == 'child'
        KlumObjectSupport.of(result.linked).structure.owningRelationship.empty
        KlumObjectSupport.of(result).structure.owningRelationship.empty
        donorKind == 'Map' || !result.child.is(source.child)

        where:
        donorKind << ['Model', 'Template', 'Map']
    }

    def 'a standalone copy of a child has no donor declaration'() {
        given:
        schema()
        def source = create('Definition') { child { value 'source' } }
        def nodeType = getClass('Node')

        when:
        def result = nodeType.Create.With { copyFrom source.child }

        then:
        result.value == 'source'
        KlumObjectSupport.of(result).structure.owningRelationship.empty
        retained(source.child).name == 'child'
    }

    def 'same-session Builder copying and nested merge retain the recipient claim and replay once'() {
        given:
        schema()
        def sourceBuilder
        def copiedBuilder
        BuilderRelationshipLifetimeTest.observe(16) {
            assert KlumBuilderSupport.of(sourceBuilder).structure.owningRelationship.orElseThrow().name == 'child'
            assert KlumBuilderSupport.of(copiedBuilder).structure.owningRelationship.orElseThrow().name == 'actual'
        }

        when:
        def result = create('Workspace') {
            definition {
                sourceBuilder = child { value 'source'; applyLater { result = value.toUpperCase() } }
            }
            recipient {
                copiedBuilder = actual { value 'initial' }
                copyFrom([actual: sourceBuilder])
            }
        }

        then:
        result.recipient.actual.value == 'source'
        result.recipient.actual.result == 'SOURCE'
        !result.recipient.actual.is(result.definition.child)
        retained(result.recipient.actual).name == 'actual'

        when:
        create('Recipient') { actual { copyFrom sourceBuilder } }

        then:
        thrown(KlumModelException)
    }

    private void schema() {
        createNonDslClass '''
            package metadata
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Slot { String value() }
            @DSL class Node { String value; String result }
            @DSL class DefinitionBase { @Slot('definition') Node child }
            @DSL class Definition extends DefinitionBase {
                List<Node> nodes
                Set<Node> unique
                @Field(keyMapping = { it.value }) Map<String, Node> indexed
                @Field(FieldType.LINK) Node linked
            }
            @DSL class Recipient {
                @Slot('recipient') Node actual
                @Field(FieldType.LINK) Node linked
            }
            @DSL class Workspace { Definition definition; Recipient recipient }
        '''
    }

    // Test-only inspection qualifies private Template retention without widening completed-object support.
    static Object retained(Object model) {
        def stateField = DslHelper.getField(model.class, '$state').orElseThrow()
        stateField.accessible = true
        def state = stateField.get(model)
        def declarationField = state.class.getDeclaredField('owningRelationship')
        declarationField.accessible = true
        declarationField.get(state)
    }

    private static Throwable failureOf(Closure operation) {
        try { operation.call(); null } catch (Throwable error) { error }
    }
}
