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
import com.blackbuild.klum.ast.process.PhaseDriver
import com.blackbuild.klum.ast.util.FactoryHelper
import com.blackbuild.klum.ast.util.TemplateManager
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Issue

import javax.tools.ToolProvider

@Issue("463")
class KlumJacksonImporterSpec extends AbstractDSLSpec {

    def "readRoot applies one captured input through the generated Builder lifecycle"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL
            class ImportedValue {
                @JsonProperty("public")
                String value
                String lifecycle

                @PostCreate
                void created() { lifecycle = "created" }

                @PostApply
                void applied() { lifecycle += "-applied" }
            }
        ''')
        def mapper = new ObjectMapper().findAndRegisterModules()
        def importer = KlumJacksonImporter.using(mapper)

        when:
        def imported = importer.readRoot(clazz, KlumJacksonInput.tree(mapper.readTree('{"public":"Ada"}').deepCopy()))

        then:
        imported.value == "Ada"
        imported.lifecycle == "created-applied"
    }

    def "readTemplate returns a marked value-only Template without lifecycle callbacks"() {
        given:
        createClass('''
            package pk

            @DSL
            class ImportedTemplate {
                String value
                boolean callbackRan

                @PostCreate
                void created() { callbackRan = true }
            }
        ''')
        def mapper = new ObjectMapper().findAndRegisterModules()

        when:
        def imported = KlumJacksonImporter.using(mapper).readTemplate(clazz, KlumJacksonInput.map([value: "Ada"]))

        then:
        imported.value == "Ada"
        !imported.callbackRan
        TemplateManager.isTemplate(imported)
    }

    def "the importer requires a caller-configured Klum module without mutating the mapper"() {
        given:
        createClass('''
            package pk

