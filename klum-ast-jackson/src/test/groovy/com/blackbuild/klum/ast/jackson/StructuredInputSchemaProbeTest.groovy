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

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.KlumGenerated
import com.blackbuild.klum.ast.layer3.DefaultValues
import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.CompileStatic
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.FieldVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Issue

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.annotation.Annotation

/** Disposable projection for an explicit fixture admission list, not a production generator. */
@Issue(['529', '932'])
class StructuredInputSchemaProbeTest extends AbstractDSLSpec {

    private static final String API_SOURCE = '''
        package inputprobe.api
        import com.blackbuild.klum.ast.*
        import com.blackbuild.klum.ast.layer3.Cluster
        import com.blackbuild.klum.ast.layer3.DefaultValues
        import java.lang.annotation.Retention
        import java.lang.annotation.Target
        import static java.lang.annotation.RetentionPolicy.RUNTIME
        import static java.lang.annotation.ElementType.TYPE

        @DefaultValues @Retention(RUNTIME) @Target(TYPE)
        @interface ProbeDefaults { String region() default 'literal-default' }

        @DSL
        abstract class ApiRoot {
            String region
            @Cluster
            abstract Map<String, ApiChild> getComponents()

            @Builder.Method
            void inheritedLocale(String locale) { region = locale }
        }

        @DSL
        abstract class ApiChild {
            static { System.setProperty('schema.probe.executed', 'abstract-static-initializer') }
            @Key String name
            String value
        }

        @DSL class ProbeChild {
            static { System.setProperty('schema.probe.executed', 'dependency-static-initializer') }
            String apiValue
        }
    '''

    private static final String SCHEMA_SOURCE = '''
        package inputprobe.schema
        import com.blackbuild.klum.ast.*
        import com.blackbuild.klum.ast.runtime.KlumFactory
        import com.fasterxml.jackson.annotation.JsonProperty
        import com.fasterxml.jackson.annotation.JsonIgnore
        import inputprobe.api.ApiRoot
        import inputprobe.api.ApiChild
        import inputprobe.api.ProbeChild as ExternalChild
        import inputprobe.api.ProbeDefaults

        enum Mode {
            SAFE, FAST;
            static { System.setProperty('schema.probe.executed', 'enum-static-initializer') }
        }

        @DSL @ProbeDefaults
        class SchemaRoot extends ApiRoot {
            static { mark('static-initializer') }
            @JsonProperty('service-name') String serviceName
            @JsonProperty('class') String category
            @JsonIgnore String internalSetting
            @Validate({ SchemaRoot.valid(it) }) int port
            boolean enabled
            @Required String requiredValue = 'provided'
            Mode mode
            Map<String, String> labels
            ProbeChild child
            ExternalChild externalChild
            KeyedChild primary
            @Field(members = 'entry') Map<String, KeyedChild> entries
            @Field(members = 'item') List<ProbeChild> items
            ApiChild unsupportedAbstract
            String initialized = mark('instance-initializer')
            @Default(code = { mark('default-closure') }) String computed
            @Field(converters = [{ String text -> mark('converter'); new URI(text) }]) URI source

            static String mark(String point) {
                System.setProperty('schema.probe.executed', point)
                point
            }
            static boolean valid(int value) { mark('validation-closure'); value >= 0 }

            /** Configure the remote endpoint.
             * @param host the remote host
             * @param port the remote port
             */
            @Builder.Method
            void endpoint(String host, int port = 8080) {
                serviceName = host
                this.port = port
            }

            @Builder.Method void shout(String value) { serviceName = value.toUpperCase() }
            @Builder.Method void mapping(Map<String, String> value) { labels = value }
            @Builder.Method void sequence(List<String> value) { serviceName = value.join(',') }

            @PostCreate void created() { mark('post-create') }
            @PostApply void applied() { mark('post-apply') }
            @Validate void checked() { mark('validation') }

            static class Factory extends KlumFactory.Unkeyed<SchemaRoot> {
                protected Factory() { super(SchemaRoot); mark('factory-constructor') }
                SchemaRoot fromContext(String context) { mark('factory'); With(region: context) }
            }
        }

        @DSL class ProbeChild { String value }
        @DSL class KeyedChild extends ApiChild { }
    '''

