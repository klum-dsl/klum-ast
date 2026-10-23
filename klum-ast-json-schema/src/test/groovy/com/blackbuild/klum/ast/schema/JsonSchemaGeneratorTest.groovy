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
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import spock.lang.Issue
import spock.lang.Tag
import spock.lang.See
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.lang.module.ModuleFinder
import javax.tools.ToolProvider
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** Runs the production tool against separately compiled binary-only inputs. */
@Issue('529')
class JsonSchemaGeneratorTest extends AbstractDSLSpec {
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
        extraction.contains('root schematracer.schema.Application')
        output.text.contains('"$schema":"https://json-schema.org/draft/2020-12/schema"')
        output.text.contains('"$ref":"#/$defs/schematracer.schema.Application_DSL%24Builder"')
        facts.text.contains('schematracer.api.ApplicationApi#region')
        facts.text.contains('schematracer.api.ApplicationApi_DSL$Builder')
        facts.text.contains('OWNED_OBJECT')
        facts.text.contains('KEYED_OWNED_MAP')
        facts.text.contains('OPERATION')
        facts.text.contains('schematracer.schema.Application#secret')
        facts.text.contains('"service-name"; name origin JSON_PROPERTY')
        facts.text.contains('"region"; name origin IDENTITY')
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
        new File(output.parentFile, 'instances.json').text = JsonOutput.toJson(cases) + '\n'

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

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#standalone-generation')
    def 'generates a schema for a compiled application without running its code'() {
        given:
        createClass '''
            package example
            import com.blackbuild.klum.ast.*
            @DSL class Application {
                static { throw new AssertionError('Schema execution') }
                String host
                int port
                Map<String, String> labels
                @Builder.Method void endpoint(Map<String, String> settings) {
                    host = settings.host
                    port = Integer.parseInt(settings.port)
                }
            }
        '''
        File output = new File(compilerConfiguration.targetDirectory, 'application.schema.json')

        when:
        def result = runTool('example.Application', output, [compilerConfiguration.targetDirectory])
        assert result.exit == 0: result.text
        def document = new JsonSlurper().parse(output)

        then:
        result.exit == 0
        document.'$schema' == 'https://json-schema.org/draft/2020-12/schema'
        document.'$defs'.'example.Application_DSL$Builder'.get('properties').endpoint ==
                [type: 'object', additionalProperties: [type: 'string']]
        !document.'$defs'.'example.Application_DSL$Builder'.containsKey('required')
    }

    def 'projects every admitted scalar field container and recursive ordinary payload'() {
        given:
        createClass '''
            package matrix
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                static { throw new AssertionError('Schema execution') }
                byte tiny
                Byte boxedTiny
                short small
                Short boxedSmall
                int count
                Integer boxedCount
                long large
                Long boxedLarge
                float single
                Float boxedSingle
                double decimal
                Double boxedDecimal
                boolean enabled
                Boolean boxedEnabled
                BigInteger huge
                BigDecimal exact
                List<String> names
                Set<Integer> ids
                SortedSet<String> sorted
                NavigableSet<String> navigable
                EnumSet<Mode> modes
                Map<String, String> labels
                SortedMap<String, Integer> sortedLabels
                NavigableMap<String, Integer> navigableLabels
                List<Map<String, List<Integer>>> nested
                @Builder.Method void batches(List<Map<String, List<Integer>>> value) { }
                @Builder.Method void settings(Map<String, List<String>> value) { }
            }
            enum Mode { SAFE, FAST }
        '''
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('matrix.Configuration', output, [compilerConfiguration.targetDirectory])
        assert result.exit == 0: result.text
        def document = new JsonSlurper().parse(output)
        def properties = document.'$defs'.'matrix.Configuration_DSL$Builder'.get('properties')

        new File(output.parentFile, 'instances.json').text = JsonOutput.toJson([
            [accepted: true, input: [tiny: -128, small: 32767, count: 2147483647, large: Long.MIN_VALUE,
                huge: new BigInteger('1234567890123456789012345678901234567890'), exact: new BigDecimal('0.123456789012345678901'),
                ids: [1, 1], modes: ['SAFE', 'FAST'], nested: [['a/b~c': [1, 2]]], batches: [[camelCase: [3]]]]],
            [accepted: true, input: [large: Long.MAX_VALUE, single: 1e100, decimal: -1e100, tiny: 1.0]],
            [accepted: false, input: [tiny: -129]], [accepted: false, input: [small: 32768]],
            [accepted: false, input: [count: 2147483648]], [accepted: false, input: [large: new BigInteger('9223372036854775808')]],
            [accepted: false, input: [huge: 1.5]], [accepted: false, input: [exact: '0.1']],
            [accepted: false, input: [nested: [[name: ['bad']]]]], [accepted: false, input: [modes: ['MISSING']]],
            [accepted: false, input: [settings: [name: 'bad']]], [accepted: false, input: [names: null]]
        ])

        then:
        result.exit == 0
        properties.size() == 27
        properties.tiny == [type: 'integer', minimum: -128, maximum: 127]
        properties.small == [type: 'integer', minimum: -32768, maximum: 32767]
        properties.count == [type: 'integer', minimum: Integer.MIN_VALUE, maximum: Integer.MAX_VALUE]
        properties.large == [type: 'integer', minimum: Long.MIN_VALUE, maximum: Long.MAX_VALUE]
        properties.huge == [type: 'integer']
        properties.exact == [type: 'number']
        properties.single == properties.decimal
        properties.boxedSingle == properties.single
        properties.boxedDecimal == properties.decimal
        properties.boxedTiny == properties.tiny
        properties.boxedSmall == properties.small
        properties.boxedCount == properties.count
        properties.boxedLarge == properties.large
        properties.boxedEnabled == properties.enabled
        properties.ids.items == properties.count
        !properties.ids.containsKey('uniqueItems')
        properties.modes == [type: 'array', items: [type: 'string', enum: ['SAFE', 'FAST']]]
        properties.sorted == properties.navigable
        properties.sortedLabels == properties.navigableLabels
        properties.nested == properties.batches
        properties.settings == [type: 'object', additionalProperties: [type: 'array', items: [type: 'string']]]
    }

