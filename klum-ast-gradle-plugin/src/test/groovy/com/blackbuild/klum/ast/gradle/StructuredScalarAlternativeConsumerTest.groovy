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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** JAR-only process isolation follows RootRecipeConsumerTest and GeneratedSchemaProvenanceConsumerTest. */
@Issue('1129')
class StructuredScalarAlternativeConsumerTest extends Specification {
    private static final String FIXTURES = '/scalar-alternative-artifacts/'
    private static final String BUILDER = 'fixture/parent/Destination_DSL$Builder.class'
    @TempDir File directory
    private final List<Map> commands = []

    def 'matched and historical JARs serve independent clients and projection with Groovy #generation'() {
        given:
        Path root = directory.toPath()
        List<Path> compiler = jars('gdslCompilerClasspath') + [groovy(generation), validationApi()]
        List<Path> runtime = jars('gdslRuntimeClasspath') + [groovy(generation), validationApi()]
        boolean named = generation != '3'
        Path parent = artifact(root, 'parent', compiler, named, ['Parent.groovy'],
                'module fixture.parent { requires com.blackbuild.klum.ast.runtime; requires jakarta.validation; exports fixture.parent; opens fixture.parent to com.blackbuild.klum.ast.runtime, org.apache.groovy; }')
        Path child = artifact(root, 'child', compiler + [parent], named, ['Child.groovy'],
                'module fixture.child { requires com.blackbuild.klum.ast.runtime; requires jakarta.validation; requires fixture.parent; exports fixture.child; opens fixture.child to com.blackbuild.klum.ast.runtime, org.apache.groovy; }')
        Path historical = root.resolve('historical.jar')
        copyFixture('historical-g' + generation + '.jar', historical)
        assert sha256(historical) == historicalHashes()[generation]
        // The old parent is kept separately: it participates only in the mixed-hierarchy rejection control.
        Path oldParent = root.resolve('historical-parent.jar')
        copyFixture('historical-parent-g' + generation + '.jar', oldParent)
        List<Path> clients = runtime + [parent, child, historical]
        Path consumer = artifact(root, 'consumer', clients, named,
                ['StaticClient.groovy', 'DynamicClient.groovy', 'Main.java', 'ArtifactResourceControl.java'],
                'module fixture.consumer { requires com.blackbuild.klum.ast.runtime; requires fixture.parent; requires fixture.child; requires historical; opens fixture.consumer to org.apache.groovy; }')
        assert clients.every { it.toString().endsWith('.jar') }
        assert clients.intersect(jars('gdslCompilerClasspath') - jars('gdslRuntimeClasspath')).empty
        assert !clients.any { it.fileName.toString().contains('gradle-plugin') }

        when:
        String classpathResult = launch(clients + [consumer], false)
        String moduleResult = named ? launch(clients + [consumer], true) : 'classpath-only'
        File project = projectionProject(root, clients, ['fixture.parent.Destination', 'fixture.child.Child',
                'fixture.historical.Selected', 'fixture.historical.IntegerOnly', 'fixture.historical.Unmarked', 'fixture.historical.Converted'])
        projectSchemas(project)
        Path output = project.toPath().resolve('build/generated/klum-json-schema')
        def documents = ['fixture.parent.Destination', 'fixture.child.Child', 'fixture.historical.Selected',
                'fixture.historical.IntegerOnly', 'fixture.historical.Unmarked', 'fixture.historical.Converted'].collectEntries { name ->
            [(name): new JsonSlurper().parse(output.resolve(name + '.schema.json').toFile())]
        }

        then:
        classpathResult.trim() == 'scalar-artifact-consumers=true'
        moduleResult.trim() == (named ? 'scalar-artifact-consumers=true' : 'classpath-only')
        fields(documents['fixture.parent.Destination']).address.anyOf*.type == ['integer', 'string']
        fields(documents['fixture.parent.Destination']).address.anyOf[0].allOf == [[minimum: 1]]
        fields(documents['fixture.child.Child']).address.anyOf[0].allOf == [[maximum: 99]]
        fields(documents['fixture.child.Child']).address.anyOf[1] == fields(documents['fixture.parent.Destination']).address.anyOf[1]
        fields(documents['fixture.historical.Selected']).address == [type: 'string']
        fields(documents['fixture.historical.IntegerOnly']).address.type == 'integer'
        !fields(documents['fixture.historical.Unmarked']).containsKey('address')
        fields(documents['fixture.historical.Converted']).address.type == 'string'

        when: 'every corrupt/missing hierarchy rejects before absent payload use or output replacement'
        Map<String, List<Path>> controls = [
                'missing-parent': runtime + [child, historical],
                'historical-parent': runtime + [oldParent, child, historical]
        ]
        ['schema', 'operation', 'marker', 'count', 'parameter'].each { kind ->
            controls[kind] = runtime + [mutatedParent(root, parent, kind), child, historical]
        }
        byte[] previous = Files.readAllBytes(output.resolve('fixture.child.Child.schema.json'))
        byte[] manifest = Files.readAllBytes(output.resolve('.klum-json-schema-outputs'))
        Map<String, String> diagnostics = [
                'missing-parent': 'fixture.parent.Destination',
                'historical-parent': 'Incomplete constraint facts',
                schema: 'Missing generated input enrichment',
                operation: 'Missing or inconsistent operation selection completeness',
                marker: 'Author marker disagrees with authoritative selection fact',
                count: 'Missing or inconsistent operation selection completeness',
                parameter: 'Incomplete constraint facts'
        ]
        controls.each { kind, inputs ->
            String rejection = launch(inputs + [consumer], false, true)
            assert !rejection.contains('corrupt contract accepted'): kind
            assert rejection.contains(diagnostics[kind]): rejection
            if (kind != 'missing-parent') assert rejection.contains('recompile and package'): rejection
            assert rejection.contains(kind == 'missing-parent' ? 'fixture/parent/Destination' : 'fixture.parent.Destination'): rejection
            setProjectionInputs(project, inputs, ['fixture.child.Child'])
            String failure = projectSchemas(project, false)
            assert failure.contains('fixture.parent.Destination'): failure
            assert failure.contains(diagnostics[kind]): failure
            assert Files.readAllBytes(output.resolve('fixture.child.Child.schema.json')) == previous
            assert Files.readAllBytes(output.resolve('.klum-json-schema-outputs')) == manifest
        }
        ['runtime', 'projection'].each { mode ->
            List<Path> inputs = clients + [consumer] + (mode == 'projection' ? [Path.of(System.getProperty('schemaPluginJar'))] : [])
            String failure = execute([tool('java'), '-cp', paths(inputs), 'fixture.consumer.ArtifactResourceControl', oldParent.toString(), mode], false)
            assert failure.contains('Public contract resource origin disagrees with loaded type fixture.parent.Destination_DSL$Builder'): failure
        }
        retain(generation, root, compiler + clients + [consumer, oldParent, Path.of(System.getProperty('schemaPluginJar'))], output, controls.keySet() + ['resource-origin'])

        then:
        !Files.exists(root.resolve('parent-sources'))
        !Files.exists(root.resolve('parent-classes'))
        !Files.exists(root.resolve('child-sources'))
        !Files.exists(root.resolve('child-classes'))
        !Files.exists(root.resolve('consumer-sources'))
        !Files.exists(root.resolve('consumer-classes'))

        where:
        generation << ['3', '4', '5']
    }

