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
import spock.lang.See
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.TempDir

@Issue('529')
class KlumJsonSchemaGenerationTest extends Specification {
    @TempDir File directory
    File consumer
    File producer
    File repository
    File trap
    boolean packagedPlugin

    @Issue(['529', '950', '954'])
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#generating-files')
    def 'generate structural input schemas from explicit DSL Model roots with Groovy #generation'() {
        given:
        fixture(generation)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.addAll(['example.KeylessUses', 'example.FallbackKey', 'example.EmptyRename', 'example.IgnoredOperationCollision'])\n")

        when:
        def result = run('generateKlumJsonSchema')
        def schema = document('example.Deployment')
        File validation = new File(System.getProperty('schemaValidationDirectory'), generation)
        validation.mkdirs()
        ['Deployment', 'Service', 'KeylessUses', 'FallbackKey', 'EmptyRename', 'IgnoredOperationCollision'].each { model ->
            new File(validation, model.toLowerCase() + '.schema.json').bytes = output('example.' + model).bytes
        }
        def root = schema.get('$defs').get('example.Deployment_DSL$Builder')
        def child = schema.get('$defs').get('example.Service_DSL$Builder::keyless')
        def selfContained = document('example.Service').get('$defs').get('example.Service_DSL$Builder')
        def secret = schema.get('$defs').get('example.SecretReference_DSL$Builder')

        then:
        result.task(':generateKlumJsonSchema').outcome == TaskOutcome.SUCCESS
        result.task(':createKlumDslSourceMirrors') == null
        schema.get('$schema') == 'https://json-schema.org/draft/2020-12/schema'
        schema.get('$ref') == '#/$defs/example.Deployment_DSL%24Builder'
        !new File(output('example.Deployment').parentFile, 'example.Deployment_DSL$Builder.schema.json').exists()
        root.get('additionalProperties') == false
        root.get('properties').get('region').get('description').contains('Inherited region documentation.')
        root.get('properties').get('services').get('additionalProperties').get('$ref') == '#/$defs/example.Service_DSL%24Builder%3A%3Akeyless'
        root.get('properties').get('credentialSecret').get('$ref') == '#/$defs/example.SecretReference_DSL%24Builder'
        root.get('properties').get('configure-input').get('additionalProperties').get('items').get('type') == 'string'
        root.get('properties').get('replicas').get('minimum') == -2147483648
        child.get('properties').get('mode').get('enum') == ['FAST', 'SAFE']
        !child.get('properties').containsKey('service/id~')
        !child.containsKey('required')
        child.get('properties').get('nested').get('$ref') == '#/$defs/example.Service_DSL%24Builder'
        selfContained.get('properties').get('service/id~') == [type: 'string']
        selfContained.get('required') == ['service/id~']
        root.get('properties').get('service').get('$ref') == '#/$defs/example.Service_DSL%24Builder'
        root.get('properties').get('fixed').get('$ref') == '#/$defs/example.Service_DSL%24Builder%3A%3Akeyless'
        root.get('properties').get('derived').get('$ref') == '#/$defs/example.Service_DSL%24Builder%3A%3Akeyless'
        child.get('properties').get('children').get('additionalProperties').get('$ref') == '#/$defs/example.Service_DSL%24Builder%3A%3Akeyless'
        schema.get('$defs').get('example.FixedCluster_DSL$Builder').get('properties').get('catalog').get('$ref') == '#/$defs/example.Service_DSL%24Builder%3A%3Akeyless'
        document('example.KeylessUses').get('$defs').get('example.Collision_DSL$Builder::keyless').get('properties').keySet() == ['value'] as Set
        document('example.FallbackKey').get('$defs').get('example.FallbackKey_DSL$Builder').get('required') == ['id/~']
        document('example.EmptyRename').get('$defs').get('example.EmptyRename_DSL$Builder').get('required') == ['name']
        document('example.IgnoredOperationCollision').get('$defs').get('example.IgnoredOperationCollision_DSL$Builder').get('required') == ['value']
        secret.get('properties').keySet() == ['inherited', 'key', 'name'] as Set
        !root.containsKey('required')
        !root.get('properties').get('computed').containsKey('default')
        schema.get('x-klum-unsupported').values().any { it.contains('outside the admitted grammar') }
        schema.get('x-klum-unsupported').get('example.Deployment#excludedChild').contains('@JsonIgnore')
        schema.get('x-klum-unsupported').get('example.Deployment#mappedServices').contains('keyMapping')
        !root.get('properties').containsKey('excludedChild')
        !root.get('properties').containsKey('mappedServices')
        !trap.exists()
        !new File(consumer, 'src').exists()
        !new File(producer, 'api/src').exists()
        !new File(producer, 'schema/src').exists()

        when: 'a separate positive control deliberately initializes the Schema'
        def control = runner(consumer, 'initializeControl').buildAndFail()

        then:
        control.output.contains('Schema initialization control')
        trap.text == 'initialized'

        where:
        generation << ['3', '4', '5']
    }

