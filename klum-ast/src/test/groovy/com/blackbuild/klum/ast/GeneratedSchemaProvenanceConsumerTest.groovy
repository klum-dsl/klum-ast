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
package com.blackbuild.klum.ast

import com.blackbuild.annodocimal.generator.ProjectionPolicy
import com.blackbuild.annodocimal.generator.SourceProjector
import groovy.lang.GroovySystem
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.Opcodes
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** Local artifact qualification, not historical compatibility or published-coordinate evidence. */
@Issue('896')
class GeneratedSchemaProvenanceConsumerTest extends Specification {
    private static final String RESOURCE = 'META-INF/klum-ast/compiler.properties'
    private static final String MARKER_DESCRIPTOR = 'Lcom/blackbuild/klum/ast/runtime/generated/GeneratedSchema;'
    private static final String DEVELOPMENT = '4.2.0-dev.471+producer'
    private static final String RELEASE_CANDIDATE = '4.2.0-rc.7+producer'
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def 'independent Java and Groovy clients read each emitter from binary Schemas with named modules=#named'() {
        given:
        Path root = temporaryFolder.newFolder().toPath()
        List<Path> dependencies = compilerJars()
        Path parentCompiler = compilerWithProvenance(root, 'development', 'compilerVersion=' + DEVELOPMENT)
        Path childCompiler = compilerWithProvenance(root, 'candidate', 'compilerVersion=' + RELEASE_CANDIDATE)
        List<Path> runtime = dependencies.findAll { !isCompiler(it) }
        Path parent = artifact(root, 'parent', withCompiler(dependencies, parentCompiler), named, [
                'Service.groovy': '''
                    package fixture.parent
                    import com.blackbuild.klum.ast.DSL
                    @DSL abstract class Service { String name }
                '''
        ], 'module fixture.parent { requires com.blackbuild.klum.ast.runtime; exports fixture.parent; opens fixture.parent to com.blackbuild.klum.ast.runtime; }')
        Path child = artifact(root, 'child', withCompiler(dependencies, childCompiler) + [parent], named, [
                'WebService.groovy': '''
                    package fixture.child
                    import com.blackbuild.klum.ast.DSL
                    import fixture.parent.Service
                    @DSL class WebService extends Service { int port }
                '''
        ], 'module fixture.child { requires com.blackbuild.klum.ast.runtime; requires fixture.parent; exports fixture.child; opens fixture.child to com.blackbuild.klum.ast.runtime; }')
        List<Path> clients = runtime + [parent, child]
        Path consumer = artifact(root, 'consumer', clients, named, [
                'GroovyClient.groovy': '''
                    package fixture.consumer
                    import groovy.transform.CompileStatic
                    import com.blackbuild.klum.ast.runtime.generated.GeneratedSchema
                    import fixture.parent.Service
                    import fixture.child.WebService
                    @CompileStatic class GroovyClient {
                        static String compilerVersion(Class<?> type) {
                            type.getDeclaredAnnotation(GeneratedSchema).compilerVersion()
                        }
                        static void verify() {
                            assert compilerVersion(Service) == '4.2.0-dev.471+producer'
                            assert compilerVersion(WebService) == '4.2.0-rc.7+producer'
                            assert WebService.Create.With { name 'web'; port 8080 }.port == 8080
                        }
                    }
                ''',
                'Main.java': '''
                    package fixture.consumer;
                    import com.blackbuild.klum.ast.runtime.generated.GeneratedSchema;
                    import fixture.parent.Service;
                    import fixture.child.WebService;
                    public class Main {
                        public static void main(String[] args) throws Exception {
                            for (Class<?> type : new Class<?>[]{Service.class, WebService.class}) {
                                GeneratedSchema marker = type.getDeclaredAnnotation(GeneratedSchema.class);
                                String expected = type == Service.class ? "4.2.0-dev.471+producer" : "4.2.0-rc.7+producer";
                                if (marker.format() != 1 || !marker.compilerVersion().equals(expected)) throw new AssertionError(type);
                                if (type.getDeclaredAnnotationsByType(GeneratedSchema.class).length != 1) throw new AssertionError(type);
                            }
                            if (GeneratedSchema.class.getDeclaredMethods().length != 2) throw new AssertionError("marker members");
                            try { Class.forName("com.blackbuild.klum.ast.compiler.internal.ast.DSLASTTransformation");
                                throw new AssertionError("compiler at runtime");
                            } catch (ClassNotFoundException expected) { }
                            GroovyClient.verify();
                            System.out.println("provenance-consumers=true");
                        }
                    }
                '''
        ], 'module fixture.consumer { requires com.blackbuild.klum.ast.runtime; requires fixture.parent; requires fixture.child; }')

        when:
        String output = launch(clients + [consumer], named, 'fixture.consumer/fixture.consumer.Main')

        then:
        output.trim() == 'provenance-consumers=true'

        when: 'unmarked binary producers remain usable without any provenance check'
        Path unmarkedParent = withoutMarkers(root, parent)
        Path unmarkedChild = withoutMarkers(root, child)
        List<Path> unmarked = runtime + [unmarkedParent, unmarkedChild]
        Path legacy = artifact(root, 'legacy', unmarked, named, [
                'Legacy.groovy': '''
                    package fixture.legacy
                    import fixture.child.WebService
                    import com.blackbuild.klum.ast.runtime.generated.GeneratedSchema
                    class Legacy {
                        static void main(String[] args) {
                            assert WebService.getDeclaredAnnotation(GeneratedSchema) == null
                            assert WebService.superclass.getDeclaredAnnotation(GeneratedSchema) == null
                            assert WebService.Create.With { name 'legacy'; port 80 }.port == 80
                            println 'unmarked-consumers=true'
                        }
                    }
                '''
        ], 'module fixture.legacy { requires com.blackbuild.klum.ast.runtime; requires fixture.parent; requires fixture.child; }')

        then:
        launch(unmarked + [legacy], named, 'fixture.legacy/fixture.legacy.Legacy').trim() == 'unmarked-consumers=true'

        where:
        // Groovy 3 is classpath-only under ADR 0014.
        named << (GroovySystem.version.startsWith('3.') ? [false] : [false, true])
    }

