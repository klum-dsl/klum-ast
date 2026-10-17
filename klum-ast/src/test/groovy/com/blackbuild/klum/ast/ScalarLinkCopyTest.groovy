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
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import spock.lang.Issue

@Issue('917')
class ScalarLinkCopyTest extends AbstractDSLSpec {
    def '#strategy LINK copy from #donorKind with #state preserves completed identity and lifecycle'() {
        given:
        schema(strategy == 'default' ? '' : '@Overwrite.Single(OverwriteStrategy.Single.MERGE)')
        def nodeType = getClass('Environment')
        def applicationType = getClass('Application')
        def existing = nodeType.Create.With {
            name 'existing'
            retained 'receiver'
            applyLater { name 'existing-completed' }
        }
        def incoming = state == 'same' ? existing : state in ['null', 'both null'] ? null : nodeType.Create.With {
            name 'incoming'
            applyLater { name 'incoming-completed' }
        }
        def donor = donorFor(applicationType, donorKind, incoming)
        def events = getClass('Events').entries.toList()
        def initial = state in ['empty', 'both null'] ? null : existing

        when:
        def result = applicationType.Create.With {
            environment initial
            copyFrom donor
        }

        then:
        result.environment.is(incoming ?: initial)
        existing.name == 'existing-completed'
        existing.retained == 'receiver'
        incoming == null || incoming.name == (state == 'same' ? 'existing-completed' : 'incoming-completed')
        incoming == null || incoming.retained == (state == 'same' ? 'receiver' : null)
        getClass('Events').entries == events
        events.containsAll(['create', 'apply', 'tree', 'validate'])

        where:
        [strategy, donorKind, state] << [['default', 'MERGE'], ['Model', 'Template', 'Map'],
                                        ['different', 'same', 'null', 'empty', 'both null']].combinations()
    }

    def '#strategy retains its explicit scalar LINK policy for #state donors'() {
        given:
        schema("@Overwrite.Single(OverwriteStrategy.Single.$strategy)")
        def nodeType = getClass('Environment')
        def applicationType = getClass('Application')
        def existing = nodeType.Create.With { name 'existing' }
        def incoming = state in ['null', 'both null'] ? null : nodeType.Create.With { name 'incoming' }
        def initial = state in ['empty', 'both null'] ? null : existing
        def events = getClass('Events').entries.toList()

        when:
        def result = applicationType.Create.With {
            environment initial
            copyFrom([environment: incoming])
        }

        then:
        result.environment.is(strategy == 'SET_IF_NULL' ? (initial ?: incoming) :
                strategy == 'ALWAYS_REPLACE' ? incoming : (incoming ?: initial))
        getClass('Events').entries == events

        where:
        [strategy, state] << [['REPLACE', 'ALWAYS_REPLACE', 'SET_IF_NULL'], ['different', 'null', 'empty', 'both null']].combinations()
    }

    def 'owned scalar #donorKind donors still merge into the existing child and replay recipes once'() {
        given:
        schema()
        def applicationType = getClass('Application')
        def source = donorKind == 'Map' ? [owned: [name: 'incoming']] : donorKind == 'Template' ?
                applicationType.Create.Template.With {
                    owned { name 'incoming'; applyLater { name 'replayed'; recipeRuns++ } }
                } : applicationType.Create.With { owned { name 'incoming' } }
        def child
        def merged

        when:
        def result = applicationType.Create.With {
            child = owned { name 'existing'; retained 'receiver' }
            copyFrom source
            merged = delegate.owned
        }

        then:
        merged.is(child)
        result.owned.name == (donorKind == 'Template' ? 'replayed' : 'incoming')
        result.owned.retained == 'receiver'
        result.owned.recipeRuns == (donorKind == 'Template' ? 1 : 0)
        !TemplateManager.isTemplate(result.owned)
        donorKind == 'Map' || !result.owned.is(source.owned)

        where:
        donorKind << ['Model', 'Template', 'Map']
    }

    def 'copying a Template with a completed LINK replays only the application recipe'() {
        given:
        schema()
        def nodeType = getClass('Environment')
        def applicationType = getClass('Application')
        def existing = nodeType.Create.With { name 'existing' }
        def incoming = nodeType.Create.With { name 'incoming' }
        def recipe = applicationType.Create.Template.With {
            environment incoming
            applyLater { label 'replayed'; recipeRuns++ }
        }
        def events = getClass('Events').entries.toList()

        when:
        def first = applicationType.Create.With { environment existing; copyFrom recipe }
        def second = applicationType.Create.With { environment existing; copyFrom recipe }

        then:
        first.environment.is(incoming)
        second.environment.is(incoming)
        first.label == 'replayed'
        second.label == 'replayed'
        first.recipeRuns == 1
        second.recipeRuns == 1
        recipe.recipeRuns == 0
        recipe.label == null
        getClass('Events').entries == events
    }