    def 'recursive definitions and fully qualified Unicode nested identities remain distinct'() {
        given:
        createClass '''
            package identities
            import com.blackbuild.klum.ast.DSL
            @DSL class Node {
                static { throw new AssertionError('Schema execution') }
                Node next
                Left.Part left
                Right.Part right
                Café café
            }
            class Left { @DSL static class Part { String left } }
            class Right { @DSL static class Part { String right } }
            @DSL class Café { String value }
        '''
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('identities.Node', output, [compilerConfiguration.targetDirectory])
        assert result.exit == 0: result.text
        def document = new JsonSlurper().parse(output)
        def properties = document.'$defs'.'identities.Node_DSL$Builder'.get('properties')

        new File(output.parentFile, 'instances.json').text = JsonOutput.toJson([
            [accepted: true, input: [next: [next: [left: [left: 'yes']]], 'café': [value: 'ok']]],
            [accepted: false, input: [right: [left: 'wrong type']]],
            [accepted: false, input: [next: [unknown: 'x']]]
        ])

        then:
        result.exit == 0
        document.'$defs'.size() == 4
        properties.next.'$ref' == document.'$ref'
        properties.left.'$ref' != properties.right.'$ref'
        properties.'café'.'$ref' == '#/$defs/identities.Caf%C3%A9_DSL%24Builder'
    }

    def 'retains compiler rejections and never exposes unclassified helpers or unsupported relationships'() {
        given:
        createClass '''
            package omitted
            import com.blackbuild.klum.ast.*
            @DSL abstract class AbstractChild { String value }
            @DSL class Child { @Key String name; String value }
            @DSL class Configuration {
                static { throw new AssertionError('Schema execution') }
                String title
                URI unsupported
                AbstractChild abstractChild
                @Field(FieldType.LINK) Child link
                @Field(keyMapping = { it.name }) Map<String, Child> mapped
                List<Child> children
                @Builder.Method void command() { }
                @Builder.Method void multiple(String a, String b) { }
                @Builder.Query String answer() { 'answer' }
            }
        '''
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('omitted.Configuration', output, [compilerConfiguration.targetDirectory])
        assert result.exit == 0: result.text
        def document = new JsonSlurper().parse(output)

        then:
        result.exit == 0
        document.'$defs'.'omitted.Configuration_DSL$Builder'.get('properties').keySet() == ['title'] as Set
        result.text.contains('rejected omitted.Configuration#mapped')
        result.text.contains('rejected omitted.Configuration#abstractChild')
        !result.text.contains('admitted OPERATION omitted.Configuration#command')
    }