    def 'compiled API Schema and API-only consumer expose deterministic concrete schema without executing Schema code'() {
        given:
        File artifacts = tempFolder.newFolder('binary-artifacts')
        File api = compileArtifact(artifacts, 'api', API_SOURCE)
        File schema = compileArtifact(artifacts, 'schema', SCHEMA_SOURCE, [api])
        File consumer = compileArtifact(artifacts, 'api-client', '''
            package inputprobe.client
            import groovy.transform.CompileStatic
            import inputprobe.api.ApiRoot
            @CompileStatic
            class ApiClient {
                static List<String> values(ApiRoot root) {
                    root.components.values().collect { it.value }
                }
            }
        ''', [api])
        File output = new File(compilerConfiguration.targetDirectory, 'schema-evidence.json')
        File repeatOutput = new File(output.parentFile, 'schema-repeat.json')

        when:
        Map result = extractInSeparateProcess(api, schema, output)
        Map repeated = extractInSeparateProcess(api, schema, repeatOutput)

        then:
        consumer.exists()
        result == repeated
        output.bytes == repeatOutput.bytes
        !result.executed
        result.schema.'$schema' == 'https://json-schema.org/draft/2020-12/schema'
        result.schema.'$defs'.keySet() == [
                'inputprobe.api.ProbeChild_DSL$Builder',
                'inputprobe.schema.KeyedChild_DSL$Builder',
                'inputprobe.schema.ProbeChild_DSL$Builder',
                'inputprobe.schema.SchemaRoot_DSL$Builder'] as Set
        result.schema.'$defs'.values().every { !it.containsKey('required') && !it.containsKey('default') }
        result.schema.'$defs'.'inputprobe.schema.SchemaRoot_DSL$Builder'.properties.keySet() == [
                'child', 'class', 'enabled', 'endpoint', 'entries', 'externalChild', 'inheritedLocale', 'items', 'labels',
                'mapping', 'mode', 'port', 'region', 'requiredValue', 'sequence', 'service-name', 'shout'] as Set
        !result.schema.'$defs'.keySet().any { it.contains('ApiChild') }
        result.schema.'$defs'.'inputprobe.schema.SchemaRoot_DSL$Builder'.properties.externalChild.'$ref' ==
                '#/$defs/inputprobe.api.ProbeChild_DSL%24Builder'
        result.unsupportedAbstract == 'abstract target inputprobe.api.ApiChild: no admitted concrete mapping'
        result.facts.inheritedRegionOwner == 'inputprobe.api.ApiRoot_DSL$Builder'
        result.facts.clusterMethods
        result.facts.factoryMethods.any { it.contains('fromContext') }
        result.facts.endpoint*.parameterNames == [['host', 'port']]
        result.facts.endpoint*.builderMethod == [true]
        result.facts.endpoint*.parameterNamesPresent == [[true, true]]
        result.facts.keyedValueSignature.contains('inputprobe.schema.KeyedChild_DSL$Builder')
        result.facts.labelsSignature.contains('java.lang.String')
        result.facts.externalNameLocations.serviceName == [field: 'service-name', operation: null]
        result.facts.ignoredLocations == [field: true, operation: false]
        result.facts.defaultOverloadExposed == false
        result.facts.concreteRootFactory
        result.facts.endpointDocumentation.contains('Configure the remote endpoint.')
        result.facts.defaultValues == [annotation: 'inputprobe.api.ProbeDefaults', literalRegion: 'literal-default', retainedProvider: true]

        when: 'only the separately compiled API dependency gains an inherited scalar property'
        File changedApi = compileArtifact(artifacts, 'api-v2', API_SOURCE.replace('String region\n', 'String region\n            String zone\n'))
        Map changed = extractInSeparateProcess(changedApi, schema, new File(output.parentFile, 'schema-changed.json'))

        then:
        changed.schema != result.schema
        changed.schema.'$defs'.'inputprobe.schema.SchemaRoot_DSL$Builder'.properties.zone == [type: 'string']
        !changed.executed

        when: 'a Schema dependency was compiled without optional Java parameter-name attributes'
        File unnamed = compileArtifact(artifacts, 'schema-no-parameters', SCHEMA_SOURCE, [api], false)
        Map unnamedEvidence = extractInSeparateProcess(api, unnamed, new File(output.parentFile, 'schema-no-parameters.json'))

        then:
        unnamedEvidence.schema == result.schema
        unnamedEvidence.facts.endpoint*.parameterNamesPresent == [[false, false]]
        unnamedEvidence.facts.endpoint*.parameterNames == [['arg0', 'arg1']]
        !unnamedEvidence.executed

        when: 'a consumer copies the generated artifact verbatim'
        File packaged = new File(artifacts, 'consumer-copy/schema.json')
        packaged.parentFile.mkdirs()
        packaged.bytes = output.bytes

        then:
        packaged.bytes == output.bytes

        when: 'the same binary Schema deliberately executes admitted operations after metadata extraction has finished'
        URLClassLoader runtime = new URLClassLoader([api.toURI().toURL(), schema.toURI().toURL()] as URL[], getClass().classLoader)
        Class binaryRoot = runtime.loadClass('inputprobe.schema.SchemaRoot')
        def completed = binaryRoot.Create.With {
            StructuredInputProbeFixture.operations(delegate, [
                    ['service-name', 'x'], ['mode', 'SAFE'], ['enabled', false], ['port', 0],
                    ['labels', [team: 'core']], ['child', [value: 'nested']],
                    ['entries', [one: [value: 'keyed']]], ['items', [[value: 'listed']]],
                    ['endpoint', ['api', 9090]], ['shout', 'hello'], ['externalChild', [apiValue: 'dependency']],
                    ['inheritedLocale', 'eu']])
        }

        then:
        completed.serviceName == 'HELLO'
        completed.port == 9090
        completed.mode.name() == 'SAFE'
        completed.child.value == 'nested'
        completed.entries.one.value == 'keyed'
        completed.items*.value == ['listed']
        completed.externalChild.apiValue == 'dependency'
        completed.region == 'eu'
        System.getProperty('schema.probe.executed') != null

        cleanup:
        runtime?.close()
        System.clearProperty('schema.probe.executed')
    }

