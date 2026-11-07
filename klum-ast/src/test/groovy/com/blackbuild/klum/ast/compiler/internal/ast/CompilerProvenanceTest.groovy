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
package com.blackbuild.klum.ast.compiler.internal.ast

import groovy.io.FileType
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.Specification

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** Exercises real code-source lookup in this JVM so baseline JaCoCo also sees the rejection paths. */
@Issue('896')
class CompilerProvenanceTest extends Specification {
    private static final String RESOURCE = 'META-INF/klum-ast/compiler.properties'
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def 'the built compiler retains provenance in #artifactProperty'() {
        given:
        Path artifact = Path.of(System.getProperty(artifactProperty))
        Path decoy = decoyResource()

        when:
        String version = versionAt(artifact, decoy)

        then:
        version == System.getProperty('klumVersion')
        if (artifactProperty == 'klumCompilerJar') {
            new JarFile(artifact.toFile()).withCloseable { jar ->
                assert jar.entries().findAll { it.name == RESOURCE }.size() == 1
            }
        } else {
            assert Files.isRegularFile(artifact.resolve(RESOURCE))
        }

        where:
        artifactProperty << ['klumCompilerJar', 'klumCompilerClasses']
    }

    def 'code-source #kind preserves the exact emitter #version despite a preceding decoy resource'() {
        given:
        Path artifact = compilerArtifact(jar, 'compilerVersion=' + version)
        Path decoy = decoyResource()

        expect:
        versionAt(artifact, decoy) == version

        where:
        jar   | kind        | version
        false | 'directory' | '4.2.0-dev.473+producer'
        false | 'directory' | '4.2.0-rc.7+producer'
        true  | 'JAR'       | '4.2.0-dev.473+producer'
        true  | 'JAR'       | '4.2.0-rc.7+producer'
    }

    def 'code-source #kind rejects #problem metadata rather than borrowing the decoy'() {
        given:
        Path artifact = compilerArtifact(jar, metadata)
        Path decoy = decoyResource()

        when:
        versionAt(artifact, decoy)

        then:
        InvocationTargetException failure = thrown()
        failure.cause instanceof IllegalStateException
        failure.cause.message.contains('Cannot establish KlumAST compiler provenance from the emitting compiler artifact')
        failure.cause.cause != null

        where:
        [jar, problem, metadata] << [
                [false, true],
                [
                        ['missing', null],
                        ['blank', 'compilerVersion='],
                        ['missing version', 'unrelated=value'],
                        ['duplicate', 'compilerVersion=one\ncompilerVersion=two'],
                        ['extra property', 'compilerVersion=one\nunrelated=value'],
                        ['malformed', 'compilerVersion=\\uZZZZ']
                ]
        ].combinations().collect { form, invalid -> [form, invalid[0], invalid[1]] }
        kind = jar ? 'JAR' : 'directory'
    }

    private Path compilerArtifact(boolean jar, String metadata) {
        Path classes = temporaryFolder.newFolder().toPath()
        [CompilerProvenance.name, CompilerProvenance.name + '$1'].each { name ->
            String resource = name.replace('.', '/') + '.class'
            Path target = classes.resolve(resource)
            Files.createDirectories(target.parent)
            CompilerProvenance.classLoader.getResourceAsStream(resource).withCloseable { input ->
                assert input != null
                Files.copy(input, target)
            }
        }
        if (metadata != null) {
            Path target = classes.resolve(RESOURCE)
            Files.createDirectories(target.parent)
            Files.writeString(target, metadata)
        }
        if (!jar) {
            return classes
        }
        Path output = temporaryFolder.newFile('compiler-' + classes.fileName + '.jar').toPath()
        new JarOutputStream(Files.newOutputStream(output)).withCloseable { target ->
            classes.toFile().eachFileRecurse(FileType.FILES) { File file ->
                target.putNextEntry(new JarEntry(classes.relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char)))
                target.write(Files.readAllBytes(file.toPath()))
                target.closeEntry()
            }
        }
        output
    }

    private Path decoyResource() {
        Path decoy = temporaryFolder.newFolder().toPath()
        Path resource = decoy.resolve(RESOURCE)
        Files.createDirectories(resource.parent)
        Files.writeString(resource, 'compilerVersion=unrelated-consumer')
        decoy
    }

    private static String versionAt(Path artifact, Path decoy) {
        new URLClassLoader([decoy.toUri().toURL(), artifact.toUri().toURL()] as URL[], ClassLoader.platformClassLoader).withCloseable { loader ->
            Class<?> compiler = loader.loadClass(CompilerProvenance.name)
            Method version = compiler.getDeclaredMethod('version')
            version.accessible = true
            version.invoke(null) as String
        }
    }
}