    def 'separately compiled inheritance keeps rejection precedence and diagnoses collisions'() {
        given:
        File artifacts = tempFolder.newFolder('collision')
        File api = compileJar(artifacts, 'api', ['''
            package collisions
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL abstract class Parent { @JsonProperty('clash') String first; String region }
        ''', '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        '''])
        File schema = compileJar(artifacts, 'schema', ['''
            package collisions
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Child extends Parent { @JsonProperty('clash') String second; String available }
        '''], [api])
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('collisions.Child', output, [api, schema])
        assert result.exit == 0: result.text
        def document = new JsonSlurper().parse(output)

        then:
        result.exit == 0
        document.'$defs'.'collisions.Child_DSL$Builder'.get('properties').keySet() == ['available', 'region'] as Set
        result.text.contains("external-name collision for 'clash': collisions.Child#second, collisions.Parent#first")
        result.text.contains('rejected collisions.Child#second')
        result.text.contains('rejected collisions.Parent#first')
    }

    def 'output is reproducible across artifact order relocation and repeated generation and changes with API bytes'() {
        given:
        File artifacts = tempFolder.newFolder('reproducible')
        File api = compileJar(artifacts, 'api', [API])
        File schema = compileSchema(artifacts, api)
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')
        def first = runTool('schematracer.schema.Application', output, [api, schema])
        String original = output.text
        File relocated = tempFolder.newFolder('relocated')
        File apiCopy = new File(relocated, 'renamed-api.jar'); apiCopy.bytes = api.bytes
        File schemaCopy = new File(relocated, 'renamed-schema.jar'); schemaCopy.bytes = schema.bytes

        when:
        def repeated = runTool('schematracer.schema.Application', output, [schemaCopy, apiCopy])

        then:
        first.exit == 0
        repeated == first
        output.text == original

        when:
        File changed = compileJar(artifacts, 'api', [API.replace('String region', 'String region; String zone')])
        def invalidated = runTool('schematracer.schema.Application', output, [changed, schema])

        then:
        invalidated.exit == 0
        output.text != original
        new JsonSlurper().parse(output).'$defs'.'schematracer.schema.Application_DSL$Builder'.get('properties').zone == [type: 'string']
    }

    def 'rejects malformed carrier #defect without running Schema code or creating output'() {
        given:
        createClass '''
            package corrupt
            import com.blackbuild.klum.ast.DSL
            @DSL class Configuration {
                static { throw new AssertionError('Schema execution') }
                String title
            }
        '''
        File carrier = new File(compilerConfiguration.targetDirectory, 'corrupt/Configuration_DSL$WirePlan.class')
        rewriteCarrier(carrier, invalidCarrier(carrierData(carrier), defect))
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('corrupt.Configuration', output, [compilerConfiguration.targetDirectory])

        then:
        result.exit != 0
        result.text.contains('recompile and package the Schema')
        result.text.contains('corrupt.Configuration')
        !result.text.contains('AssertionError: Schema execution')
        !output.exists()

        where:
        defect << ['header', 'terminator', 'old-record', 'role', 'source', 'descriptor', 'name-origin',
                   'fixed-key', 'unknown-scalar', 'identity', 'noncanonical-token', 'payload', 'trailing-payload', 'enum-type', 'payload-signature']
    }

    def 'missing #missing fails the whole admitted root even for optional child input'() {
        given:
        createClass '''
            package missing
            import com.blackbuild.klum.ast.DSL
            @DSL class Child { String value }
            @DSL class Configuration {
                static { throw new AssertionError('Schema execution') }
                Child child
            }
        '''
        assert new File(compilerConfiguration.targetDirectory, "missing/$missing" + '.class').delete()
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('missing.Configuration', output, [compilerConfiguration.targetDirectory])

        then:
        result.exit != 0
        result.text.contains('Missing binary')
        result.text.contains('recompile and package the Schema')
        !output.exists()

        where:
        missing << ['Configuration_DSL$WirePlan', 'Configuration_DSL$Builder', 'Configuration_DSL$Factory',
                    'Child', 'Child_DSL$WirePlan', 'Child_DSL$Builder', 'Child_DSL$Factory']
    }