    def 'one overlapping projected operation object configures the shared runtime fixture'() {
        given:
        createNonDslClass(StructuredInputProbeFixture.SOURCE)
        Class root = getClass('inputprobe.ProbeRoot')
        Map calls = ['service-name': 'x', mode: 'SAFE', enabled: false, port: 0, labels: [team: 'core'],
                     child: [value: 'nested'], entries: [one: [value: 'keyed']], items: [[value: 'listed']],
                     endpoint: ['api', 9090], shout: 'hello']

        when:
        def result = root.Create.With {
            StructuredInputProbeFixture.operations(delegate, calls.collect { key, value -> [key, value] })
        }

        then:
        result.serviceName == 'HELLO'
        result.mode.name() == 'SAFE'
        result.port == 9090
        !result.enabled
        result.labels == [team: 'core']
        result.child.value == 'nested'
        result.entries.one.value == 'keyed'
        result.items*.value == ['listed']
        result.requiredValue == 'provided'
    }

    private File compileArtifact(File root, String name, String source, List<File> dependencies = [], boolean parameters = true) {
        File classes = new File(root, name)
        assert classes.mkdirs()
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = classes
        configuration.parameters = parameters
        configuration.warningLevel = 0
        configuration.optimizationOptions.groovydoc = true
        GroovyClassLoader compiler = new GroovyClassLoader(getClass().classLoader, configuration)
        dependencies.each { compiler.addClasspath(it.path) }
        try {
            compiler.parseClass(source, name.replace('-', '_') + '.groovy')
        } finally {
            compiler.close()
        }
        assert classes.listFiles()
        assert !classes.listFiles().any { it.name.endsWith('.groovy') || it.name.endsWith('.java') }
        classes
    }

    private Map extractInSeparateProcess(File api, File schema, File output) {
        Set<String> classpath = new LinkedHashSet<>()
        classpath.addAll(System.getProperty('java.class.path').split(File.pathSeparator).toList())
        classpath.add(new File(BinaryExtraction.protectionDomain.codeSource.location.toURI()).path)
        ClassLoader current = getClass().classLoader
        while (current != null) {
            if (current instanceof URLClassLoader)
                current.URLs.findAll { it.protocol == 'file' }.each { classpath.add(new File(it.toURI()).path) }
            current = current.parent
        }
        output.parentFile.mkdirs()
        String java = new File(System.getProperty('java.home'), 'bin/java').path
        Process process = new ProcessBuilder(java, '-cp', classpath.join(File.pathSeparator),
                BinaryExtraction.name, api.path, schema.path, output.path).redirectErrorStream(true).start()
        String diagnostics = process.inputStream.text
        assert process.waitFor() == 0: diagnostics
        new ObjectMapper().readValue(output, Map)
    }

    /** Runs in its own JVM; its loader receives class directories, never Schema sources or mirrors. */
    @CompileStatic
    static class BinaryExtraction {
        static void main(String[] args) {
            assert System.getProperty('schema.probe.executed') == null
            URLClassLoader binaries = new URLClassLoader([new File(args[0]).toURI().toURL(),
                    new File(args[1]).toURI().toURL()] as URL[], BinaryExtraction.classLoader)
            try {
                Map result = evidence(binaries)
                assert System.getProperty('schema.probe.executed') == null
                result.executed = false
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(new File(args[2]), result)
            } finally {
                binaries.close()
            }
        }

