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
import com.blackbuild.klum.ast.runtime.KlumException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('867')
class LifecycleParticipantTest extends AbstractDSLSpec {
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#external-field-participants-lp-1')
    def 'reuses typed fact binding across Application and Domain Schemas before child field actions'() {
        given:
        createSecondaryClass """
            package participants
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.runtime.*
            import groovy.transform.CompileStatic
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface RelationshipRole { String value() }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = AutoLink, handler = BindFactsHandler)
            @interface BindFacts { String value() }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = AutoLink, handler = ObserveFactsHandler)
            @interface ObserveFacts {}

            @CompileStatic
            class BindFactsHandler implements LifecycleMutationHandler<BindFacts> {
                void mutate(LifecycleMutationContext<BindFacts> context) {
                    // Consumer-owned common Schema bases supply the typed relationship contract.
                    def application = ApplicationBase.Create.narrowBuilder(context.containingBuilder)
                    def domain = DomainBase.Create.narrowBuilder(context.targetBuilder)
                    assert context.getAnnotation(BindFacts).get() == context.annotation
                    assert !context.getAnnotation(Deprecated).present
                    def role = context.getAnnotation(RelationshipRole).get().value()
                    domain.facts {
                        source application.environment.factSource + ':' + context.annotation.value()
                        relationshipRole role
                        incomingField context.fieldName
                        declaredDomain context.declaredType.simpleName
                        concreteApplication KlumBuilderSupport.of(context.containingBuilder).modelType.simpleName
                    }
                }
            }
            @CompileStatic
            class ObserveFactsHandler implements LifecycleMutationHandler<ObserveFacts> {
                void mutate(LifecycleMutationContext<ObserveFacts> context) {
                    def facts = Facts.Create.narrowBuilder(context.targetBuilder)
                    facts.observed(facts.source)
                }
            }
            @DSL class Environment { String factSource }
            @DSL class Facts {
                String source
                String observed
                String relationshipRole
                String incomingField
                String declaredDomain
                String concreteApplication
            }
            @DSL abstract class DomainBase {
                String configured
                @ObserveFacts Facts facts
            }
            @DSL class KafkaDomain extends DomainBase {}
            @DSL class QueueDomain extends DomainBase {}
            @DSL abstract class ApplicationBase { Environment environment }
            @DSL class OrdersBase extends ApplicationBase {
                @BindFacts('messaging') @RelationshipRole('orders') KafkaDomain messaging
            }
            @DSL class Orders extends OrdersBase {}
            @DSL class Notifications extends ApplicationBase {
                @BindFacts('delivery') @RelationshipRole('notifications') QueueDomain delivery
            }
        """

        when:
        def orders = Orders.Create.With {
            environment { factSource 'production' }
            messaging { configured 'existing-kafka' }
        }
        def notifications = Notifications.Create.With {
            environment { factSource 'staging' }
            delivery { configured 'existing-queue' }
        }

        then:
        orders.messaging.configured == 'existing-kafka'
        orders.messaging.facts.source == 'production:messaging'
        orders.messaging.facts.observed == 'production:messaging'
        orders.messaging.facts.relationshipRole == 'orders'
        orders.messaging.facts.incomingField == 'messaging'
        orders.messaging.facts.declaredDomain == 'KafkaDomain'
        orders.messaging.facts.concreteApplication == 'Orders'
        notifications.delivery.configured == 'existing-queue'
        notifications.delivery.facts.source == 'staging:delivery'
        notifications.delivery.facts.observed == 'staging:delivery'
        notifications.delivery.facts.relationshipRole == 'notifications'
        notifications.delivery.facts.incomingField == 'delivery'
        notifications.delivery.facts.declaredDomain == 'QueueDomain'
        notifications.delivery.facts.concreteApplication == 'Notifications'
    }