    def 'identical duplicate binaries are harmless and conflicting identities fail independently of artifact order'() {
        given:
        File artifacts = tempFolder.newFolder('duplicates')
        File first = compileJar(artifacts, 'first', ['''
            package duplicate
            import com.blackbuild.klum.ast.DSL
            @DSL class Configuration { String value }
        '''])
        File same = new File(artifacts, 'same.jar'); same.bytes = first.bytes
        File changed = compileJar(artifacts, 'changed', ['''
            package duplicate
            import com.blackbuild.klum.ast.DSL
            @DSL class Configuration { int value }
        '''])
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def identical = runTool('duplicate.Configuration', output, [first, same])
        def conflicting = runTool('duplicate.Configuration', new File(output.parentFile, 'failed.json'), [first, changed])
        def reversed = runTool('duplicate.Configuration', new File(output.parentFile, 'failed.json'), [changed, first])

        then:
        identical.exit == 0
        conflicting.exit != 0
        reversed.exit != 0
        conflicting.text.contains('Conflicting binary duplicate.Configuration')
        reversed.text.contains('Conflicting binary duplicate.Configuration')
        !new File(output.parentFile, 'failed.json').exists()
    }

    def 'abstract root is rejected without choosing an implementation'() {
        given:
        createClass '''
            package roots
            import com.blackbuild.klum.ast.DSL
            @DSL abstract class Configuration { String value }
        '''
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('roots.Configuration', output, [compilerConfiguration.targetDirectory])

        then:
        result.exit != 0
        result.text.contains('Root/relationship is abstract')
        !output.exists()
    }

    def 'fixed child keys stay construction facts and retained prose follows exact declarations'() {
        given:
        createClass '''
            package attributed
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.layer3.Cluster
            import com.blackbuild.annodocimal.annotations.AnnoDoc
            @DSL @AnnoDoc('A configured application.') class Application {
                static { throw new AssertionError('Schema execution') }
                @AnnoDoc('The public host name.') String host
                @SlotMarker Slot slot
                @Cluster(value = SlotMarker, fixedKeys = true) Map<String, Slot> getSlots() {}
                @AnnoDoc('Changes the endpoint.')
                @Builder.Method void endpoint(Map<String, String> settings) { }
            }
            @interface SlotMarker {}
            @DSL class Slot { @Key String name; String value }
        '''
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('attributed.Application', output, [compilerConfiguration.targetDirectory])
        assert result.exit == 0: result.text
        def document = new JsonSlurper().parse(output)
        def definition = document.'$defs'.'attributed.Application_DSL$Builder'

        then:
        result.exit == 0
        definition.description == 'A configured application.'
        definition.get('properties').host.description == 'The public host name.'
        definition.get('properties').endpoint.description == 'Changes the endpoint.'
        definition.get('properties').slot == ['$ref': '#/$defs/attributed.Slot_DSL%24Builder']
        document.'$defs'.'attributed.Slot_DSL$Builder'.get('properties').keySet() == ['value'] as Set
        !output.text.contains('"const":')
        !output.text.contains('"required":')
    }

    def 'reads a closed named Schema module as bytes without exports opens or a runtime classpath'() {
        given:
        File artifacts = tempFolder.newFolder('modules')
        File api = compileJar(artifacts, 'api', [API])
        File schema = compileSchema(artifacts, api)
        File moduleSource = new File(artifacts, 'module-info.java')
        moduleSource.text = 'module example.closed.schema { }'
        File moduleClasses = new File(artifacts, 'module'); assert moduleClasses.mkdirs()
        assert ToolProvider.systemJavaCompiler.run(null, null, null, '--release', '17', '-d', moduleClasses.path, moduleSource.path) == 0
        File named = new File(artifacts, 'named-schema.jar')
        new JarOutputStream(new FileOutputStream(named)).withCloseable { archive ->
            new JarFile(schema).withCloseable { original ->
                original.entries().each { entry ->
                    archive.putNextEntry(new JarEntry(entry.name))
                    archive.write(original.getInputStream(entry).bytes)
                    archive.closeEntry()
                }
            }
            archive.putNextEntry(new JarEntry('module-info.class'))
            archive.write(new File(moduleClasses, 'module-info.class').bytes)
            archive.closeEntry()
        }
        def descriptor = ModuleFinder.of(named.toPath()).find('example.closed.schema').get().descriptor()
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('schematracer.schema.Application', output, [api, named])

        then:
        !descriptor.open
        descriptor.exports().empty
        descriptor.opens().empty
        result.exit == 0
        output.exists()
        System.getProperty('schema.tracer.executed') == null
    }

