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
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue("853")
class LinkToModelTargetTest extends AbstractDSLSpec {

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/release/4.0.x/docs/user/Layer3.md#automatic-creation-and-linking")
    def "declarative link accepts a completed DSL Model through a sealed LINK provider"() {
        given:
        createOrderSchema()
        def completedEnvironment = getClass('OrderEnvironment').Create.With {
            messaging { topic 'orders' }
        }
        def wrapper

        when:
        instance = clazz.Create.With {
            environment(completedEnvironment)
            wrapper = delegate.environment
            kafka()
        }

        then:
        wrapper.sealed
        wrapper.completedModel.is(completedEnvironment)
        instance.environment.is(completedEnvironment)
        instance.kafka.facts.is(completedEnvironment.messaging)
        instance.kafka.facts.environment.is(completedEnvironment)
        instance.kafka.linkedTopic == 'orders'
        instance.kafka.application.is(instance)
        completedEnvironment.messaging.postTreeRuns == 1
    }

    def "declarative link accepts an active Builder without changing ownership or materialization"() {
        given:
        createOrderSchema(false)
        def messagingBuilder

        when:
        instance = clazz.Create.With {
            kafka()
            environment {
                messagingBuilder = messaging { topic 'orders' }
            }
        }

        then:
        instance.kafka.facts.is(instance.environment.messaging)
        instance.kafka.facts.environment.is(instance.environment)
        instance.kafka.facts.is(messagingBuilder.completedModel)
        instance.kafka.linkedTopic == 'orders'
        instance.kafka.application.is(instance)
        instance.environment.messaging.postTreeRuns == 1
        getClass('MessagingFacts').isInstance(instance.kafka.facts)
    }

    def "declarative link rejects an unrelated target with field and type diagnostics"() {
        given:
        createOrderSchema(true, targetField)
        def completedEnvironment = getClass('OrderEnvironment').Create.With {
            messaging { topic 'orders' }
            unrelated { description 'other' }
            label 'not messaging facts'
        }

        when:
        clazz.Create.With {
            environment(completedEnvironment)
            kafka()
        }

        then:
        KlumVisitorException error = thrown()
        error.message.contains('OrderKafka')
        error.message.contains('#facts')
        error.message.contains(targetType)
        error.message.contains('not compatible with the field type')
        error.message.contains('MessagingFacts_DSL$Builder')

        where:
        targetField | targetType
        'unrelated' | 'OtherFacts'
        'label'     | 'java.lang.String'
    }

    private void createOrderSchema(boolean completedProvider = true, String targetField = 'messaging') {
        createClass """
            package pk

            import com.blackbuild.klum.ast.Owner
            import com.blackbuild.klum.ast.PostTree
            import com.blackbuild.klum.ast.layer3.LinkTo

            @DSL class OrderApplication {
                ${completedProvider ? '@Field(FieldType.LINK)' : ''} OrderEnvironment environment
                OrderKafka kafka
            }
            @DSL class OrderEnvironment {
                MessagingFacts messaging
                OtherFacts unrelated
                String label
            }
            @DSL class MessagingFacts {
                @Owner OrderEnvironment environment
                String topic
                int postTreeRuns
                @PostTree void countPostTree() { postTreeRuns++ }
            }
            @DSL class OtherFacts {
                String description
            }
            @DSL class OrderKafka {
                @Owner OrderApplication application
                @Field(FieldType.LINK)
                @LinkTo(provider = { application.environment }, field = '${targetField}')
                MessagingFacts facts
                String linkedTopic
                @PostTree void captureLink() { linkedTopic = facts?.topic }
            }
        """
    }
}
