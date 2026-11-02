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

import groovy.io.FileType
import groovy.json.JsonSlurper
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.Opcodes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.JarEntry
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

@Issue('957')
class BinaryOwnedRelationshipsTest extends Specification {
    @TempDir File directory
    File producer
    File consumer
    File repository
    File trap

    @Issue(['957', '954', '952'])
    def 'binary owned relationships support #capability with Groovy #generation'() {
        given:
        fixture(generation)

        when:
        def result = runner(consumer, capability == 'projection' ? 'generateKlumJsonSchema' : 'interpret').build()

        then:
        if (capability == 'projection') {
            def schema = new JsonSlurper().parse(new File(consumer, 'build/generated/klum-json-schema/example.Deployment.schema.json'))
            def properties = schema.'$defs'.'example.Deployment_DSL$Builder'.properties
            assert properties.elements.additionalProperties == false
            assert properties.elements.properties.keySet() == ['fixed'] as Set
            assert properties.elements.properties.fixed.additionalProperties.'$ref' == '#/$defs/example.ConcreteElement_DSL%24Builder%3A%3Akeyless'
            def element = schema.'$defs'.'example.ConcreteElement_DSL$Builder::keyless'
            assert element.properties.keySet() == ['value'] as Set
            assert element.additionalProperties == false
            assert !element.containsKey('required')
            assert properties.containsKey('facts')
            assert properties.facts.'$ref' == '#/$defs/example.Facts_DSL%24Builder'
            assert properties.inherited.'$ref' == properties.facts.'$ref'
            assert properties.workloads.additionalProperties.'$ref' == '#/$defs/example.Workload_DSL%24Builder%3A%3Akeyless'
            assert properties.inheritedWorkloads.additionalProperties.'$ref' == properties.workloads.additionalProperties.'$ref'
            def workload = schema.'$defs'.'example.Workload_DSL$Builder::keyless'
            assert workload.additionalProperties == false
            assert !workload.properties.containsKey('name')
            assert !workload.containsKey('required')
            assert properties.local.'$ref' == '#/$defs/example.LocalFacts_DSL%24Builder'
            assert properties.labels.additionalProperties.type == 'string'
            assert !properties.containsKey('ignored')
            assert !properties.containsKey('abstractFacts')
            assert !properties.containsKey('genericFacts')
            assert !properties.containsKey('consumeBuilder')
            assert !properties.containsKey('scoped')
            assert properties.virtual.'$ref' == properties.facts.'$ref'
            assert !properties.containsKey('mapped')
            assert !trap.exists()
            def retained = new File('build/schema-validation/virtual-scopes-' + generation + '.schema.json')
            retained.parentFile.mkdirs()
            retained.text = new File(consumer, 'build/generated/klum-json-schema/example.Deployment.schema.json').text
        } else {
            assert result.output.contains('BINARY-RELATIONSHIPS-OK')
            assert trap.exists()
        }
        !new File(producer, 'api/src').exists()
        !new File(producer, 'schema/src').exists()
        !new File(producer, 'client/src').exists()
        !new File(consumer, 'src').exists()
        repository.traverse(type: FileType.FILES) { File artifact ->
            assert !artifact.name.endsWith('-sources.jar')
            assert !artifact.name.endsWith('-javadoc.jar')
        }

        where:
        [generation, capability] << [['3', '4', '5'], ['projection', 'interpret']].combinations()
    }

