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
package com.blackbuild.klum.ast.schema

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Issue

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** SCHEMA-T: one vertical API -> Schema -> binary-only projection proof. */
@Issue('529')
class SchemaTracerTest extends AbstractDSLSpec {
    private static final String API = '''
        package schematracer.api
        import com.blackbuild.klum.ast.DSL
        class Traps {
            static boolean valid(int value) { hit('validation closure'); value >= 0 }
            static String hit(String point) {
                System.setProperty('schema.tracer.executed', point)
                if (!Boolean.getBoolean('schema.tracer.allowExecution')) {
                    throw new AssertionError('SCHEMA-T user code: ' + point)
                }
                'provided'
            }
        }
        @DSL abstract class ApplicationApi {
            static { Traps.hit('API initialization') }
            String region
        }
    '''

    private static final String SCHEMA = '''
        package schematracer.schema
        import com.blackbuild.klum.ast.*
        import com.blackbuild.klum.ast.runtime.KlumFactory
        import com.fasterxml.jackson.annotation.JsonIgnore
        import com.fasterxml.jackson.annotation.JsonProperty
        import schematracer.api.ApplicationApi
        import schematracer.api.Traps

        enum Mode {
            SAFE, FAST
            static { Traps.hit('enum initialization') }
        }
        @DSL class Component {
            static { Traps.hit('child initialization') }
            @Key String name
            String value
        }
        @DSL class Application extends ApplicationApi {
            static { Traps.hit('Schema initialization') }
            @JsonProperty('service-name') String serviceName
            @JsonIgnore String secret
            @Validate({ Traps.valid(it) }) int port
            boolean enabled
            double ratio
            Mode mode
            @Field(key = { Traps.hit('child key closure'); 'primary' }) Component primary
            Map<String, Component> components
            Map<String, String> labels
            @Required String supplied = Traps.hit('initializer')
            @Default(code = { Traps.hit('default closure') }) String computed
            @JsonIgnore
            @Field(converters = [{ String text -> Traps.hit('converter'); new URI(text) }]) URI internalUri
            @Builder.Method void endpoint(Map<String, String> settings) {
                Traps.hit('operation')
                serviceName = settings.host
                port = Integer.parseInt(settings.port)
            }
            @PostCreate void created() { Traps.hit('post-create') }
            @PostApply void applied() { Traps.hit('post-apply') }
            @Validate void checked() { Traps.hit('completed validation') }
            static class Factory extends KlumFactory.Unkeyed<Application> {
                static { Traps.hit('factory initialization') }
                protected Factory() { super(Application); Traps.hit('factory constructor') }
            }
        }
    '''

    @Issue(['529', '932'])
    def 'binary-only current carrier projects real schema and agrees with bounded WIRE-1 shapes'() {
        given:
        File artifacts = new File(compilerConfiguration.targetDirectory, 'artifacts')
        assert artifacts.mkdirs()
        File api = compileJar(artifacts, 'api', [API])
        File schema = compileSchema(artifacts, api)
        File output = new File(artifacts.parentFile, 'schema.json')
        File facts = new File(artifacts.parentFile, 'facts.json')
        List<Map> cases = instances()
        def runtimeLoader = new URLClassLoader([api.toURI().toURL(), schema.toURI().toURL()] as URL[], getClass().classLoader)

        when: 'a fresh JVM has only test helper classes and Groovy, with API/Schema JARs passed as data'
        String extraction = extract(api, schema, output, facts)

        then:
        extraction == ''
        output.text.contains('"$schema":"https://json-schema.org/draft/2020-12/schema"')
        output.text.contains('"$ref":"#/$defs/schematracer.schema.Application_DSL%24Builder"')
        facts.text.contains('schematracer.api.ApplicationApi#region')
        facts.text.contains('schematracer.api.ApplicationApi_DSL$Builder')
        facts.text.contains('"role":"OWNED_OBJECT"')
        facts.text.contains('"role":"KEYED_OWNED_MAP"')
        facts.text.contains('"role":"OPERATION"')
        facts.text.contains('schematracer.schema.Application#secret')
        !output.text.contains('"secret":')
        !output.text.contains('"required":')
        !output.text.contains('"default":')
        System.getProperty('schema.tracer.executed') == null

        when: 'the identical fixture deliberately constructs models only after extraction'
        System.setProperty('schema.tracer.allowExecution', 'true')
        Class root = Class.forName('schematracer.schema.Application', false, runtimeLoader)
        cases.each { sample ->
            boolean accepted = false
            try {
                def result = KlumStructuredInput.readRoot(root, null, sample.input as Map, sample.name as String)
                accepted = true
                if (sample.name == 'complete') {
                    assert result.region == 'eu'
                    assert result.serviceName == 'api'
                    assert result.port == 8080
                    assert result.primary.name == 'primary'
                    assert result.primary.value == 'direct'
                    assert result.components.'a/b~c'.name == 'a/b~c'
                    assert result.components.'a/b~c'.value == 'keyed'
                    assert result.labels.'app.kubernetes.io/name' == 'orders'
                    assert result.mode.name() == 'SAFE'
                }
            } catch (KlumModelException failure) {
                assert !sample.accepted: "Unexpected runtime failure for $sample.name: $failure"
            }
            assert accepted == sample.accepted: "WIRE-1 mismatch for $sample.name"
            sample.runtimeAccepted = accepted
        }
        new File(output.parentFile, 'instances.json').text = SchemaTracerProjector.json(cases) + '\n'

        then:
        cases.count { it.runtimeAccepted } == 5
        cases.count { !it.runtimeAccepted } == 12
        System.getProperty('schema.tracer.executed') != null

        cleanup:
        runtimeLoader?.close()
        System.clearProperty('schema.tracer.allowExecution')
        System.clearProperty('schema.tracer.executed')
    }

