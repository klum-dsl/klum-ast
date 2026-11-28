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

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = 'Checks exact Git objects on each qualification run')
abstract class VerifyProseDocumentationTask extends DefaultTask {
    @InputDirectory abstract DirectoryProperty getObjectDirectory()
    @InputFile abstract RegularFileProperty getClassificationFile()

    @TaskAction
    void verify() {
        def result = new JsonSlurper().parse(classificationFile.get().asFile)
        if (result.lane != 'docs' || !result.mergeBase || !result.identity?.head || !result.identity?.merge)
            throw new IllegalArgumentException('Positive, identity-bound prose classification required')
        List<String> files = result.records.collect {
            if (it.paths.size() != 1) throw new IllegalArgumentException('Expected one admitted path')
            it.paths[0] as String
        }
        def checker = new ProseDocumentationChecker(objectDirectory.get().asFile.absolutePath, result.identity.head as String)
        checker.git('diff', '--check', result.mergeBase as String, result.identity.head as String, '--')
        [result.identity.head, result.identity.merge].unique().each { revision ->
            ProseDocumentationChecker.verify(objectDirectory.get().asFile.absolutePath, revision as String, files)
        }
    }
}
