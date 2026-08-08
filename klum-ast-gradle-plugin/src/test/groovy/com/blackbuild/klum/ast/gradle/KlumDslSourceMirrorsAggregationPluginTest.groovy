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
package com.blackbuild.klum.ast.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.Specification

@Issue('559')
class KlumDslSourceMirrorsAggregationPluginTest extends Specification {

    def "registers a no-payload aggregate on the root project"() {
        given:
        Project root = ProjectBuilder.builder().build()

        when:
        root.pluginManager.apply(KlumDslSourceMirrorsAggregationPlugin)
        def aggregate = root.tasks.named(KlumDslSourceMirrorsAggregationPlugin.TASK_NAME).get()

        then:
        aggregate.group == 'klum'
        aggregate.description == 'Refreshes IDE-only Klum DSL source mirrors for all Schema projects.'
        aggregate.actions.empty
        aggregate.outputs.files.files.empty
    }

    def "rejects application to a child project"() {
        given:
        Project root = ProjectBuilder.builder().build()
        Project child = ProjectBuilder.builder().withParent(root).build()

        when:
        new KlumDslSourceMirrorsAggregationPlugin().apply(child)

        then:
        IllegalStateException exception = thrown()
        exception.message == 'The Klum DSL source-mirror aggregate belongs to the root project.'
    }
}