    def 'missing or invalid own compiler provenance fails Schema compilation: #label'() {
        given:
        Path root = temporaryFolder.newFolder().toPath()
        Path compiler = compilerWithProvenance(root, 'invalid', metadata)
        // A decoy on the classpath must never repair the executing compiler's missing/invalid metadata.
        Path decoy = Files.createDirectories(root.resolve('decoy'))
        Path resource = decoy.resolve(RESOURCE)
        Files.createDirectories(resource.parent)
        Files.writeString(resource, 'compilerVersion=unrelated-consumer')
        Path source = root.resolve('Service.groovy')
        Files.writeString(source, 'package fixture.invalid; import com.blackbuild.klum.ast.DSL; @DSL class Service { }')
        Path configuration = root.resolve('compiler-config.groovy')
        Files.writeString(configuration, 'configuration.parameters = true; configuration.optimizationOptions.groovydoc = true')
        List<Path> inputs = [decoy] + withCompiler(compilerJars(), compiler)

        when:
        String output = execute([tool('java'), '-cp', paths(inputs), 'org.codehaus.groovy.tools.FileSystemCompiler',
                                 '--configscript', configuration.toString(), '-d', root.resolve('classes').toString(),
                                 source.toString()], false)

        then:
        output.contains('Cannot establish KlumAST compiler provenance from the emitting compiler artifact')
        !output.contains('ExceptionInInitializerError')

        where:
        label       | metadata
        'missing'   | null
        'blank'     | 'compilerVersion='
        'ambiguous' | 'compilerVersion=one\ncompilerVersion=two'
    }

    private static Path artifact(Path root, String name, List<Path> inputs, boolean named,
                                 Map<String, String> sources, String descriptor) {
        Path sourceDir = Files.createDirectories(root.resolve(name + '-sources'))
        Path classes = Files.createDirectories(root.resolve(name + '-classes'))
        sources.each { file, text -> Files.writeString(sourceDir.resolve(file), text.stripIndent()) }
        List<String> groovy = sources.keySet().findAll { it.endsWith('.groovy') }.collect { sourceDir.resolve(it).toString() }
        List<String> java = sources.keySet().findAll { it.endsWith('.java') }.collect { sourceDir.resolve(it).toString() }
        List<String> compiler = named ? [tool('java'), '--module-path', paths(inputs), '--add-modules', 'ALL-MODULE-PATH',
                '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler'] :
                [tool('java'), '-cp', paths(inputs), 'org.codehaus.groovy.tools.FileSystemCompiler']
        execute(compiler + ['--classpath', paths(inputs + [classes]), '-d', classes.toString()] + groovy)
        if (named) {
            Files.writeString(sourceDir.resolve('module-info.java'), descriptor)
            execute([tool('javac'), '--module-path', paths(inputs), '-d', classes.toString(),
                     sourceDir.resolve('module-info.java').toString()] + java)
        } else if (java) {
            execute([tool('javac'), '-cp', paths(inputs + [classes]), '-d', classes.toString()] + java)
        }
        if (name in ['parent', 'child']) {
            Path modelClass = classes.resolve(name == 'parent' ? 'fixture/parent/Service.class' : 'fixture/child/WebService.class')
            Path mirrors = Files.createDirectories(root.resolve(name + '-mirrors'))
            ClassLoader original = Thread.currentThread().contextClassLoader
            new URLClassLoader((inputs + [classes]).collect { it.toUri().toURL() } as URL[], original).withCloseable { loader ->
                try {
                    Thread.currentThread().contextClassLoader = loader
                    new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(modelClass, mirrors)
                } finally {
                    Thread.currentThread().contextClassLoader = original
                }
            }
            String mirror = Files.readString(mirrors.resolve(name == 'parent' ? 'fixture/parent/Service.java' : 'fixture/child/WebService.java'))
            assert mirror.contains('@GeneratedSchema(')
            assert mirror.contains('format = 1')
            assert mirror.contains(name == 'parent' ? DEVELOPMENT : RELEASE_CANDIDATE)
            mirrors.toFile().deleteDir()
        }
        Path jar = root.resolve('fixture.' + name + '.jar')
        execute([tool('jar'), '--create', '--file', jar.toString(), '-C', classes.toString(), '.'])
        sourceDir.toFile().deleteDir()
        classes.toFile().deleteDir()
        jar
    }

