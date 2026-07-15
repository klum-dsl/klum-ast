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
package com.blackbuild.klum.ast.util

import spock.lang.Specification

class TemplateRecipeStateTest extends Specification {

    def "recipe replay uses the common scheduling boundary"() {
        given:
        def constructor = TemplateRecipeState.getDeclaredConstructor(Map)
        constructor.accessible = true
        Map<Integer, List<Closure>> actions = [
                (40): [{ throw new AssertionError("recipe action must not execute") }]
        ]
        TemplateRecipeState state = constructor.newInstance([actions] as Object[])
        def recipient = new TestRuntimeBuilder<TestObject>(TestObject)

        when:
        state.replayInto(recipient)

        then:
        KlumModelException error = thrown()
        error.message == "Cannot schedule applyLater for phase 'instantiate' (40): deferred Builder actions must run " +
                "before materialization at phase 40. Use a phase below 40, or a ModelVisitingPhaseAction for " +
                "completed-model work. at "
    }
}
