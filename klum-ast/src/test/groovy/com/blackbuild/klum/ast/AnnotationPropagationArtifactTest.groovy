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
import java.util.concurrent.TimeUnit

@Issue('890')
class AnnotationPropagationArtifactTest extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def 'compiler jar registers its provider without exporting compiler internals'() {
        when:
        def descriptor = ModuleFinder.of(Path.of(System.getProperty('klumCompilerJar'))).findAll().first().descriptor()

        then:
        descriptor.exports().empty
        descriptor.provides().find {
            it.service() == 'com.blackbuild.klum.cast.compiler.EffectiveDeclarationContributor'
        }.providers() == ['com.blackbuild.klum.ast.compiler.internal.ast.AnnotationPropagationContributor']
    }

    def 'separate annotation Schema and Java consumer artifacts qualify propagation (#named)'() {
        given:
        Path root = temporaryFolder.newFolder().toPath()
        Path sources = Files.createDirectories(root.resolve('sources'))
        List<Path> dependencies = runtimeAndCompilerJars()
        String policies = new File('src/test/java/com/blackbuild/klum/ast/propagation/PropagationPolicies.java').text
                .replace('package com.blackbuild.klum.ast.propagation;', 'package propagation.policy;')
        Files.writeString(sources.resolve('PropagationPolicies.java'), policies)
        Path policyClasses = Files.createDirectories(root.resolve('policy-classes'))
        execute([javaTool('javac'), '--release', '17', '--class-path', pathString(dependencies),
                 '-d', policyClasses.toString(), sources.resolve('PropagationPolicies.java').toString()])
        if (named) descriptor(sources, policyClasses, dependencies, 'policy', '', false)
        dependencies.add(jar(root, policyClasses, 'propagation.policy.jar'))

        Files.writeString(sources.resolve('Application.groovy'), '''
            package propagation.schema
            import com.blackbuild.klum.ast.DSL
            import propagation.policy.PropagationPolicies.*
            @DSL class Service { String name }
            @DSL class Application {
                @RequiredValue String name
                @ConfiguredChild(name='orders') Service child
                @Group Map<String, Service> services
                @Query String summary() { name.toUpperCase() }
            }
        ''')
        Path schemaClasses = Files.createDirectories(root.resolve('schema-classes'))
        List<String> compiler = named ?
                [javaTool('java'), '--module-path', pathString(dependencies), '--add-modules', 'ALL-MODULE-PATH',
                 '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler'] :
                [javaTool('java'), '--class-path', pathString(dependencies), 'org.codehaus.groovy.tools.FileSystemCompiler']
        execute(compiler + ['--classpath', pathString(dependencies), '-d', schemaClasses.toString(),
                            sources.resolve('Application.groovy').toString()])
        if (named) descriptor(sources, schemaClasses, dependencies, 'schema', 'requires propagation.policy;', true)
        dependencies.add(jar(root, schemaClasses, 'propagation.schema.jar'))

        Files.writeString(sources.resolve('Main.java'), '''
            package propagation.consumer;
            import propagation.schema.Application;
            import propagation.schema.Application_DSL;
            import propagation.policy.PropagationPolicies.ConfiguredChild;
            import com.blackbuild.klum.ast.layer3.AutoCreate;
            import java.util.Map;
            public class Main {
                public static String query(Application_DSL.Builder<Application> builder) { return builder.summary(); }
                public static boolean accepts(Application_DSL.Factory factory, Object value) {
                    return factory.isModelOrBuilder(value);
                }
                public static void main(String[] args) throws Exception {
                    Application value = Application.Create.With(Map.of("name", "configured"));
                    if (!value.getChild().getName().equals("orders") || !value.summary().equals("CONFIGURED"))
                        throw new AssertionError("propagation behavior");
                    if (!accepts(Application.Create, value)) throw new AssertionError("public factory");
                    var field = Application.class.getDeclaredField("child");
                    if (field.getAnnotation(AutoCreate.class) == null ||
                            !field.getAnnotation(ConfiguredChild.class).name().equals("orders"))
                        throw new AssertionError("physical annotations");
                    try {
                        Class.forName("com.blackbuild.klum.ast.compiler.internal.ast.AnnotationPropagationContributor");
                        throw new AssertionError("compiler leaked onto runtime path");
                    } catch (ClassNotFoundException expected) { }
                    System.out.println("propagation-artifacts=true");
                }
            }
        ''')
        Path consumerClasses = Files.createDirectories(root.resolve('consumer-classes'))
        execute([javaTool('javac'), '--release', '17', '--class-path', pathString(dependencies),
                 '-d', consumerClasses.toString(), sources.resolve('Main.java').toString()])
        if (named) descriptor(sources, consumerClasses, dependencies, 'consumer',
                'requires propagation.policy; requires propagation.schema;', false)
        dependencies.add(jar(root, consumerClasses, 'propagation.consumer.jar'))
        // Runtime linkage uses only emitted artifacts and ordinary runtime libraries.
        Path compilerJar = Path.of(System.getProperty('klumCompilerJar'))
        List<Path> runtime = dependencies.findAll { path ->
            path != compilerJar &&
                    !['anno-docimal-global-ast-', 'anno-docimal-ast-', 'klum-cast-compile-', 'klum-cast-spi-']
                            .any { prefix -> path.fileName.toString().startsWith(prefix) }
        }

        when:
        String output = named ?
                execute([javaTool('java'), '--module-path', pathString(runtime),
                         '-m', 'propagation.consumer/propagation.consumer.Main']) :
                execute([javaTool('java'), '--class-path', pathString(runtime), 'propagation.consumer.Main'])

        then:
        output.trim() == 'propagation-artifacts=true'

        where:
        // Groovy 3 remains classpath-only; Groovy 4/5 also qualify named module activation.
        named << (GroovySystem.version.startsWith('3.') ? [false] : [false, true])
    }

    private static void descriptor(Path sources, Path classes, List<Path> dependencies, String name,
                                   String requires, boolean schema) {
        Files.writeString(sources.resolve('module-info.java'), """
            module propagation.$name {
                requires com.blackbuild.klum.ast.annotations;
                requires com.blackbuild.klum.ast.runtime;
                requires org.apache.groovy;
                $requires
                exports propagation.$name;
                ${schema ? 'requires static com.blackbuild.klum.ast.compiler; opens propagation.schema to com.blackbuild.klum.ast.runtime;' : ''}
            }
        """)
        execute([javaTool('javac'), '--module-path', pathString(dependencies), '-d', classes.toString(),
                 sources.resolve('module-info.java').toString()])
    }

    private static Path jar(Path root, Path classes, String name) {
        Path artifact = root.resolve(name)
        execute([javaTool('jar'), '--create', '--file', artifact.toString(), '-C', classes.toString(), '.'])
        classes.toFile().deleteDir()
        artifact
    }

    private static List<Path> runtimeAndCompilerJars() {
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

    private static String pathString(List<Path> paths) { paths*.toString().join(File.pathSeparator) }
    private static String javaTool(String name) { Path.of(System.getProperty('java.home'), 'bin', name).toString() }

    private static String execute(List<String> command) {
        assert !command.any { it in ['--add-reads', '--add-exports', '--add-opens', '--patch-module'] }
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().remove('CLASSPATH')
        Process process = builder.start()
        // Drain concurrently so compiler diagnostics cannot fill the pipe before process exit.
        StringBuilder output = new StringBuilder()
        Thread reader = Thread.start { process.inputStream.withReader { output.append(it.text) } }
        boolean completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(5000)
        assert completed : "Timed out: $command\n$output"
        assert process.exitValue() == 0 : "Failed: $command\n$output"
        output.toString()
    }
}
