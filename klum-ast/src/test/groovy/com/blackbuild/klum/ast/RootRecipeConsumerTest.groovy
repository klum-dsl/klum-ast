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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Qualifies JAR-only clients in independent compiler/JVM processes, including supported named modules. */
@Issue('829')
class RootRecipeConsumerTest extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def 'JAR-only Java and static and dynamic Groovy clients consume recipe overlays with named modules=#named'() {
        when:
        String output = qualify(named)

        then:
        output.trim() == 'recipe-consumers=true'

        where:
        // ADR 0014 keeps Groovy 3 classpath-only; JpmsGroovyModuleIdentityTest covers its negative module boundary.
        named << (GroovySystem.version.startsWith('3.') ? [false] : [false, true])
    }

    private String qualify(boolean named) {
        Path root = temporaryFolder.newFolder().toPath()
        List<Path> dependencies = compilerJars()
        Path schema = buildArtifact(root, 'schema', dependencies, named,
                ['Models.groovy', 'OrderRecipe.groovy', 'LegacyRecipe.groovy'], [], '''
            module fixture.recipe {
                requires com.blackbuild.klum.ast.runtime;
                exports fixture.recipe;
                opens fixture.recipe to com.blackbuild.klum.ast.runtime;
            }
        ''')
        Path consumer = buildArtifact(root, 'consumer', dependencies + [schema], named,
                ['GroovyClients.groovy'], ['Main.java'], '''
            module fixture.consumer {
                requires com.blackbuild.klum.ast.runtime;
                requires fixture.recipe;
                opens fixture.consumer to org.apache.groovy;
            }
        ''')
        assertJavaRejections(root, dependencies + [schema])
        List<Path> runtime = (dependencies + [schema, consumer]).findAll { path ->
            path != Path.of(System.getProperty('klumCompilerJar')) &&
                    !path.fileName.toString().startsWith('anno-docimal-ast-') &&
                    !path.fileName.toString().startsWith('anno-docimal-global-ast-') &&
                    !path.fileName.toString().startsWith('klum-cast-compile-')
        }
        assert runtime.every { it.fileName.toString().endsWith('.jar') }
        execute(named ? [javaTool('java'), '--module-path', paths(runtime), '-m', 'fixture.consumer/fixture.consumer.Main'] :
                [javaTool('java'), '--class-path', paths(runtime), 'fixture.consumer.Main'])
    }

    private Path buildArtifact(Path root, String name, List<Path> inputs, boolean named,
                               List<String> groovySources, List<String> javaSources, String descriptor) {
        Path sources = Files.createDirectories(root.resolve(name + '-sources'))
        Path classes = Files.createDirectories(root.resolve(name + '-classes'))
        (groovySources + javaSources).each { file ->
            getClass().getResourceAsStream('/root-recipe/' + name + '/' + file).withCloseable { stream ->
                assert stream != null : "Missing fixture $name/$file"
                Files.copy(stream, sources.resolve(file))
            }
        }
        List<String> compiler = named ? [javaTool('java'), '--module-path', paths(inputs),
                '--add-modules', 'ALL-MODULE-PATH', '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler'] :
                [javaTool('java'), '--class-path', paths(inputs), 'org.codehaus.groovy.tools.FileSystemCompiler']
        execute(compiler + ['--classpath', paths(inputs + [classes]), '-d', classes.toString()] +
                groovySources.collect { sources.resolve(it).toString() })
        if (javaSources) {
            execute([javaTool('javac'), '--class-path', paths(inputs + [classes]), '-d', classes.toString()] +
                    javaSources.collect { sources.resolve(it).toString() })
        }
        if (named) {
            Files.writeString(sources.resolve('module-info.java'), descriptor.stripIndent())
            execute([javaTool('javac'), '--module-path', paths(inputs), '-d', classes.toString(),
                     sources.resolve('module-info.java').toString()] + javaSources.collect { sources.resolve(it).toString() })
        }
        if (name == 'schema') {
            assertMirrors(root, classes)
            Path markers = Files.createDirectories(classes.resolve('META-INF/klum-model'))
            Files.writeString(markers.resolve('fixture.recipe.Application.properties'), 'model-class=fixture.recipe.OrderRecipe')
            Files.writeString(markers.resolve('fixture.recipe.LegacyApplication.properties'), 'model-class=fixture.recipe.LegacyRecipe')
        }
        Path jar = root.resolve('fixture.' + name + '.jar')
        execute([javaTool('jar'), '--create', '--file', jar.toString(), '-C', classes.toString(), '.'])
        // Neither source discovery nor loose class directories can rescue downstream compilation or execution.
        sources.toFile().deleteDir()
        classes.toFile().deleteDir()
        jar
    }

    private static void assertJavaRejections(Path root, List<Path> inputs) {
        Path source = root.resolve('Invalid.java')
        Map<String, String> rejected = [
                'ambiguous': 'Application.Create.FromClasspath(null)',
                'DelegatingScript': 'Application.Create.From(Script.class, (Closure<?>) null)'
        ]
        rejected.each { diagnostic, call ->
            Files.writeString(source, """
                import fixture.recipe.Application;
                import groovy.lang.Closure;
                import groovy.lang.Script;
                class Invalid { Object create() { return $call; } }
            """.stripIndent())
            String output = execute([javaTool('javac'), '-Xdiags:verbose', '--class-path', paths(inputs),
                                     '-d', root.toString(), source.toString()], false)
            assert output.contains(diagnostic) : output
        }
        Files.delete(source)
    }

    private static void assertMirrors(Path root, Path classes) {
        Path mirrors = Files.createDirectories(root.resolve('mirrors'))
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(
                classes.resolve('fixture/recipe/Application_DSL.class'), mirrors)
        String mirror = Files.readString(mirrors.resolve('fixture/recipe/Application_DSL.java'))
        assert mirror.contains('From(Class<? extends DelegatingScript>')
        assert mirror.contains('Application FromClasspath(@DelegatesTo(strategy = 3, value = Builder.class) Closure<?>')
        assert mirror.contains('FromClasspath(ClassLoader')
        assert mirror.contains('@DelegatesTo(strategy = 3, value = Builder.class) Closure<?>')
        assert mirror.contains('strategy = 3') // Closure.DELEGATE_ONLY in the Java source mirror.
        assert !mirror.contains('FromStruct(Map<?, ?> body, Closure')
        mirrors.toFile().deleteDir()
    }

    private static List<Path> compilerJars() {
        List<Path> jars = ['klumAnnotationsJar', 'klumRuntimeJar', 'klumCompilerJar'].collect { Path.of(System.getProperty(it)) }
        jars.addAll(System.getProperty('java.class.path').split(File.pathSeparator).findAll { entry ->
            String name = Path.of(entry).fileName.toString()
            ['groovy-3.', 'groovy-4.', 'groovy-5.', 'anno-docimal-annotations-', 'anno-docimal-ast-',
             'anno-docimal-global-ast-', 'klum-cast-annotations-', 'klum-cast-compile-', 'klum-cast-spi-',
             'jspecify-'].any { name.startsWith(it) }
        }.collect { Path.of(it) })
        jars.unique()
    }

    private static String paths(List<Path> inputs) { inputs*.toString().join(File.pathSeparator) }
    private static String javaTool(String name) { Path.of(System.getProperty('java.home'), 'bin', name).toString() }

    private static String execute(List<String> command, boolean success = true) {
        assert !command.any { it in ['--add-reads', '--add-exports', '--add-opens', '--patch-module'] }
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().remove('CLASSPATH')
        Process process = builder.start()
        StringBuilder output = new StringBuilder()
        Thread reader = Thread.start { process.inputStream.withReader { output.append(it.text) } }
        boolean completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
        }
        reader.join(5000)
        assert completed : "Timed out: $command\n$output"
        assert (process.exitValue() == 0) == success : "Failed: $command\n$output"
        output.toString()
    }
}
