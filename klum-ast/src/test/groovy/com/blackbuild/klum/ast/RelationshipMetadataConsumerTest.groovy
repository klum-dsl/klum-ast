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

import java.lang.module.ModuleFinder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

/** Runs real compiler and JVM processes with JAR-only Schema inputs and no test-runner classpath. */
@Issue(['856', '868'])
class RelationshipMetadataConsumerTest extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def 'separately compiled Java and dynamic and static Groovy consumers link through public metadata APIs'() {
        when:
        String output = qualify(false)

        then:
        output.trim() == 'metadata-consumers=true'
    }

    def 'the supported Groovy module boundary qualifies metadata without extra exports or opens'() {
        when:
        // Groovy 3 is deliberately classpath-only (ADR 0014); its negative module probe is covered separately.
        String output = GroovySystem.version.startsWith('3.') ? qualifyGroovy3Boundary() : qualify(true)

        then:
        output.trim() == (GroovySystem.version.startsWith('3.') ? 'groovy3-classpath-only=true' : 'metadata-consumers=true')
    }

    private String qualify(boolean named) {
        Path root = temporaryFolder.newFolder(named ? 'named' : 'classpath').toPath()
        List<Path> dependencies = runtimeAndCompilerJars()
        Path probe = buildArtifact(root, 'probe', dependencies, named,
                ['Binding.java', 'JavaProbe.java'], ['Readers.groovy'], '''
                    module fixture.probe {
                        requires com.blackbuild.klum.ast.runtime;
                        exports fixture.annotation;
                        exports fixture.probe;
                    }
                ''')
        if (named) {
            def descriptor = ModuleFinder.of(probe).findAll().first().descriptor()
            assert descriptor.opens().empty // Annotation/probe packages require no reflective value access.
        }
        Path base = buildArtifact(root, 'base', dependencies + [probe], named, [], ['Base.groovy'], '''
                    module fixture.base {
                        requires com.blackbuild.klum.ast.runtime;
                        requires fixture.probe;
                        exports fixture.base;
                        opens fixture.base to com.blackbuild.klum.ast.runtime;
                    }
                ''')
        Path leaf = buildArtifact(root, 'leaf', dependencies + [probe, base], named, [], ['Leaf.groovy'], '''
                    module fixture.leaf {
                        requires transitive fixture.base;
                        requires com.blackbuild.klum.ast.runtime;
                        exports fixture.leaf;
                        opens fixture.leaf to com.blackbuild.klum.ast.runtime;
                    }
                ''')
        List<Path> schemaInputs = dependencies + [probe, base, leaf]
        Path consumer = buildArtifact(root, 'consumer', schemaInputs, named, ['Main.java'], ['GroovyWriters.groovy'], '''
                    module fixture.consumer {
                        requires com.blackbuild.klum.ast.runtime;
                        requires fixture.probe;
                        requires fixture.leaf;
                        opens fixture.consumer to org.apache.groovy;
                    }
                ''')
        // At runtime even the consuming Schema's compiler is absent: only delivered runtime and upstream dependencies.
        List<Path> runtime = (dependencies + [probe, base, leaf, consumer]).findAll { path ->
            path != Path.of(System.getProperty('klumCompilerJar')) &&
                    !path.fileName.toString().startsWith('anno-docimal-ast-') &&
                    !path.fileName.toString().startsWith('anno-docimal-global-ast-') &&
                    !path.fileName.toString().startsWith('klum-cast-compile-')
        }
        assert runtime.every { it.fileName.toString().endsWith('.jar') }
        List<String> command = named ? [javaTool('java'), '--module-path', pathString(runtime),
                '-m', 'fixture.consumer/fixture.consumer.Main'] :
                [javaTool('java'), '--class-path', pathString(runtime), 'fixture.consumer.Main']
        execute(command)
    }

    private Path buildArtifact(Path root, String name, List<Path> inputs, boolean named,
                               List<String> javaSources, List<String> groovySources, String descriptor) {
        Path sources = Files.createDirectories(root.resolve(name + '-sources'))
        Path classes = Files.createDirectories(root.resolve(name + '-classes'))
        (javaSources + groovySources).each { file ->
            getClass().getResourceAsStream('/relationship-metadata/' + name + '/' + file).withCloseable { stream ->
                assert stream != null : "Missing fixture $name/$file"
                Files.copy(stream, sources.resolve(file))
            }
        }
        // Probe annotations/Java generics precede Groovy; the final Java writer depends on compiled Groovy writers.
        if (name == 'probe')
            compileJava(sources, classes, javaSources, inputs + [classes], false)
        if (groovySources) {
            List<Path> compilerInputs = inputs + [classes]
            List<String> command = named ? [javaTool('java'), '--module-path', pathString(inputs),
                    '--add-modules', 'ALL-MODULE-PATH', '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler',
                    '--classpath', pathString(compilerInputs)] :
                    [javaTool('java'), '--class-path', pathString(inputs),
                     'org.codehaus.groovy.tools.FileSystemCompiler', '--classpath', pathString(compilerInputs)]
            execute(command + ['-d', classes.toString()] + groovySources.collect { sources.resolve(it).toString() })
        }
        if (name != 'probe')
            compileJava(sources, classes, javaSources, inputs + [classes], false)
        if (named) {
            Files.writeString(sources.resolve('module-info.java'), descriptor.stripIndent())
            compileJava(sources, classes, ['module-info.java'] + javaSources, inputs, true)
        }
        if (name in ['base', 'leaf']) {
            Path mirrors = Files.createDirectories(root.resolve(name + '-mirrors'))
            // SourceProjector resolves nested declaration names from sibling classfiles, not a dependency classpath.
            // Give projection its own combined bytecode tree; these files never enter a consumer compiler/runtime path.
            Path projectionInputs = Files.createDirectories(root.resolve(name + '-projection-inputs'))
            classes.toFile().eachFileRecurse { file ->
                if (file.name.endsWith('.class')) {
                    Path target = projectionInputs.resolve(classes.relativize(file.toPath()))
                    Files.createDirectories(target.parent)
                    Files.copy(file.toPath(), target)
                }
            }
            inputs.findAll { it.fileName.toString() == 'fixture.base.jar' }.each { baseJar ->
                new JarFile(baseJar.toFile()).withCloseable { jar ->
                    jar.entries().findAll { it.name.startsWith('fixture/base/') && it.name.endsWith('.class') }.each { entry ->
                        Path target = projectionInputs.resolve(entry.name)
                        Files.createDirectories(target.parent)
                        jar.getInputStream(entry).withCloseable { Files.copy(it, target) }
                    }
                }
            }
            String packageName = 'fixture/' + name + '/'
            List<String> types = name == 'base' ? ['RootBase', 'ChildBase'] : ['Root', 'Child']
            types.each { type ->
                new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(
                        projectionInputs.resolve(packageName + type + '_DSL.class'), mirrors)
                String mirror = Files.readString(mirrors.resolve(packageName + type + '_DSL.java'))
                assert mirror.contains('interface Builder<')
                assert !['getModelType', 'getStructure', 'getOwningRelationship', 'KlumBuilderSupport'].any { mirror.contains(it) } : mirror
            }
        }
        Path jar = root.resolve('fixture.' + name + '.jar')
        execute([javaTool('jar'), '--create', '--file', jar.toString(), '-C', classes.toString(), '.'])
        // Later compilers and execution cannot fall back to Schema/annotation source discovery.
        sources.toFile().deleteDir()
        classes.toFile().deleteDir()
        jar
    }

    private void compileJava(Path sources, Path classes, List<String> files, List<Path> inputs, boolean named) {
        if (!files) return
        execute([javaTool('javac'), named ? '--module-path' : '--class-path', pathString(inputs),
                 '-d', classes.toString()] + files.collect { sources.resolve(it).toString() })
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

    private static String qualifyGroovy3Boundary() {
        def descriptor = ModuleFinder.of(Path.of(System.getProperty('klumRuntimeJar'))).findAll().first().descriptor()
        assert descriptor.requires()*.name().contains('org.apache.groovy')
        assert !descriptor.requires()*.name().contains('org.codehaus.groovy')
        'groovy3-classpath-only=true'
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
