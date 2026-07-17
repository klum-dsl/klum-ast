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
import com.fasterxml.jackson.databind.ObjectMapper

import javax.tools.ToolProvider

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

    def "Java consumers compile against the root and Template importer descriptors"() {
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

            class JacksonConsumer {
                void importValue(ObjectMapper mapper) {
                    KlumJacksonImporter importer = KlumJacksonImporter.using(mapper);
                    JavaImportedValue root = importer.readRoot(JavaImportedValue.class, KlumJacksonInput.map(java.util.Map.of()));
                    JavaImportedValue template = importer.readTemplate(JavaImportedValue.class, KlumJacksonInput.map(java.util.Map.of()));
                }
            }
        ''')
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
