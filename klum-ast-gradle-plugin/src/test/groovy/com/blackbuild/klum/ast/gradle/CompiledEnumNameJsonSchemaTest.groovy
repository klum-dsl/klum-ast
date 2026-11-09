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

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

@Issue('1064')
class CompiledEnumNameJsonSchemaTest extends Specification {
    @TempDir File directory
    File producer
    File consumer

    def 'packaged Java and Groovy enum value names agree in Struct and JSON Schema on Groovy #lane'() {
        given:
        fixture(lane)
        write(producer, 'domain/src/main/java/enumjson/JavaTier.java', '''
package enumjson;
import com.blackbuild.klum.ast.Struct;
public enum JavaTier {
    @Struct.Name("next-day") NEXT_DAY, ECONOMY;
    static {
        if (!Boolean.getBoolean("enum.control.allowed")) throw new AssertionError("Java enum initialized");
        System.setProperty("enum.java.initialized", "yes");
    }
}
''')
        write(producer, 'domain/src/main/groovy/enumjson/GroovyTier.groovy', '''
package enumjson
import com.blackbuild.klum.ast.Struct
enum GroovyTier {
    @Struct.Name('week-end') WEEK_END,
    ECONOMY

    static {
        if (!Boolean.getBoolean('enum.control.allowed')) throw new AssertionError('Groovy enum initialized')
        System.setProperty('enum.groovy.initialized', 'yes')
    }
}
''')
        write(producer, 'schema/src/main/groovy/enumjson/Delivery.groovy', '''
package enumjson
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.runtime.KlumModelException

@DSL class Delivery {
    JavaTier tier
    List<GroovyTier> windows
}

class EnumInputControl {
    static void main(String[] args) {
        rejects([tier: 'NEXT_DAY'], '/tier')
        rejects([windows: ['WEEK_END']], '/windows/0')
        assert System.getProperty('enum.java.initialized') == null
        assert System.getProperty('enum.groovy.initialized') == null
        def result = Delivery.Create.FromStruct([tier: 'next-day', windows: ['week-end', 'ECONOMY']])
        assert result.tier == JavaTier.NEXT_DAY
        assert result.windows == [GroovyTier.WEEK_END, GroovyTier.ECONOMY]
        assert System.getProperty('enum.java.initialized') == 'yes'
        assert System.getProperty('enum.groovy.initialized') == 'yes'
    }

    private static void rejects(Map document, String path) {
        try {
            Delivery.Create.FromStruct(document, 'delivery.yaml')
            throw new AssertionError('raw enum name was accepted')
        } catch (KlumModelException failure) {
            assert failure.message.contains("delivery.yaml at '" + path + "'")
        }
    }
}
''')
        new File(consumer, 'build.gradle').append('''
tasks.register('enumInputControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'enumjson.EnumInputControl'
    systemProperty 'enum.control.allowed', 'true'
}
''')
        runner(producer, ':schema:jar').build()

        when:
        def result = runner(consumer, 'generateKlumJsonSchema', 'enumInputControl').build()
        def generated = new JsonSlurper().parse(new File(consumer,
                'build/generated/klum-json-schema/enumjson.Delivery.schema.json'))
        def fields = generated.'$defs'.'enumjson.Delivery_DSL$Builder'.properties

        then:
        result.task(':generateKlumJsonSchema').outcome == TaskOutcome.SUCCESS
        result.task(':enumInputControl').outcome == TaskOutcome.SUCCESS
        generated.'$schema' == 'https://json-schema.org/draft/2020-12/schema'
        fields.tier.type == 'string'
        fields.tier.enum == ['ECONOMY', 'next-day']
        fields.windows.items == [type: 'string', enum: ['ECONOMY', 'week-end']]

        when:
        def javaSource = new File(producer, 'domain/src/main/java/enumjson/JavaTier.java')
        javaSource.text = javaSource.text.replace('next-day', 'ECONOMY')
        def invalid = runner(producer, ':schema:compileGroovy').buildAndFail()

        then:
        invalid.output.contains("Duplicate structured enum token 'ECONOMY' in enumjson.JavaTier for constants NEXT_DAY and ECONOMY")

        where:
        lane << ['3', '4', '5']
    }

    private void fixture(String lane) {
        producer = new File(directory, 'producer')
        consumer = new File(directory, 'consumer')
        String groovy = quote(System.getProperty('gdslGroovy' + lane))
        String compiler = classpath(System.getProperty('gdslCompilerClasspath'))
        String runtime = classpath(System.getProperty('gdslRuntimeClasspath'))
        write(producer, 'settings.gradle', "include 'domain', 'schema'")
        write(producer, 'build.gradle', """
subprojects {
    apply plugin: 'groovy'
    dependencies { implementation files($groovy, $compiler) }
    tasks.withType(GroovyCompile).configureEach {
        groovyClasspath = files($groovy)
        groovyOptions.parameters = true
        groovyOptions.optimizationOptions.groovydoc = true
    }
}
project(':schema') { dependencies { implementation project(':domain') } }
""")
        write(consumer, 'settings.gradle', "rootProject.name = 'enum-name-consumer'")
        String artifacts = ['domain', 'schema'].collect { quote(new File(producer, "${it}/build/libs/${it}.jar").path) }.join(', ')
        write(consumer, 'build.gradle', """
plugins { id 'com.blackbuild.klum-ast-schema' }
['api', 'implementation', 'compileOnly', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
klumSchema.jsonSchema {
    rootTypes.set(['enumjson.Delivery'])
    classpath.setFrom(files($artifacts, $runtime, $groovy))
}
""")
    }

    private static void write(File root, String path, String text) {
        File file = new File(root, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    private static String classpath(String value) { value.split(File.pathSeparator).collect { quote(it) }.join(', ') }
    private static String quote(String value) { "'" + value.replace('\\', '\\\\').replace("'", "\\'") + "'" }

    private GradleRunner runner(File project, String... arguments) {
        Properties metadata = new Properties()
        getClass().classLoader.getResourceAsStream('plugin-under-test-metadata.properties').withCloseable { metadata.load(it) }
        List<File> dependencies = metadata.getProperty('implementation-classpath').split(File.pathSeparator)
                .collect { new File(it) }.findAll { it.isFile() }
        GradleRunner.create().withProjectDir(project)
                .withPluginClasspath(dependencies + new File(System.getProperty('schemaPluginJar')))
                .withDebug(true).withArguments(arguments.toList() + ['--stacktrace', '--console=plain', '--max-workers=2'])
    }
}
