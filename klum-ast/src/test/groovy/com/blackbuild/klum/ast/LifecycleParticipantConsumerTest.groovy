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
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Issue('867')
class LifecycleParticipantConsumerTest extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def 'Java handlers and separately compiled Java and static and dynamic Groovy consumers use public contracts'() {
        when:
        String output = qualify('valid')

        then:
        output.trim() == 'participant-consumers=true'
    }

    def 'runtime rejects a precompiled declaration replaced after Schema compilation (#signature)'() {
        when:
        String output = qualify(signature)

        then:
        output.trim() == 'binary-defense=true'

        where:
        signature << ['mismatch', 'raw', 'unresolved', 'creators', 'phase']
    }

    def 'qualifies #phase #form composition in a compiled #author annotation library with separate Schema and consumer'() {
        given:
        Path root = temporaryFolder.newFolder().toPath()
        Path sources = Files.createDirectories(root.resolve('sources'))
        Path classes = Files.createDirectories(root.resolve('classes'))
        List<Path> jars = runtimeAndCompilerJars()
        ['Composition.java', 'CompositionDomain.groovy', 'CompositionApplication.groovy', 'CompositionMain.java'].each { file ->
            getClass().getResourceAsStream('/lifecycle-participants/' + file).withCloseable {
                Files.copy(it, sources.resolve(file))
            }
        }
        compileGroovy(sources, classes, jars, ['CompositionDomain.groovy'])
        Path domain = jar(root, classes, 'domain.jar')
        classes.toFile().deleteDir()
        Files.createDirectories(classes)
        String declaration = Files.readString(sources.resolve('Composition.java'))
        if (form == 'container') {
            declaration = declaration.replace('@LifecycleMutator(phase = AutoLink.class, handler = ZFirst.class)\n    @LifecycleMutator(phase = AutoLink.class, handler = ASecond.class)',
                    '@LifecycleMutator.List({@LifecycleMutator(phase = AutoLink.class, handler = ZFirst.class), @LifecycleMutator(phase = AutoLink.class, handler = ASecond.class)})')
        }
        if (form == 'mixed') {
            declaration = declaration.replace('@LifecycleMutator(phase = AutoLink.class, handler = ASecond.class)',
                    '@LifecycleMutator.List({@LifecycleMutator(phase = AutoLink.class, handler = ASecond.class)})')
        }
        if (author == 'Groovy') declaration = declaration.replace('List({', 'List([').replace('ASecond.class)})', 'ASecond.class)])')
        String phasePackage = phase in ['AutoCreate', 'AutoLink'] ? 'com.blackbuild.klum.ast.layer3' : 'com.blackbuild.klum.ast'
        declaration = declaration.replace('import com.blackbuild.klum.ast.layer3.AutoLink;', "import $phasePackage.$phase;")
                .replace('phase = AutoLink.class', "phase = ${phase}.class")
        String librarySource = author == 'Java'  ? 'Composition.java' : 'Composition.groovy'
        Files.writeString(sources.resolve(librarySource), declaration)
        if (author == 'Java') compileJava(sources, classes, jars + [domain], [librarySource])
        else compileGroovy(sources, classes, jars + [domain], [librarySource])
        Path library = jar(root, classes, 'annotations.jar')
        classes.toFile().deleteDir()
        Files.createDirectories(classes)
        compileGroovy(sources, classes, jars + [domain, library], ['CompositionApplication.groovy'])
        Path schema = jar(root, classes, 'schema.jar')
        classes.toFile().deleteDir()
        Files.createDirectories(classes)
        if (form == 'mixed') {
            Path main = sources.resolve('CompositionMain.java')
            String consumerSource = Files.readString(main)
            // Mixed singular/container relative order is unspecified; only both mutations after creation are required.
            consumerSource = consumerSource.replace('!result.getSupplied().getValue().equals("created:first:second")',
                    '!(result.getSupplied().getValue().equals("created:first:second") || result.getSupplied().getValue().equals("created:second:first"))')
                    .replace('!result.getExisting().getValue().equals("configured:first:second")',
                    '!(result.getExisting().getValue().equals("configured:first:second") || result.getExisting().getValue().equals("configured:second:first"))')
            Files.writeString(main, consumerSource)
        }
        compileJava(sources, classes, jars + [domain, library, schema], ['CompositionMain.java'])
        Path consumer = jar(root, classes, 'consumer.jar')
        classes.toFile().deleteDir()
        sources.toFile().deleteDir()

        when:
        String output = execute([javaTool('java'), '--class-path', pathString(runtimeJars(jars) + [domain, library, schema, consumer]),
                                 'participant.fixture.CompositionMain'])

        then:
        output.trim() == 'compiled-composition=true'

        where:
        author   | form        | phase
        'Java'   | 'repeated'  | 'AutoLink'
        'Java'   | 'container' | 'AutoLink'
        'Groovy' | 'repeated'  | 'AutoLink'
        'Groovy' | 'container' | 'AutoLink'
        'Java'   | 'mixed'     | 'AutoLink'
        'Groovy' | 'mixed'     | 'AutoLink'
        'Java'   | 'repeated'  | 'AutoCreate'
        'Java'   | 'repeated'  | 'Default'
        'Java'   | 'repeated'  | 'PostTree'
    }

    private static Path jar(Path root, Path classes, String name) {
        Path artifact = root.resolve(name)
        execute([javaTool('jar'), '--create', '--file', artifact.toString(), '-C', classes.toString(), '.'])
        artifact
    }

    private static List<Path> runtimeJars(List<Path> jars) {
        jars.findAll { path ->
            path != Path.of(System.getProperty('klumCompilerJar')) &&
                    !path.fileName.toString().startsWith('anno-docimal-ast-') &&
                    !path.fileName.toString().startsWith('anno-docimal-global-ast-') &&
                    !path.fileName.toString().startsWith('klum-cast-compile-')
        }
    }

    private String qualify(String signature) {
        Path root = temporaryFolder.newFolder().toPath()
        Path sources = Files.createDirectories(root.resolve('sources'))
        Path classes = Files.createDirectories(root.resolve('classes'))
        List<Path> jars = runtimeAndCompilerJars()
        List<String> fixtures = ['Knowledge.groovy', 'Binding.java', 'BindFacts.java', 'SupplyDomain.java',
                                 'Application.groovy', 'Writers.groovy', 'Main.java']
        fixtures.each { file ->
            getClass().getResourceAsStream('/lifecycle-participants/' + file).withCloseable {
                Files.copy(it, sources.resolve(file))
            }
        }
        compileGroovy(sources, classes, jars, ['Knowledge.groovy'])
        compileJava(sources, classes, jars, ['Binding.java', 'BindFacts.java', 'SupplyDomain.java'])
        compileGroovy(sources, classes, jars, ['Application.groovy', 'Writers.groovy'])
        compileJava(sources, classes, jars, ['Main.java'])
        if (signature == 'creators') {
            Path binding = sources.resolve('Binding.java')
            String declaration = Files.readString(binding).replace(
                    '@LifecycleCreator(phase = AutoLink.class, handler = SupplyDomain.class)',
                    '@LifecycleCreator.List({@LifecycleCreator(phase = AutoLink.class, handler = SupplyDomain.class), @LifecycleCreator(phase = AutoLink.class, handler = SupplyDomain.class)})')
            Files.writeString(binding, declaration)
            compileJava(sources, classes, jars, ['Binding.java'])
            Path main = sources.resolve('Main.java')
            Files.writeString(main, Files.readString(main).replace('annotation parameter must resolve exactly', 'Competing lifecycle creators'))
            compileJava(sources, classes, jars, ['Main.java'])
        } else if (signature == 'phase') {
            Path binding = sources.resolve('Binding.java')
            Files.writeString(binding, Files.readString(binding)
                    .replace('import com.blackbuild.klum.ast.layer3.AutoLink;', 'import com.blackbuild.klum.ast.Validate;')
                    .replace('phase = AutoLink.class', 'phase = Validate.class'))
            compileJava(sources, classes, jars, ['Binding.java'])
            Path main = sources.resolve('Main.java')
            Files.writeString(main, Files.readString(main).replace('annotation parameter must resolve exactly',
                    'support only AutoCreate, AutoLink, Default and PostTree'))
            compileJava(sources, classes, jars, ['Main.java'])
        } else if (signature != 'valid') {
            getClass().getResourceAsStream('/lifecycle-participants/InvalidBindFacts.java').withCloseable {
                String invalid = it.text
                if (signature == 'raw') invalid = invalid.replace('LifecycleMutationHandler<Deprecated>', 'LifecycleMutationHandler').replace('LifecycleMutationContext<Deprecated>', 'LifecycleMutationContext')
                if (signature == 'unresolved') invalid = invalid.replace('import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler;', 'import java.lang.annotation.Annotation;\nimport com.blackbuild.klum.ast.runtime.LifecycleMutationHandler;').replace('public class BindFacts', 'public class BindFacts<A extends Annotation>').replace('<Deprecated>', '<A>')
                Files.writeString(sources.resolve('BindFacts.java'), invalid)
            }
            compileJava(sources, classes, jars, ['BindFacts.java'])
        }
        Path artifact = root.resolve('participants.jar')
        execute([javaTool('jar'), '--create', '--file', artifact.toString(), '-C', classes.toString(), '.'])
        sources.toFile().deleteDir()
        classes.toFile().deleteDir()
        List<Path> runtime = runtimeJars(jars) + [artifact]
        execute([javaTool('java'), '--class-path', pathString(runtime), 'participant.fixture.Main'] +
                (signature != 'valid' ? ['invalid'] : []))
    }

    private static void compileGroovy(Path sources, Path classes, List<Path> jars, List<String> files) {
        execute([javaTool('java'), '--class-path', pathString(jars), 'org.codehaus.groovy.tools.FileSystemCompiler',
                 '--classpath', pathString(jars + [classes]), '-d', classes.toString()] +
                files.collect { sources.resolve(it).toString() })
    }

    private static void compileJava(Path sources, Path classes, List<Path> jars, List<String> files) {
        execute([javaTool('javac'), '--class-path', pathString(jars + [classes]), '-d', classes.toString()] +
                files.collect { sources.resolve(it).toString() })
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