    @Issue('954')
    def 'unrepresentable required keys fail closed before replacing successful outputs: #model'() {
        given:
        fixture('3')
        run('generateKlumJsonSchema')
        byte[] previous = output('example.Deployment').bytes
        File manifest = new File(output('example.Deployment').parentFile, '.klum-json-schema-outputs')
        String previousManifest = manifest.text
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.$model'])\n")

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        failed.output.contains("root 'example.$model'")
        failed.output.contains("example.$declaration#name")
        failed.output.contains("example.$declaration" + '_DSL$Builder')
        failed.output.contains('key position MEMBER')
        failed.output.contains(reason)
        !relationship || failed.output.contains("example.$model#child")
        output('example.Deployment').bytes == previous
        manifest.text == previousManifest
        !output("example.$model").exists()
        !trap.exists()

        where:
        model             | declaration | reason                    | relationship
        'Ignored'         | 'Ignored'   | '@JsonIgnore(true)'       | false
        'Collision'       | 'Collision' | 'external-name collision' | false
        'IgnoredHolder'   | 'Ignored'   | '@JsonIgnore(true)'       | true
        'CollisionHolder' | 'Collision' | 'external-name collision' | true
    }

    @Issue('954')
    def 'missing key facts cannot silently project a keyed document: #model'() {
        given:
        fixture('3')
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.$model'])\n")

        when:
        def result = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        result.output.contains("root 'example.$model'")
        result.output.contains('example.Legacy_DSL$Builder')
        result.output.contains('key position ' + position)
        result.output.contains(reason)
        result.output.contains('recompile and package the Schema')
        !output('example.' + model).exists()
        !trap.exists()

        where:
        model          | position         | reason
        'Legacy'       | 'MEMBER'         | 'missing construction key metadata'
        'LegacyHolder' | 'UNKNOWN_DIRECT' | 'missing direct relationship key-authority metadata'
    }

