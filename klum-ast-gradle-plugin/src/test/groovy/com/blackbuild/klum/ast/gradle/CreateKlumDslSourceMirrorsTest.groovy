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
import spock.lang.Specification
import spock.lang.TempDir

class CreateKlumDslSourceMirrorsTest extends Specification {

    @TempDir
    File projectDirectory

    Project project
    CreateKlumDslSourceMirrors task

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(projectDirectory).build()
        task = project.tasks.create('createMirrors', CreateKlumDslSourceMirrors)
    }

    def "creates a mirror and removes stale output"() {
        given:
        File outputDirectory = new File(projectDirectory, 'mirrors')
        File stale = new File(outputDirectory, 'Stale_DSL.java')
        stale.parentFile.mkdirs()
        stale.text = 'stale'
        task.classes(classFile(Direct_DSL))
        task.outputDirectory.set(outputDirectory)

        when:
        task.createMirrors()

        then:
        !stale.exists()
        File mirror = new File(outputDirectory, 'com/blackbuild/klum/ast/gradle/Direct_DSL.java')
        mirror.text.contains('interface Direct_DSL')
        mirror.text.contains('interface Builder')
    }

    private static File classFile(Class<?> type) {
        File classesDirectory = new File(type.protectionDomain.codeSource.location.toURI())
        new File(classesDirectory, type.name.replace('.', '/') + '.class')
    }
}

interface Direct_DSL {
    interface Builder {
    }
}
