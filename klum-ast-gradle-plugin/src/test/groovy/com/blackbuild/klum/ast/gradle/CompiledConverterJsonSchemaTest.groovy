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

@Issue('981')
class CompiledConverterJsonSchemaTest extends Specification {
    @TempDir File directory
    File producer
    File consumer
    String generation

    def 'packaged Groovy #lane producer projects exact converter parameters while preserving field ownership'() {
        given:
        fixture(lane)
        runner(producer, ':schema:jar').build()
        ['domain', 'parent', 'schema'].each {
            assert new File(producer, "$it/src").deleteDir()
            assert new File(producer, "$it/build/classes").deleteDir()
        }

        when:
        runner(consumer, 'generateKlumJsonSchema').build()
        def schema = document()
        def fields = schema.'$defs'.'converterjson.Service_DSL$Builder'.properties
        File evidence = new File(System.getProperty('schemaValidationDirectory'), 'converter-' + lane)
        evidence.mkdirs()
        new File(evidence, 'converter.schema.json').bytes = output().bytes

        then:
        fields.'port-number'.type == 'string'
        fields.'port-number'.allOf == [[pattern: '^[0-9]{1,8}$'], [not: [type: 'string', pattern: '[^ -~]']]]
        fields.'port-number'.'x-klum-converter-constraint-source' == 'converterjson.PortNumber#<init>(Ljava/lang/String;)V parameter[0]'
        fields.'port-number'.description.contains('Inherited port prose.')
        fields.'port-number'.examples == ['1234']
        fields.'port-number'.deprecated
        fields.factory.allOf == fields.'port-number'.allOf
        !fields.factory.containsKey('deprecated')
        fields.factory.'x-klum-converter-constraint-source'.contains('#parse(')
        fields.'field-omit'.allOf == fields.factory.allOf
        fields.'field-text'.allOf == fields.factory.allOf
        fields.'domain-pattern'.allOf == fields.factory.allOf
        fields.'domain-pattern'.description.contains('converted domain value')
        fields.'text-only'.type == 'string'
        !fields.'text-only'.containsKey('allOf')
        fields.'text-only'.description.contains('parameter projection policy TEXT_ONLY')
        fields.omitted == [type: 'string', description: 'Retained prose.', examples: ['retained example'], deprecated: true]
        fields.absent == [type: 'string', description: 'Absent constraint.']
        fields.memory.description.contains('unsupported Pattern source syntax')
        !fields.memory.containsKey('allOf')
        fields.flagged.description.contains('nonempty Pattern flags')
        !fields.flagged.containsKey('allOf')
        !fields.'group-redefined'.containsKey('allOf')
        fields.'group-redefined'.description.contains('group redefinition/sequence')
        !fields.containsKey('hidden')
        !schema.'$defs'.keySet().any { it.contains('PortNumber') || it.contains('FactoryPort') }
        !schema.'$defs'.'converterjson.Service_DSL$Builder'.containsKey('required')
        !new File(directory, 'executed').exists()

        when: 'the unchanged generation is cacheable'
        def repeated = runner(consumer, 'generateKlumJsonSchema').build()

        then:
        repeated.task(':generateKlumJsonSchema').outcome == TaskOutcome.UP_TO_DATE

        when: 'a deliberate control initializes the domain outside generation'
        def initialized = runner(consumer, 'initializationControl').buildAndFail()

        then:
        initialized.output.contains('domain initialized')
        new File(directory, 'executed').text == 'domain'

        where:
        lane << ['3', '4', '5']
    }

    def 'packaged projection excludes a converter overridden by an ignored binary descendant method'() {
        given:
        fixture('3')
        write(producer, 'schema/src/main/groovy/converterjson/Overriding.groovy', '''
package converterjson
import com.blackbuild.klum.ast.*
@DSL @Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) class Overriding extends Base {
    @Builder.Method @Struct.Name('other') @Struct.Ignore
    PortNumber port(String raw) { throw new AssertionError('authored override executed') }
}
''')
        runner(producer, ':schema:jar').build()
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['converterjson.Overriding'])\n")

        when:
        runner(consumer, 'generateKlumJsonSchema').build()
        def schema = new JsonSlurper().parse(new File(consumer,
                'build/generated/klum-json-schema/converterjson.Overriding.schema.json'))
        def root = schema.'$defs'.'converterjson.Overriding_DSL$Builder'

