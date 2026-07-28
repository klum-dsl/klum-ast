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
package com.blackbuild.klum.ast.runtime.generated

import com.blackbuild.klum.ast.runtime.KlumModelException
import spock.lang.Issue
import spock.lang.Specification

@Issue('391')
class GeneratedModelSupportTest extends Specification {

    def "materializes generated model state through the opaque bridge"() {
        given:
        def builder = new BridgeBuilder(value: 'configured')

        when:
        def model = GeneratedModelSupport.$klum$instantiate(builder, BridgeModel)

        then:
        model.value == 'configured'
        model.state instanceof GeneratedObjectState
    }

    def "rejects a forgeable token and a model without the generated constructor"() {
        when: 'handwritten code supplies a same-package but non-authoritative token'
        GeneratedModelSupport.$klum$requireMaterializationToken(new GeneratedMaterializationToken())

        then:
        def tokenError = thrown(KlumModelException)
        tokenError.message.contains('internal materialization')

        when: 'the requested implementation has no synthetic generated constructor'
        GeneratedModelSupport.$klum$instantiate(new BridgeBuilder(), NoConstructorModel)

        then:
        def constructorError = thrown(KlumModelException)
        constructorError.message.contains('No internal Builder constructor')
    }

    private static class BridgeModel {
        String value
        GeneratedObjectState state

        BridgeModel(BridgeBuilder builder, GeneratedMaterializationToken token) {
            GeneratedModelSupport.$klum$requireMaterializationToken(token)
            value = GeneratedModelSupport.$klum$snapshotField(builder, 'value')
            state = GeneratedModelSupport.$klum$createState(builder, this)
        }
    }

    private static class NoConstructorModel {
    }

    private static class BridgeBuilder extends GeneratedKlumBuilder<BridgeModel> {
        String value

        BridgeBuilder() {
            super(BridgeModel)
        }

        @Override
        protected Class<? extends BridgeModel> $modelImplementationType() {
            BridgeModel
        }
    }
}
