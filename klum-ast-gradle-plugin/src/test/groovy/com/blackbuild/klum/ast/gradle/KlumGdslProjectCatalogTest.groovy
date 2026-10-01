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

@Issue('805')
class KlumGdslProjectCatalogTest extends Specification {
    def "distinct archive registrations reject #kind for the same Schema identity"() {
        given:
        def first = archive('one.jar', 'org.example:schema:1.0', '.environment.groovy')
        def second = archive('two.jar', 'org.example:schema:1.0', suffix)

        when:
        KlumGdslProjectValidation.validate([first, second], ['org.example:schema:1.0'])

        then:
        GradleException failure = thrown()
        failure.message.contains(reason)
        failure.message.contains('org.example:schema:1.0')
        failure.message.contains('one.jar and two.jar')
        failure.message.contains('previous IDE output is stale')

        where:
        kind        | suffix                 | reason
        'duplicates'| '.environment.groovy'  | 'Duplicate GDSL payload identity'
        'new bytes' | '.new.groovy'          | 'Different GDSL payloads for one Schema GAV'
    }

    def "suffix overlap has no target priority and reports both mapping identities and a witness"() {
        given:
        def first = archive('one.jar', 'org.one:schema:1.0', '.environment.groovy')
        def second = archive('two.jar', 'org.two:schema:2.0', suffix)

        when:
        KlumGdslProjectValidation.validate([first, second], ['org.one:schema:1.0', 'org.two:schema:2.0'])

        then:
        GradleException failure = thrown()
        failure.message.contains('Overlapping project-wide GDSL mappings')
        failure.message.contains('org.one:schema:1.0 / environment (.environment.groovy)')
        failure.message.contains('org.two:schema:2.0 / environment (' + suffix + ')')
        failure.message.contains('witness filename: recipe.environment.groovy')

        where:
        suffix << ['.groovy', '.environment.groovy']
    }

    def "case-sensitive disjoint catalogs allow equal IDs and payload basenames"() {
        given:
        def archives = [archive('one.jar', 'org.one:schema:1.0', '.environment.groovy'),
                        archive('two.jar', 'org.two:schema:2.0', '.Environment.groovy')]

        when:
        KlumGdslProjectValidation.validate(archives, archives*.coordinates())

        then:
        notThrown(GradleException)
    }

    private static KlumGdslArchive archive(String path, String origin, String suffix) {
        new KlumGdslArchive(new File(path), origin, [new KlumGdslMetadataFormat.Mapping('environment', suffix, 'example.Environment')])
    }
}