    def 'live same-session LINK aliases still merge into their owned Builder'() {
        given:
        schema()
        def nodeType = getClass('Environment')
        def applicationType = getClass('Application')
        def incoming = nodeType.Create.With { name 'incoming' }
        def alias
        def merged

        when:
        def result = applicationType.Create.With {
            alias = owned { name 'existing'; retained 'receiver' }
            environment alias
            copyFrom([environment: incoming])
            merged = delegate.environment
        }

        then:
        merged.is(alias)
        result.environment.is(result.owned)
        result.owned.name == 'incoming'
        result.owned.retained == 'receiver'
    }

    def 'a #kind donor retains the sealed completed LINK merge boundary'() {
        given:
        schema()
        def nodeType = getClass('Environment')
        def existing = nodeType.Create.With { name 'existing' }

        when:
        getClass('Application').Create.With {
            environment existing
            def source = kind == 'live Builder' ? owned { name 'live' } :
                    delegate.environment
            copyFrom([environment: source])
        }

        then:
        def error = thrown(KlumModelException)
        error.message.contains('A sealed Builder cannot be configured')
        existing.name == 'existing'

        where:
        kind << ['live Builder', 'sealed wrapper']
    }

    def 'LINK containers retain completed identity under collection replacement and map MERGE_VALUES'() {
        given:
        schema()
        def nodeType = getClass('Environment')
        def existing = nodeType.Create.With { name 'existing' }
        def incoming = nodeType.Create.With { name 'incoming' }
        def events = getClass('Events').entries.toList()

        when:
        def result = getClass('Application').Create.With {
            environments = [existing]
            indexed = [same: existing]
            copyFrom([environments: [incoming], indexed: [same: incoming]])
        }

        then:
        result.environments.size() == 1
        result.environments.first().is(incoming)
        result.indexed.same.is(incoming)
        getClass('Events').entries == events
    }

    def 'a #kind value cannot be merged recursively into a completed LINK'() {
        given:
        schema()
        def nodeType = getClass('Environment')
        def existing = nodeType.Create.With { name 'existing' }
        def incoming = kind == 'Template' ? nodeType.Create.Template.With { name 'recipe' } : [name: 'data']
        def events = getClass('Events').entries.toList()

        when:
        getClass('Application').Create.With {
            environment existing
            copyFrom([environment: incoming])
        }

        then:
        def error = thrown(KlumModelException)
        error.message.contains('A sealed Builder cannot be configured')
        existing.name == 'existing'
        getClass('Events').entries == events

        where:
        kind << ['Template', 'Map recipe']
    }

    def 'invalid LINK inputs retain #kind diagnostics'() {
        given:
        schema()
        def nodeType = getClass('Environment')
        def existing = nodeType.Create.With { name 'existing' }
        def recipe = nodeType.Create.Template.With { name 'recipe' }

        when:
        getClass('Application').Create.With {
            if (kind == 'Template relationship') {
                environment recipe
            } else if (kind == 'fresh Builder') {
                environment nodeType.Create.AsBuilder().With { name 'fresh' }
            } else {
                environment existing
                copyFrom([environment: 42])
            }
        }

        then:
        def error = thrown(KlumModelException)
        error.message.contains(message)
        existing.name == 'existing'

        where:
        kind                    | message
        'Template relationship' | 'Template'
        'fresh Builder'         | 'Fresh Builder inputs are not supported for LINK'
        'wrong donor type'      | 'Field environment is not of expected type'
    }

    def 'populated completed OPTIONAL_LINK retains its existing sealed merge boundary'() {
        given:
        schema()
        def nodeType = getClass('Environment')
        def existing = nodeType.Create.With { name 'existing' }
        def incoming = nodeType.Create.With { name 'incoming' }

        when:
        getClass('Application').Create.With {
            optional existing
            copyFrom([optional: incoming])
        }

        then:
        def error = thrown(KlumModelException)
        error.message.contains('A sealed Builder cannot be configured')
        existing.name == 'existing'
        incoming.name == 'incoming'
    }

    private static Object donorFor(Class applicationType, String kind, Object value) {
        if (kind == 'Map') return [environment: value]
        if (kind == 'Template') return applicationType.Create.Template.With { environment value }
        applicationType.Create.With { environment value }
    }

    private void schema(String strategy = '') {
        createNonDslClass """
            package linkcopy
            import com.blackbuild.klum.ast.copy.Overwrite
            import com.blackbuild.klum.ast.copy.OverwriteStrategy

            class Events {
                static List<String> entries = []
            }
            @DSL class Environment {
                String name
                String retained
                int recipeRuns
                @PostCreate void created() { Events.entries << 'create' }
                @PostApply void applied() { Events.entries << 'apply' }
                @PostTree void finished() { Events.entries << 'tree' }
                @Validate void validated() { Events.entries << 'validate' }
            }
            @DSL class Application {
                @Field(FieldType.LINK)
                $strategy
                Environment environment
                Environment owned
                String label
                int recipeRuns
                @Field(FieldType.OPTIONAL_LINK) Environment optional
                @Field(FieldType.LINK) List<Environment> environments
                @Field(value = FieldType.LINK, keyMapping = { it.name })
                @Overwrite.Map(OverwriteStrategy.Map.MERGE_VALUES)
                Map<String, Environment> indexed
            }
        """
    }
}
