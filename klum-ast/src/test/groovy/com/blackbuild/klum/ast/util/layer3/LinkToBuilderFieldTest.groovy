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
package com.blackbuild.klum.ast.runtime.internal.layer3

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import groovy.lang.MissingPropertyException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag
import spock.lang.Unroll

@Issue("841")
class LinkToBuilderFieldTest extends AbstractDSLSpec {

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/release/4.0.x/docs/user/Layer3.md#automatic-creation-and-linking")
    def "explicit provider field links through the owner environment during construction"() {
        given:
        createOrderSchema()
        def messagingFacts = getClass('MessagingFacts').newInstance('orders')
        def environmentBuilder

        when:
        instance = clazz.Create.With {
            kafka()
            environmentBuilder = environment {
                messaging messagingFacts
            }
        }

        then: "explicit fields read configured storage even when a Builder accessor returns null"
        environmentBuilder.messaging == null
        instance.environment.messaging.is(messagingFacts)
        instance.kafka.facts.is(messagingFacts)
        instance.kafka.linkedTopic == 'orders'
        instance.kafka.application.is(instance)
    }

    def "plain 4.0.1 owner environment path links immutable facts without a custom accessor"() {
        given:
        createOrderSchema('application.environment', 'messaging', false)
        def messagingFacts = getClass('MessagingFacts').newInstance('orders')

        when:
        instance = clazz.Create.With {
            kafka()
            environment { messaging messagingFacts }
        }

        then:
        instance.kafka.facts.is(messagingFacts)
        instance.kafka.linkedTopic == 'orders'
        instance.kafka.application.is(instance)
    }

    def "explicit provider property without storage retains Builder getter fallback"() {
        given:
        createOrderSchema('application.environment', 'alias')
        def messagingFacts = getClass('MessagingFacts').newInstance('orders')

        when:
        instance = clazz.Create.With {
            kafka()
            environment { messaging messagingFacts }
        }

        then:
        instance.kafka.facts.is(messagingFacts)
        instance.kafka.linkedTopic == 'orders'
    }

    def "completed model provider keeps ordinary property access"() {
        given:
        createOrderSchema('application.providers.environment')
        def messagingFacts = getClass('MessagingFacts').newInstance('orders')
        def completedEnvironment = getClass('OrderEnvironment').Create.With {
            messaging messagingFacts
        }

        when:
        instance = clazz.Create.With {
            providers([environment: completedEnvironment])
            kafka()
        }

        then:
        instance.kafka.facts.is(completedEnvironment.messaging)
        instance.kafka.linkedTopic == 'orders'
    }

    def "map provider retains missing and null key absence behavior"() {
        given:
        createOrderSchema(provider)

        when:
        instance = clazz.Create.With {
            kafka()
        }

        then:
        instance.kafka.facts == null
        instance.kafka.linkedTopic == null

        where:
        provider << ['[:]', '[messaging: null]']
    }

    def "null provider and null Builder field leave the link unset"() {
        given:
        createOrderSchema()

        when:
        instance = clazz.Create.With {
            kafka()
            if (configureEnvironment) {
                environment()
            }
        }

        then:
        instance.kafka.facts == null
        instance.kafka.linkedTopic == null

        where:
        configureEnvironment << [false, true]
    }

    def "missing explicit Builder property retains the missing property diagnostic"() {
        given:
        createOrderSchema('application.environment', 'missingMessaging')

        when:
        clazz.Create.With {
            kafka()
            environment()
        }

        then:
        KlumVisitorException error = thrown()
        error.cause instanceof MissingPropertyException
        error.cause.property == 'missingMessaging'
    }

    def "auto link preserves an explicitly configured target"() {
        given:
        createOrderSchema()
        def configured = getClass('MessagingFacts').newInstance('configured')
        def fallback = getClass('MessagingFacts').newInstance('fallback')

        when:
        instance = clazz.Create.With {
            kafka { facts configured }
            environment { messaging fallback }
        }

        then:
        instance.kafka.facts.is(configured)
        instance.kafka.linkedTopic == 'configured'
    }

    def "Builder relationship target preserves ownership and materialization identity"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.Owner
            import com.blackbuild.klum.ast.layer3.LinkTo
            import com.blackbuild.klum.ast.PostTree

