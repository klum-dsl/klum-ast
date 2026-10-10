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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('867')
class LifecycleParticipantPhaseTest extends AbstractDSLSpec {
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#field-participants-in-four-phases-lp-3')
    def 'creates then mutates direct fields before callbacks in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = $phase, handler = Supply)
            @LifecycleMutator(phase = $phase, handler = ZFirst)
            @LifecycleMutator(phase = $phase, handler = ASecond)
            @interface Configured { boolean enabled() default true }
            class Supply implements LifecycleCreationHandler<Configured> {
                KlumBuilder<?> create(LifecycleFieldContext<Configured> c) {
                    c.annotation.enabled() ? Service.Create.AsBuilder().With([value: 'created']) : null
                }
            }
            class ZFirst implements LifecycleMutationHandler<Configured> {
                void mutate(LifecycleMutationContext<Configured> c) {
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':first')
                }
            }
            class ASecond implements LifecycleMutationHandler<Configured> {
                void mutate(LifecycleMutationContext<Configured> c) {
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':second')
                }
            }
            @DSL class Service { String value }
            @DSL class Application {
                @Configured Service supplied
                @Configured Service existing
                @Configured(enabled = false) Service absent
                String observed
                @$phase void observe() {
                    assert supplied.value == 'created:first:second'
                    assert existing.value == 'configured:first:second'
                    observed = 'method'
                }
                @${phase == 'Default' ? 'Default(code = { null })' : phase} Closure callback = { observed += ':closure' }
            }
        """

        when:
        def application = Application.Create.With { existing { value 'configured' } }

        then:
        application.supplied.value == 'created:first:second'
        application.existing.value == 'configured:first:second'
        application.absent == null
        application.observed == 'method:closure'

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }

    def 'parent field work precedes new grandchild fields and child callbacks in #phase (#existing)'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = $phase, handler = SupplyChild)
            @LifecycleMutator(phase = $phase, handler = AddGrandchild)
            @interface Managed {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = $phase, handler = ObserveParent)
            @interface Observed {}
            class SupplyChild implements LifecycleCreationHandler<Managed> {
                KlumBuilder<?> create(LifecycleFieldContext<Managed> c) { Child.Create.AsBuilder().One() }
            }
            class AddGrandchild implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    Child.Create.narrowBuilder(c.targetBuilder).grandchild { value 'parent' }
                }
            }
            class ObserveParent implements LifecycleMutationHandler<Observed> {
                void mutate(LifecycleMutationContext<Observed> c) {
                    def target = Grandchild.Create.narrowBuilder(c.targetBuilder)
                    assert target.value == 'parent'
                    target.value(target.value + ':field')
                }
            }
            @DSL class Application { @Managed Child child }
            @DSL class Child {
                @Observed Grandchild grandchild
                @$phase void observe() { grandchild.value += ':method' }
                @${phase == 'Default' ? 'Default(code = { null })' : phase}
                Closure callback = { grandchild.value += ':closure' }
            }
            @DSL class Grandchild {
                String value
                String early = 'pending'
                @AutoCreate void earlyPhase() { early = 'ran' }
            }
        """

        when:
        def application = Application.Create.With {
            if (existing) {
                child {}
            }
        }

        then:
        application.child.grandchild.value == 'parent:field:method:closure'
        application.child.grandchild.early == (phase == 'AutoCreate' ? 'ran' : 'pending')

        where:
        [phase, existing] << [['AutoCreate', 'AutoLink', 'Default', 'PostTree'], [true, false]].combinations()
    }

    def 'cluster AutoCreate sees direct field results and fills nulls without rerunning participants'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = AutoCreate, handler = Supply)
            @LifecycleMutator(phase = AutoCreate, handler = Configure)
            @interface Managed { boolean enabled() default true }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = AutoCreate, handler = ConfigureBuiltIn)
            @interface Tune {}
            class Supply implements LifecycleCreationHandler<Managed> {
                KlumBuilder<?> create(LifecycleFieldContext<Managed> c) {
                    c.annotation.enabled() ? Service.Create.AsBuilder().With([value: 'created']) : null
                }
            }
            class Configure implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    assert c.annotation.enabled() : 'null-return field must not be revisited after clusters'
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':mutated')
                }
            }
            class ConfigureBuiltIn implements LifecycleMutationHandler<Tune> {
                void mutate(LifecycleMutationContext<Tune> c) {
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':mutated')
                }
            }
            @DSL class Service { String value }
            @DSL abstract class ServiceGroup {
                @Cluster @AutoCreate({ [value: 'cluster'] }) Map<String, Service> services
            }
            @DSL class Application extends ServiceGroup {
                @Managed Service supplied
                @Managed Service existing
                @Managed(enabled = false) Service remaining
                @AutoCreate({ [value: 'builtin'] }) @Tune Service builtin
                String observed
                @AutoCreate void observe() {
                    assert supplied.value == 'created:mutated'
                    assert existing.value == 'existing:mutated'
                    assert remaining.value == 'cluster'
                    assert builtin.value == 'builtin:mutated'
                    observed = 'method'
                }
                @AutoCreate Closure callback = { observed += ':closure' }
            }
        '''

        when:
        def application = Application.Create.With { existing { value 'existing' } }

        then:
        application.supplied.value == 'created:mutated'
        application.existing.value == 'existing:mutated'
        application.remaining.value == 'cluster'
        application.builtin.value == 'builtin:mutated'
        application.services.size() == 4
        application.observed == 'method:closure'
    }

    def 'one annotation composes independent creators and mutations across all four phases'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = AutoCreate, handler = Decline)
            @LifecycleCreator(phase = AutoLink, handler = Supply)
            @LifecycleCreator(phase = Default, handler = MustSkip)
            @LifecycleCreator(phase = PostTree, handler = MustSkip)
            @LifecycleMutator(phase = AutoCreate, handler = MustSkip)
            @LifecycleMutator(phase = AutoLink, handler = Linked)
            @LifecycleMutator(phase = Default, handler = Defaulted)
            @LifecycleMutator(phase = PostTree, handler = Finalized)
            @interface Managed {}
            class Decline implements LifecycleCreationHandler<Managed> {
                KlumBuilder<?> create(LifecycleFieldContext<Managed> c) { null }
            }
            class Supply implements LifecycleCreationHandler<Managed> {
                KlumBuilder<?> create(LifecycleFieldContext<Managed> c) { Service.Create.AsBuilder().With([value: 'created']) }
            }
            class MustSkip implements LifecycleCreationHandler<Managed>, LifecycleMutationHandler<Managed> {
                KlumBuilder<?> create(LifecycleFieldContext<Managed> c) { throw new AssertionError('existing target') }
                void mutate(LifecycleMutationContext<Managed> c) { throw new AssertionError('null target') }
            }
            class Linked implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    def t = Service.Create.narrowBuilder(c.targetBuilder)
                    t.value(t.value + ':linked')
                }
            }
            class Defaulted implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    def t = Service.Create.narrowBuilder(c.targetBuilder)
                    t.value(t.value + ':defaulted')
                }
            }
            class Finalized implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    def t = Service.Create.narrowBuilder(c.targetBuilder)
                    t.value(t.value + ':finalized')
                }
            }
            @DSL class Service { String value }
            @DSL class Application { @Managed Service service }
        '''

        when:
        def application = Application.Create.One()

        then:
        application.service.value == 'created:linked:defaulted:finalized'
    }

    def 'rejects same-phase creators including built-ins in #phase (#competing)'() {
        when:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = $phase, handler = Supply)
            @interface Managed {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = $phase, handler = OtherSupply)
            @interface Other {}
            class Supply implements LifecycleCreationHandler<Managed> {
                KlumBuilder<?> create(LifecycleFieldContext<Managed> c) { null }
            }
            class OtherSupply implements LifecycleCreationHandler<Other> {
                KlumBuilder<?> create(LifecycleFieldContext<Other> c) { null }
            }
            @DSL class Service {}
            @DSL class Application { @Managed $competing Service service }
        """

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains("Competing lifecycle creators for $phase on service")

        where:
        phase        | competing
        'AutoCreate' | '@Other'
        'AutoLink'   | '@Other'
        'Default'    | '@Other'
        'PostTree'   | '@Other'
        'AutoCreate' | '@AutoCreate'
        'AutoLink'   | '@LinkTo'
        'Default'    | '@Default(code = { null })'
    }

    def 'built-in direct Default creation precedes an external Default mutation'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.runtime.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = Default, handler = Configure)
            @interface Managed {}
            class Configure implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':mutated')
                }
            }
            @DSL class Service { String value }
            @DSL class Application {
                @Default(code = { Service.Create.AsBuilder().With([value: 'default']) })
                @Managed Service service
            }
        '''

        when:
        def application = Application.Create.One()

        then:
        application.service.value == 'default:mutated'
    }

    def 'built-in defaults retain owner then containing field then type then direct precedence'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.layer3.DefaultValues
            import java.lang.annotation.*
            interface OwnerSettings { String getOwnerValue() }
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.TYPE])
            @DefaultValues @interface Settings {
                String ownerValue() default ''
                String containingValue() default ''
                String typeValue() default ''
            }
        '''
        createSecondaryClass '''
            @DSL class Application implements OwnerSettings {
                String ownerValue
                @Settings(ownerValue = 'containing', containingValue = 'containing') Service service
            }
            @DSL @OwnerProvidedDefaults(OwnerSettings)
            @Settings(ownerValue = 'type', containingValue = 'type', typeValue = 'type')
            class Service implements OwnerSettings {
                @Owner Application owner
                @Default(code = { 'direct' }) String ownerValue
                @Default(code = { 'direct' }) String containingValue
                @Default(code = { 'direct' }) String typeValue
                @Default(code = { 'direct' }) String directValue
                String observed
                @Default void observe() { observed = [ownerValue, containingValue, typeValue, directValue].join(':') }
                @Default(code = { null }) Closure callback = { observed += ':closure' }
            }
        '''

        when:
        def application = Application.Create.With {
            ownerValue 'owner'
            service {}
        }

        then:
        application.service.ownerValue == 'owner'
        application.service.containingValue == 'containing'
        application.service.typeValue == 'type'
        application.service.directValue == 'direct'
        application.service.observed == 'owner:containing:type:direct:closure'
    }

    def 'built-in LinkTo only fills unset direct relationships'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.layer3.*
            @DSL class Service { String value }
            @DSL class Application {
                Service source
                @LinkTo(provider = { delegate }, field = 'source') Service missing
                @LinkTo(provider = { delegate }, field = 'source') Service configured
                String observed
                @AutoLink void observe() {
                    assert missing.is(source)
                    assert configured.value == 'configured'
                    observed = 'method'
                }
                @AutoLink Closure callback = { observed += ':closure' }
            }
        '''

        when:
        def application = Application.Create.With {
            source { value 'source' }
            configured { value 'configured' }
        }

        then:
        application.missing.is(application.source)
        !application.configured.is(application.source)
        application.configured.value == 'configured'
        application.observed == 'method:closure'
    }

    def 'built-in Default still supports construction-only scalar fields'() {
        given:
        createSecondaryClass '''
            @DSL class Application {
                @Field(FieldType.BUILDER) @Default(code = { 'scratch' }) String temporary
                String observed
                @Default void observe() { observed = temporary }
            }
        '''

        when:
        def application = Application.Create.One()

        then:
        application.observed == 'scratch'
    }

    def 'built-in AutoCreate retains initialized construction-only Closure callbacks'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.layer3.AutoCreate
            @DSL class Application {
                String observed
                @AutoCreate void observe() { observed = 'method' }
                @Field(FieldType.BUILDER) @AutoCreate Closure callback = { observed += ':closure' }
            }
        '''

        when:
        def application = Application.Create.One()

        then:
        application.observed == 'method:closure'
    }
}
