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
//file:noinspection GrPackage
package com.blackbuild.klum.ast.jackson

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import com.blackbuild.klum.ast.util.KlumObjectSupport
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue("464")
@Tag("documentary")
class JacksonYamlInteroperabilityDocumentaryTest extends AbstractDSLSpec {

    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Jackson-Integration.md#one-foreign-yaml-input-one-enriched-output")
    def "imports one foreign YAML document through one Builder lifecycle and exports an enriched YAML projection"() {
        given:
        createInteroperabilitySchema()
        def mapper = yamlMapper()
        def importer = KlumJacksonImporter.using(mapper)
        def input = '''
legacy_deployment: storefront
note: null
tags: []
services:
  - id: api
components:
  - kind: worker
    id: indexer
    queue: products
primary: api
'''

        when:
        def deployment = importer.readRoot(clazz, KlumJacksonInput.parser(mapper.factory.createParser(input)).named("foreign-deployment.yaml"))
        def output = mapper.readTree(mapper.writeValueAsString(deployment))

        then: "one foreign input is adapted, rather than preserved as a Klum wire format"
        deployment.name == "storefront"
        deployment.note == null
        deployment.tags.empty
        deployment.services*.class*.simpleName == ["LinkTarget"]
        deployment.components*.class*.simpleName == ["Worker"]
        deployment.primary.is(deployment.services[0])
        deployment.lifecycle == "ready:storefront"
        getClass("pk.Deployment").events == ["create", "apply", "tree", "validate"]
        KlumObjectSupport.of(deployment).structure.fullPath == ""

        and: "ordinary Jackson export intentionally contains enriched, differently named data"
        output.get("deployment_name").asText() == "storefront"
        output.get("lifecycle").asText() == "ready:storefront"
        output.get("primary").asText() == "api"
        output.get("services").size() == 1
        output.get("components").size() == 1
        !output.has("legacy_deployment")
        !output.has("producer")
        !output.fieldNames().any { it.toLowerCase().contains("klum") }
    }

    def "foreign YAML import fixtures keep unknown fields, LINK resolution, Templates, and diagnostic paths explicit"() {
        given:
        createInteroperabilitySchema()
        def mapper = yamlMapper()
        def importer = KlumJacksonImporter.using(mapper)

        when: "unknown fields follow the caller's configured policy"
        importer.readRoot(clazz, yamlInput(mapper, "deployment_name: store\nunexpected: value\n"))

        then:
        RuntimeException unknown = thrown()
        unknown.cause instanceof JsonMappingException
        unknown.message.contains("unexpected")
        unknown.message.contains("foreign-deployment.yaml")

        when: "the same foreign document is accepted only when the caller opts into leniency"
        def lenient = new ObjectMapper(new YAMLFactory()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).findAndRegisterModules()
        def imported = KlumJacksonImporter.using(lenient).readRoot(clazz, yamlInput(lenient, "deployment_name: store\nunexpected: value\n"))

        then:
        imported.name == "store"

        when: "an inline LINK is not silently converted to owned composition"
        importer.readRoot(clazz, yamlInput(mapper, "deployment_name: store\nprimary: { id: api }\n"))

        then:
        RuntimeException linkFailure = thrown()
        linkFailure.cause instanceof JsonMappingException
        linkFailure.message.contains("LINK property")

        when: "Template input remains value-only and does not start a lifecycle"
        def eventsBeforeTemplate = getClass("pk.Deployment").events.size()
        def template = importer.readTemplate(clazz, yamlInput(mapper, "deployment_name: template\n"))

        then:
        template.name == "template"
        getClass("pk.Deployment").events.size() == eventsBeforeTemplate
    }