    def 'empty enum is diagnosed rather than producing an invalid Draft enum assertion'() {
        given:
        createClass '''
            package emptyenum
            import com.blackbuild.klum.ast.DSL
            @DSL class Configuration { Empty mode }
            enum Empty { }
        '''
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('emptyenum.Configuration', output, [compilerConfiguration.targetDirectory])

        then:
        result.exit != 0
        result.text.contains('Unprojectable empty enum emptyenum.Empty')
        !output.exists()
    }

    def 'relationship payload must agree with the selected public contract for #role'() {
        given:
        createClass '''
            package relationshipidentity
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                static { throw new AssertionError('Schema execution') }
                Expected child
                Map<String, Expected> children
            }
            @DSL class Expected { @Key String name; String expected }
            @DSL class Different { @Key String name; String different }
        '''
        File carrier = new File(compilerConfiguration.targetDirectory, 'relationshipidentity/Configuration_DSL$WirePlan.class')
        String changed = carrierData(carrier).readLines().collect { line ->
            String[] fields = line.split('\\|', -1)
            if (fields[0] == 'entry' && fields[2] == role) {
                fields[10] = fields[10].replace(token('relationshipidentity.Expected'), token('relationshipidentity.Different'))
                        .replace(token('relationshipidentity.Expected_DSL$Builder'), token('relationshipidentity.Different_DSL$Builder'))
            }
            fields.join('|')
        }.join('\n') + '\n'
        rewriteCarrier(carrier, changed)
        File output = new File(compilerConfiguration.targetDirectory, 'schema.json')

        when:
        def result = runTool('relationshipidentity.Configuration', output, [compilerConfiguration.targetDirectory])

        then:
        result.exit != 0
        result.text.contains('Relationship payload does not match selected public contract')
        result.text.contains('relationshipidentity.Configuration#')
        !result.text.contains('AssertionError: Schema execution')
        !output.exists()

        where:
        role << ['OWNED_OBJECT', 'KEYED_OWNED_MAP']
    }

    private static Map runTool(String root, File output, List<File> artifacts) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path, System.getProperty('schemaToolAgent'),
                '-cp', System.getProperty('schemaToolClasspath'), 'com.blackbuild.klum.ast.schema.JsonSchemaGenerator', root, output.path]
        command.addAll(artifacts*.path)
        def process = new ProcessBuilder(command).redirectErrorStream(true).start()
        String diagnostics = process.inputStream.text
        [exit: process.waitFor(), text: diagnostics]
    }

    private static String carrierData(File file) {
        String[] data = new String[1]
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (name == 'DATA') data[0] = (String) value
                null
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        data[0]
    }

    private static void rewriteCarrier(File file, String data) {
        def writer = new ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
                new ClassReader(file.bytes).className, null, 'java/lang/Object', null)
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                'DATA', 'Ljava/lang/String;', null, data).visitEnd()
        writer.visitEnd()
        file.bytes = writer.toByteArray()
    }

    private static String token(String value) { Base64.urlEncoder.withoutPadding().encodeToString(value.getBytes('UTF-8')) }

    private static String invalidCarrier(String data, String defect) {
        List<String> lines = data.readLines()
        String[] entry = lines[2].split('\\|', -1)
        switch (defect) {
            case 'header': return data.replace('WIRE-PLAN/B', 'UNKNOWN')
            case 'terminator': return data.trim()
            case 'old-record': return lines.take(2).join('\n') + '\n' + entry.take(11).join('|') + '\n'
            case 'identity': return data.replace(token('corrupt.Configuration'), token('other.Configuration'))
            case 'role': entry[2] = 'UNKNOWN'; break
            case 'source': entry[3] = token('other.Configuration#title'); break
            case 'descriptor': entry[6] = token('absent(java.lang.String):void'); break
            case 'name-origin': entry[7] = 'CAMEL'; break
            case 'fixed-key': entry[11] = token('child'); break
            case 'payload-signature': entry[10] = entry[10].replace(token('java.lang.String'), token('int')); break
            case 'unknown-scalar': entry[10] = entry[10].replace(token('java.lang.String'), token('java.net.URI')); break
            case 'noncanonical-token': entry[1] += '='; break
            case 'payload': entry[10] = '[MAP'; break
            case 'trailing-payload': entry[10] += 'x'; break
            case 'enum-type': entry[10] = entry[10].replace('SCALAR', 'ENUM'); break
        }
        lines[2] = entry.join('|')
        lines.join('\n') + '\n'
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
        def result = runTool('schematracer.schema.Application', output, [api, schema])
        assert result.exit == 0: result.text
        facts.text = result.text
        result.text
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
