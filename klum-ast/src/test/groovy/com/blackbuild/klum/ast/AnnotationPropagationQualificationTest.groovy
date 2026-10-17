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

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.Specification

import java.lang.module.ModuleFinder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

/** Slow, subprocess-only shipping gate; run annotationPropagationArtifactsGroovy3/4/5 explicitly. */
@Issue('890')
class AnnotationPropagationQualificationTest extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()
    private static final Path FIXTURES = Path.of('src/test/fixtures/annotation-propagation-artifacts')
    private static final Map<String, String> CAST_HASHES = [
            'klum-cast-annotations': '3bb36f43fc90f27a04568673988fb8afb128d6ef00d7fe4c6de658eec5e981d2',
            'klum-cast-spi': '7aa1cfc700de2ca275f6c9124e28859f93d313023896c582fa8bf7d7a14d8e52',
            'klum-cast-compile': 'aa04039be352872bf48c9f60e6f45f140d88c1428886cb334c21529471cf71a2'
    ]

    def 'shipped artifacts qualify separate libraries foreign discovery and binary snapshots (named=#named)'() {
        given:
        assert Runtime.version().feature() == 17 : 'Run this shipping gate with Java 17'
        Path root = temporaryFolder.newFolder().toPath()
        List<Path> shipped = shippedJars()
        verifyServices(shipped)
        Path foreign = compile(root, 'foreign', ['ForeignTrigger.java'], shipped, named,
                'requires org.apache.groovy; exports qualification.foreign;')
        List<Path> compiler = shipped + [foreign]
        Path transform = compile(root, 'transform', ['ForeignTransform.groovy'], compiler, named,
                'requires org.apache.groovy; opens qualification.transform to org.apache.groovy;')
        compiler += [transform]
        List<Path> policies = compilePolicies(root.resolve('v1'), compiler, named, false)
        List<Path> schemaPath = compiler + policies
        Path schema = compile(root, 'schema', ['Application.groovy'], schemaPath, named, schemaDescriptor())
        byte[] oldSchema = Files.readAllBytes(schema)
        Path consumer = compile(root, 'consumer', ['StaticConsumer.groovy', 'Main.java'],
                runtimeJars(shipped) + [foreign] + policies + [schema],
                named, consumerDescriptor())
        List<Path> runtime = runtimeJars(shipped) + [foreign] + policies + [schema, consumer]

        when: 'the actual shipped provider supplies local triggers before normal discovery'
        String original = runConsumer(runtime, named, true, true)

        then:
        original.trim() == "artifacts=true;snapshot=true;named=$named"

        when: 'the actual KlumCast traversal sees a conflict introduced by the provider'
        String conflict = rejectSchema(root, 'Conflict', "@RequiredValue @Validate String name", schemaPath, named)
        String duplicate = rejectSchema(root, 'Duplicate', "@Repeated @AutoCreate Service child", schemaPath, named)

        then:
        conflict.contains('Required') && conflict.contains('Validate')
        duplicate.contains('Repeated') && duplicate.contains('AutoCreate')
        duplicate.contains('Duplicate')

        when: 'annotation-library requests change without recompiling the old Schema'
        List<Path> changed = compilePolicies(root.resolve('v2'), compiler, named, true)
        String retained = runConsumer(runtimeJars(shipped) + [foreign] + changed + [schema, consumer], named, true, false)
        Path recompiled = compile(root.resolve('v2'), 'schema', ['Application.groovy'], compiler + changed,
                named, schemaDescriptor())
        String refreshed = runConsumer(runtimeJars(shipped) + [foreign] + changed + [recompiled, consumer], named, false, false)

        then:
        retained.trim() == "artifacts=true;snapshot=true;named=$named"
        refreshed.trim() == "artifacts=true;snapshot=false;named=$named"
        Files.readAllBytes(schema) == oldSchema

        where:
        named << (GroovySystem.version.startsWith('3.') ? [false] : [false, true])
    }

    private static List<Path> compilePolicies(Path root, List<Path> compiler, boolean named, boolean changed) {
        String javaDescriptor = 'requires com.blackbuild.klum.ast.annotations; requires qualification.foreign; exports qualification.javaapi;'
        String groovyDescriptor = 'requires com.blackbuild.klum.ast.annotations; requires qualification.javaapi; exports qualification.groovyapi;'
        Path javaPolicy = compile(root, 'javaapi', ['JavaPolicies.java'], compiler, named, javaDescriptor, changed)
        [javaPolicy, compile(root, 'groovyapi', ['GroovyPolicies.groovy'], compiler + [javaPolicy],
                named, groovyDescriptor, changed)]
    }

    private static String schemaDescriptor() {
        """
            requires com.blackbuild.klum.ast.annotations;
            requires com.blackbuild.klum.ast.runtime;
            requires static com.blackbuild.klum.ast.compiler;
            requires qualification.foreign;
            requires qualification.javaapi;
            requires qualification.groovyapi;
            requires org.apache.groovy;
            exports qualification.schema;
            opens qualification.schema to com.blackbuild.klum.ast.runtime;
        """
    }

    private static String consumerDescriptor() {
        """
            requires com.blackbuild.klum.ast.annotations;
            requires com.blackbuild.klum.ast.runtime;
            requires qualification.foreign;
            requires qualification.javaapi;
            requires qualification.groovyapi;
            requires qualification.schema;
            requires org.apache.groovy;
            exports qualification.consumer;
        """
    }

    private static Path compile(Path root, String module, List<String> fixtures, List<Path> dependencies,
                                boolean named, String descriptor, boolean changed = false) {
        Path sources = Files.createDirectories(root.resolve(module).resolve('sources'))
        Path classes = Files.createDirectories(root.resolve(module).resolve('classes'))
        List<Path> inputs = fixtures.collect { name ->
            String text = Files.readString(FIXTURES.resolve(name))
            if (changed) {
                text = text.replace('@PropagateAnnotation(AutoCreate.class)\n', '')
                        .replace('@PropagateAnnotation(AutoCreate)\n', '')
                        .replace('@PropagateAnnotation(AutoCreate.class), ', '')
                        .replace('@PropagateAnnotation(AutoCreate), ', '')
            }
            Path source = sources.resolve(name)
            Files.writeString(source, text)
            source
        }
        List<Path> groovy = inputs.findAll { it.toString().endsWith('.groovy') }
        if (groovy) execute(groovyc(dependencies, named) + ['-d', classes.toString()] + groovy*.toString())
        List<Path> java = inputs.findAll { it.toString().endsWith('.java') }
        if (named) {
            // Author the descriptor once. javac compiles only library/consumer Java and the descriptor;
            // all Schema expansion and local transforms are performed by Groovy above.
            Path moduleInfo = sources.resolve('module-info.java')
            Files.writeString(moduleInfo, "module qualification.$module { $descriptor }")
            java.add(moduleInfo)
        }
        if (java) {
            List<String> modulePath = named ? ['--module-path', paths(dependencies)] : []
            execute([tool('javac'), '--release', '17'] + modulePath +
                    ['--class-path', paths(dependencies + [classes]), '-d', classes.toString()] + java*.toString())
        }
        Path artifact = root.resolve("qualification.${module}.jar")
        execute([tool('jar'), '--create', '--file', artifact.toString(), '-C', classes.toString(), '.'])
        assert !dependencies.any { Files.isDirectory(it) } : 'Only jars cross compilation boundaries'
        artifact
    }

    private static String rejectSchema(Path root, String name, String field, List<Path> dependencies, boolean named) {
        Path source = root.resolve("${name}.groovy")
        Files.writeString(source, """
            package qualification.negative
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Validate
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import qualification.javaapi.JavaPolicies.RequiredValue
            import qualification.javaapi.JavaPolicies.Repeated
            @DSL class Service {}
            @DSL class $name { $field }
        """)
        execute(groovyc(dependencies, named) + ['-d', root.resolve(name).toString(), source.toString()], false)
    }

    private static List<String> groovyc(List<Path> dependencies, boolean named) {
        List<String> launcher = named ?
                [tool('java'), '-Dgroovy.attach.groovydoc=true', '--module-path', paths(dependencies), '--add-modules', 'ALL-MODULE-PATH',
                 '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler'] :
                [tool('java'), '-Dgroovy.attach.groovydoc=true', '--class-path', paths(dependencies), 'org.codehaus.groovy.tools.FileSystemCompiler']
        // Match the ordinary build's AnnoDocimal compiler options, without a customizer.
        launcher + ['--classpath', paths(dependencies), '--parameters']
    }

    private static String runConsumer(List<Path> runtime, boolean named, boolean copied, boolean requested) {
        assert runtime.every { Files.isRegularFile(it) }
        List<String> launcher = named ?
                [tool('java'), '--module-path', paths(runtime), '-m', 'qualification.consumer/qualification.consumer.Main'] :
                [tool('java'), '--class-path', paths(runtime), 'qualification.consumer.Main']
        execute(launcher + [copied.toString(), named.toString(), requested.toString()])
    }

    private static List<Path> runtimeJars(List<Path> shipped) {
        shipped.findAll { path ->
            path != Path.of(System.getProperty('klumCompilerJar')) &&
                    !['klum-cast-compile-', 'klum-cast-spi-', 'anno-docimal-global-ast-', 'anno-docimal-ast-']
                            .any { path.fileName.toString().startsWith(it) }
        }
    }

    private static void verifyServices(List<Path> shipped) {
        Path compiler = Path.of(System.getProperty('klumCompilerJar'))
        String service = 'com.blackbuild.klum.cast.compiler.EffectiveDeclarationContributor'
        String provider = 'com.blackbuild.klum.ast.compiler.internal.ast.AnnotationPropagationContributor'
        new JarFile(compiler.toFile()).withCloseable { jar ->
            assert jar.getInputStream(jar.getJarEntry("META-INF/services/$service")).text.trim() == provider
        }
        def descriptor = ModuleFinder.of(compiler).findAll().first().descriptor()
        assert descriptor.exports().empty
        assert descriptor.provides().find { it.service() == service }.providers() == [provider]
        CAST_HASHES.each { artifact, hash ->
            Path jar = shipped.find { it.fileName.toString() == "$artifact-0.5.0.jar" }
            assert jar != null : "Missing exact public 0.5.0 artifact: $artifact"
            assert MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(jar)).encodeHex().toString() == hash
        }
    }

    private static List<Path> shippedJars() {
        List<Path> jars = ['klumAnnotationsJar', 'klumRuntimeJar', 'klumCompilerJar'].collect {
            Path.of(System.getProperty(it))
        }
        jars.addAll(System.getProperty('java.class.path').split(File.pathSeparator).findAll { entry ->
            String name = Path.of(entry).fileName.toString()
            ['groovy-3.', 'groovy-4.', 'groovy-5.', 'anno-docimal-annotations-', 'anno-docimal-ast-',
             'anno-docimal-global-ast-', 'klum-cast-annotations-', 'klum-cast-compile-', 'klum-cast-spi-',
             'jspecify-'].any { name.startsWith(it) }
        }.collect { Path.of(it) })
        jars.unique()
    }

    private static String paths(List<Path> jars) { jars*.toString().join(File.pathSeparator) }
    private static String tool(String name) { Path.of(System.getProperty('java.home'), 'bin', name).toString() }

    private static String execute(List<String> command, boolean successful = true) {
        assert !command.any { option ->
            ['--add-reads', '--add-exports', '--add-opens', '--patch-module'].any {
                option == it || option.startsWith(it + '=')
            }
        }
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
        ['CLASSPATH', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'].each { builder.environment().remove(it) }
        Process process = builder.start()
        StringBuilder output = new StringBuilder()
        Thread reader = Thread.start { process.inputStream.withReader { output.append(it.text) } }
        boolean completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(5000)
        assert completed : "Timed out: $command\n$output"
        assert (process.exitValue() == 0) == successful : "Unexpected result: $command\n$output"
        output.toString()
    }
}
