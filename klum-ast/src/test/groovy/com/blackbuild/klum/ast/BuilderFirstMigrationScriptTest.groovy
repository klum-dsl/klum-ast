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

@Issue("710")
class BuilderFirstMigrationScriptTest extends Specification {

    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "rewrites only direct type-qualified Template creation calls"() {
        given:
        Path schemaModule = temporaryFolder.newFolder('schema-module').toPath()
        Path source = schemaModule.resolve('src/main/groovy/TemplateMigration.groovy')
        Files.createDirectories(source.parent)
        Files.writeString(source, '''
            class TemplateMigration {
                void configure() {
                    Service.Create.Template(name: "catalog")
                    Service.Create.Template { region "eu-central" }
                    Service.Create.TemplateFrom(file)
                    Service.Template.Create(name: "billing")
                    Service.Template.Create { region "us-east" }
                    Service.Template.CreateFrom(file)

                    Service.Template.With(template) { }
                    Service.Template.WithAll([template]) { }
                    type.Template.Create { }
                    custom().Template.Create { }
                    holder.Service.Template.Create { }
                    def reference = Service.Template.&Create
                    def literal = "Service.Template.Create { }"
                    def script = """Service.Create.Template { }"""
                    // Service.Create.Template { }
                    /* Service.Template.CreateFrom(file) */
                }
            }
        '''.stripIndent())
        git(schemaModule, 'init', '-q')
        git(schemaModule, 'config', 'user.email', 'migration-test@example.invalid')
        git(schemaModule, 'config', 'user.name', 'Migration Test')
        git(schemaModule, 'add', '.')
        git(schemaModule, 'commit', '-qm', 'fixture')

        when:
        ProcessResult result = run(schemaModule, 'bash', migrationScript().toString())

        then:
        result.exitCode == 0
        result.output.contains('Starter edits applied')
        Files.readString(source).stripIndent() == '''
            class TemplateMigration {
                void configure() {
                    Service.Create.Template.With(name: "catalog")
                    Service.Create.Template.With { region "eu-central" }
                    Service.Create.Template.From(file)
                    Service.Create.Template.With(name: "billing")
                    Service.Create.Template.With { region "us-east" }
                    Service.Create.Template.From(file)

                    Service.Template.With(template) { }
                    Service.Template.WithAll([template]) { }
                    type.Template.Create { }
                    custom().Template.Create { }
                    holder.Service.Template.Create { }
                    def reference = Service.Template.&Create
                    def literal = "Service.Template.Create { }"
                    def script = """Service.Create.Template { }"""
                    // Service.Create.Template { }
                    /* Service.Template.CreateFrom(file) */
                }
            }
        '''.stripIndent()
    }

    private static Path migrationScript() {
        Path directory = Path.of('').toAbsolutePath()
        while (directory != null) {
            Path candidate = directory.resolve('docs/user/assets/migrate-3x-to-4x-builder-first.sh')
            if (Files.exists(candidate))
                return candidate
            directory = directory.parent
        }
        throw new IllegalStateException('Cannot locate the Builder-first migration helper asset.')
    }

    private static void git(Path directory, String... arguments) {
        ProcessResult result = run(directory, 'git', *arguments)
        assert result.exitCode == 0 : result.output
    }

    private static ProcessResult run(Path directory, String... command) {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start()
        new ProcessResult(process.waitFor(), process.inputStream.text)
    }

    private static class ProcessResult {

        final int exitCode
        final String output

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode
            this.output = output
        }
    }
}
