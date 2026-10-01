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

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

@Issue('805')
class KlumModelGdslBinaryContractTest extends Specification {
    @TempDir File directory

    def "binary GDSL targets the real public generated Builder with Groovy #generation"() {
        given:
        File producer = new File(directory, 'schema')
        File consumer = new File(directory, 'model')
        File repository = new File(directory, 'repository')
        [producer, consumer].each { it.mkdirs() }
        String groovy = fileNotation(System.getProperty("gdslGroovy$generation"))
        String compiler = classpathNotation(System.getProperty('gdslCompilerClasspath'))
        String runtime = classpathNotation(System.getProperty('gdslRuntimeClasspath'))
        new File(producer, 'settings.gradle').text = "rootProject.name = 'environment-schema'"
        new File(producer, 'build.gradle').text = """
plugins { id 'com.blackbuild.klum-ast-schema'; id 'maven-publish' }
group = 'org.example'
version = '1.0'
klumSchema.groovyVersion = '$generation'
['api', 'compileOnly', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
dependencies { groovy files($groovy); compileOnly files($compiler) }
tasks.withType(GroovyCompile).configureEach { groovyClasspath = files($groovy) }

klumSchema.gdsl {
    publish = true
    mappings { environment { fileNameSuffix = '.environment.groovy'; modelType = 'example.Environment' } }
}
publishing.repositories { maven { name = 'fixture'; url = '${repository.toURI()}' } }
"""
        source(producer, 'src/main/groovy/example/Environment.groovy', '''
package example
import com.blackbuild.klum.ast.DSL
@DSL class Environment { String region }
''')
        runner(producer, 'publishMavenJavaPublicationToFixtureRepository').build()
        // Consumer has only published Schema bytes and framework binary libraries; no producer plugin or mirrors.
        new File(consumer, 'settings.gradle').text = "rootProject.name = 'catalog-model'"
        new File(consumer, 'build.gradle').text = """
plugins { id 'com.blackbuild.klum-ast-model' }
klumModel.groovyVersion = '$generation'
['api', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
repositories { maven { url = '${repository.toURI()}' } }
tasks.withType(GroovyCompile).configureEach { groovyClasspath = files($groovy) }

klumModel {
    schemas { schema 'org.example:environment-schema:1.0' }
    gdsl { enabled = true }
}
dependencies {
    groovy files($groovy)
    implementation files($runtime, $groovy)
    klumGdsl 'org.example:environment-schema'
}
tasks.register('verifyBinaryContract', JavaExec) {
    dependsOn 'classes', 'materializeKlumDslGdsl'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'example.Verify'
    args layout.buildDirectory.dir('generated/klum-dsl-ide/gdsl').get().asFile.absolutePath
}
"""
        source(consumer, 'src/main/groovy/example/Verify.groovy', '''
package example
import com.blackbuild.klum.ast.DSL
import groovy.transform.BaseScript
import groovy.util.DelegatingScript
import java.lang.reflect.Modifier

class Verify {
    static void main(String[] args) {
        File root = new File(args[0])
        File payload = null
        root.eachFileRecurse { if (it.name == 'environment.gdsl') payload = it }
        assert payload != null
        Class resolvedBuilder = null
        def binding = new Binding([
            contributor: { context, Closure contribution -> contribution.call() },
            context: { value -> value }, scriptScope: { -> [:] },
            place: [containingFile: [name: 'catalog.environment.groovy']],
            findClass: { String name ->
                Class type = Class.forName(name.replace('_DSL.Builder', '_DSL$Builder'))
                [qualifiedName: type.name, type: type,
                 modifierList: [findAnnotation: { String annotation -> type.getAnnotation(DSL) }]]
            },
            delegatesTo: { resolved -> resolvedBuilder = resolved.type }
        ])
        new GroovyShell(Verify.classLoader, binding).evaluate(payload)
        assert resolvedBuilder != null
        assert resolvedBuilder.name == 'example.Environment_DSL$Builder'
        assert resolvedBuilder.interface && Modifier.isPublic(resolvedBuilder.modifiers)
        assert resolvedBuilder.getMethod('region', String).declaringClass == resolvedBuilder
        Class recipe = new GroovyClassLoader(Verify.classLoader).parseClass("""
            import groovy.transform.BaseScript
            import groovy.util.DelegatingScript
            @BaseScript DelegatingScript script
            region 'eu'
        """, 'catalog.environment.groovy')
        def model = Environment.Create.From(recipe)
        assert model.region == 'eu'
        assert Environment.Create.With { region 'control' }.region == 'control'
        println "real-builder=${resolvedBuilder.name}; region=${model.region}; groovy=${GroovySystem.version}"
    }
}
''')

        when:
        def result = runner(consumer, 'verifyBinaryContract').build()

        then:
        result.output.contains('real-builder=example.Environment_DSL$Builder; region=eu; groovy=' + generation + '.')
        result.task(':createKlumDslSourceMirrors') == null
        !new File(consumer, 'build/generated/sources/klum-dsl-ide').exists()

        where:
        generation << ['3', '4', '5']
    }

    private static String classpathNotation(String path) {
        path.split(File.pathSeparator).collect { fileNotation(it) }.join(', ')
    }

    private static String fileNotation(String path) {
        "'" + path.replace('\\', '\\\\').replace("'", "\\'") + "'"
    }

    private static void source(File directory, String path, String content) {
        File file = new File(directory, path)
        file.parentFile.mkdirs()
        file.text = content
    }

    private GradleRunner runner(File project, String... arguments) {
        GradleRunner.create().withProjectDir(project).withPluginClasspath().withDebug(true)
                .withTestKitDir(new File(directory, project.name + '-gradle-home'))
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain', '--max-workers=2'])
    }
}