    private static Map historicalHashes() {
        [
                '3': '731876432315be7a00f3910f995bb0bc5a5cb67eeb3bc5df367b14d13cb7a0ed',
                '4': '17c07ca3f153e0c1baeb39b61230606eb2e13269da838e7076734aa998317d76',
                '5': '02c24456ba2fa4f65ebc3050bd96dcbde02ece6a581b151f0277628e9089e613'
        ]
    }

    private static Map fields(Map document) { document.'$defs'.values().first().properties as Map }

    private Path artifact(Path root, String name, List<Path> inputs, boolean named, List<String> sources, String descriptor) {
        Path sourceDir = Files.createDirectories(root.resolve(name + '-sources'))
        Path classes = Files.createDirectories(root.resolve(name + '-classes'))
        sources.each { copyFixture(it, sourceDir.resolve(it)) }
        List<String> groovySources = sources.findAll { it.endsWith('.groovy') }.collect { sourceDir.resolve(it).toString() }
        List<String> javaSources = sources.findAll { it.endsWith('.java') }.collect { sourceDir.resolve(it).toString() }
        List<String> compiler = named ? [tool('java'), '--module-path', paths(inputs), '--add-modules', 'ALL-MODULE-PATH',
                '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler'] :
                [tool('java'), '-cp', paths(inputs), 'org.codehaus.groovy.tools.FileSystemCompiler']
        Path config = sourceDir.resolve('config.groovy')
        Files.writeString(config, 'configuration.parameters = true; configuration.optimizationOptions.groovydoc = true')
        execute(compiler + ['--configscript', config.toString(), '--classpath', paths(inputs + [classes]), '-d', classes.toString()] + groovySources)
        if (named) {
            Path module = sourceDir.resolve('module-info.java')
            Files.writeString(module, descriptor)
            execute([tool('javac'), '--module-path', paths(inputs), '-d', classes.toString(), module.toString()] + javaSources)
        } else if (javaSources) {
            execute([tool('javac'), '-cp', paths(inputs + [classes]), '-d', classes.toString()] + javaSources)
        }
        Path jar = root.resolve('fixture.' + name + '.jar')
        execute([tool('jar'), '--create', '--file', jar.toString(), '-C', classes.toString(), '.'])
        sourceDir.toFile().deleteDir()
        classes.toFile().deleteDir()
        jar
    }

