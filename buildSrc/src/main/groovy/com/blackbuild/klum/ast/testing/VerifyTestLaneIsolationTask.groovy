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
package com.blackbuild.klum.ast.testing

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Checks lane configuration values without retaining the Gradle models that supplied them. */
@DisableCachingByDefault(because = 'Verification has no outputs and must check JUnit directory existence on every invocation')
abstract class VerifyTestLaneIsolationTask extends DefaultTask {
    @Nested
    abstract ListProperty<Lane> getLanes()

    @TaskAction
    void verifyIsolation() {
        lanes.get().each { lane ->
            int generation = lane.generation.get()
            def components = lane.modules.get()
            def groovyComponents = components.findAll {
                it.startsWith('org.codehaus.groovy:') || it.startsWith('org.apache.groovy:')
            }
            String expectedGroovyGroup = generation == 3 ? 'org.codehaus.groovy' : 'org.apache.groovy'
            if (groovyComponents.isEmpty() || groovyComponents.any {
                def coordinate = it.split(':')
                coordinate[0] != expectedGroovyGroup || coordinate[2] != lane.groovyVersion.get()
            })
                throw new GradleException("Groovy $generation lane contains mismatched Groovy modules: $groovyComponents")

            def spockComponents = components.findAll { it.startsWith('org.spockframework:') }
            if (spockComponents.isEmpty() || spockComponents.any { it.split(':')[2] != lane.spockVersion.get() })
                throw new GradleException("Groovy $generation lane contains mismatched Spock modules: $spockComponents")

            def forbiddenOutput = lanes.get().findAll { it.generation.get() != generation }
                    .collectMany { it.classes.get() + it.resources.get() }
            VerifyTestLaneIsolationTask.checkClasspath(generation, 'source-set compile', lane.sourceSetCompileClasspath.get(), forbiddenOutput)
            VerifyTestLaneIsolationTask.checkClasspath(generation, 'Groovy compile', lane.groovyCompileClasspath.get(), forbiddenOutput)
            VerifyTestLaneIsolationTask.checkClasspath(generation, 'test runtime', lane.testRuntimeClasspath.get(), forbiddenOutput)

            if (lane.testClasses.get() != lane.classes.get())
                throw new GradleException("Groovy $generation executes unexpected test classes: ${lane.testClasses.get()}")

            String expectedResults = lane.expectedResults.get()
            if (lane.actualResults.get() != expectedResults)
                throw new GradleException("Groovy $generation writes JUnit results outside $expectedResults")
            if (!new File(expectedResults).directory)
                throw new GradleException("Groovy $generation did not produce JUnit results in $expectedResults")
        }
    }

    private static void checkClasspath(int generation, String kind, Set<String> classpath, List<String> forbidden) {
        def leaked = classpath.intersect(forbidden)
        if (!leaked.isEmpty())
            throw new GradleException("Groovy $generation $kind classpath contains another lane's output: $leaked")
    }

    /** Only coordinate identities and path membership matter to these assertions, not class/resource bytes. */
    abstract static class Lane {
        @Input abstract Property<Integer> getGeneration()
        @Input abstract Property<String> getGroovyVersion()
        @Input abstract Property<String> getSpockVersion()
        @Input abstract SetProperty<String> getModules()
        @Input abstract SetProperty<String> getClasses()
        @Input abstract SetProperty<String> getResources()
        @Input abstract SetProperty<String> getSourceSetCompileClasspath()
        @Input abstract SetProperty<String> getGroovyCompileClasspath()
        @Input abstract SetProperty<String> getTestRuntimeClasspath()
        @Input abstract SetProperty<String> getTestClasses()
        @Input abstract Property<String> getExpectedResults()
        @Input abstract Property<String> getActualResults()

        static Set<String> moduleCoordinates(ResolvedComponentResult root) {
            Set<String> modules = []
            Set<Object> visited = []
            List<ResolvedComponentResult> pending = [root]
            while (!pending.isEmpty()) {
                def component = pending.remove(0)
                if (!visited.add(component.id)) continue
                if (component.id instanceof ModuleComponentIdentifier) {
                    def id = component.id
                    modules.add("${id.group}:${id.module}:${id.version}".toString())
                }
                component.dependencies.findAll { it instanceof ResolvedDependencyResult }.each {
                    pending.add(it.selected)
                }
            }
            modules
        }
    }
}
