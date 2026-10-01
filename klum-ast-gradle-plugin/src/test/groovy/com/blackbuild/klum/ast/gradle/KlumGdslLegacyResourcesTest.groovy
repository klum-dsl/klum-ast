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

import org.gradle.api.GradleException
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

@Issue('805')
class KlumGdslLegacyResourcesTest extends Specification {
    @TempDir File directory

    def "recognizes #kind copies independently of a removed mapping and preserves the file"() {
        given:
        File source = new File(directory, 'environment.gdsl')
        source.text = kind == 'legacy' ? recipe('.old.groovy') : KlumGdslMetadataFormat.payload(new KlumGdslMetadataFormat.Mapping('environment', suffix, 'example.Environment'))
        def archive = new KlumGdslArchive(new File('empty-metadata.jar'), 'org.example:schema:2.0', [])

        when:
        KlumGdslLegacyResources.validate([archive], [source] as Set, [source] as Set)

        then:
        GradleException failure = thrown()
        failure.message.contains('Recognized legacy GDSL copy')
        failure.message.contains(source.path)
        failure.message.contains('org.example:schema:2.0 in empty-metadata.jar')
        source.file

        where:
        kind        | suffix
        'legacy'    | '.old.groovy'
        'generated' | '.old.groovy'
        'generated' | ".old'quote.groovy"
    }

    def "arbitrary custom contributors are not interpreted or rejected as legacy copies"() {
        given:
        File source = new File(directory, 'custom.gdsl')
        source.text = content
        def mapping = new KlumGdslMetadataFormat.Mapping('environment', '.environment.groovy', 'example.Environment')
        def archive = new KlumGdslArchive(new File('metadata.jar'), 'org.example:schema:1.0', [mapping])

        when:
        KlumGdslLegacyResources.validate([archive], [source] as Set, [source] as Set)

        then:
        notThrown(GradleException)
        source.text == content

        where:
        content << [recipe('.environment.groovy') + "println 'custom'\n",
                    recipe('.environment.groovy').replace('endsWith', 'contains'),
                    recipe(' .environment.groovy '),
                    "throw new IllegalStateException('do not execute custom contributor')"]
    }

    private static String recipe(String suffix) {
        """contributor(context(scope: scriptScope())) {
            if (place.containingFile.name.endsWith('$suffix'))
                delegatesTo(findClass('example.Environment_DSL.Builder'))
        }
"""
    }
}