    def 'initialization traps fail intentional execution of API Schema enum child and factory'() {
        given:
        File artifacts = tempFolder.newFolder('controls')
        File api = compileJar(artifacts, 'api', [API])
        File schema = compileSchema(artifacts, api)

        when:
        List<String> trapped = []
        ['schematracer.api.ApplicationApi', 'schematracer.schema.Application', 'schematracer.schema.Mode',
         'schematracer.schema.Component', 'schematracer.schema.Application$Factory'].each { name ->
            new URLClassLoader([api.toURI().toURL(), schema.toURI().toURL()] as URL[], getClass().classLoader).withCloseable { binaries ->
                try {
                    Class.forName(name, true, binaries)
                    throw new AssertionError("Initialization trap did not fire: $name")
                } catch (AssertionError | ExceptionInInitializerError failure) {
                    assert System.getProperty('schema.tracer.executed') != null
                    assert (failure.cause ?: failure).message.contains('SCHEMA-T user code:')
                    trapped << name
                }
            }
        }

        then:
        trapped.size() == 5

        cleanup:
        System.clearProperty('schema.tracer.executed')
    }

    private File compileSchema(File artifacts, File api) {
        // Naming stubs are compiler inputs only; extraction never queries their behavior.
        compileJar(artifacts, 'schema', [SCHEMA, '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        ''', '''
            package com.fasterxml.jackson.annotation
            @interface JsonIgnore { boolean value() default true }
        '''], [api])
    }

    private File compileJar(File artifacts, String name, List<String> sources, List<File> dependencies = []) {
        File classes = new File(artifacts, name + '-classes')
        def configuration = new CompilerConfiguration()
        configuration.targetDirectory = classes
        configuration.warningLevel = 0
        def compiler = new GroovyClassLoader(getClass().classLoader, configuration)
        dependencies.each { compiler.addClasspath(it.path) }
        try {
            def unit = new CompilationUnit(configuration, null, compiler)
            sources.eachWithIndex { source, index -> unit.addSource("Fixture${index}.groovy", source) }
            unit.compile()
        } finally {
            compiler.close()
        }
        File jar = new File(artifacts, name + '.jar')
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { archive ->
            classes.eachFileRecurse { file ->
                if (file.file) {
                    assert file.name.endsWith('.class'): "Unexpected non-binary output: $file"
                    archive.putNextEntry(new JarEntry(classes.toPath().relativize(file.toPath()).toString()))
                    archive.write(file.bytes)
                    archive.closeEntry()
                }
            }
        }
        assert classes.deleteDir() // Extraction cannot fall back to compilation output or mirrors.
        new JarFile(jar).withCloseable { archive ->
            assert archive.entries().toList().every { it.name.endsWith('.class') }
        }
        jar
    }

    private String extract(File api, File schema, File output, File facts) {
        String helperClasses = new File(SchemaTracerProjector.protectionDomain.codeSource.location.toURI()).path
        String groovyJar = new File(GroovyObject.protectionDomain.codeSource.location.toURI()).path
        def process = new ProcessBuilder(new File(System.getProperty('java.home'), 'bin/java').path,
                '-cp', [helperClasses, groovyJar].join(File.pathSeparator), SchemaTracerProjector.name,
                'schematracer.schema.Application', api.path, schema.path, output.path, facts.path)
                .redirectErrorStream(true).start()
        String diagnostics = process.inputStream.text
        assert process.waitFor() == 0: diagnostics
        diagnostics
    }

    private static List<Map> instances() {
        List<Map> cases = [
            [name: 'empty', input: [:]],
            [name: 'false-zero-empty', input: [enabled: false, port: 0, labels: [:], components: [:]]],
            [name: 'renamed', input: ['service-name': 'orders', ratio: 0.5d]],
            [name: 'literal-data-keys', input: [components: ['': [value: 'empty key']], labels: ['a/b~c': 'literal']]],
            [name: 'complete', input: [region: 'eu', 'service-name': 'orders', enabled: true, mode: 'SAFE',
                primary: [value: 'direct'], components: ['a/b~c': [value: 'keyed']],
                labels: ['app.kubernetes.io/name': 'orders'], endpoint: [host: 'api', port: '8080']]]
        ].collect { it + [accepted: true] }
        cases + [
            [name: 'wrong-scalar', input: [port: '8080']],
            [name: 'wrong-enum', input: [mode: 'UNKNOWN']],
            [name: 'wrong-nested', input: [primary: []]],
            [name: 'unknown', input: [unknown: 'x']],
            [name: 'excluded', input: [secret: 'x']],
            [name: 'raw-rename', input: [serviceName: 'x']],
            [name: 'duplicate-child-key', input: [components: [one: [name: 'two']]]],
            [name: 'wrong-operation', input: [endpoint: ['api', '8080']]],
            [name: 'wrong-operation-value', input: [endpoint: [port: 8080]]],
            [name: 'wrong-keyed-map', input: [components: []]],
            [name: 'wrong-data-value', input: [labels: [team: 1]]],
            [name: 'null', input: ['service-name': null]]
        ].collect { it + [accepted: false] }
    }
}
