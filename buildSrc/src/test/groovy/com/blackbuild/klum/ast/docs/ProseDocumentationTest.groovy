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
package com.blackbuild.klum.ast.docs

import groovy.json.JsonOutput
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

@Issue('1127')
class ProseDocumentationTest extends Specification {
    @TempDir File repository

    String commit(Map<String, String> files) {
        if (!new File(repository, '.git').exists()) {
            git('init', '-q')
            git('config', 'user.name', 'Prose fixture')
            git('config', 'user.email', 'prose@example.invalid')
        }
        files.each { path, text ->
            File file = new File(repository, path)
            file.parentFile.mkdirs()
            file.text = text
        }
        git('add', '.')
        git('commit', '-qm', 'fixture')
        git('rev-parse', 'HEAD').trim()
    }

    String git(String... args) {
        def process = (['git', '-C', repository.absolutePath] + args.toList()).execute()
        String output = process.text
        assert process.waitFor() == 0
        output
    }

    def 'renders CommonMark and checks reference image Unicode duplicate and source-line links while ignoring code'() {
        given:
        String revision = commit([
            'CONTEXT.md': """# Context
[heading][ref]

[ref]: docs/adr/0001-test.md#café-1
![image](asset.svg)
[source](source.txt#L1-L2)
[external](https://example.invalid/)
`[inert](missing)`
```
[inert](missing)
```
""",
            'docs/adr/0001-test.md': '# Test\n## Café\n## Café\n',
            'asset.svg': '<svg/>', 'source.txt': 'one\ntwo\n',
            'incoming.md': '# Incoming\n[Context](CONTEXT.md#context)\n'
        ])

        when:
        ProseDocumentationChecker.verify(repository.absolutePath, revision, ['CONTEXT.md'])

        then:
        notThrown(Exception)
    }

    @Unroll
    def 'rejects #reason'() {
        given:
        String revision = commit(['CONTEXT.md': text, 'target.md': '# Target\n',
                                  'incoming.md': incoming])

        when:
        ProseDocumentationChecker.verify(repository.absolutePath, revision, ['CONTEXT.md'])

        then:
        IllegalArgumentException failure = thrown()
        failure.message.contains([
            'missing H1': 'Expected one H1', 'missing file': 'Git failed', 'missing anchor': 'Missing or unsupported fragment',
            'incoming break': 'Missing or unsupported fragment', 'line range': 'Missing or unsupported fragment',
            'escape': 'Link escapes repository', 'scheme': 'Unsupported link scheme', 'bad HTTP': 'Unsupported link scheme',
            'query': 'Unsupported relative URL'
        ][reason])

        where:
        reason           | text                                       | incoming
        'missing H1'     | 'No heading'                               | '# Incoming'
        'missing file'   | '# Context\n[bad](missing.md)'              | '# Incoming'
        'missing anchor' | '# Context\n[bad](target.md#absent)'        | '# Incoming'
        'incoming break' | '# Context'                                | '# Incoming\n[bad](CONTEXT.md#removed)'
        'line range'     | '# Context\n[bad](target.md#L1-L9)'        | '# Incoming'
        'escape'         | '# Context\n[bad](../outside)'             | '# Incoming'
        'scheme'         | '# Context\n[bad](javascript:alert)'      | '# Incoming'
        'bad HTTP'       | '# Context\n[bad](https:missing-host)'     | '# Incoming'
        'query'          | '# Context\n[bad](target.md?query)'        | '# Incoming'
    }

    def 'source fragments preserve GitHub spacing while rendered site IDs retain their existing normalization'() {
        given:
        String revision = commit(['CONTEXT.md': '# Context\n[decision](target.md#selected-yaml-interface--1114)\n',
                                  'target.md': '# Target\n## Selected YAML interface — #1114\n'])
        def checker = new ProseDocumentationChecker(repository.absolutePath, revision)
        def document = checker.document('target.md')

        when:
        ProseDocumentationChecker.verify(repository.absolutePath, revision, ['CONTEXT.md'])

        then:
        notThrown(Exception)
        StaticDocumentationPageRenderer.assignHeadingIds(document).values().toSet().contains('selected-yaml-interface-1114')
        StaticDocumentationPageRenderer.assignHeadingIds(document, true).values().toSet().contains('selected-yaml-interface--1114')

        when:
        checker.verifyDestination('CONTEXT.md', 'target.md#selected-yaml-interface-1114')

        then:
        IllegalArgumentException failure = thrown()
        failure.message.contains('Missing or unsupported fragment')
    }

    def 'task checks integration-tree incoming links after a valid head'() {
        given:
        String head = commit(['CONTEXT.md': '# Context\n', 'incoming.md': '# Incoming\n'])
        String merge = commit(['incoming.md': '# Incoming\n[broken](CONTEXT.md#removed)\n'])
        File manifest = new File(repository, 'classification.json')
        manifest.text = JsonOutput.toJson([lane: 'docs', mergeBase: head,
            identity: [head: head, merge: merge], records: [[paths: ['CONTEXT.md']]]])
        def project = ProjectBuilder.builder().withProjectDir(repository).build()
        def task = project.tasks.create('verifyProse', VerifyProseDocumentationTask)
        task.objectDirectory.set(repository)
        task.classificationFile.set(manifest)

        when:
        task.verify()

        then:
        IllegalArgumentException failure = thrown()
        failure.message.contains('Missing or unsupported fragment')
    }

    def 'task does not snapshot unrelated working tree or generated product outputs'() {
        given:
        String head = commit(['CONTEXT.md': '# Context\n'])
        File manifest = new File(repository, 'classification.json')
        manifest.text = JsonOutput.toJson([lane: 'docs', mergeBase: head,
            identity: [head: head, merge: head], records: [[paths: ['CONTEXT.md']]]])
        File productOutput = new File(repository, 'build/product-output.txt')
        productOutput.parentFile.mkdirs()
        productOutput.text = 'not a prose input'
        def project = ProjectBuilder.builder().withProjectDir(repository).build()
        def task = project.tasks.create('verifyProse', VerifyProseDocumentationTask)
        task.objectDirectory.set(repository)
        task.classificationFile.set(manifest)

        when:
        Set<File> inputs = task.inputs.files.files

        then:
        inputs == [manifest] as Set
    }

    def 'validates exact Git trees rather than working-copy repairs'() {
        given:
        String revision = commit(['CONTEXT.md': '# Context\n[broken](missing.md)'])
        new File(repository, 'CONTEXT.md').text = '# Repaired'

        when:
        ProseDocumentationChecker.verify(repository.absolutePath, revision, ['CONTEXT.md'])

        then:
        thrown(IllegalArgumentException)
    }
}
