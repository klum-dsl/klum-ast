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

import com.blackbuild.klum.ast.runtime.KlumException
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

/** LP-7 evidence only: container annotations remain unsupported. */
@Issue('867')
class LifecycleParticipantContainerProbeTest extends AbstractDSLSpec {
    def 'rejects #shape #role field participation in #phase'() {
        when:
        createSecondaryClass declaration(phase, role, shape)

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains('Lifecycle participant on children requires a non-static direct DSL field retained on the Schema')

        where:
        [phase, role, shape] << [phases(), ['Creator', 'Mutator'],
            ['List<Child>', 'Set<Child>', 'Map<String, Child>', 'List', 'Set', 'Map', 'List<? extends Child>', 'Map<String, ? extends Child>', 'Child[]']].combinations()
    }

    def 'accepts otherwise identical direct field #role declaration in #phase'() {
        given:
        createSecondaryClass declaration(phase, role, 'Child')

        when:
        def result = Parent.Create.One()

        then:
        result.children == null

        where:
        [phase, role] << [phases(), ['Creator', 'Mutator']].combinations()
    }

    def 'type participation visits owned #shape entries with field name but no occurrence context in #phase'() {
        given:
        schema(shape, phase)
        def childType = Child

        when:
        def result = Parent.Create.With {
            def a = childType.Create.AsBuilder().With { name 'a' }
            def b = childType.Create.AsBuilder().With { name 'b' }
            children = shape == 'Map' ? [('a.b[0]'): a, second: b] : [a, b]
        }
        def values = entries(result.children)

        then:
        values*.visits == [1, 1]
        values*.incoming == ['children', 'children']
        values*.declaration == (phase == 'AutoCreate' ? ['before-owner', 'before-owner'] : ['children', 'children'])
        values.every { it.parent.is(result) }
        values.every { KlumObjectSupport.of(it).structure.owningRelationship.orElseThrow().name == 'children' }
        values.every { KlumObjectSupport.of(it).constructionPath != null }

        where:
        [shape, phase] << [shapes(), phases()].combinations()
    }

    def 'null and empty #shape containers have no child type invocations'() {
        given:
        schema(shape, 'AutoLink')

        when:
        def empty = Parent.Create.One()
        def absent = Parent.Create.With { children = null }

        then:
        entries(empty.children).empty
        absent.children == null
        Child.calls == 0

        where:
        shape << shapes()
    }

    def 'null #shape entries survive materialization without a target invocation'() {
        given:
        schema(shape, 'AutoLink')

        when:
        def result = Parent.Create.With { children = shape == 'Map' ? [one: null] : [null] }

        then:
        entries(result.children) == [null]
        Child.calls == 0

        where:
        shape << shapes()
    }

    def 'mixed optional #shape entries preserve sealed targets and visit owned identities once'() {
        given:
        schema(shape, 'AutoLink', 'OPTIONAL_LINK')
        def childType = Child
        def completed = childType.Create.With { name 'external' }
        childType.calls = 0

        when:
        def result = Parent.Create.With {
            def owned = childType.Create.AsBuilder().With { name 'owned' }
            children = shape == 'Map' ? [owned: owned, again: owned, external: completed] : [owned, owned, completed]
            owned.peer(owned)
        }
        def values = entries(result.children)
        def owned = values.find { it.name == 'owned' }

        then:
        childType.calls == 1
        owned.visits == 1
        owned.peer.is(owned)
        owned.parent.is(result)
        values.find { it.name == 'external' }.is(completed)
        shape == 'Set' || values[0].is(values[1])
        KlumObjectSupport.of(completed).structure.owningRelationship.empty

        where:
        shape << shapes()
    }

    def 'LINK #shape aliases do not turn incoming traversal into ownership or repeat type calls'() {
        given:
        schema(shape, 'AutoLink', 'LINK', 'Child direct')
        def completed = Child.Create.With { name 'external' }
        Child.calls = 0

        when:
        def result = Parent.Create.With {
            def owned = direct { name 'owned' }
            children = shape == 'Map' ? [owned: owned, external: completed] : [owned, owned, completed]
        }

        then:
        Child.calls == 1
        result.direct.incoming == 'direct'
        result.direct.declaration == 'direct'
        entries(result.children).find { it.name == 'owned' }.is(result.direct)
        entries(result.children).find { it.name == 'external' }.is(completed)

        where:
        shape << shapes()
    }

    def 'composition #shape rejects completed children and LINK rejects fresh children'() {
        given:
        schema(shape, 'AutoLink', 'DEFAULT', "@Field(value = FieldType.LINK, members = 'linkedChild'${shape == 'Map' ? ', keyMapping = { it.name }' : ''}) $shape<${shape == 'Map' ? 'String, ' : ''}Child> linked")
        def childType = Child
        def completed = childType.Create.With { name 'external' }

        when:
        Parent.Create.With { children = shape == 'Map' ? [one: completed] : [completed] }

        then:
        KlumException compositionFailure = thrown()
        messages(compositionFailure).contains('Completed DSL Object inputs')

        when:
        Parent.Create.With {
            def fresh = childType.Create.AsBuilder().With { name 'fresh' }
            linked = shape == 'Map' ? [one: fresh] : [fresh]
        }

        then:
        KlumException linkFailure = thrown()
        messages(linkFailure).contains('Fresh Builder inputs are not supported for LINK')

        where:
        shape << shapes()
    }