            @DSL
            class UnconfiguredImport { String value }
        ''')
        def mapper = new ObjectMapper()

        when:
        KlumJacksonImporter.using(mapper).readRoot(clazz, KlumJacksonInput.map([value: "Ada"]))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("requires KlumAstModule")
        mapper.registeredModuleIds.empty
    }

    def "readRoot honours a type-level custom Jackson deserializer as an explicit opt-out"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.core.JsonParser
            import com.fasterxml.jackson.databind.DeserializationContext
            import com.fasterxml.jackson.databind.annotation.JsonDeserialize
            import com.fasterxml.jackson.databind.deser.std.StdDeserializer

            @JsonDeserialize(using = CustomImportedValueDeserializer)
            @DSL
            class CustomImportedValue { String value }

            class CustomImportedValueDeserializer extends StdDeserializer<CustomImportedValue> {
                CustomImportedValueDeserializer() { super(CustomImportedValue) }

                @Override
                CustomImportedValue deserialize(JsonParser parser, DeserializationContext context) {
                    def tree = parser.codec.readTree(parser)
                    return CustomImportedValue.Create.With { value "custom:${tree.value.asText()}" }
                }
            }
        ''')
        def importer = KlumJacksonImporter.using(new ObjectMapper())

        when:
        def imported = importer.readRoot(clazz, KlumJacksonInput.map([value: "Ada"]))

        then:
        imported.value == "custom:Ada"
    }

    def "borrowed parsers remain open and named sources appear in import diagnostics"() {
        given:
        createClass('''
            package pk

            @DSL
            class ParserImportedValue { String value }
        ''')
        def mapper = new ObjectMapper().findAndRegisterModules()
        def importer = KlumJacksonImporter.using(mapper)
        def validParser = mapper.factory.createParser('{"value":"Ada"}')
        def invalidParser = mapper.factory.createParser('{')

        when:
        def imported = importer.readRoot(clazz, KlumJacksonInput.parser(validParser))
        importer.readRoot(clazz, KlumJacksonInput.parser(invalidParser).named("config.yaml"))

        then:
        imported.value == "Ada"
        !validParser.closed
        def exception = thrown(RuntimeException)
        exception.message.startsWith("Jackson readRoot import of pk.ParserImportedValue failed:")
        exception.message.contains('$/ParserImportedValue.readRoot:jackson(config.yaml)')
        exception.message.contains(":line ")
        exception.cause instanceof JsonProcessingException
    }

    def "Builder modes stay in the active Construction session and preserve Builder identity"() {
        given:
        createClass('''
            package pk

            @DSL
            class ImportedParent {
                ImportedChild child
            }

            @DSL
            class ImportedChild {
                String value
            }
        ''')
        def parentType = clazz
        def childType = getClass("pk.ImportedChild")
        def importer = KlumJacksonImporter.using(new ObjectMapper().findAndRegisterModules())

        when:
        PhaseDriver.withBuilderLifecycle(
                { FactoryHelper.createBuilder(parentType, null) },
                { parentBuilder ->
                    def child = importer.readBuilder(childType.Create.AsBuilder, KlumJacksonInput.map([value: "first"]))
                    def applied = importer.applyToBuilder(child, KlumJacksonInput.map([value: "second"]))
                    assert applied.is(child)
                    assert child.value == "second"
                }
        )

        then:
        noExceptionThrown()
    }

    def "readBuilder rejects calls outside an active Construction session"() {
        given:
        createClass('''
            package pk

            @DSL
            class DetachedImportedValue { String value }
        ''')
        def importer = KlumJacksonImporter.using(new ObjectMapper().findAndRegisterModules())

        when:
        importer.readBuilder(clazz.Create.AsBuilder, KlumJacksonInput.map([value: "Ada"]))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("active Construction session")
    }

    def "applyToBuilder rejects a Builder after its Construction session completes"() {
        given:
        createClass('''
            package pk

            @DSL
            class SealedImportedValue { String value }
        ''')
        def importer = KlumJacksonImporter.using(new ObjectMapper().findAndRegisterModules())
        def builder
        PhaseDriver.withBuilderLifecycle(
                { FactoryHelper.createBuilder(clazz, null) },
                { builder = it }
        )

        when:
        importer.applyToBuilder(builder, KlumJacksonInput.map([value: "Ada"]))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("active Construction session")
    }

    def "applyToBuilder rejects an unowned Builder without an active Construction session"() {
        given:
        createClass('''
            package pk

            @DSL
            class UnownedImportedValue { String value }
        ''')
        def importer = KlumJacksonImporter.using(new ObjectMapper().findAndRegisterModules())
        def builder = FactoryHelper.createBuilder(clazz, null)

        when:
        importer.applyToBuilder(builder, KlumJacksonInput.map([value: "Ada"]))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("active Construction session")
    }

    def "Java consumers compile against every importer descriptor"() {
        given:
        createClass('''
            package pk

            @DSL
            class JavaImportedValue { String value }
        ''')

        expect:
        compileJavaConsumer('''
            package sample;

            import com.blackbuild.klum.ast.jackson.KlumJacksonImporter;
            import com.blackbuild.klum.ast.jackson.KlumJacksonInput;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import pk.JavaImportedValue;
            import pk.JavaImportedValue_DSL;

            import java.util.Map;

            class JacksonConsumer {
                void importValue(ObjectMapper mapper) {
                    KlumJacksonImporter importer = KlumJacksonImporter.using(mapper);
                    JavaImportedValue root = importer.readRoot(JavaImportedValue.class, KlumJacksonInput.map(Map.of()));
                    JavaImportedValue template = importer.readTemplate(JavaImportedValue.class, KlumJacksonInput.map(Map.of()));
                    JavaImportedValue_DSL.Builder builder = importer.readBuilder(
                            JavaImportedValue.Create.getAsBuilder(), KlumJacksonInput.map(Map.of()));
                    JavaImportedValue_DSL.Builder applied = importer.applyToBuilder(builder, KlumJacksonInput.map(Map.of()));
                }
            }
        ''')
    }

    def "statically compiled Groovy consumers infer every importer type"() {
        given:
        createClass('''
            package pk

            @DSL
            class StaticImportedValue { String value }
        ''')

        when:
        Class<?> consumer = createSecondaryClass('''
            package pk

            import com.blackbuild.klum.ast.jackson.KlumJacksonImporter
            import com.blackbuild.klum.ast.jackson.KlumJacksonInput
            import com.fasterxml.jackson.databind.ObjectMapper
            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticJacksonConsumer {
                static StaticImportedValue root(ObjectMapper mapper) {
                    KlumJacksonImporter.using(mapper).readRoot(StaticImportedValue, KlumJacksonInput.map([value: "root"]))
                }

                static StaticImportedValue template(ObjectMapper mapper) {
                    KlumJacksonImporter.using(mapper).readTemplate(StaticImportedValue, KlumJacksonInput.map([value: "template"]))
                }

                static StaticImportedValue_DSL.Builder builder(ObjectMapper mapper) {
                    KlumJacksonImporter importer = KlumJacksonImporter.using(mapper)
                    StaticImportedValue_DSL.Builder builder = importer.readBuilder(
                            StaticImportedValue.Create.AsBuilder, KlumJacksonInput.map([value: "builder"]))
                    importer.applyToBuilder(builder, KlumJacksonInput.map([value: "applied"]))
                }
            }
        ''', 'pk/StaticJacksonConsumer.groovy')

        then:
        consumer != null
    }

    private void compileJavaConsumer(String source) {
        File sourceFile = new File(tempFolder.root, 'sample/JacksonConsumer.java')
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source.stripIndent()
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)
        def errors = new ByteArrayOutputStream()
        int result = ToolProvider.systemJavaCompiler.run(null, null, errors, '-classpath', classpath,
                '-d', compilerConfiguration.targetDirectory.absolutePath, sourceFile.absolutePath)
        assert result == 0: errors.toString()
    }
}
