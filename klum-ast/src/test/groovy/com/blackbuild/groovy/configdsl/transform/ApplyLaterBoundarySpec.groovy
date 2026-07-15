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

import com.blackbuild.klum.ast.process.KlumPhase
import com.blackbuild.klum.ast.util.KlumBuilder
import com.blackbuild.klum.ast.util.KlumModelException
import spock.lang.Unroll

class ApplyLaterBoundarySpec extends AbstractDSLSpec {

    @Unroll
    def "#method accepts the last pre-materialization integer phase"() {
        given:
        createPhaseTarget()

        when:
        instance = clazz.Create.With {
            KlumBuilder builder = delegate
            builder."$method"(39) {
                events "$method:39"
            }
        }

        then:
        instance.events == ["$method:39"]

        where:
        method << ["applyLater", "scheduleApplyLater"]
    }

    @Unroll
    def "#method rejects integer phase #phase immediately"() {
        given:
        createPhaseTarget()

        when:
        clazz.Create.With {
            KlumBuilder builder = delegate
            builder."$method"(phase) {
                events "too late"
            }
        }

        then:
        KlumModelException error = thrown()
        error.message == expectedMessage + ' at $/p.PhaseTarget.With'

        where:
        method               | phase | expectedMessage
        "applyLater"         | 40    | boundaryMessage("'instantiate' (40)")
        "scheduleApplyLater" | 40    | boundaryMessage("'instantiate' (40)")
        "applyLater"         | 41    | boundaryMessage("41")
        "scheduleApplyLater" | 41    | boundaryMessage("41")
    }

    @Unroll
    def "#method accepts a named custom phase below materialization"() {
        given:
        createPhaseTarget()
        KlumPhase phase = new NamedPhase(39, "last-builder-phase")

        when:
        instance = clazz.Create.With {
            KlumBuilder builder = delegate
            builder."$method"(phase) {
                events "$method:${phase.name}"
            }
        }

        then:
        instance.events == ["$method:last-builder-phase"]

        where:
        method << ["applyLater", "scheduleApplyLater"]
    }

    @Unroll
    def "#method names rejected custom phase #phaseNumber"() {
        given:
        createPhaseTarget()
        KlumPhase phase = new NamedPhase(phaseNumber, phaseName)

        when:
        clazz.Create.With {
            KlumBuilder builder = delegate
            builder."$method"(phase) {
                events "too late"
            }
        }

        then:
        KlumModelException error = thrown()
        error.message == boundaryMessage("'$phaseName' ($phaseNumber)") + ' at $/p.PhaseTarget.With'

        where:
        method               | phaseNumber | phaseName
        "applyLater"         | 40          | "custom-materialize"
        "scheduleApplyLater" | 40          | "custom-materialize"
        "applyLater"         | 41          | "custom-completed"
        "scheduleApplyLater" | 41          | "custom-completed"
    }

    @Unroll
    def "#method without a phase keeps its default pre-materialization behavior"() {
        given:
        createPhaseTarget()

        when:
        instance = clazz.Create.With {
            KlumBuilder builder = delegate
            builder."$method" {
                events method
            }
        }

        then:
        instance.events == [method]

        where:
        method << ["applyLater", "scheduleApplyLater"]
    }

    def "Template creation rejects a post-materialization recipe immediately"() {
        given:
        createPhaseTarget()

        when:
        clazz.Template.Create {
            applyLater(40) {
                events "too late"
            }
        }

        then:
        KlumModelException error = thrown()
        error.message == boundaryMessage("'instantiate' (40)") + ' at $'
    }

    private void createPhaseTarget() {
        createClass '''
            package pk

            @DSL
            class PhaseTarget {
                List<String> events
            }
        '''
    }

    private static String boundaryMessage(String phase) {
        "Cannot schedule applyLater for phase $phase: deferred Builder actions must run before materialization at phase 40. " +
                "Use a phase below 40, or a ModelVisitingPhaseAction for completed-model work."
    }

    private static final class NamedPhase implements KlumPhase {
        final int number
        final String name

        NamedPhase(int number, String name) {
            this.number = number
            this.name = name
        }
    }
}
