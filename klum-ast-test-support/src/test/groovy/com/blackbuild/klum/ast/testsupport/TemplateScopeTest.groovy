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
package com.blackbuild.klum.ast.testsupport

import com.blackbuild.klum.ast.DSL
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.See
import spock.lang.Specification
import spock.lang.Tag

import java.util.concurrent.atomic.AtomicReference

@Issue("658")
@Tag("documentary")
@See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Testing-Models-and-Schemas.md#reuse-templates-across-a-spock-feature")
class TemplateScopeTest extends Specification {

    @AutoCleanup
    TemplateScope generalTemplates = new TemplateScope()

    @AutoCleanup
    TemplateScope specializedTemplates = new TemplateScope()

    def setup() {
        specializedTemplates.with(Delivery.Create.Template.With(region: 'specialized'))
        generalTemplates.with(
                Delivery.Create.Template.With(region: 'general'),
                DeliveryOptions.Create.Template.With(enabled: false)
        )
    }

    def cleanup() {
        assert delivery().region == 'specialized'
    }

    def "keeps setup Templates active for the feature and owned Builder creation"() {
        given:
        specializedTemplates.with(DeliveryOptions.Create.Template.With(enabled: true))

        when:
        def delivery = delivery()

        then:
        delivery.region == 'specialized'
        delivery.options.enabled
    }

    def "does not propagate active Templates to a worker thread"() {
        given:
        AtomicReference<Delivery> workerDelivery = new AtomicReference<>()
        AtomicReference<Throwable> workerFailure = new AtomicReference<>()

        when:
        Thread worker = new Thread({
            try {
                workerDelivery.set(delivery())
            } catch (Throwable failure) {
                workerFailure.set(failure)
            }
        })
        worker.start()
        worker.join()

        then:
        workerFailure.get() == null
        delivery().region == 'specialized'
        workerDelivery.get().region == null
        !workerDelivery.get().options.enabled
    }

    private static Delivery delivery() {
        Delivery.Create.With {
            options {}
        }
    }
}

@DSL
class Delivery {
    String region
    DeliveryOptions options
}

@DSL
class DeliveryOptions {
    boolean enabled
}
