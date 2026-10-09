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

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

@Issue('805')
class KlumGdslMetadataFormatTest extends Specification {
    def "v1 catalog is canonical sorted data and contributors resolve only real Builders"() {
        when:
        def mappings = validate([mapping('zulu', '.zulu.groovy', 'example.Zulu'),
                                 mapping('environment', '.environment.groovy', 'example.Environment')])
        def payload = KlumGdslMetadataFormat.payload(mappings.first())
        def catalog = KlumGdslMetadataFormat.catalog(mappings)

        then:
        payload == '''contributor(context(scope: scriptScope())) {
    if (place.containingFile.name.endsWith('.environment.groovy')) {
        def model = findClass('example.Environment')
        if (!model?.modifierList?.findAnnotation('com.blackbuild.klum.ast.DSL')) return
        def builder = findClass(model.qualifiedName + '_DSL.Builder')
        if (builder != null) delegatesTo(builder)
    }
}
'''
        catalog == '{"mappings":[{"id":"environment","fileNameSuffix":".environment.groovy","modelType":"example.Environment","payloadSha256":"' +
                KlumGdslMetadataFormat.sha256(payload) +
                '"},{"id":"zulu","fileNameSuffix":".zulu.groovy","modelType":"example.Zulu","payloadSha256":"' +
                KlumGdslMetadataFormat.sha256(KlumGdslMetadataFormat.payload(mappings.last())) + '"}]}\n'
        new JsonSlurper().parseText(catalog).mappings*.modelType == ['example.Environment', 'example.Zulu']
    }

    @Unroll
    def "rejects invalid declaration #id #suffix #model"() {
        when:
        validate([mapping(id, suffix, model)])

        then:
        def error = thrown(GradleException)
        error.message.contains('org.example:environment-schema:1.2.0')
        error.message.contains(reason)

        where:
        id            | suffix                   | model                             | reason
        '../escape'   | '.environment.groovy'    | 'example.Environment'             | 'mapping ID'
        'Upper'       | '.environment.groovy'    | 'example.Environment'             | 'mapping ID'
        'environment' | ''                       | 'example.Environment'             | 'fileNameSuffix'
        'environment' | '.environment.Groovy'    | 'example.Environment'             | 'fileNameSuffix'
        'environment' | '/environment.groovy'    | 'example.Environment'             | 'fileNameSuffix'
        'environment' | '\\environment.groovy'  | 'example.Environment'             | 'fileNameSuffix'
        'environment' | '\nenvironment.groovy'  | 'example.Environment'             | 'fileNameSuffix'
        'environment' | '.environment.groovy'    | ''                                | 'modelType'
        'environment' | '.environment.groovy'    | 'example..Environment'            | 'modelType'
        'environment' | '.environment.groovy'    | 'example.Environment_DSL.Builder' | 'modelType'
        'environment' | '.environment.groovy'    | 'example.class'                   | 'modelType'
        'environment' | '.environment.groovy'    | "'); throw new Exception('x')"    | 'modelType'
    }

    @Unroll
    def "rejects overlapping suffixes including normalized duplicates #left and #right"() {
        when:
        validate([mapping('left', left, 'example.Environment'), mapping('right', right, 'example.Environment')])

        then:
        def error = thrown(GradleException)
        error.message.contains('Overlapping GDSL mappings')
        error.message.contains('left')
        error.message.contains('right')
        error.message.contains('witness filename:')

        where:
        left                   | right
        '.groovy'              | '.environment.groovy'
        '.environment.groovy'  | '.groovy'
        '.environment.groovy'  | '.environment.groovy'
        '.cafe\u0301.groovy'    | '.caf\u00e9.groovy'
    }

    def "duplicate IDs fail and case-sensitive disjoint suffixes remain distinct"() {
        when:
        validate([mapping('same', '.a.groovy', 'example.A'), mapping('same', '.b.groovy', 'example.B')])

        then:
        thrown(GradleException)

        when:
        def disjoint = validate([mapping('lower', '.a.groovy', 'example.A'), mapping('upper', '.A.groovy', 'example.A')])

        then:
        disjoint.size() == 2
    }

    def "suffixes are escaped literal data rather than supplied executable code"() {
        given:
        String suffix = "\u00e9.'; throw new Exception('supplied'); \$.groovy"

        when:
        def validated = validate([mapping('literal', suffix, 'example.Environment')])
        String payload = KlumGdslMetadataFormat.payload(validated.first())
        def parsed = new JsonSlurper().parseText(KlumGdslMetadataFormat.catalog(validated))

        then:
        payload.contains("endsWith('\u00e9.\\'; throw new Exception(\\'supplied\\'); \$.groovy')")
        parsed.mappings.first().fileNameSuffix == suffix
    }

    def "empty enabled metadata has an explicit empty catalog"() {
        when:
        String catalog = KlumGdslMetadataFormat.catalog(validate([]))

        then:
        catalog == '{"mappings":[]}\n'
    }

    private static Map<String, String> mapping(String id, String suffix, String model) {
        [id: id, fileNameSuffix: suffix, modelType: model]
    }

    private static def validate(List<Map<String, String>> mappings) {
        KlumGdslMetadataFormat.validate(mappings, 'org.example:environment-schema:1.2.0')
    }
}