    def 'tracks roots output ownership and binary dependency documentation without stale files'() {
        given:
        fixture('3')
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.add('other.Service')\n")
        run('generateKlumJsonSchema')
        byte[] first = output('example.Deployment').bytes
        File unrelated = new File(output('example.Deployment').parentFile, 'consumer-owned.txt')
        unrelated.text = 'preserve me'

        when: 'unchanged roots are reused'
        def reused = run('generateKlumJsonSchema')

        then:
        output('example.Service').exists()
        output('other.Service').exists()
        document('example.Service').get('$defs').get('example.Service_DSL$Builder').get('properties').containsKey('image')
        document('other.Service').get('$defs').get('other.Service_DSL$Builder').get('properties').containsKey('endpoint')
        reused.task(':generateKlumJsonSchema').outcome == TaskOutcome.UP_TO_DATE

        when: 'replace a resolved API artifact under the same coordinate with different retained prose'
        File manifest = new File(output('example.Deployment').parentFile, '.klum-json-schema-outputs')
        File legacy = new File(manifest.parentFile, 'example.Deployment_DSL$Builder.schema.json')
        legacy.bytes = first
        manifest.append(legacy.name + '\n')
        File untracked = new File(manifest.parentFile, 'consumer-owned_DSL$Builder.schema.json')
        untracked.text = 'consumer-owned schema'
        publishApi('Changed inherited region documentation.')
        def changed = run('generateKlumJsonSchema', '--refresh-dependencies')

        then:
        changed.task(':generateKlumJsonSchema').outcome == TaskOutcome.SUCCESS
        output('example.Deployment').text.contains('Changed inherited region documentation.')
        output('example.Deployment').bytes != first
        !legacy.exists()
        untracked.text == 'consumer-owned schema'
        manifest.readLines() == ['example.Deployment.schema.json', 'example.Service.schema.json', 'other.Service.schema.json']
        !trap.exists()

        when: 'only one Model root remains'
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Service'])\n")
        run('generateKlumJsonSchema')

        then:
        !output('example.Deployment').exists()
        !output('other.Service').exists()
        output('example.Service').exists()
        unrelated.text == 'preserve me'
        untracked.text == 'consumer-owned schema'
        manifest.readLines() == ['example.Service.schema.json']

        when: 'a missing root fails before replacing the last successful files'
        byte[] previous = output('example.Service').bytes
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Missing'])\n")
        def failed = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        failed.output.contains("root 'example.Missing'")
        failed.output.contains('Schema/API/runtime/annotation classpath')
        output('example.Service').bytes == previous
    }

    def 'reuses configuration and build caches after output deletion and project relocation'() {
        given:
        fixture('3')
        String cache = new File(directory, 'build-cache').absolutePath.replace('\\', '/')
        new File(consumer, 'settings.gradle').append("\nbuildCache { local { directory = '$cache' } }\n")
        run('generateKlumJsonSchema', '--configuration-cache', '--build-cache')
        String original = output('example.Deployment').text

        when:
        def reused = run('generateKlumJsonSchema', '--configuration-cache', '--build-cache')

        then:
        reused.output.contains('Configuration cache entry reused')
        reused.task(':generateKlumJsonSchema').outcome == TaskOutcome.UP_TO_DATE

        when:
        output('example.Deployment').parentFile.deleteDir()
        def cached = run('generateKlumJsonSchema', '--configuration-cache', '--build-cache')

        then:
        cached.task(':generateKlumJsonSchema').outcome == TaskOutcome.FROM_CACHE
        output('example.Deployment').text == original

        when:
        File relocated = new File(directory, 'relocated')
        relocated.mkdirs()
        ['settings.gradle', 'build.gradle'].each { new File(relocated, it).bytes = new File(consumer, it).bytes }
        consumer = relocated
        def moved = run('generateKlumJsonSchema', '--build-cache')

        then:
        moved.task(':generateKlumJsonSchema').outcome == TaskOutcome.FROM_CACHE
        output('example.Deployment').text == original
        !trap.exists()
    }

    def 'diagnoses empty invalid and unqualified roots without executing Schema code: #roots'() {
        given:
        fixture('3')
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set($roots)\n")

        when:
        def result = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        result.output.contains(message)
        !trap.exists()
        !output('example.Deployment').exists()

        where:
        roots                           | message
        '[]'                            | 'at least one fully qualified DSL Model'
        "['../Deployment']"             | 'Expected a fully qualified DSL Model binary name'
        "['java.lang.String']"          | "root 'java.lang.String'"
        "['example.AbstractDeployment']" | 'Builder self bound and Factory result disagree'
    }

    def 'rejects generated Builder root names with DSL Model guidance'() {
        given:
        fixture('3')
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Deployment_DSL\$Builder'])\n")

        when:
        def result = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        result.output.contains('Generated Builder names are not rootTypes values')
        result.output.contains("use DSL Model name 'example.Deployment'")
        !trap.exists()
        !output('example.Deployment').exists()
    }

