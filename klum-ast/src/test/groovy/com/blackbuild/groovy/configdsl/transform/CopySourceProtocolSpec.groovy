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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.util.KlumBuilder
import com.blackbuild.klum.ast.util.KlumModelException
import com.blackbuild.klum.ast.util.TemplateManager

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CopySourceProtocolSpec extends AbstractDSLSpec {

    def "an ordinary completed model is a values-only copy source"() {
        given:
        createCopySchema()
        def Node = getClass("pk.CopyNode")
        def source = Node.Create.With {
            name "source"
            events "configured"
            applyLater {
                events "action:$name"
            }
        }

        when:
        instance = Node.Create.With {
            copyFrom source
            name "recipient"
        }

        then:
        source.events == ["configured", "action:source"]
        instance.events == ["configured", "action:source"]
        !instance.is(source)
    }

    def "a marked Template copies values and replays its recipe actions"() {
        given:
        createCopySchema()
        def Node = getClass("pk.CopyNode")
        def template = Node.Template.Create {
            name "template"
            events "configured"
            applyLater {
                events "action:$name"
            }
        }

        when:
        instance = Node.Create.With {
            copyFrom template
            name "recipient"
        }

        then:
        TemplateManager.instance.isTemplate(template)
        !TemplateManager.instance.isTemplate(instance)
        template.events == ["configured"]
        instance.events == ["configured", "action:recipient"]
        !instance.is(template)
    }

    def "a same-session unsealed Builder copies values and a dehydrated action snapshot without identity conversion"() {
        given:
        createCopySchema()
        def Node = getClass("pk.CopyNode")
        KlumBuilder sourceBuilder
        KlumBuilder recipientBuilder
        boolean copiedActionDehydrated
        def actionsField = KlumBuilder.getDeclaredField("applyLaterClosures")
        actionsField.accessible = true

        when:
        instance = clazz.Create.With {
            KlumBuilder rootBuilder = delegate
            sourceBuilder = Node.Create.AsBuilder.With {
                name "source"
                events "configured"
                applyLater(25) {
                    events "first:$name"
                }
                applyLater(25) {
                    events "second:$name"
                }
            }
            rootBuilder.setSingleField("source", sourceBuilder)

            recipientBuilder = Node.Create.AsBuilder.With {
                copyFrom sourceBuilder
                name "recipient"
            }
            rootBuilder.setSingleField("recipient", recipientBuilder)

            Closure copiedAction = (actionsField.get(recipientBuilder) as Map<Integer, List<Closure>>)[25][0]
            copiedActionDehydrated = copiedAction.owner == null
                    && copiedAction.delegate == null
                    && copiedAction.thisObject == null
        }

        then:
        instance.source.events == ["configured", "first:source", "second:source"]
        instance.recipient.events == ["configured", "first:recipient", "second:recipient"]
        !instance.source.is(instance.recipient)
        sourceBuilder.completedModel.is(instance.source)
        recipientBuilder.completedModel.is(instance.recipient)
        !sourceBuilder.template
        !recipientBuilder.template
        !TemplateManager.instance.isTemplate(instance.source)
        !TemplateManager.instance.isTemplate(instance.recipient)
        copiedActionDehydrated
    }

    def "a same-session Builder snapshot remains ephemeral when an action captures a non-serializable value"() {
        given:
        createCopySchema()
        def Node = getClass("pk.CopyNode")
        def captured = new NonSerializableCapture(value: "captured")

        when:
        instance = clazz.Create.With {
            KlumBuilder rootBuilder = delegate
            KlumBuilder sourceBuilder = Node.Create.AsBuilder.With {
                name "source"
                applyLater(25) {
                    events captured.value
                }
            }
            rootBuilder.setSingleField("source", sourceBuilder)

            KlumBuilder recipientBuilder = Node.Create.AsBuilder.With {
                copyFrom sourceBuilder
                name "recipient"
            }
            rootBuilder.setSingleField("recipient", recipientBuilder)
        }

        then:
        instance.source.events == ["captured"]
        instance.recipient.events == ["captured"]
    }

    def "a sealed Builder is rejected as a copy source"() {
        given:
        createCopySchema()
        def Node = getClass("pk.CopyNode")
        KlumBuilder sealedSource
        clazz.Create.With {
            KlumBuilder rootBuilder = delegate
            sealedSource = Node.Create.AsBuilder.With(name: "source")
            rootBuilder.setSingleField("source", sealedSource)
        }

        when:
        Node.Create.With {
            copyFrom sealedSource
        }

        then:
        KlumModelException error = thrown()
        error.message.contains("Cannot copy from a sealed Builder")
        error.message.contains("completed model for a values-only copy")
        error.message.contains("marked Template to replay recipe actions")
    }

    def "a Builder from another Construction session is rejected as a copy source"() {
        given:
        createCopySchema()
        def Node = getClass("pk.CopyNode")
        def sourceBuilder = new AtomicReference<KlumBuilder>()
        def sourceReady = new CountDownLatch(1)
        def releaseSource = new CountDownLatch(1)
        def sourceFailure = new AtomicReference<Throwable>()
        Thread sourceSession = Thread.start {
            try {
                clazz.Create.With {
                    KlumBuilder rootBuilder = delegate
                    KlumBuilder source = Node.Create.AsBuilder.With(name: "source")
                    rootBuilder.setSingleField("source", source)
                    sourceBuilder.set(source)
                    sourceReady.countDown()
                    releaseSource.await(10, TimeUnit.SECONDS)
                }
            } catch (Throwable failure) {
                sourceFailure.set(failure)
                sourceReady.countDown()
            }
        }
        assert sourceReady.await(10, TimeUnit.SECONDS)

        when:
        Node.Create.With {
            copyFrom sourceBuilder.get()
        }

        then:
        KlumModelException error = thrown()
        error.message.contains("outside this active Construction session")
        error.message.contains("Create.AsBuilder inside the same root Builder lifecycle")

        cleanup:
        releaseSource.countDown()
        sourceSession?.join(10_000)
        assert !sourceSession?.alive
        assert sourceFailure.get() == null
    }

    def "a live Builder snapshot contains only actions that have not executed yet"() {
        given:
        createCopySchema()
        def Node = getClass("pk.CopyNode")
        KlumBuilder sourceBuilder
        KlumBuilder recipientBuilder
        def actionsField = KlumBuilder.getDeclaredField("applyLaterClosures")
        actionsField.accessible = true

        when:
        instance = clazz.Create.With {
            KlumBuilder rootBuilder = delegate
            sourceBuilder = Node.Create.AsBuilder.With {
                name "source"
                applyLater(1) {
                    events "executed:$name"
                }
                applyLater(25) {
                    events "pending:$name"
                }
            }
            rootBuilder.setSingleField("source", sourceBuilder)

            recipientBuilder = Node.Create.AsBuilder.One()
            rootBuilder.setSingleField("recipient", recipientBuilder)
            rootBuilder.applyLater(10) {
                recipientBuilder.copyFrom(sourceBuilder)
                recipientBuilder.apply(name: "recipient")
            }
        }

        then:
        instance.source.events == ["executed:source", "pending:source"]
        instance.recipient.events == ["executed:source", "pending:recipient"]
        (actionsField.get(sourceBuilder) as Map).isEmpty()
        (actionsField.get(recipientBuilder) as Map).isEmpty()
    }

    def "a live Builder snapshot includes later unexecuted actions in the current phase"() {
        given:
        createCopySchema()
        def Node = getClass("pk.CopyNode")
        KlumBuilder sourceBuilder
        KlumBuilder recipientBuilder

        when:
        instance = clazz.Create.With {
            KlumBuilder rootBuilder = delegate
            sourceBuilder = Node.Create.AsBuilder.With(name: "source")
            rootBuilder.setSingleField("source", sourceBuilder)

            recipientBuilder = Node.Create.AsBuilder.With(name: "recipient")
            rootBuilder.setSingleField("recipient", recipientBuilder)

            sourceBuilder.applyLater(10) {
                recipientBuilder.copyFrom(sourceBuilder)
                recipientBuilder.apply(name: "recipient")
            }
            sourceBuilder.applyLater(10) {
                events "pending:$name"
            }
        }

        then:
        instance.source.events == ["pending:source"]
        instance.recipient.events == ["pending:recipient"]
    }

    def "a Map remains a value-only copy source"() {
        given:
        createCopySchema()
        def Node = getClass("pk.CopyNode")

        when:
        instance = Node.Create.With {
            copyFrom(name: "mapped", events: ["value"])
        }

        then:
        instance.name == "mapped"
        instance.events == ["value"]
        !TemplateManager.instance.isTemplate(instance)
    }

    private void createCopySchema() {
        createClass '''
            package pk

            @DSL
            class CopyRoot {
                CopyNode source
                CopyNode recipient
            }

            @DSL
            class CopyNode {
                String name
                List<String> events
            }
        '''
    }

    private static final class NonSerializableCapture {
        String value
    }

}