    private static byte[] stripLiteralAssociation(String name, byte[] bytes) {
        if (name != 'example/Deployment_DSL$Builder.class') {
            return bytes
        }
        def writer = new ClassWriter(0)
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, methodName, descriptor, signature, exceptions)) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        annotation.endsWith('GeneratedInput$LiteralAlternative;') ? null : super.visitAnnotation(annotation, visible)
                    }
                }
            }
        }, 0)
        writer.toByteArray()
    }

    @Issue('952')
    def 'corrupt binary literal association prevents every new schema output without initializing user code'() {
        given:
        fixture('3')
        File artifact = new File(repository, 'fixture/schema/1.0/schema-1.0.jar')
        File changed = new File(directory, 'corrupted.jar')
        new JarFile(artifact).withCloseable { archive ->
            new JarOutputStream(new FileOutputStream(changed)).withCloseable { output ->
                archive.entries().each { entry ->
                    byte[] bytes = archive.getInputStream(entry).bytes
                    bytes = stripLiteralAssociation(entry.name, bytes)
                    output.putNextEntry(new JarEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        artifact.bytes = changed.bytes
        new File(consumer, 'build.gradle') << "\nklumSchema.jsonSchema.rootTypes.add('example.LocalFacts')\n"

        when:
        def result = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        result.output.contains('literal alternative completeness')
        !new File(consumer, 'build/generated/klum-json-schema/example.LocalFacts.schema.json').exists()
        !new File(consumer, 'build/generated/klum-json-schema/example.Deployment.schema.json').exists()
        !trap.exists()
    }

    private void fixture(String generation) {
        producer = new File(directory, 'producer')
        consumer = new File(directory, 'consumer')
        repository = new File(directory, 'repository')
        trap = new File(directory, 'initialized')
        producer.mkdirs()
        consumer.mkdirs()
        String groovy = notation(System.getProperty("gdslGroovy$generation"))
        String compiler = classpath(System.getProperty('gdslCompilerClasspath'))
        String runtime = classpath(System.getProperty('gdslRuntimeClasspath'))
        new File(producer, 'settings.gradle').text = "include 'api', 'schema', 'client'"
        new File(producer, 'build.gradle').text = """
subprojects {
    apply plugin: 'groovy'
    repositories { maven { url = '${repository.toURI()}' } }
    dependencies { implementation files($groovy, $compiler) }
    tasks.withType(GroovyCompile).configureEach { groovyClasspath = files($groovy) }
}
project(':schema') { dependencies { implementation 'fixture:api:1.0' } }
project(':client') { dependencies { implementation 'fixture:schema:1.0' } }
"""
        writeSource('api', 'Contracts.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.fasterxml.jackson.annotation.*
class Trace { static final List<String> events = [] }
@DSL class Facts {
    static { new File('${trap.absolutePath}').text = 'initialized' }
    @JsonProperty('host-name') String host
    @Owner Object owner
    @PostCreate void created() { Trace.events.add('facts:create') }
    @PostApply void applied() { Trace.events.add('facts:apply') }
    @PostTree void tree() { Trace.events.add('facts:tree') }
}
@DSL class Workload {
    @Key String name
    String image
    @Owner Object owner
    @PostCreate void created() { Trace.events.add('workload:create') }
    @PostApply void applied() { Trace.events.add('workload:apply') }
    @PostTree void tree() { Trace.events.add('workload:tree') }
}
@DSL class GenericFacts<T> { T value }
@DSL abstract class AbstractFacts { String host }
@DSL abstract class DeploymentApi { Facts apiLocal }
""")
        writeSource('api', 'JsonProperty.groovy', '''
package com.fasterxml.jackson.annotation
import java.lang.annotation.*
@Retention(RetentionPolicy.RUNTIME) @Target([ElementType.METHOD, ElementType.FIELD])
@interface JsonProperty { String value() default '' }
''')
        writeSource('api', 'JsonIgnore.groovy', '''
package com.fasterxml.jackson.annotation
import java.lang.annotation.*
@Retention(RetentionPolicy.RUNTIME) @Target([ElementType.METHOD, ElementType.FIELD])
@interface JsonIgnore { boolean value() default true }
''')
        runner(producer, ':api:jar').build()
        publish('api', '')
        new File(producer, 'api').deleteDir()

        writeSource('schema', 'Deployment.groovy', '''
package example
import com.blackbuild.klum.ast.*
import com.fasterxml.jackson.annotation.JsonIgnore
@DSL class DeploymentBase extends DeploymentApi {
    Facts inherited
    Map<String, Workload> inheritedWorkloads
}
@DSL class Deployment extends DeploymentBase {
    Facts facts
    @Field(alternatives = { [fixed: ConcreteElement] }) Map<String, Element> elements
    Map<String, Workload> workloads
    LocalFacts local
    Map<String, String> labels
    @JsonIgnore Facts ignored
    AbstractFacts abstractFacts
    GenericFacts<String> genericFacts
    @Builder.Method void consumeBuilder(Facts_DSL.Builder value) {}
    @Builder.Method void scoped(@DelegatesToBuilder(Facts) Closure body) { facts(body) }
    @Field void virtual(Facts value) {}
    @Field(keyMapping = { it.name }) Map<String, Workload> mapped
}
@DSL class LocalFacts { String host }
@DSL abstract class Element {
    @Key String name
    @Owner Object owner
    String value
}
@DSL class ConcreteElement extends Element {}
''')
        runner(producer, ':schema:jar').build()
        publish('schema', '<dependencies><dependency><groupId>fixture</groupId><artifactId>api</artifactId><version>1.0</version></dependency></dependencies>')
        new File(producer, 'schema').deleteDir()

        writeSource('client', 'Consumer.groovy', '''
package example
import com.blackbuild.klum.ast.runtime.KlumModelException
import java.lang.reflect.ParameterizedType
class Consumer {
    static void main(String[] args) {
        // Binary relationship getters erase the generated self argument; local getters retain it.
        def direct = Deployment_DSL.Builder.getMethod('getFacts').genericReturnType
        assert direct instanceof Class && direct.name == 'example.Facts_DSL$Builder'
        assert Deployment_DSL.Builder.getMethod('getLocal').genericReturnType instanceof ParameterizedType
        assert Deployment_DSL.Builder.getMethod('getInherited').genericReturnType == direct
        assert Deployment_DSL.Builder.getMethod('getWorkloads').genericReturnType.actualTypeArguments[1] == Workload_DSL.Builder
        assert GenericFacts_DSL.Builder.typeParameters.length == 2
        def input = [facts: ['host-name': 'direct'], inherited: ['host-name': 'base'],
                     workloads: [('a/b~c'): [image: 'one']], inheritedWorkloads: [other: [image: 'two']],
                     elements: [fixed: [primary: [value: 'configured']]], local: [host: 'local'], labels: [('host-name'): 'literal'], virtual: ['host-name': 'virtual']]
        Trace.events.clear()
        def nativeModel = Deployment.Create.With {
            facts { host 'direct' }
            inherited { host 'base' }
            workloads { workload('a/b~c') { image 'one' } }
            inheritedWorkloads { inheritedWorkload('other') { image 'two' } }
            elements { fixed('primary') { value 'configured' } }
            local { host 'local' }
            labels(['host-name': 'literal'])
            virtual { host 'virtual' }
        }
        def nativeTrace = Trace.events.toList()
        Trace.events.clear()
        def model = Deployment.Create.FromStruct(input, 'binary.yaml')
        assert model.facts.host == nativeModel.facts.host
        assert model.inherited.host == nativeModel.inherited.host
        assert model.workloads['a/b~c'].name == 'a/b~c'
        assert model.workloads['a/b~c'].image == 'one'
        assert model.inheritedWorkloads.other.image == 'two'
        assert model.facts.owner.is(model)
        assert model.inherited.owner.is(model)
        assert model.workloads['a/b~c'].owner.is(model)
        assert model.inheritedWorkloads.other.owner.is(model)
        assert model.elements.primary instanceof ConcreteElement
        assert model.elements.primary.name == 'primary'
        assert model.elements.primary.value == 'configured'
        assert model.elements.primary.owner.is(model)
        assert model.local.host == 'local'
        assert model.labels == ['host-name': 'literal']
        assert Trace.events == nativeTrace
        Trace.events.clear()
        def applied = Deployment.Create.With {
            delegate.applyStruct(input, 'active.yaml')
        }
        assert applied.facts.host == 'direct'
        assert applied.inheritedWorkloads.other.owner.is(applied)
        assert Trace.events == nativeTrace
        Trace.events.clear()
        try {
            Deployment.Create.FromStruct([facts: ['host-name': 'earlier'], workloads: [('a/b~c'): [image: 42]],
                     inherited: ['host-name': 'never']], 'invalid.yaml')
            assert false: 'Invalid nested input must reject'
        } catch (KlumModelException failure) {
            assert failure.message.contains('invalid.yaml')
            assert failure.message.contains('/workloads/a~1b~0c/image')
        }
        assert Trace.events == ['facts:create', 'facts:apply', 'workload:create']
        Trace.events.clear()
        for (String rejected : ['abstractFacts', 'genericFacts', 'consumeBuilder', 'mapped', 'ignored', 'scoped']) {
            try {
                Deployment.Create.FromStruct([(rejected): [:]], 'rejected.yaml')
                assert false: "Must reject $rejected"
            } catch (KlumModelException failure) {
                assert failure.message.contains(rejected)
            }
        }
        assert Trace.events.empty
        println 'BINARY-RELATIONSHIPS-OK'
    }
}
''')
        runner(producer, ':client:jar').build()
        publish('client', '')
        new File(producer, 'client').deleteDir()
        new File(consumer, 'settings.gradle').text = "rootProject.name = 'binary-consumer'"
        new File(consumer, 'build.gradle').text = """
plugins { id 'com.blackbuild.klum-ast-schema' }
['api', 'implementation', 'compileOnly', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
repositories { maven { url = '${repository.toURI()}' } }
configurations { schemaBinary; clientBinary }
dependencies {
    schemaBinary 'fixture:schema:1.0'
    clientBinary 'fixture:client:1.0'
}
klumSchema.jsonSchema {
    rootTypes.add('example.Deployment')
    classpath.setFrom(configurations.schemaBinary, files($runtime, $groovy))
}
tasks.register('interpret', JavaExec) {
    classpath = files(configurations.clientBinary, klumSchema.jsonSchema.classpath)
    mainClass = 'example.Consumer'
}
"""
    }

    private void writeSource(String module, String name, String text) {
        File source = new File(producer, "$module/src/main/groovy/$name")
        source.parentFile.mkdirs()
        source.text = text
    }

    private void publish(String artifact, String dependencies) {
        File location = new File(repository, "fixture/$artifact/1.0")
        location.mkdirs()
        new File(location, "$artifact-1.0.jar").bytes = new File(producer, "$artifact/build/libs/$artifact" + '.jar').bytes
        new File(location, "$artifact-1.0.pom").text = """<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>$artifact</artifactId><version>1.0</version>$dependencies</project>"""
    }

    private static String notation(String path) { "'" + path.replace('\\', '\\\\').replace("'", "\\'") + "'" }
    private static String classpath(String value) { value.split(File.pathSeparator).collect { notation(it) }.join(', ') }
    private GradleRunner runner(File project, String... arguments) {
        GradleRunner.create().withProjectDir(project).withPluginClasspath().withDebug(true)
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain', '--max-workers=2', '--offline'])
    }
}