    private File projectionProject(Path root, List<Path> inputs, List<String> roots) {
        File project = Files.createDirectories(root.resolve('projection')).toFile()
        new File(project, 'settings.gradle').text = "rootProject.name = 'scalar-artifact-projection'"
        setProjectionInputs(project, inputs, roots)
        project
    }

    private static void setProjectionInputs(File project, List<Path> inputs, List<String> roots) {
        new File(project, 'build.gradle').text = """
plugins { id 'com.blackbuild.klum-ast-schema' }
['api', 'implementation', 'compileOnly', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
klumSchema.jsonSchema {
    rootTypes.set(${JsonOutput.toJson(roots)})
    classpath.setFrom(files(${inputs.collect { "'" + it.toString().replace("'", "\\'") + "'" }.join(', ')}))
}
"""
    }

    private String projectSchemas(File project, boolean success = true) {
        // Packaged plugin and JAR-only Schema/runtime inputs match the plugin's existing TestKit infrastructure.
        Properties metadata = new Properties()
        getClass().classLoader.getResourceAsStream('plugin-under-test-metadata.properties').withCloseable { metadata.load(it) }
        List<File> dependencies = metadata.getProperty('implementation-classpath').split(File.pathSeparator).collect { new File(it) }.findAll { it.isFile() }
        GradleRunner runner = GradleRunner.create().withProjectDir(project)
                .withPluginClasspath(dependencies + new File(System.getProperty('schemaPluginJar')))
                .withDebug(true).withArguments('generateKlumJsonSchema', '--stacktrace', '--console=plain', '--max-workers=2')
        def result = success ? runner.build() : runner.buildAndFail()
        commands.add([command: ['GradleRunner', project.absolutePath, 'generateKlumJsonSchema'], success: success, output: result.output])
        result.output
    }

    private String launch(List<Path> inputs, boolean named, boolean reject = false) {
        execute((named ? [tool('java'), '--module-path', paths(inputs), '-m', 'fixture.consumer/fixture.consumer.Main'] :
                [tool('java'), '-cp', paths(inputs), 'fixture.consumer.Main']) + (reject ? ['reject'] : []), !reject)
    }

    private static Path mutatedParent(Path root, Path parent, String kind) {
        Path output = root.resolve('parent-' + kind + '.jar')
        new JarFile(parent.toFile()).withCloseable { input ->
            new JarOutputStream(Files.newOutputStream(output)).withCloseable { jar ->
                input.entries().each { entry ->
                    byte[] bytes = input.getInputStream(entry).withCloseable { it.readAllBytes() }
                    if (entry.name == BUILDER) bytes = corrupt(bytes, kind)
                    jar.putNextEntry(new JarEntry(entry.name))
                    jar.write(bytes)
                    jar.closeEntry()
                }
            }
        }
        output
    }