    def 'root callbacks can create late #shape entries without replaying earlier phases'() {
        given:
        schema(shape, 'PostTree', 'DEFAULT', """
            @PostTree void supply() { child { name 'late' } }
        """, '@AutoCreate void early() { early = true }')

        when:
        def result = Parent.Create.One()
        def child = entries(result.children).first()

        then:
        child.visits == 1
        !child.early
        child.declaration == 'children'
        child.parent == null
        KlumObjectSupport.of(child).structure.owningRelationship.orElseThrow().name == 'children'

        where:
        shape << shapes()
    }

    def 'growing a live #shape enumeration from a child callback fails with the existing iterator cause'() {
        given:
        schema(shape, 'AutoLink', 'DEFAULT', 'boolean added', '', """
            def parent = Parent.Create.narrowBuilder(c.containingBuilder)
            if (!parent.added) {
                parent.added(true)
                parent.child { name 'late' }
            }
        """)
        def childType = Child

        when:
        Parent.Create.With {
            def a = childType.Create.AsBuilder().With { name 'a' }
            def b = childType.Create.AsBuilder().With { name 'b' }
            children = shape == 'Map' ? [a: a, b: b] : [a, b]
        }

        then:
        Exception failure = thrown()
        causes(failure).any { it instanceof ConcurrentModificationException }

        where:
        shape << shapes()
    }

    def 'Template and FromMap #shape routes keep type dispatch separate from field participation'() {
        given:
        schema(shape, 'AutoLink')
        def childType = Child
        def recipe = Parent.Create.Template.With {
            def entry = childType.Create.AsBuilder().With { name 'recipe' }
            children = shape == 'Map' ? [one: entry] : [entry]
        }

        when:
        def first = Parent.Create.With { copyFrom recipe }
        def second = Parent.Create.With { copyFrom recipe }
        def input = shape == 'Map' ? [one: [name: 'map']] : shape == 'Set' ? [[name: 'map']].toSet() : [[name: 'map']]
        def mapped = Parent.Create.FromMap([children: input])

        then:
        entries(recipe.children)*.visits == [0]
        entries(first.children)*.visits == [1]
        entries(second.children)*.visits == [1]
        entries(mapped.children)*.visits == [1]
        !entries(first.children).first().is(entries(second.children).first())
        entries(mapped.children).first().parent.is(mapped)
        // Copy placement is not an authoritative normalized attachment; absence is permitted.
        KlumObjectSupport.of(entries(first.children).first()).structure.owningRelationship.empty

        where:
        shape << shapes()
    }

    def 'child invocation failures retain enumeration diagnostics without adding index or key context (#shape)'() {
        given:
        schema(shape, 'AutoLink', 'DEFAULT', '', '', "throw new IllegalStateException('probe failure')")
        def childType = Child

        when:
        Parent.Create.With {
            def entry = childType.Create.AsBuilder().With { name 'failed' }
            children = shape == 'Map' ? [('a.b[0]'): entry] : [entry]
        }

        then:
        KlumException failure = thrown()
        messages(failure).contains('Participant Observed handler Observe during AutoLink on Child')
        messages(failure).contains(shape == 'Map' ? "children.'a.b[0]'" : 'children[0]')
        causes(failure).last().message == 'probe failure'

        where:
        shape << ['List', 'Map']
    }

    private static String declaration(String phase, String role, String shape) {
        String handler = role == 'Creator' ? """
            class Handle implements LifecycleCreationHandler<Managed> {
                KlumBuilder<?> create(LifecycleFieldContext<Managed> c) { null }
            }
        """ : """
            class Handle implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {}
            }
        """
        """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @Lifecycle$role(phase = $phase, handler = Handle)
            @interface Managed {}
            $handler
            @DSL class Child {}
            @DSL class Parent { @Managed $shape children }
        """
    }

    private void schema(String shape, String phase, String fieldType = 'DEFAULT', String parentExtra = '',
                        String childExtra = '', String handlerExtra = '') {
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            @LifecycleMutator(phase = $phase, handler = Observe)
            @interface Observed {}
            class Observe implements LifecycleMutationHandler<Observed> {
                void mutate(LifecycleMutationContext<Observed> c) {
                    assert c.isType() && c.declaredType == Child
                    def child = Child.Create.narrowBuilder(c.targetBuilder)
                    child.visits(child.visits + 1)
                    Child.calls++
                    if (c.containingBuilder != null) {
                        child.incoming(c.fieldName)
                        if ('$phase' == 'AutoCreate') {
                            child.declaration('before-owner')
                        } else {
                            def owning = KlumBuilderSupport.of(c.targetBuilder).structure.owningRelationship
                            child.declaration(owning.map { it.name }.orElse('absent'))
                        }
                    }
                    $handlerExtra
                }
            }
            @Observed @DSL class Child {
                static int calls
                String name
                int visits
                String incoming
                String declaration
                boolean early
                @Owner Parent parent
                @Field(FieldType.LINK) Child peer
                $childExtra
            }
            @DSL class Parent {
                @Field(value = FieldType.$fieldType, members = 'child'${shape == 'Map' ? ', keyMapping = { it.name }' : ''})
                $shape<${shape == 'Map' ? 'String, ' : ''}Child> children
                $parentExtra
            }
        """
    }

    private static List<String> phases() { ['AutoCreate', 'AutoLink', 'Default', 'PostTree'] }
    private static List<String> shapes() { ['List', 'Set', 'Map'] }
    private static List entries(Object container) {
        container instanceof Map ? container.values().toList() : container.toList()
    }
    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable current = failure; current != null; current = current.cause) result << current
        result
    }
    private static String messages(Throwable failure) { causes(failure)*.message.join('\n') }
}