    private static Path compilerWithProvenance(Path root, String name, String metadata) {
        rewriteJar(Path.of(System.getProperty('klumCompilerJar')), root.resolve(name + '-compiler.jar')) { entry, bytes ->
            entry == RESOURCE ? (metadata == null ? null : metadata.getBytes('ISO-8859-1')) : bytes
        }
    }

    /** Remove only the inert annotation; this reconstructs an unmarked binary, not a historical release. */
    private static Path withoutMarkers(Path root, Path source) {
        rewriteJar(source, root.resolve('unmarked-' + source.fileName)) { entry, bytes ->
            if (!entry.endsWith('.class')) return bytes
            ClassWriter writer = new ClassWriter(0)
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    descriptor == MARKER_DESCRIPTOR ? null : super.visitAnnotation(descriptor, visible)
                }
            }, 0)
            writer.toByteArray()
        }
    }

    private static Path rewriteJar(Path input, Path output, Closure<byte[]> transform) {
        new JarFile(input.toFile()).withCloseable { jar ->
            new JarOutputStream(Files.newOutputStream(output)).withCloseable { target ->
                jar.entries().each { entry ->
                    byte[] bytes = transform(entry.name, jar.getInputStream(entry).withCloseable { it.readAllBytes() })
                    if (bytes != null) {
                        target.putNextEntry(new JarEntry(entry.name))
                        target.write(bytes)
                        target.closeEntry()
                    }
                }
            }
        }
        output
    }

    private static List<Path> withCompiler(List<Path> inputs, Path compiler) {
        inputs.collect { it == Path.of(System.getProperty('klumCompilerJar')) ? compiler : it }
    }

    private static boolean isCompiler(Path path) {
        path == Path.of(System.getProperty('klumCompilerJar')) ||
                ['anno-docimal-ast-', 'anno-docimal-global-ast-', 'klum-cast-compile-'].any { path.fileName.toString().startsWith(it) }
    }

    private static List<Path> compilerJars() {
        List<Path> jars = ['klumAnnotationsJar', 'klumRuntimeJar', 'klumCompilerJar'].collect { Path.of(System.getProperty(it)) }
        jars.addAll(System.getProperty('java.class.path').split(File.pathSeparator).findAll { entry ->
            ['groovy-3.', 'groovy-4.', 'groovy-5.', 'anno-docimal-annotations-', 'anno-docimal-ast-',
             'anno-docimal-global-ast-', 'klum-cast-annotations-', 'klum-cast-compile-', 'klum-cast-spi-',
             'jspecify-'].any { Path.of(entry).fileName.toString().startsWith(it) }
        }.collect { Path.of(it) })
        jars.unique()
    }

    private static String launch(List<Path> inputs, boolean named, String main) {
        execute(named ? [tool('java'), '--module-path', paths(inputs), '-m', main] :
                [tool('java'), '-cp', paths(inputs), main.split('/')[1]])
    }

    private static String paths(List<Path> inputs) { inputs*.toString().join(File.pathSeparator) }
    private static String tool(String name) { Path.of(System.getProperty('java.home'), 'bin', name).toString() }

    private static String execute(List<String> command, boolean success = true) {
        assert !command.any { it in ['--add-reads', '--add-exports', '--add-opens', '--patch-module'] }
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().remove('CLASSPATH')
        Process process = builder.start()
        StringBuilder output = new StringBuilder()
        Thread reader = Thread.start { process.inputStream.withReader { output.append(it.text) } }
        boolean completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(5000)
        assert completed : "Timed out: $command\n$output"
        assert (process.exitValue() == 0) == success : "Failed: $command\n$output"
        output.toString()
    }
}