    private static byte[] corrupt(byte[] bytes, String kind) {
        ClassWriter writer = new ClassWriter(0)
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (kind == 'schema' && descriptor.endsWith('GeneratedInput$Schema;')) return null
                def visitor = super.visitAnnotation(descriptor, visible)
                if (kind != 'count' || !descriptor.endsWith('GeneratedInput$Schema;')) return visitor
                new AnnotationVisitor(Opcodes.ASM9, visitor) {
                    @Override void visit(String member, Object value) { super.visit(member, member == 'operationCount' ? 999 : value) }
                }
            }
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                def visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name != 'address' || !descriptor.startsWith('(Ljava/lang/String;)')) return visitor
                new MethodVisitor(Opcodes.ASM9, visitor) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (kind == 'operation' && annotation.endsWith('GeneratedInput$Operation;') ||
                                kind == 'marker' && annotation.endsWith('Struct$Operation;')) {
                            return null
                        }
                        def downstream = super.visitAnnotation(annotation, visible)
                        if (kind != 'parameter' || !annotation.endsWith('GeneratedInput$Operation;')) return downstream
                        new AnnotationVisitor(Opcodes.ASM9, downstream) {
                            @Override void visit(String member, Object value) {
                                if (!member.startsWith('parameterConstraint')) super.visit(member, value)
                            }
                            @Override AnnotationVisitor visitArray(String member) {
                                member == 'parameterConstraints' ? null : super.visitArray(member)
                            }
                        }
                    }
                }
            }
        }, 0)
        writer.toByteArray()
    }

    private void retain(String generation, Path root, List<Path> artifacts, Path schemas, Set<String> controls) {
        Path retained = Path.of(System.getProperty('schemaValidationDirectory'), 'scalar-artifacts-g' + generation)
        retained.toFile().deleteDir()
        Files.createDirectories(retained)
        List<Path> inputs = (artifacts + root.toFile().listFiles().findAll { it.name.endsWith('.jar') }.collect { it.toPath() }).unique()
        inputs.each { input -> Files.copy(input, retained.resolve(input.fileName)) }
        schemas.toFile().listFiles().findAll { it.name.endsWith('.json') }.each { Files.copy(it.toPath(), retained.resolve(it.name)) }
        Files.writeString(retained.resolve('evidence.json'), JsonOutput.prettyPrint(JsonOutput.toJson([
                generation: generation, java: System.getProperty('java.version'),
                artifacts: inputs.collect { [path: it.toString(), sha256: sha256(it)] },
                controls: controls, commands: commands])))
    }

    private void copyFixture(String name, Path target) {
        getClass().getResourceAsStream(FIXTURES + name).withCloseable { stream ->
            assert stream != null: name
            Files.copy(stream, target)
        }
    }

    private static String sha256(Path path) { MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex().toString() }
    private static List<Path> jars(String property) { System.getProperty(property).split(File.pathSeparator).collect { Path.of(it) } }
    private static Path groovy(String generation) { Path.of(System.getProperty('gdslGroovy' + generation)) }
    private static Path validationApi() { Path.of(System.getProperty('schemaValidationApi')) }
    private static String paths(List<Path> inputs) { inputs*.toString().join(File.pathSeparator) }
    private static String tool(String name) { Path.of(System.getProperty('java.home'), 'bin', name).toString() }

    private String execute(List<String> command, boolean success = true) {
        assert !command.any { it in ['--add-reads', '--add-exports', '--add-opens', '--patch-module'] }
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().remove('CLASSPATH')
        Process process = builder.start()
        StringBuilder output = new StringBuilder()
        Thread reader = Thread.start { process.inputStream.withReader { output.append(it.text) } }
        boolean completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(5000)
        commands.add([command: command, success: success, output: output.toString()])
        assert completed: "Timed out: $command\n$output"
        assert (process.exitValue() == 0) == success: "Failed: $command\n$output"
        output.toString()
    }
}
