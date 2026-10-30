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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.blackbuild.klum.ast.docs

import org.gradle.api.GradleException
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

@Issue('522')
class PreparePendingDocumentationStageTest extends Specification {
    @TempDir File repository
    String revision
    String masterRevision

    def setup() {
        git('init', '--initial-branch=master')
        git('config', 'user.name', 'Release fixture')
        git('config', 'user.email', 'release-fixture@example.invalid')
        new File(repository, 'source.txt').text = 'base source'
        git('add', 'source.txt')
        git('commit', '-m', 'base source')
        masterRevision = git('rev-parse', 'HEAD')
        git('update-ref', 'refs/remotes/origin/master', masterRevision)
        new File(repository, 'source.txt').text = 'maintenance correction'
        git('commit', '-am', 'maintenance correction')
        revision = git('rev-parse', 'HEAD')
        git('update-ref', 'refs/remotes/origin/release/4.1.x', revision)
    }

    @Unroll
    def 'accepts #stage #version from the selected #branch line'() {
        given:
        if (branch == 'master')
            git('update-ref', 'refs/remotes/origin/master', revision)

        when:
        PreparePendingDocumentationStageTask.validateIdentity(repository, stage, version, revision, version, branch)

        then:
        notThrown(GradleException)

        where:
        stage       | version        | branch
        'final'     | '4.1.1'        | 'release/4.1.x'
        'candidate' | '4.1.1-rc.1'   | 'release/4.1.x'
        'final'     | '4.1.1'        | 'master'
        'candidate' | '4.1.1-rc.1'   | 'master'
    }

    @Unroll
    def 'rejects disallowed source branch #branch even when master contains the revision'() {
        given:
        git('update-ref', 'refs/remotes/origin/master', revision)

        when:
        PreparePendingDocumentationStageTask.validateIdentity(repository, 'final', '4.1.1', revision, '4.1.1', branch)

        then:
        GradleException failure = thrown()
        failure.message.contains('source branch must be master or release/4.1.x')

        where:
        branch << ['release/4.0.x', 'release/4.2.x', 'main', 'feature/release', '', null]
    }

    def 'rejects a revision outside the selected maintenance line even when master contains it'() {
        given:
        git('update-ref', 'refs/remotes/origin/master', revision)
        git('update-ref', 'refs/remotes/origin/release/4.1.x', masterRevision)

        when:
        PreparePendingDocumentationStageTask.validateIdentity(repository, 'final', '4.1.1', revision, '4.1.1', 'release/4.1.x')

        then:
        GradleException failure = thrown()
        failure.message.contains('merge-base --is-ancestor ' + revision + ' origin/release/4.1.x')
    }

    def 'rejects a missing selected maintenance ref even when master contains the revision'() {
        given:
        git('update-ref', 'refs/remotes/origin/master', revision)
        git('update-ref', '-d', 'refs/remotes/origin/release/4.1.x')

        when:
        PreparePendingDocumentationStageTask.validateIdentity(repository, 'final', '4.1.1', revision, '4.1.1', 'release/4.1.x')

        then:
        GradleException failure = thrown()
        failure.message.contains('origin/release/4.1.x')
    }

    def 'rejects a revision outside master even when the maintenance line contains it'() {
        when:
        PreparePendingDocumentationStageTask.validateIdentity(repository, 'final', '4.1.1', revision, '4.1.1', 'master')

        then:
        GradleException failure = thrown()
        failure.message.contains('origin/master')
    }

    def 'retains the clean checkout requirement for maintenance releases'() {
        given:
        new File(repository, 'source.txt').text = 'uncommitted correction'

        when:
        PreparePendingDocumentationStageTask.validateIdentity(repository, 'final', '4.1.1', revision, '4.1.1', 'release/4.1.x')

        then:
        GradleException failure = thrown()
        failure.message.contains('clean checked-out source revision')
    }

    private String git(String... arguments) {
        Process process = new ProcessBuilder(['git'] + arguments.toList()).directory(repository).redirectErrorStream(true).start()
        String output = process.inputStream.text.trim()
        assert process.waitFor() == 0: output
        output
    }
}