    def "export fixtures independently prove ordinary POJO projection, custom serializers, LINK projection, and Template rejection"() {
        given:
        createExportSchema()
        def mapper = yamlMapper()
        def Node = getClass("pk.ExportNode")
        def TagValue = getClass("pk.TargetTag")
        def external = Node.Create.With("api") {}
        def exported = clazz.Create.With {
            deploymentName "storefront"
            primary external
            targetTag TagValue.newInstance(value: "stable")
            lifecycle "enriched"
        }

        when:
        def output = mapper.readTree(mapper.writeValueAsString(exported))

        then:
        output.get("deployment_name").asText() == "storefront"
        output.get("lifecycle").asText() == "enriched"
        output.get("primary").asText() == "ref:api"
        output.get("target_tag").asText() == "tag:stable"
        !output.fieldNames().any { it.toLowerCase().contains("klum") }

        when: "a marked Template cannot claim to be an ordinary external document"
        def template = clazz.Template.Create { deploymentName "template" }
        mapper.writeValueAsString(template)

        then:
        JsonMappingException rejected = thrown()
        rejected.message.contains("Cannot serialize marked Template")
    }

    private static ObjectMapper yamlMapper() {
        new ObjectMapper(new YAMLFactory()).findAndRegisterModules()
    }

    private static KlumJacksonInput yamlInput(ObjectMapper mapper, String source) {
        KlumJacksonInput.parser(mapper.factory.createParser(source)).named("foreign-deployment.yaml")
    }

    private void createInteroperabilitySchema() {
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonAlias
            import com.fasterxml.jackson.annotation.JsonIdentityInfo
            import com.fasterxml.jackson.annotation.JsonIdentityReference
            import com.fasterxml.jackson.annotation.JsonProperty
            import com.fasterxml.jackson.annotation.JsonSubTypes
            import com.fasterxml.jackson.annotation.JsonTypeInfo
            import com.fasterxml.jackson.annotation.ObjectIdGenerators

            @DSL
            class Deployment {
                static List<String> events = []

                @JsonProperty("deployment_name")
                @JsonAlias("legacy_deployment")
                String name
                String note = "default-note"
                List<String> tags = ["default-tag"]
                List<LinkTarget> services
                List<Component> components

                @JsonIdentityReference(alwaysAsId = true)
                @Field(FieldType.LINK)
                LinkTarget primary

                @JsonProperty(access = JsonProperty.Access.READ_ONLY)
                String lifecycle

                @PostCreate
                void created() { events << "create" }

                @PostApply
                void applied() {
                    events << "apply"
                    lifecycle = "ready:$name"
                }

                @PostTree
                void completedTree() { events << "tree" }

                @Validate
                void validated() { events << "validate" }
            }

            @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
            @JsonSubTypes([
                @JsonSubTypes.Type(value = Service, name = "service"),
                @JsonSubTypes.Type(value = Worker, name = "worker")
            ])
            @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
            @DSL
            abstract class Component {
                @Key String id
                @Owner Deployment deployment
            }

            @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
            @DSL
            class LinkTarget {
                @Key String id
                @Owner Deployment deployment
            }

            @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
            @DSL
            class Service extends Component {
                int port
            }

            @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
            @DSL
            class Worker extends Component {
                String queue
            }
        ''')
    }

    private void createExportSchema() {
        createClass('''
            package pk

            import com.fasterxml.jackson.core.JsonGenerator
            import com.fasterxml.jackson.annotation.JsonProperty
            import com.fasterxml.jackson.databind.JsonSerializer
            import com.fasterxml.jackson.databind.SerializerProvider
            import com.fasterxml.jackson.databind.annotation.JsonSerialize

            @DSL
            class ExportRoot {
                @JsonProperty("deployment_name")
                String deploymentName
                String lifecycle

                @JsonSerialize(using = ExportNodeReferenceSerializer)
                @Field(FieldType.LINK)
                ExportNode primary

                @JsonProperty("target_tag")
                @JsonSerialize(using = TargetTagSerializer)
                TargetTag targetTag
            }

            @DSL
            class ExportNode {
                @Key String id
            }

            class TargetTag {
                String value
            }

            class ExportNodeReferenceSerializer extends JsonSerializer<ExportNode> {
                @Override
                void serialize(ExportNode value, JsonGenerator generator, SerializerProvider provider) {
                    generator.writeString("ref:$value.id")
                }
            }

            class TargetTagSerializer extends JsonSerializer<TargetTag> {
                @Override
                void serialize(TargetTag value, JsonGenerator generator, SerializerProvider provider) {
                    generator.writeString("tag:$value.value")
                }
            }
        ''')
    }
}
