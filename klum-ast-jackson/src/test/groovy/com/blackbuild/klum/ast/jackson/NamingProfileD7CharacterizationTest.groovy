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
package com.blackbuild.klum.ast.jackson

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import spock.lang.Issue

/** Current behavior and Jackson comparison only; no proposed naming implementation. */
@Issue('959')
class NamingProfileD7CharacterizationTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    def cleanup() { artifacts?.close() }

    private Class namingSchema() {
        createClass '''
            package namingd7
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Builder
            import com.fasterxml.jackson.annotation.JsonIgnore
            import com.fasterxml.jackson.annotation.JsonProperty
            import com.fasterxml.jackson.databind.annotation.JsonNaming
            import com.fasterxml.jackson.databind.PropertyNamingStrategies

            @DSL class NamingBase { String inheritedName }
            @DSL
            @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
            class NamingRoot extends NamingBase {
                /** Service prose.
                 * @example "catalog"
                 */
                String serviceName
                @JsonProperty('Keep/THIS~Name') String literalName
                @JsonIgnore String hidden
                Map<String, String> labels
                /** Configure prose.
                 * @example {"rawKey":"catalog"}
                 */
                @Builder.Method void configureHTTP(Map<String, String> value) {
                    serviceName = value.rawKey
                }
            }
        '''
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('namingd7.NamingRoot', false, artifacts)
    }

    def 'class JsonNaming governs Jackson properties while structured input retains compiled names'() {
        given:
        Class type = namingSchema()
        def mapper = new ObjectMapper().findAndRegisterModules()

        when:
        def jackson = KlumJacksonImporter.using(mapper).readRoot(type,
                KlumJacksonInput.map([service_name: 'api', inherited_name: 'base',
                                      'Keep/THIS~Name': 'literal', labels: [URL_Instance: 'same']]))
        def structured = KlumStructuredInput.readRoot(type, null,
                [configureHTTP: [rawKey: 'api'], inheritedName: 'base',
                 'Keep/THIS~Name': 'literal', labels: [URL_Instance: 'same']], 'naming.yaml')

        then:
        jackson.serviceName == 'api'
        structured.serviceName == 'api'
        jackson.inheritedName == 'base'
        structured.inheritedName == 'base'
        jackson.literalName == 'literal'
        structured.literalName == 'literal'
        jackson.labels == [URL_Instance: 'same']
        structured.labels == jackson.labels
    }

    def 'structured input rejects Jackson-only names and aliases: #external'() {
        given:
        Class type = namingSchema()

        when:
        KlumStructuredInput.readRoot(type, null, [(external): 'value'], 'naming.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(external)

        where:
        external << ['service_name', 'inherited_name', 'literalName', 'hidden', 'configure_http']
    }

    def 'AnnoDocimal retains example tags as documentation on public generated declarations'() {
        given:
        namingSchema()
        Class builder = Class.forName('namingd7.NamingRoot_DSL$Builder', false, artifacts)

        when:
        String fieldDoc = builder.getMethod('getServiceName').getAnnotation(AnnoDoc).value()
        String operationDoc = builder.getMethod('configureHTTP', Map).getAnnotation(AnnoDoc).value()

        then:
        fieldDoc.contains('@example "catalog"')
        operationDoc.contains('@example {"rawKey":"catalog"}')
    }

    def 'Jackson snake and kebab do not share acronym and leading underscore rules: #nativeName'() {
        when:
        String snakeName = PropertyNamingStrategies.SNAKE_CASE.nameForField(null, null, nativeName)
        String kebabName = PropertyNamingStrategies.KEBAB_CASE.nameForField(null, null, nativeName)

        then:
        snakeName == snake
        kebabName == kebab

        where:
        nativeName    | snake          | kebab
        'URLValue'    | 'urlvalue'     | 'url-value'
        'myURLValue'  | 'my_urlvalue'  | 'my-url-value'
        '_privateURL' | 'private_url'  | '_private-url'
    }
}