        private static Map evidence(ClassLoader binaries) {
            Class root = Class.forName('inputprobe.schema.SchemaRoot', false, binaries)
            Class builder = Class.forName(root.name + '_DSL$Builder', false, binaries)
            Class factory = Class.forName(root.name + '_DSL$Factory', false, binaries)
            Class keyed = Class.forName('inputprobe.schema.KeyedChild', false, binaries)
            Class child = Class.forName('inputprobe.schema.ProbeChild', false, binaries)
            Class externalChild = Class.forName('inputprobe.api.ProbeChild', false, binaries)
            Class mode = Class.forName('inputprobe.schema.Mode', false, binaries)
            Class abstractType = Class.forName('inputprobe.api.ApiChild', false, binaries)
            Class<? extends Annotation> defaultAnnotation = (Class<? extends Annotation>) Class.forName('inputprobe.api.ProbeDefaults', false, binaries)
            Annotation defaults = root.getAnnotation(defaultAnnotation)
            Map definitions = new TreeMap()
            definitions[externalChild.name + '_DSL$Builder'] = definition(binaries, externalChild, ['apiValue'])
            definitions[keyed.name + '_DSL$Builder'] = definition(binaries, keyed, ['value'])
            definitions[child.name + '_DSL$Builder'] = definition(binaries, child, ['value'])
            definitions[builder.name] = definition(binaries, root,
                    ['region', 'serviceName', 'category', 'internalSetting', 'port', 'enabled', 'requiredValue', 'labels'])
            Map properties = (Map) ((Map) definitions.get(builder.name)).get('properties')
            if (builder.methods.any { it.name == 'zone' && it.parameterTypes.toList() == [String] })
                properties.zone = scalar(builder.getMethod('zone', String).genericParameterTypes[0])
            properties.mode = [type: 'string', enum: enumNames(binaries, mode)]
            assert builder.getMethod('mode', mode)
            ['shout', 'inheritedLocale'].each { String name ->
                Method operation = builder.getMethod(name, String)
                assert operation.isAnnotationPresent(Builder.Method)
                properties[name] = scalar(operation.genericParameterTypes[0])
            }
            Method mapping = builder.getMethod('mapping', Map)
            Method sequence = builder.getMethod('sequence', List)
            Method endpoint = builder.getMethod('endpoint', String, Integer.TYPE)
            assert [mapping, sequence, endpoint].every { it.isAnnotationPresent(Builder.Method) }
            properties.mapping = scalar(mapping.genericParameterTypes[0])
            assert ((ParameterizedType) sequence.genericParameterTypes[0]).actualTypeArguments.toList() == [String]
            properties.sequence = [type: 'array', items: [type: 'string']]
            // Candidate positional-pair syntax only; the maintainer still chooses the language.
            properties.endpoint = [type: 'array', prefixItems: endpoint.genericParameterTypes.collect { scalar(it) },
                                   minItems: 2, maxItems: 2, description: endpoint.getAnnotation(AnnoDoc)?.value()]
            // Select only fixed concrete creators. Their Map values-bag/Class/Factory arguments are never projected.
            requireCreator(builder, 'child', child.name + '_DSL$Builder')
            requireCreator(builder, 'externalChild', externalChild.name + '_DSL$Builder')
            requireCreator(builder, 'entry', keyed.name + '_DSL$Builder')
            requireCreator(builder, 'item', child.name + '_DSL$Builder')
            properties.child = reference(child)
            properties.externalChild = reference(externalChild)
            properties.entries = [type: 'object', additionalProperties: reference(keyed)]
            properties.items = [type: 'array', items: reference(child)]
            String unsupported
            try {
                requireConcrete(abstractType)
                throw new AssertionError('abstract type unexpectedly admitted')
            } catch (IllegalArgumentException failure) {
                unsupported = failure.message
            }
            List<Method> endpoints = builder.methods.findAll { it.name == 'endpoint' }
            Method service = builder.getMethod('serviceName', String)
            Method ignored = builder.getMethod('internalSetting', String)
            Map facts = [
                    inheritedRegionOwner: builder.getMethod('region', String).declaringClass.name,
                    clusterMethods: builder.methods.findAll { it.name == 'components' }*.toGenericString().sort(),
                    factoryMethods: factory.methods.findAll { it.name == 'fromContext' }*.toGenericString().sort(),
                    concreteRootFactory: factory.methods.any { it.name == 'With' },
                    endpoint: endpoints.sort { it.parameterCount }.collect { Method method ->
                        [signature: method.toGenericString(), parameterNames: method.parameters*.name,
                         parameterNamesPresent: method.parameters*.namePresent,
                         builderMethod: method.isAnnotationPresent(Builder.Method)]
                    },
                    defaultOverloadExposed: endpoints.any { it.parameterCount == 1 },
                    labelsSignature: builder.getMethod('labels', Map).toGenericString(),
                    keyedValueSignature: builder.getMethod('getEntries').toGenericString(),
                    externalNameLocations: [serviceName: [field: root.getDeclaredField('serviceName').getAnnotation(JsonProperty)?.value(),
                                                          operation: service.getAnnotation(JsonProperty)?.value()]],
                    ignoredLocations: [field: root.getDeclaredField('internalSetting').isAnnotationPresent(JsonIgnore),
                                       operation: ignored.isAnnotationPresent(JsonIgnore)],
                    generatedTags: builder.getAnnotation(KlumGenerated)?.tags()?.toList(),
                    inheritedOperation: builder.getMethod('inheritedLocale', String).isAnnotationPresent(Builder.Method)
            ]
            facts.endpointDocumentation = endpoint.getAnnotation(AnnoDoc)?.value()
            facts.defaultValues = [annotation: defaultAnnotation.name,
                                  literalRegion: defaultAnnotation.getMethod('region').invoke(defaults),
                                  retainedProvider: defaultAnnotation.isAnnotationPresent(DefaultValues)]
            [schema: ['$schema': 'https://json-schema.org/draft/2020-12/schema',
                      '$ref': pointer(builder.name), '$defs': definitions], facts: facts, unsupportedAbstract: unsupported]
        }

