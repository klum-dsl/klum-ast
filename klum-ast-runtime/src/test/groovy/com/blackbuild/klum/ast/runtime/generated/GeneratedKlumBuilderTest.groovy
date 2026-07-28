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

import com.blackbuild.klum.ast.runtime.DefaultKlumPhase
import com.blackbuild.klum.ast.runtime.KlumPhase
import spock.lang.Issue
import spock.lang.Specification

@Issue('391')
class GeneratedKlumBuilderTest extends Specification {

    def "generated Builder bridge delegates state and lifecycle hooks"() {
        given:
        def builder = new BridgeBuilder()
        def applied = []

        when:
        builder.assignRelationships()
        builder.setValue('configured')
        builder.copyRecipe(new BridgeModel(value: 'recipe'))
        builder.schedule { applied << 'default' }
        builder.schedule(DefaultKlumPhase.DEFAULT) { applied << 'phase' }
        builder.schedule(DefaultKlumPhase.DEFAULT.number) { applied << 'number' }
        builder.applyLater { applied << 'public-default' }
        builder.applyLater(DefaultKlumPhase.DEFAULT) { applied << 'public-phase' }
        builder.applyLater(DefaultKlumPhase.DEFAULT.number) { applied << 'public-number' }
        builder.executeApplyLaterClosures(DefaultKlumPhase.APPLY_LATER.number)
        builder.executeApplyLaterClosures(DefaultKlumPhase.DEFAULT.number)

        then:
        builder.value == 'configured'
        applied == ['default', 'public-default', 'phase', 'number', 'public-phase', 'public-number']
    }

    private static class BridgeModel {
        String value
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

        void assignRelationships() {
            $assignRelationships()
        }

        void setValue(String value) {
            $setSingleField('value', value)
        }

        void copyRecipe(BridgeModel recipe) {
            $copyFromRecipe(recipe)
        }

        void schedule(Closure<?> closure) {
            $scheduleApplyLater(closure)
        }

        void schedule(KlumPhase phase, Closure<?> closure) {
            $scheduleApplyLater(phase, closure)
        }

        void schedule(Integer phase, Closure<?> closure) {
            $scheduleApplyLater(phase, closure)
        }
    }
}