    def 'rejects a Builder contract belonging to a different DSL Model'() {
        given:
        fixture('3')
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Misdirected'])\n")

        when:
        def result = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        result.output.contains("belongs to DSL Model 'example.Service', not requested DSL Model 'example.Misdirected'")
        !trap.exists()
        !output('example.Misdirected').exists()
    }

    def 'missing isolated runtime cannot fall back to plugin or daemon runtime classes'() {
        given:
        fixture('3')
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.classpath.setFrom(configurations.schemaBinary)\n")

        when:
        def result = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        result.output.contains('Schema/API/runtime/annotation classpath')
        !trap.exists()
    }

    def 'ordinary build graph does not select generation'() {
        given:
        fixture('3')

        when:
        def result = run('assemble')

        then:
        result.task(':generateKlumJsonSchema') == null
        !output('example.Deployment').exists()
    }

    def 'default classpath compiles local roots and resolves their binary Schema dependencies'() {
        given:
        fixture('3', true)
        String groovy = notation(System.getProperty('gdslGroovy3'))
        String compiler = classpath(System.getProperty('gdslCompilerClasspath'))
        String runtime = classpath(System.getProperty('gdslRuntimeClasspath'))
        new File(consumer, 'build.gradle').append("""
dependencies {
    implementation 'fixture:schema:1.0'
    implementation files($runtime)
    compileOnly files($compiler)
    groovy files($groovy)
}
tasks.withType(GroovyCompile).configureEach { groovyClasspath = files($groovy) }
klumSchema.jsonSchema.rootTypes.set(['example.Local', 'example.Deployment'])
""")
        File local = new File(consumer, 'src/main/groovy/example/Local.groovy')
        local.parentFile.mkdirs()
        local.text = '''
package example
import com.blackbuild.klum.ast.DSL
@DSL class Local extends DeploymentApi { String title }
'''

        when:
        def result = run('generateKlumJsonSchema')

        then:
        result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        document('example.Local').get('$defs').get('example.Local_DSL$Builder').get('properties').containsKey('region')
        output('example.Deployment').exists()
        !trap.exists()
    }