        then:
        !root.properties.containsKey('port-number')
        !root.properties.containsKey('other')
        root.additionalProperties == false
        !new File(directory, 'executed').exists()
    }

    def 'changed domain followed by Schema recompilation fails atomically even through omitted owned input'() {
        given:
        fixture('3')
        runner(producer, ':schema:jar').build()
        runner(consumer, 'generateKlumJsonSchema').build()
        def previous = output().parentFile.listFiles().collectEntries { [(it.name): it.bytes.toList()] }
        File domain = new File(producer, 'domain/src/main/java/converterjson/ChildOnly.java')
        domain.text = domain.text.replace('[0-9]{1,8}', '[')

        when:
        runner(producer, ':schema:jar', '--rerun-tasks').build()
        def failed = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        failed.output.contains('Unclosed character class')
        failed.output.contains('parameter[0]')
        failed.output.contains('ChildOnly')
        output().parentFile.listFiles().collectEntries { [(it.name): it.bytes.toList()] } == previous
        !new File(directory, 'executed').exists()
    }

    private void fixture(String lane) {
        generation = lane
        producer = new File(directory, 'producer')
        consumer = new File(directory, 'consumer')
        producer.mkdirs()
        consumer.mkdirs()
        String groovy = quote(System.getProperty('gdslGroovy' + lane))
        String compiler = classpath(System.getProperty('gdslCompilerClasspath'))
        String runtime = classpath(System.getProperty('gdslRuntimeClasspath'))
        String api = quote(System.getProperty('schemaValidationApi'))
        write(producer, 'settings.gradle', "include 'domain', 'parent', 'schema'")
        write(producer, 'build.gradle', """
subprojects {
    apply plugin: 'groovy'
    dependencies { implementation files($groovy, $compiler, $api) }
    tasks.withType(GroovyCompile).configureEach {
        groovyClasspath = files($groovy)
        groovyOptions.optimizationOptions.groovydoc = true
    }
}
project(':parent') { dependencies { implementation project(':domain') } }
project(':schema') { dependencies { implementation project(':domain'); implementation project(':parent') } }
""")
        domain('PortNumber', '@Pattern(regexp="[0-9]{1,8}")')
        domain('TextOnly', '@Pattern(regexp="[0-9]{1,8}") @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.TEXT_ONLY)')
        domain('Omitted', '@Pattern(regexp="[0-9]{1,8}") @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT)')
        domain('ChildOnly', '@Pattern(regexp="[0-9]{1,8}") @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT)')
        domain('GroupRedefined', '@Pattern(regexp="[0-9]{1,8}")', '@GroupSequence({GroupRedefined.class})')
        domain('Absent', '')
        domain('Memory', '@Pattern(regexp="[0-9]+(?:\\\\.[0-9]+)?(?:[kMGTPE]|[KMGTPE]i)?")')
        domain('Flagged', '@Pattern(regexp="[0-9]{1,8}", flags=Pattern.Flag.CASE_INSENSITIVE)')
        write(producer, 'domain/src/main/groovy/converterjson/FactoryPort.groovy', '''
package converterjson
import jakarta.validation.constraints.Pattern
class FactoryPort {
    private FactoryPort() {}
    /** Factory prose must not become field prose. */
    @Deprecated
    static FactoryPort parse(@Pattern(regexp='[0-9]{1,8}') String raw) { throw new AssertionError('converter body') }
}
''')
        write(producer, 'parent/src/main/groovy/converterjson/Base.groovy', '''
package converterjson
import com.blackbuild.klum.ast.*
@DSL class Base {
    /** Inherited port prose. */
    @Struct.Name('port-number') @Struct.Examples(['1234']) @Deprecated
    @Converters(includeConstructors=true) PortNumber port
}
''')
        write(producer, 'schema/src/main/groovy/converterjson/Service.groovy', '''
package converterjson
import com.blackbuild.klum.ast.*
import jakarta.validation.constraints.Pattern
@DSL @Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) class Service extends Base {
    static { throw new AssertionError('Schema initialized') }
    FactoryPort factory
    @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT) FactoryPort fieldOmit
    @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.TEXT_ONLY) FactoryPort fieldText
    @Pattern(regexp='[A-Z]{2}') FactoryPort domainPattern
    @Converters(includeConstructors=true) TextOnly textOnly
    /** Retained prose. */
    @Struct.Examples(['retained example']) @Deprecated
    @Converters(includeConstructors=true) Omitted omitted
    /** Absent constraint. */
    @Converters(includeConstructors=true) Absent absent
    @Converters(includeConstructors=true) Memory memory
    @Converters(includeConstructors=true) Flagged flagged
    @Converters(includeConstructors=true) GroupRedefined groupRedefined
    @Struct.Ignore @Converters(includeConstructors=true) PortNumber hidden
    Child child
    String calculated = { throw new AssertionError('initializer executed') }()
    @Default(code={ throw new AssertionError('default executed') }) String computed
    @PostCreate void created() { throw new AssertionError('lifecycle executed') }
    @Validate void validate() { throw new AssertionError('validator executed') }
}
@DSL class Child { @Converters(includeConstructors=true) ChildOnly childValue }
class InitializationControl {
    static void main(String[] args) { Class.forName('converterjson.PortNumber', true, InitializationControl.classLoader) }
}
''')
        write(consumer, 'settings.gradle', "rootProject.name = 'converter-consumer'")
        String artifacts = ['domain', 'parent', 'schema'].collect { quote(new File(producer, "${it}/build/libs/${it}.jar").path) }.join(', ')
        write(consumer, 'build.gradle', """
plugins { id 'com.blackbuild.klum-ast-schema' }
['api', 'implementation', 'compileOnly', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
klumSchema.jsonSchema {
    rootTypes.set(['converterjson.Service'])
    classpath.setFrom(files($artifacts, $runtime, $groovy, $api))
}
tasks.register('initializationControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'converterjson.InitializationControl'
}
""")
    }

    private void domain(String name, String annotations, String classAnnotation = '') {
        write(producer, "domain/src/main/java/converterjson/${name}.java", """
package converterjson;
import com.blackbuild.klum.ast.Struct;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.GroupSequence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
$classAnnotation
public class $name {
    static {
        try { Files.writeString(Path.of(${quote(new File(directory, 'executed').path).replace("'", '"')}), "domain"); }
        catch (IOException error) { throw new AssertionError(error); }
        if (Boolean.parseBoolean("true")) throw new AssertionError("domain initialized");
    }
    public $name($annotations String raw) { throw new AssertionError("constructor executed"); }
}
""")
    }

    private File output() { new File(consumer, 'build/generated/klum-json-schema/converterjson.Service.schema.json') }
    private Map document() { (Map) new JsonSlurper().parse(output()) }
    private static void write(File root, String path, String text) {
        File file = new File(root, path)
        file.parentFile.mkdirs()
        file.text = text
    }
    private static String quote(String path) { "'" + path.replace('\\', '\\\\').replace("'", "\\'") + "'" }
    private static String classpath(String value) { value.split(File.pathSeparator).collect { quote(it) }.join(', ') }
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