            @DSL class OrderApplication {
                OrderEnvironment environment
                OrderKafka kafka
            }
            @DSL class OrderEnvironment {
                MessagingFacts messaging
            }
            @DSL class MessagingFacts {
                @Owner OrderEnvironment environment
                String topic
            }
            @DSL class OrderKafka {
                @Owner OrderApplication application
                @Field(FieldType.LINK)
                @LinkTo(provider = { application.environment }, field = 'messaging')
                MessagingFacts facts
                String linkedTopic

                @PostTree void captureLink() { linkedTopic = facts?.topic }
            }
        '''

        when:
        instance = clazz.Create.With {
            kafka()
            environment { messaging { topic 'orders' } }
        }

        then:
        instance.kafka.facts.is(instance.environment.messaging)
        instance.kafka.facts.environment.is(instance.environment)
        instance.kafka.application.is(instance)
        instance.kafka.linkedTopic == 'orders'
        getClass('MessagingFacts').isInstance(instance.kafka.facts)
    }

    def "sealed LINK provider exposes completed facts to declarative and imperative auto link"() {
        given:
        createSealedOrderSchema()
        def facts = getClass('MessagingFacts').newInstance('orders')
        def completedEnvironment = getClass('OrderEnvironment').Create.With { messaging facts }
        def wrapper

        when:
        instance = clazz.Create.With {
            environment(completedEnvironment)
            wrapper = delegate.environment
            kafka()
            imperativeKafka()
        }

        then:
        wrapper.isSealed()
        wrapper.completedModel.is(completedEnvironment)
        instance.environment.is(completedEnvironment)
        instance[selectedKafka].facts.is(facts)
        instance[selectedKafka].linkedTopic == 'orders'
        instance[selectedKafka].application.is(instance)

        where:
        selectedKafka << ['kafka', 'imperativeKafka']
    }

    def "sealed LINK provider retains null completed properties"() {
        given:
        createSealedOrderSchema()
        def completedEnvironment = getClass('OrderEnvironment').Create.With { }

        when:
        instance = clazz.Create.With {
            environment(completedEnvironment)
            kafka()
            imperativeKafka()
        }

        then:
        instance.kafka.facts == null
        instance.imperativeKafka.facts == null
        instance.environment.is(completedEnvironment)
    }

    def "sealed LINK provider retains missing property diagnostics"() {
        given:
        createSealedOrderSchema('missingMessaging')
        def completedEnvironment = getClass('OrderEnvironment').Create.With { }

        when:
        clazz.Create.With {
            environment(completedEnvironment)
            kafka()
        }

        then:
        KlumVisitorException error = thrown()
        error.cause instanceof MissingPropertyException
        error.cause.property == 'missingMessaging'

    }

    def "imperative auto link rejects an unknown property on a sealed LINK provider"() {
        given:
        createSealedOrderSchema('messaging', 'facts = PropertyReader.missingMessaging(application.environment)')
        def completedEnvironment = getClass('OrderEnvironment').Create.With { }
        def wrapper

        when:
        clazz.Create.With {
            environment(completedEnvironment)
            wrapper = delegate.environment
            imperativeKafka()
        }

        then:
        KlumVisitorException error = thrown()
        error.cause instanceof MissingPropertyException
        error.cause.property == 'missingMessaging'
        wrapper.isSealed()
        wrapper.getMetaClass().getMetaProperty('missingMessaging') == null
        completedEnvironment.metaClass.getMetaProperty('missingMessaging') == null
    }

    def "sealed LINK provider exposes a getter-only completed Model property"() {
        given:
        createSealedOrderSchema('messaging', 'linkedTopic = PropertyReader.alias(application.environment)')
        def facts = topic == null ? null : getClass('MessagingFacts').newInstance(topic)
        def completedEnvironment = getClass('OrderEnvironment').Create.With { messaging facts }
        def wrapper

        when:
        instance = clazz.Create.With {
            environment(completedEnvironment)
            wrapper = delegate.environment
            kafka()
            imperativeKafka()
        }

        then:
        completedEnvironment.alias == topic
        !wrapper.class.methods.any { it.name == 'getAlias' }
        wrapper.alias == topic
        wrapper.getInstanceAttributeOrGetter('alias') == topic
        instance.imperativeKafka.linkedTopic == topic
        instance.kafka.aliasTopic == topic

        where:
        topic << ['orders', null]
    }

    def "sealed LINK property forwarding preserves Builder infrastructure and super reads"() {
        given:
        createSealedOrderSchema()
        def facts = getClass('MessagingFacts').newInstance('orders')
        def completedEnvironment = getClass('OrderEnvironment').Create.With { messaging facts }
        def wrapper

        when:
        instance = clazz.Create.With {
            environment(completedEnvironment)
            wrapper = delegate.environment
            imperativeKafka()
        }

        then:
        completedEnvironment.modelType == 'domain-model-type'
        wrapper.modelType.is(getClass('OrderEnvironment'))
        wrapper.completedModel.is(completedEnvironment)
        wrapper.sealed
        wrapper.builderOnly == 'builder-only'
        wrapper.getInstanceAttributeOrGetter('modelType').is(getClass('OrderEnvironment'))
        wrapper.getInstanceProperty('builderOnly') == 'builder-only'
        wrapper.getInstanceAttributeOrGetter('completedModel').is(completedEnvironment)
        !wrapper.class.is(completedEnvironment.class)
        wrapper.getInstanceAttribute('messaging') == null
        wrapper.getMetaClass().getProperty(wrapper, 'messaging').is(facts)
        wrapper.getMetaClass().getProperty(wrapper, 'alias') == 'orders'
        wrapper.getMetaClass().getProperty(wrapper, 'builderOnly') == 'builder-only'
        wrapper.getMetaClass().getProperty(wrapper.class, wrapper, 'alias', false, false) == 'orders'
        wrapper.getMetaClass().getProperty(wrapper.class, wrapper, 'modelType', false, false).is(getClass('OrderEnvironment'))
        wrapper.getMetaClass().getProperty(wrapper.class, wrapper, 'messaging', true, false) == null
    }

    def "Model-only and unknown properties do not expand the generated Builder contract"() {
        when:
        createSealedOrderSchema('messaging', "linkedTopic = application.environment.$property")

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains("No such property: $property")
        error.message.contains('OrderEnvironment_DSL$Builder')

        where:
        property << ['alias', 'missingMessaging']
    }

    def "completed LINK reads depend on wrapper state across configuration Builder methods and PostTree"() {
        given:
        createSealedOrderSchema('messaging', 'linkedTopic = PropertyReader.alias(application.environment)')
        def facts = topic == null ? null : getClass('MessagingFacts').newInstance(topic)
        def completedEnvironment = getClass('OrderEnvironment').Create.With { messaging facts }
        def wrapper
        def configurationFacts
        def configurationAlias
        def customFacts
        def customAlias

        when:
        instance = clazz.Create.With {
            environment(completedEnvironment)
            wrapper = delegate.environment
            configurationFacts = delegate.environment.messaging
            configurationAlias = delegate.environment.alias
            customFacts = delegate.readEnvironmentFacts()
            customAlias = delegate.readEnvironmentAlias()
            kafka()
            imperativeKafka()
        }

        then:
        configurationFacts.is(facts)
        configurationAlias == topic
        customFacts.is(facts)
        customAlias == topic
        instance.kafka.facts.is(facts)
        instance.kafka.aliasTopic == topic
        instance.kafka.postTreeFacts.is(facts)
        instance.kafka.postTreeAlias == topic
        instance.imperativeKafka.linkedTopic == topic
        instance.environment.is(completedEnvironment)
        instance.kafka.application.is(instance)
        instance.imperativeKafka.application.is(instance)
        completedEnvironment.application == null
        completedEnvironment.postTreeRuns == 1
        wrapper.completedModel.is(completedEnvironment)
        wrapper.postTreeRuns == 1
        wrapper.getInstanceAttribute('messaging') == null

        where:
        topic << ['orders', null]
    }

    @Unroll("#featureName [#path]")
    def "completed LINK property forwarding preserves supported sealed mutation rejection"() {
        given:
        createSealedOrderSchema()
        def facts = getClass('MessagingFacts').newInstance('orders')
        def replacement = getClass('MessagingFacts').newInstance('replacement')
        def completedEnvironment = getClass('OrderEnvironment').Create.With { messaging facts }
        def wrapper

        when:
        clazz.Create.With {
            environment(completedEnvironment)
            wrapper = delegate.environment
            mutate(wrapper, replacement)
        }

        then:
        KlumModelException error = thrown()
        error.message.contains('sealed Builder cannot be configured')
        completedEnvironment.messaging.is(facts)
        wrapper.completedModel.is(completedEnvironment)
        wrapper.messaging.is(facts)
        wrapper.alias == 'orders'
        wrapper.getInstanceAttribute('messaging') == null

        where:
        path                   | mutate
        'property setter'      | { builder, candidate -> builder.messaging = candidate }
        'explicit setter'      | { builder, candidate -> builder.setMessaging(candidate) }
        'DSL configurator'     | { builder, candidate -> builder.messaging(candidate) }
        'configuration map'    | { builder, candidate -> builder.apply([messaging: candidate]) }
        'configuration closure'| { builder, candidate -> builder.apply { messaging candidate } }
        'attribute helper'     | { builder, candidate -> builder.setInstanceAttribute('messaging', candidate) }
        'field helper'         | { builder, candidate -> builder.setSingleField('messaging', candidate) }
    }

    private void createSealedOrderSchema(String fieldName = 'messaging',
                                         String autoLinkBody = 'facts = application.environment.messaging') {
        createClass """
            package pk

            import groovy.transform.Immutable
            import com.blackbuild.klum.ast.Owner
            import com.blackbuild.klum.ast.PostTree
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.Mutator
            import com.blackbuild.klum.ast.layer3.LinkTo

            @DSL class OrderApplication {
                @Field(FieldType.LINK) OrderEnvironment environment
                OrderKafka kafka
                ImperativeOrderKafka imperativeKafka
                @Mutator MessagingFacts readEnvironmentFacts() { environment.messaging }
                @Mutator String readEnvironmentAlias() { PropertyReader.alias(environment) }
            }
            @DSL class OrderEnvironment {
                @Owner OrderApplication application
                MessagingFacts messaging
                int postTreeRuns
                @PostTree void countPostTree() { postTreeRuns++ }
                String getAlias() { messaging?.topic }
                String getModelType() { 'domain-model-type' }
                @Mutator String getBuilderOnly() { 'builder-only' }
            }
            @DSL class OrderKafka {
                @Owner OrderApplication application
                @Field(FieldType.LINK)
                @LinkTo(provider = { application.environment }, field = '$fieldName')
                MessagingFacts facts
                @LinkTo(provider = { application.environment }, field = 'alias')
                String aliasTopic
                String linkedTopic
                MessagingFacts postTreeFacts
                String postTreeAlias
                @PostTree void captureLink() {
                    linkedTopic = facts?.topic
                    postTreeFacts = application.environment.messaging
                    postTreeAlias = PropertyReader.alias(application.environment)
                }
            }
            @DSL class ImperativeOrderKafka {
                @Owner OrderApplication application
                @Field(FieldType.LINK) MessagingFacts facts
                String linkedTopic
                @AutoLink void linkFacts() { $autoLinkBody }
                @PostTree void captureLink() { if (facts != null) linkedTopic = facts.topic }
            }
            @Immutable class MessagingFacts {
                String topic
            }
            // Deliberately dynamic: unknown and Model-only properties are absent from the
            // generated Builder contract, so the lifecycle verifier rejects direct typed access.
            class PropertyReader {
                static MessagingFacts missingMessaging(Object environment) { environment.missingMessaging }
                static String alias(Object environment) { environment.alias }
            }
        """
    }

    private void createOrderSchema(String provider = 'application.environment', String fieldName = 'messaging',
                                   boolean maskMessaging = true) {
        createClass """
            package pk

            import groovy.transform.Immutable
            import com.blackbuild.klum.ast.Mutator
            import com.blackbuild.klum.ast.Owner
            import com.blackbuild.klum.ast.layer3.LinkTo
            import com.blackbuild.klum.ast.PostTree

            @DSL class OrderApplication {
                OrderEnvironment environment
                OrderKafka kafka
                Map<String, Object> providers
            }
            @DSL class OrderEnvironment {
                MessagingFacts messaging

                // A Builder-specific property need not expose the configured field value.
                ${maskMessaging ? "@Mutator MessagingFacts getMessaging() { null }" : ""}
                @Mutator MessagingFacts getAlias() { messaging }
            }
            @DSL class OrderKafka {
                @Owner OrderApplication application
                @Field(FieldType.LINK)
                @LinkTo(provider = { $provider }, field = '$fieldName')
                MessagingFacts facts
                String linkedTopic

                @PostTree void captureLink() { linkedTopic = facts?.topic }
            }
            @Immutable class MessagingFacts {
                String topic
            }
        """
    }
}