    private void fixture(String generation, boolean defaults = false) {
        packagedPlugin = generation == '5'
        producer = new File(directory, 'producer')
        consumer = new File(directory, 'consumer')
        repository = new File(directory, 'repository')
        trap = new File(directory, 'schema-executed')
        producer.mkdirs()
        consumer.mkdirs()
        String groovy = notation(System.getProperty("gdslGroovy$generation"))
        String compiler = classpath(System.getProperty('gdslCompilerClasspath'))
        String runtime = classpath(System.getProperty('gdslRuntimeClasspath'))
        new File(producer, 'settings.gradle').text = "include 'api', 'schema'"
        new File(producer, 'build.gradle').text = """
subprojects {
    apply plugin: 'groovy'
    dependencies { implementation files($groovy, $compiler) }
    tasks.withType(GroovyCompile).configureEach {
        groovyClasspath = files($groovy)
        groovyOptions.optimizationOptions.groovydoc = true
    }
}
project(':schema') { dependencies { implementation project(':api') } }
"""
        writeSource('schema/src/main/groovy/example/Deployment.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.layer3.Cluster
import java.lang.annotation.*
@DSL class Deployment extends DeploymentApi {
    static { new File('${trap.absolutePath}').text = 'initialized'; throw new AssertionError('Schema initialization control') }
    Map<String, Service> services
    Service service
    @Field(key = Field.FieldName) Service fixed
    @Field(key = { throw new AssertionError('key provider executed') }) Service derived
    FixedCluster cluster
    @JsonIgnore Ignored excludedChild
    @Field(keyMapping = { throw new AssertionError('keyMapping executed') }) Map<String, Service> mappedServices
    SecretReference credentialSecret
    int replicas
    long capacity
    BigInteger counter
    BigDecimal budget
    double weight
    Set<String> labels
    Map<String, String> annotations
    @Default(code={ throw new AssertionError('default executed') }) String computed
    String initialized = { throw new AssertionError('initializer executed') }()
    @Builder.Method @Policy(Mode.SAFE)
    void configure(Map<String, List<String>> input) { throw new AssertionError('operation executed') }
    @Builder.Method void unsupported(Object input) {}
    @PostCreate void created() { throw new AssertionError('lifecycle executed') }
}
@DSL class Service extends ServiceApi {
    String image
    Mode mode
    Service nested
    Map<String, Service> children
}
@DSL class FixedCluster { Service catalog; @Cluster(fixedKeys = true) Map<String, Service> getServices() {} }
@DSL class Ignored { @Key @JsonIgnore String name; String value }
@DSL class Collision { @Key @JsonProperty('value') String name; String value }
@DSL class IgnoredHolder { Ignored child }
@DSL class CollisionHolder { Collision child }
@DSL class KeylessUses {
    Map<String, Ignored> ignored
    Map<String, Collision> collisions
    @Field(key = Field.FieldName) Ignored fixedIgnored
    @Field(key = Field.FieldName) Collision fixedCollision
}
@DSL class FallbackKey { @Key @JsonProperty('id/~') String key; @JsonIgnore(false) String value }
@DSL class EmptyRename { @Key @JsonProperty('') @JsonIgnore(false) String name }
@DSL class IgnoredOperationCollision { @Key @JsonProperty('value') String name; @JsonIgnore String value }
@DSL class BaseSecret { String inherited }
@DSL class SecretReference extends BaseSecret { String name; String key }
@DSL abstract class AbstractDeployment { String value }
enum Mode {
    SAFE, FAST;
    static { throw new AssertionError('enum executed') }
}
@Retention(RetentionPolicy.RUNTIME) @interface Policy { Mode value() }
""")
        File deployment = new File(producer, 'schema/src/main/groovy/example/Deployment.groovy')
        deployment.text = deployment.text.replace('import java.lang.annotation.*', 'import java.lang.annotation.*\nimport com.fasterxml.jackson.annotation.JsonProperty')
                .replace('@Builder.Method @Policy', "@Builder.Method @JsonProperty('configure-input') @Policy")
        deployment.text = deployment.text.replace('import com.fasterxml.jackson.annotation.JsonProperty', 'import com.fasterxml.jackson.annotation.*')
        writeSource('schema/src/main/groovy/other/Service.groovy', """
package other
import com.blackbuild.klum.ast.DSL
@DSL class Service { String endpoint }
""")
        // Malformed binary contract: no Misdirected Model exists, and both contract signatures name Service.
        writeSource('schema/src/main/groovy/example/Misdirected_DSL.groovy', """
package example
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
class Misdirected_DSL {
    @GeneratedInput.Schema interface Builder<SELF extends Service> {}
    interface Factory { Service One() }
}
""")
        // An older binary may retain the native key but have no key-input facts.
        writeSource('schema/src/main/groovy/example/Legacy_DSL.groovy', """
package example
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import java.util.function.Consumer
class Legacy {}
class Legacy_DSL {
    @GeneratedInput.Schema(key = 'name') interface Builder<SELF extends Legacy> {}
    interface Factory { Legacy One(String key) }
}
class LegacyHolder {}
class LegacyHolder_DSL {
    @GeneratedInput.Schema interface Builder<SELF extends LegacyHolder> {
        @GeneratedInput.Field('child') Legacy_DSL.Builder getChild()
        @GeneratedInput.Selection(field = 'child', role = 'OWNED_OBJECT') void child(Consumer input)
    }
    interface Factory { LegacyHolder One() }
}
""")
        publishApi('Inherited region documentation.')
        runner(producer, ':schema:jar').build()
        publish('schema', 'schema/build/libs/schema.jar', '<dependencies><dependency><groupId>fixture</groupId><artifactId>api</artifactId><version>1.0</version></dependency></dependencies>')
        new File(producer, 'schema/src').deleteDir()
        new File(producer, 'api/src').deleteDir()
        new File(consumer, 'settings.gradle').text = "rootProject.name = 'schema-consumer'"
        new File(consumer, 'build.gradle').text = """
plugins { id 'com.blackbuild.klum-ast-schema' }
['api', 'implementation', 'compileOnly', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
repositories { maven { url = '${repository.toURI()}' } }
configurations { schemaBinary }
dependencies { schemaBinary 'fixture:schema:1.0' }
klumSchema.jsonSchema {
    rootTypes.add('example.Deployment')
    rootTypes.add('example.Service')
    ${defaults ? '' : 'classpath.setFrom(configurations.schemaBinary, files(' + runtime + ', ' + groovy + '))'}
}
tasks.register('initializeControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.InitializationControl'
}
"""
    }

    private void publishApi(String documentation) {
        // Use a literal naming annotation to exercise exact-contract selective metadata without Jackson linkage.
        writeSource('api/src/main/groovy/com/fasterxml/jackson/annotation/JsonProperty.groovy', """
package com.fasterxml.jackson.annotation
import java.lang.annotation.*
@Retention(RetentionPolicy.RUNTIME) @Target([ElementType.METHOD, ElementType.FIELD])
@interface JsonProperty { String value() default '' }
""")
        writeSource('api/src/main/groovy/com/fasterxml/jackson/annotation/JsonIgnore.groovy', '''
package com.fasterxml.jackson.annotation
import java.lang.annotation.*
@Retention(RetentionPolicy.RUNTIME) @Target([ElementType.METHOD, ElementType.FIELD])
@interface JsonIgnore { boolean value() default true }
''')
        writeSource('api/src/main/groovy/example/DeploymentApi.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.fasterxml.jackson.annotation.JsonProperty
@DSL abstract class ServiceApi { @Key @JsonProperty('service/id~') String name }
@DSL abstract class DeploymentApi {
    /** $documentation */
    String region
}
class InitializationControl {
    static void main(String[] args) { Class.forName('example.Deployment', true, InitializationControl.classLoader) }
}
""")
        runner(producer, ':api:jar').build()
        publish('api', 'api/build/libs/api.jar', '')
    }

    private void publish(String artifact, String path, String dependencies) {
        File location = new File(repository, "fixture/$artifact/1.0")
        location.mkdirs()
        new File(location, "$artifact-1.0.jar").bytes = new File(producer, path).bytes
        new File(location, "$artifact-1.0.pom").text = """<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>$artifact</artifactId><version>1.0</version>$dependencies</project>"""
    }

    private void writeSource(String path, String text) {
        File source = new File(producer, path)
        source.parentFile.mkdirs()
        source.text = text
    }

    private File output(String model) { new File(consumer, 'build/generated/klum-json-schema/' + model + '.schema.json') }
    private Map document(String model) { (Map) new JsonSlurper().parse(output(model)) }
    private static String notation(String path) { "'" + path.replace('\\', '\\\\').replace("'", "\\'") + "'" }
    private static String classpath(String value) { value.split(File.pathSeparator).collect { notation(it) }.join(', ') }
    private def run(String... arguments) { runner(consumer, arguments).build() }
    private GradleRunner runner(File project, String... arguments) {
        GradleRunner result = GradleRunner.create().withProjectDir(project)
        if (packagedPlugin) {
            Properties metadata = new Properties()
            getClass().classLoader.getResourceAsStream('plugin-under-test-metadata.properties').withCloseable { metadata.load(it) }
            List<File> dependencies = metadata.getProperty('implementation-classpath').split(File.pathSeparator)
                    .collect { new File(it) }.findAll { it.isFile() }
            result.withPluginClasspath(dependencies + new File(System.getProperty('schemaPluginJar')))
        } else {
            result.withPluginClasspath()
        }
        result.withDebug(!arguments.toList().contains('--configuration-cache'))
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain', '--max-workers=2'])
    }
}