        private static Map definition(ClassLoader binaries, Class model, List<String> admitted) {
            requireConcrete(model)
            Class contract = Class.forName(model.name + '_DSL$Builder', false, binaries)
            Map properties = new TreeMap()
            admitted.each { String name ->
                Method operation = contract.methods.find { it.name == name && it.parameterCount == 1 &&
                        it.parameterTypes[0] in [String, Integer.TYPE, Boolean.TYPE, Map] }
                assert operation: "No admitted operation $name on ${contract.name}"
                // Existing retained field annotations supplement public operation signatures only for naming/ignore.
                def declaration = model
                while (declaration != null && !declaration.declaredFields.any { it.name == name })
                    declaration = declaration.superclass
                assert declaration
                def field = declaration.getDeclaredField(name)
                if (!field.isAnnotationPresent(JsonIgnore)) {
                    String external = field.getAnnotation(JsonProperty)?.value() ?: name
                    assert !properties.containsKey(external): "external name collision: $external"
                    properties[external] = scalar(operation.genericParameterTypes[0])
                }
            }
            [type: 'object', properties: properties, additionalProperties: false]
        }

        private static Map scalar(Type type) {
            if (type == String) return [type: 'string']
            if (type == Integer.TYPE) return [type: 'integer']
            if (type == Boolean.TYPE) return [type: 'boolean']
            if (type instanceof ParameterizedType && type.rawType == Map && type.actualTypeArguments.toList() == [String, String])
                return [type: 'object', additionalProperties: [type: 'string']]
            throw new IllegalArgumentException("not in measured subset: $type")
        }

        private static Method requireCreator(Class builder, String operation, String result) {
            Method method = builder.methods.find { it.name == operation && it.returnType.name == result &&
                    !it.parameterTypes.any { it == Class || it.simpleName.endsWith('Factory') || it.name.contains('KlumFactory') } }
            assert method: "No fixed-target creator $operation returning $result"
            method
        }

        private static void requireConcrete(Class type) {
            if (Modifier.isAbstract(type.modifiers))
                throw new IllegalArgumentException("abstract target ${type.name}: no admitted concrete mapping")
        }

        private static Map reference(Class model) { ['$ref': pointer(model.name + '_DSL$Builder')] }

        private static String pointer(String identity) {
            '#/$defs/' + identity.replace('~', '~0').replace('/', '~1').replace('$', '%24')
        }

        private static List<String> enumNames(ClassLoader binaries, Class type) {
            List<String> names = []
            binaries.getResourceAsStream(type.name.replace('.', '/') + '.class').withCloseable { stream ->
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        if ((access & Opcodes.ACC_ENUM) != 0) names.add(name)
                        null
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
            }
            names
        }
    }
}