    def 'rejects a handler whose annotation generic does not exactly match its domain annotation'() {
        when:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = AutoLink, handler = WrongHandler)
            @interface BindFacts {}
            class WrongHandler implements LifecycleMutationHandler<Deprecated> {
                void mutate(LifecycleMutationContext<Deprecated> context) {}
            }
            @DSL class Domain {}
            @DSL class Application { @BindFacts Domain domain }
        """

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains('must resolve exactly to BindFacts')
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#external-field-participants-lp-1')
    def 'creates missing relationships before mutation and leaves null results unset (#mode)'() {
        given:
        createSecondaryClass """
            package creation
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.FieldType
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.runtime.*
            import groovy.transform.CompileStatic
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = AutoLink, handler = SupplyDomain)
            @LifecycleMutator(phase = AutoLink, handler = ConfigureDomain)
            @interface Supplied { boolean enabled() default true }
            ${mode}
            abstract class TypedCreator<A extends Annotation> implements LifecycleCreationHandler<A> {}
            ${mode}
            class SupplyDomain extends TypedCreator<Supplied> {
                private boolean used
                KlumBuilder<?> create(LifecycleFieldContext<Supplied> context) {
                    assert !used
                    used = true
                    assert context.fieldType == FieldType.DEFAULT
                    return context.annotation.enabled() ? Domain.Create.AsBuilder().With([value: 'created']) : null
                }
            }
            ${mode}
            abstract class TypedMutator<A extends Annotation> implements LifecycleMutationHandler<A> {}
            ${mode}
            class ConfigureDomain extends TypedMutator<Supplied> {
                private boolean used
                void mutate(LifecycleMutationContext<Supplied> context) {
                    assert !used
                    used = true
                    def target = Domain.Create.narrowBuilder(context.targetBuilder)
                    target.value(target.value + '-mutated')
                }
            }
            @DSL class Domain { String value }
            @DSL class Application {
                @Supplied Domain first
                @Supplied Domain second
                @Supplied Domain existing
                @Supplied(enabled = false) Domain absent
            }
        """

        when:
        def application = Application.Create.With { existing { value 'configured' } }
        def another = Application.Create.One()

        then:
        application.first.value == 'created-mutated'
        application.second.value == 'created-mutated'
        application.existing.value == 'configured-mutated'
        application.absent == null
        another.first.value == 'created-mutated'
        another.absent == null

        where:
        mode << ['', '@CompileStatic']
    }

    def 'rejects raw and unresolved and wildcard annotation parameters (#signature)'() {
        when:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = AutoLink, handler = BadHandler)
            @interface Binding {}
            class BadHandler${variables} implements LifecycleMutationHandler${signature} {
                void mutate(LifecycleMutationContext context) {}
            }
            @DSL class Domain {}
            @DSL class Application { @Binding Domain domain }
        """

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains('raw, wildcard, unresolved and mismatched')

        where:
        variables                 | signature
        ''                        | ''
        '<A extends Annotation>'  | '<A>'
        ''                        | '<?>'
    }

    def 'rejects unsupported field placement and unqualified phases (#declaration)'() {
        when:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = ${phase}, handler = Handler)
            @interface Binding {}
            class Handler implements LifecycleMutationHandler<Binding> {
                void mutate(LifecycleMutationContext<Binding> context) {}
            }
            @DSL class Domain {}
            @DSL class Application { @Binding ${declaration} }
        """

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains(diagnostic)

        where:
        declaration                         | phase       | diagnostic
        'String value'                      | 'AutoLink'  | 'non-static direct DSL field'
        'List<Domain> domains'              | 'AutoLink'  | 'non-static direct DSL field'
        'Map<String, Domain> domains'       | 'AutoLink'  | 'non-static direct DSL field'
        'static Domain domain'              | 'AutoLink'  | 'non-static direct DSL field'
        '@Field(FieldType.BUILDER) Domain domain' | 'AutoLink' | 'retained on the Schema'
        'Domain domain'                      | 'PostTree'  | 'only phase = AutoLink'
    }

    def 'rejects non-public no-arg or non-concrete handlers (#body)'() {
        when:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = AutoLink, handler = Handler)
            @interface Binding {}
            ${modifier} class Handler implements LifecycleMutationHandler<Binding> {
                ${body}
                void mutate(LifecycleMutationContext<Binding> context) {}
            }
            @DSL class Domain {}
            @DSL class Application { @Binding Domain domain }
        """

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains('public concrete class and public no-arg constructor')

        where:
        modifier   | body
        ''         | 'private Handler() {}'
        ''         | 'Handler(String argument) {}'
        'abstract' | ''
    }

    def 'checked creator assignment rejects foreign-session and inappropriate FieldType results (#kind)'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = AutoLink, handler = Supplier)
            @interface Supplied {}
            class Capture { static KlumBuilder<?> builder }
            class Supplier implements LifecycleCreationHandler<Supplied> {
                KlumBuilder<?> create(LifecycleFieldContext<Supplied> context) { ${result} }
            }
            @DSL class Domain { String value }
            @DSL class Application { @Supplied ${field} }
        """
        def capture = getClass('Capture')
        if (kind == 'foreign-session') {
            try {
                Domain.Create.With {
                    capture.builder = delegate
                    throw new IllegalStateException('abort to retain an unsealed foreign Builder')
                }
            } catch (IllegalStateException expected) {
                assert expected.message == 'abort to retain an unsealed foreign Builder'
            }
        }

        when:
        Application.Create.One()

        then:
        KlumException failure = thrown()
        def causes = causeMessages(failure)
        causes.any { it.contains(diagnostic) }
        causes.any { it.contains('Participant Supplied handler Supplier during AutoLink on Application.domain') }

        where:
        kind              | field                                       | result                               | diagnostic
        'foreign-session' | 'Domain domain'                             | 'return Capture.builder'             | 'Construction session'
        'fresh LINK'      | '@Field(FieldType.LINK) Domain domain'      | 'return Domain.Create.AsBuilder().One()' | 'Fresh Builder inputs are not supported for LINK'
    }

    def 'participant invocation preserves the cause and a later lifecycle remains usable (#role, #failureType)'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @${marker}(phase = AutoLink, handler = Handler)
            @interface Binding {}
            class Handler implements ${handlerRole}<Binding> {
                ${resultType} ${operation}(${contextType}<Binding> context) {
                    throw new ${failureType}('domain failure')
                }
            }
            @DSL class Domain {}
            @DSL class Application { @Binding Domain domain }
        """

        when:
        if (role == 'creator') {
            Application.Create.One()
        } else {
            Application.Create.With { domain {} }
        }

        then:
        KlumException failure = thrown()
        causeMessages(failure).any { it.contains('Participant Binding handler Handler during AutoLink on Application.domain') }
        failure.cause.cause.class.simpleName == failureType
        failure.cause.cause.message == 'domain failure'

        when:
        def domain = Domain.Create.One()

        then:
        domain != null

        where:
        role      | failureType
        'creator' | 'IllegalStateException'
        'creator' | 'AssertionError'
        'creator' | 'NoClassDefFoundError'
        'mutator' | 'IllegalStateException'
        'mutator' | 'AssertionError'
        'mutator' | 'NoClassDefFoundError'
        marker = role == 'creator' ? 'LifecycleCreator' : 'LifecycleMutator'
        handlerRole = role == 'creator' ? 'LifecycleCreationHandler' : 'LifecycleMutationHandler'
        resultType = role == 'creator' ? 'KlumBuilder<?>' : 'void'
        operation = role == 'creator' ? 'create' : 'mutate'
        contextType = role == 'creator' ? 'LifecycleFieldContext' : 'LifecycleMutationContext'
    }

    private static List<String> causeMessages(Throwable failure) {
        List<String> messages = []
        for (Throwable cause = failure; cause != null; cause = cause.cause) {
            messages.add(cause.message ?: '')
        }
        messages
    }

    def 'rejects participants placed on a Schema type or method (#placement)'() {
        when:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.TYPE, ElementType.FIELD, ElementType.METHOD])
            @LifecycleMutator(phase = AutoLink, handler = Handler)
            @interface Binding {}
            class Handler implements LifecycleMutationHandler<Binding> {
                void mutate(LifecycleMutationContext<Binding> context) {}
            }
            ${placement == 'type' ? '@Binding' : ''}
            @DSL class Application {
                ${placement == 'method' ? '@Binding void configure() {}' : ''}
            }
        """

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains('LP-1 lifecycle participants require direct Schema field placement')

        where:
        placement << ['type', 'method']
    }
}
