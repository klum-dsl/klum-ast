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
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.MethodVisitor
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.See
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.TempDir

@Issue('529')
class KlumJsonSchemaGenerationTest extends Specification {
    private static final String DOCUMENTARY_TEXT = 'a "quoted" \\path\nMünchen 😀'
    @TempDir File directory
    @Shared @TempDir File patternCache
    @Shared String patternTrapPath
    int producerRunnerCalls
    File consumer
    File producer
    File repository
    File trap
    boolean packagedPlugin

    @Issue('959')
    def 'project independent first-level composing constraints without domain classes with Groovy #generation'() {
        given:
        compositionFixture(generation)
        String compositionApi = new File(repository, 'fixture/api/1.0/api-1.0.jar').absolutePath
        new File(producer, 'build.gradle').append("""
project(':api') {
    tasks.register('compositionInitializationControl', JavaExec) {
        classpath = files('${compositionApi}') + configurations.runtimeClasspath
        mainClass = 'example.CompositionInitializationControl'
        args 'example.CompositionValidator'
    }
}
""")
        def validatorControl = runner(producer, ':api:compositionInitializationControl').buildAndFail()
        assert validatorControl.output.contains('validator initialized')
        removeCompositionTypes()
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.CompositionService'])\n")

        when:
        run('generateKlumJsonSchema')
        def schema = document('example.CompositionService')
        def properties = schema.'$defs'.'example.CompositionService_DSL$Builder'.properties
        File validation = new File(System.getProperty('schemaValidationDirectory'), generation)
        validation.mkdirs()
        new File(validation, 'composition.schema.json').bytes = output('example.CompositionService').bytes

        then:
        properties.range.allOf == [[maximum: 100], [minimum: 1]]
        properties.multiple.allOf == [[minimum: 5], [minimum: 10], [maximum: 100], [minimum: 1]]
        properties.contradiction.allOf == [[maximum: 5], [minimum: 10]]
        properties.decimal.exclusiveMinimum == 1.25
        properties.decimal.maximum == 9.75
        properties.code.allOf == [[pattern: '^[A-Z]{2}$'], [not: [type: 'string', pattern: '[^ -~]']]]
        properties.code.description.contains('Email')
        properties.'intersecting-patterns'.allOf == [[pattern: '^A[A-Z]$'], [not: [type: 'string', pattern: '[^ -~]']],
            [pattern: '^[A-Z]{2}$'], [not: [type: 'string', pattern: '[^ -~]']]]
        properties.'direct-with-email'.allOf[0].pattern == '^[A-Z]{2}$'
        properties.'direct-with-email'.description.contains('Email')
        !properties.'flagged-code'.containsKey('allOf')
        !properties.'repeated-code'.containsKey('allOf')
        !properties.code.containsKey('format')
        properties.'Inherited/Range'.'x-klum-constraint-source' == 'example.CompositionBase#inheritedRange'
        properties.'Inherited/Range'.description.contains('Inherited prose.')
        properties.'Inherited/Range'.description.contains('Percentage of traffic sent to this service.')
        properties.'Inherited/Range'.deprecated
        properties.'text-only'.description.contains('Percentage of traffic sent to this service.')
        !properties.'text-only'.containsKey('allOf')
        properties.omitted == [type: 'integer', minimum: -2147483648, maximum: 2147483647, description: 'Retained prose.', examples: ['explicit field example']]
        properties.'description-only'.description.contains('Must be valid Groovy code.')
        properties.'propagation-only'.description.contains('Must be present.')
        properties.'explicit-and'.allOf == [[maximum: 10], [minimum: 1]]
        ['or', 'all-false', 'overridden', 'nested', 'grouped', 'payload', 'repeated', 'size-only'].every {
            !properties[it].containsKey('allOf') && properties[it].description.contains('Runtime constraint')
        }
        properties.'independent-pattern'.allOf[0].pattern == '^[A-Z]{2}$'
        properties.'independent-pattern'.description.contains('Either')
        !properties.'unsupported-pattern'.containsKey('allOf')
        !properties.'wrong-type'.containsKey('allOf')
        !properties.container.items.containsKey('pattern')
        !schema.'$defs'.'example.CompositionService_DSL$Builder'.containsKey('required')
        !trap.exists()
        ['api', 'schema', 'owned'].every { !new File(producer, "$it/src").exists() && !new File(producer, "$it/build/classes").exists() }

        when:
        new File(consumer, 'build.gradle').append('''
tasks.register('compositionInitializationControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.CompositionInitializationControl'
    args 'example.CompositionService'
}
''')
        def initialized = runner(consumer, 'compositionInitializationControl').buildAndFail()
        then:
        initialized.output.contains('composition initialized')
        trap.text == 'initialized'

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    def 'reject corrupt composition and documentary facts before suppression: #kind #selectedField'() {
        given:
        compositionFixture('3')
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.CompositionService'])\n")
        run('generateKlumJsonSchema')
        File outputDirectory = output('example.CompositionService').parentFile
        def previous = outputDirectory.listFiles().collectEntries { [(it.name): it.bytes.toList()] }
        stripConstraintFacts('CompositionService', kind, selectedField)
        new File(consumer, 'build.gradle').append('''
tasks.register('compositionReadControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.CompositionReadControl'
}
''')

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema', '--refresh-dependencies').buildAndFail()
        then:
        failed.output.contains(reason)
        failed.output.contains('CompositionService')
        failed.output.contains('artifact')
        outputDirectory.listFiles().collectEntries { [(it.name): it.bytes.toList()] } == previous
        !trap.exists()
        when:
        def rejected = run('compositionReadControl', '--refresh-dependencies')
        then:
        rejected.output.contains('Rejected composition metadata before execution')
        !trap.exists()
        where:
        kind          | selectedField | reason
        'count'       | 'omitted'     | 'Missing constraint count'
        'stripped-rules' | 'omitted'   | 'Constraint count mismatch'
        'origin'      | 'range'       | 'Missing composition/description facts'
        'owner'       | 'range'       | 'Missing composition/description facts'
        'description' | 'omitted'     | 'Missing composition/description facts'
        'origin'      | 'textOnly'    | 'Missing composition/description facts'
        'bad-origin'  | 'omitted'     | 'Inconsistent composition origin'
        'bad-owner'   | 'omitted'     | 'Missing composing constraint owner'
        'bad-description' | 'omitted' | 'Composition/description fact must be a String'
    }

    @Issue('959')
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#advisory-input-examples')
    def 'project advisory examples at exact source-free input positions with Groovy #generation'() {
        given:
        fixture(generation, false, false, false, false, false, true, false, true)
        new File(consumer, 'build.gradle').append('''
klumSchema.jsonSchema.rootTypes.set(['example.ExampleService'])
tasks.register('exampleControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.ExampleControl'
}
''')

        when:
        run('generateKlumJsonSchema', '--build-cache')
        def schema = document('example.ExampleService')
        def root = schema.get('$defs').get('example.ExampleService_DSL$Builder')
        def properties = root.properties
        def member = schema.get('$defs').get('example.ExampleChild_DSL$Builder')
        def keyless = schema.get('$defs').get('example.ExampleChild_DSL$Builder::keyless')
        byte[] first = output('example.ExampleService').bytes
        File validation = new File(System.getProperty('schemaValidationDirectory'), generation)
        validation.mkdirs()
        new File(validation, 'examples.schema.json').bytes = first

        then:
        properties.'service.name'.examples == ['Catalog', 'catalog.internal', 'Catalog']
        properties.'service.name'.deprecated
        properties.'binary-constant'.examples == ['binary constant']
        !properties.'service.name'.containsKey('default')
        properties.'display-name'.examples == ['inherited']
        properties.'inherited-child'.examples == ['inherited database']
        !output('example.ExampleService').text.contains('SECRET_PAYLOAD')
        properties.configure.examples == ['override']
        properties.'configure-base'.examples == ['base']
        properties.endpoint.examples == ['API endpoint settings']
        properties.tags.examples == ['blue, green']
        !properties.tags.items.containsKey('examples')
        properties.labels.examples == ['team: ops']
        !properties.labels.additionalProperties.containsKey('examples')
        properties.primary.examples == ['primary database']
        properties.primary.containsKey('$ref')
        !properties.sibling.containsKey('examples')
        properties.fixed.examples == ['fixed database']
        properties.children.examples == ['worker databases']
        member.properties.key.examples == ['catalog']
        member.properties.'child.name'.examples == ['child']
        !member.containsKey('examples')
        !keyless.containsKey('examples')
        !keyless.properties.containsKey('key')
        !root.containsKey('required')
        !properties.containsKey('secret')
        !properties.containsKey('hidden')
        !properties.containsKey('link')
        !properties.containsKey('unsupported')
        properties.escaped.examples == [DOCUMENTARY_TEXT]
        properties.varied.examples == ['null', '{not JSON', '01', '[unclosed']
        properties.replicas.examples == ['42', 'many replicas']
        properties.enabled.examples == ['true', 'enabled']
        !trap.exists()
        !new File(producer, 'api/src').exists()
        !new File(producer, 'schema/build/classes').exists()
        !new File(producer, 'owned/src').exists()

        when:
        def unchanged = run('generateKlumJsonSchema', '--build-cache')
        output('example.ExampleService').parentFile.deleteDir()
        def cached = run('generateKlumJsonSchema', '--build-cache')
        def control = runner(consumer, 'exampleControl').buildAndFail()

        then:
        unchanged.task(':generateKlumJsonSchema').outcome == TaskOutcome.UP_TO_DATE
        cached.task(':generateKlumJsonSchema').outcome == TaskOutcome.FROM_CACHE
        output('example.ExampleService').bytes == first
        control.output.contains('Schema initialization control')
        trap.text == 'initialized'

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    def 'invalid carried examples fail before replacing any output: #target #kind'() {
        given:
        fixture('3', false, false, false, false, false, true, false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Deployment', 'example.ExampleService'])\n")
        run('generateKlumJsonSchema')
        File outputDirectory = output('example.ExampleService').parentFile
        Map<String, List<Byte>> previous = outputDirectory.listFiles().collectEntries { [(it.name): it.bytes.toList()] }
        rewriteNamingFacts(target, 'examples-' + kind, artifact)

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        failed.output.toLowerCase().contains('examples')
        if (kind == 'excessive') {
            assert failed.output.contains('excessive documentary text')
        }
        failed.output.contains('example.' + target)
        failed.output.contains('artifact')
        !failed.output.contains('SECRET_PAYLOAD')
        outputDirectory.listFiles().collectEntries { [(it.name): it.bytes.toList()] } == previous
        !trap.exists()

        where:
        target           | artifact | kind
        'ExampleService' | 'schema' | 'missing'
        'ExampleService' | 'schema' | 'duplicate'
        'ExampleService' | 'schema' | 'scalar'
        'ExampleService' | 'schema' | 'element'
        'ExampleService' | 'schema' | 'nested'
        'ExampleService' | 'schema' | 'excessive'
        'ExampleBase'    | 'api'    | 'missing'
        'ExampleChild'   | 'owned'  | 'missing'
    }

    @Issue('959')
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#conservative-pattern-projection')
    def 'project bounded release identifiers from source-free artifacts with Groovy #generation'() {
        given:
        fixture(generation, false, false, false, false, false, false, false, false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Release', 'example.PatternKey', 'example.PatternGrouped'])\n")
        new File(producer, 'api/build/classes').deleteDir()
        new File(producer, 'schema/build/classes').deleteDir()

        when:
        run('generateKlumJsonSchema', '--configuration-cache')
        def schema = document('example.Release')
        def release = schema.'$defs'.'example.Release_DSL$Builder'
        def properties = release.properties
        File validation = new File(System.getProperty('schemaValidationDirectory'), generation)
        validation.mkdirs()
        new File(validation, 'pattern.schema.json').bytes = output('example.Release').bytes

        then:
        properties.slug.allOf == [[pattern: '^[a-z][a-z0-9-]{0,62}$'], [not: [type: 'string', pattern: '[^ -~]']]]
        properties.slug.description == 'Release slug.'
        properties.slug.examples == ['release-1', 'NOT VALID']
        properties.'text-only'.examples == ['outside']
        properties.'Inherited/Code'.allOf[0].pattern == '^[A-Z]{2}$'
        properties.'Inherited/Code'.'x-klum-constraint-source' == 'example.PatternBase#inheritedCode'
        properties.'optional-token'.allOf[0].pattern == '^a{0,2}$'
        properties.flagged.description.contains('nonempty Pattern flags')
        ['flagged', 'unix', 'multiline', 'dotall', 'unicode-case', 'canonical'].every { properties[it].description.contains('nonempty Pattern flags') && !properties[it].containsKey('allOf') }
        properties.'constant-fallback'.description.contains('regexp="[a-z][a-z0-9-]{0,62}"')
        properties.payload.description.contains('groups/payload')
        properties.composed.description.contains('@example.ComposedPattern()')
        properties.composed.allOf[0].pattern == '^a$'
        properties.alias.description.contains('@example.AliasedPattern()')
        !properties.alias.containsKey('allOf')
        properties.controls.description.contains('regexp="a\\u000a"')
        properties.grouped.description.contains('groups/payload')
        properties.unbounded.description.contains('unsupported Pattern source syntax')
        properties.malformed.description.contains('regexp="["')
        properties.repeated.description.contains('Pattern$List')
        properties.'singleton-list'.description.contains('Pattern$List')
        !properties.'singleton-list'.containsKey('allOf')
        !properties.'mixed-list'.containsKey('allOf')
        properties.'mixed-list'.description.contains('Pattern$List')
        properties.'text-only'.description.contains('field projection policy TEXT_ONLY')
        properties.omitted == [type: 'string', description: 'Retained prose.', deprecated: true, examples: ['outside']]
        !properties.containsKey('excluded')
        !properties.'wrong-type'.containsKey('allOf')
        !properties.container.items.containsKey('pattern')
        properties.first.'$ref' == properties.second.'$ref'
        schema.'$defs'.'example.PatternChild_DSL$Builder'.properties.'child-code'.allOf[0].pattern == '^[A-Z]{2}$'
        !document('example.PatternKey').'$defs'.'example.PatternKey_DSL$Builder'.properties.name.containsKey('allOf')
        document('example.PatternGrouped').'$defs'.'example.PatternGrouped_DSL$Builder'.properties.value.description.contains('group redefinition')
        !release.containsKey('required')
        !trap.exists()
        ['api', 'schema'].every { !new File(producer, "$it/src").exists() && !new File(producer, "$it/build/classes").exists() }

        when:
        def admitted = run('patternAdmissionControl')
        def cached = run('generateKlumJsonSchema', '--configuration-cache')
        then:
        admitted.output.contains('Pattern remains independent of interpreter admission')
        cached.output.contains('Reusing configuration cache.')
        cached.task(':generateKlumJsonSchema').outcome == TaskOutcome.UP_TO_DATE

        when:
        def control = runner(consumer, 'patternInitializeControl').buildAndFail()
        then:
        control.output.contains('Pattern Schema initialized')
        trap.text == 'initialized'

        where:
        generation << ['3', '4', '5']
    }

    @Issue('895')
    def 'Pattern producer fixture is shared while destructive copies and traps remain private'() {
        given:
        File root = directory
        directory = new File(root, 'first')
        patternCorruptionFixture()
        File firstRepository = repository
        File firstTrap = trap
        File firstJar = new File(firstRepository, 'fixture/schema/1.0/schema-1.0.jar')
        byte[] firstBytes = firstJar.bytes
        File cachedJar = new File(patternCache, 'fixture/schema/1.0/schema-1.0.jar')
        byte[] cachedBytes = cachedJar.bytes
        int callsAfterFirst = producerRunnerCalls

        when:
        directory = new File(root, 'second')
        patternCorruptionFixture()
        File secondJar = new File(repository, 'fixture/schema/1.0/schema-1.0.jar')
        firstJar.bytes = [1, 2, 3] as byte[]
        def control = runner(consumer, 'patternInitializeControl').buildAndFail()
        directory = new File(root, 'third')
        patternCorruptionFixture()
        File thirdJar = new File(repository, 'fixture/schema/1.0/schema-1.0.jar')

        then:
        producerRunnerCalls == callsAfterFirst
        secondJar.bytes != firstJar.bytes
        new JarFile(thirdJar).withCloseable { it.getEntry('example/Release.class') != null }
        cachedJar.bytes == cachedBytes
        firstBytes.length > 3
        control.output.contains('Pattern Schema initialized')
        new File(root, 'second/schema-executed').text == 'initialized'
        !firstTrap.exists()
        !trap.exists()
    }

    @Issue(['959', '895'])
    def 'corrupt Pattern facts fail both consumers atomically: #target #selectedField #kind'() {
        given:
        patternCorruptionFixture()
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Release'])\n")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.Release').bytes
        File manifest = new File(output('example.Release').parentFile, '.klum-json-schema-outputs')
        String previousManifest = manifest.text
        stripConstraintFacts(target, kind, selectedField)
        when:
        def failed = runner(consumer, 'generateKlumJsonSchema', '--refresh-dependencies').buildAndFail()
        then:
        failed.output.contains(reason)
        failed.output.contains('recompile and package')
        output('example.Release').bytes == previous
        manifest.text == previousManifest
        !trap.exists()
        when:
        def rejected = runner(consumer, 'patternReadControl', '--refresh-dependencies').build()
        then:
        rejected.output.contains('Rejected Pattern metadata before execution')
        !trap.exists()
        where:
        target | selectedField | kind | reason
        'Release' | 'slug' | 'regexp' | 'Missing direct Pattern facts'
        'Release' | 'textOnly' | 'regexp' | 'Missing direct Pattern facts'
        'Release' | 'omitted' | 'regexp' | 'Missing direct Pattern facts'
        'Release' | 'slug' | 'patternFlagsEmpty' | 'Missing direct Pattern facts'
        'Release' | 'textOnly' | 'patternFlagsEmpty' | 'Missing direct Pattern facts'
        'Release' | 'omitted' | 'patternFlagsEmpty' | 'Missing direct Pattern facts'
        'Release' | 'slug' | 'bad-regexp' | 'Pattern regexp must be a String'
        'Release' | 'omitted' | 'bad-flags' | 'Pattern flag emptiness must be a Boolean'
        'PatternBase' | 'inheritedCode' | 'regexp' | 'Missing direct Pattern facts'
        'PatternChild' | 'childCode' | 'patternFlagsEmpty' | 'Missing direct Pattern facts'
        'Release' | 'slug' | 'duplicate-pattern' | 'Inconsistent singleton Pattern eligibility'
        'Release' | 'textOnly' | 'duplicate-pattern' | 'Inconsistent singleton Pattern eligibility'
        'Release' | 'omitted' | 'duplicate-pattern' | 'Inconsistent singleton Pattern eligibility'
        'Release' | 'omitted' | 'duplicate-regexp' | 'Duplicate constraint member regexp'
        'Release' | 'textOnly' | 'unknown-pattern' | 'Unknown constraint member futurePatternFact'
    }

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

    @Issue('959')
    def 'every participating artifact requires the current metadata protocol: #generation #target #kind'() {
        given:
        fixture(generation, false, false, false, false, false, true)
        new File(consumer, 'build.gradle').append('''
klumSchema.jsonSchema.rootTypes.set(['example.NamingService'])
tasks.register('rejectedNamingControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.NamingControl'
    args('NamingService', 'omit-owned')
}
''')
        run('generateKlumJsonSchema')
        byte[] previous = output('example.NamingService').bytes
        rewriteNamingFacts(target, kind)

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema').buildAndFail()
        def interpreted = run('rejectedNamingControl')

        then:
        failed.output.contains('metadata protocol version')
        failed.output.contains('example.' + target)
        failed.output.contains('artifact')
        failed.output.contains('recompile and package')
        interpreted.output.contains('Rejected naming contract before execution')
        output('example.NamingService').bytes == previous
        !trap.exists()

        where:
        generation | target          | kind
        '3'        | 'NamingService' | 'protocol-missing'
        '3'        | 'NamingService' | 'protocol-older'
        '4'        | 'NamingService' | 'protocol-future'
        '5'        | 'NamingService' | 'protocol-malformed'
        '3'        | 'NamingBase'    | 'protocol-missing'
        '4'        | 'NamingBase'    | 'protocol-older'
        '5'        | 'NamingBase'    | 'protocol-future'
        '3'        | 'NamingDetails' | 'protocol-missing'
        '4'        | 'NamingWorker'  | 'protocol-older'
        '5'        | 'NamingDetails' | 'protocol-future'
    }

    @Issue('959')
    def 'correct protocol never permits missing or corrupt required facts: #root #kind'() {
        given:
        fixture('3', false, true, false, true, true, true)
        new File(consumer, 'build.gradle').append("""
klumSchema.jsonSchema.rootTypes.set(['example.$root'])
tasks.register('rejectedNamingControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.NamingControl'
    args('$root')
}
""")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.' + root).bytes
        stripConstraintFacts(root, kind)

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema').buildAndFail()
        def interpreted = run('rejectedNamingControl')

        then:
        failed.output.contains(diagnostic)
        failed.output.contains('example.' + root)
        failed.output.contains('recompile and package')
        interpreted.output.contains('Rejected naming contract before execution')
        output('example.' + root).bytes == previous
        !trap.exists()

        where:
        root         | kind             | diagnostic
        'Capacity'   | 'field'          | 'Missing constraint facts'
        'Projection' | 'policy'         | 'Missing constraint projection policy'
        'Projection' | 'unknown-policy' | 'Unknown constraint projection policy'
        'Price'      | 'decimalValue'   | 'missing direct decimal facts'
        'Price'      | 'bad-decimal'    | 'Invalid decimal bound'
    }

    @Issue('959')
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#literal-names-and-exclusion')
    def 'project literal names and exclusions from source-free artifacts with Groovy #generation'() {
        given:
        fixture(generation, false, false, false, false, false, true, true)
        new File(consumer, 'build.gradle').append('''
klumSchema.jsonSchema.rootTypes.set(['example.LiteralService', 'example.LiteralKeyFallback', 'example.LiteralKeyOwner'])
tasks.register('literalControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.LiteralControl'
}
''')

        when:
        run('generateKlumJsonSchema')
        def schema = document('example.LiteralService')
        def service = schema.get('$defs').get('example.LiteralService_DSL$Builder')
        def worker = schema.get('$defs').get('example.LiteralWorker_DSL$Builder::keyless')
        def details = schema.get('$defs').get('example.LiteralDetails_DSL$Builder')

        then:
        service.properties.keySet() == ['Service/ID', 'URL/exact', 'url-value', 'displayName', 'display-name',
                                        'set/Endpoint', 'details', 'workers', 'labels', 'items/exact'] as Set
        service.required == ['Service/ID']
        service.additionalProperties == false
        service.properties.'items/exact'.type == 'array'
        details.properties.keySet() == ['child/Name'] as Set
        worker.properties.keySet() == ['Image/Name'] as Set
        document('example.LiteralKeyFallback').get('$defs').get('example.LiteralKeyFallback_DSL$Builder').required == ['Fallback/ID']
        document('example.LiteralKeyOwner').get('$defs').get('example.LiteralKeyCollision_DSL$Builder::keyless').properties.keySet() == ['Key/exact'] as Set
        !trap.exists()
        ['api', 'schema', 'owned'].every { !new File(producer, "$it/src").exists() && !new File(producer, "$it/build/classes").exists() }
        !new File(consumer, 'src').exists()

        when:
        def interpreted = run('literalControl')

        then:
        interpreted.output.contains('Literal interpreter/schema agreement passed')
        trap.exists()

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    def 'literal collision and excluded key fail before execution and preserve schema output: #target'() {
        given:
        fixture(generation, false, false, false, false, false, true, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.LiteralService'])\n")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.LiteralService').bytes
        new File(consumer, 'build.gradle').append("""
klumSchema.jsonSchema.rootTypes.set(['example.$target'])
tasks.register('rejectedLiteralControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.LiteralControl'
    args('$target')
}
""")

        when:
        def rejected = runner(consumer, 'generateKlumJsonSchema').buildAndFail()
        def interpreted = run('rejectedLiteralControl')

        then:
        rejected.output.contains(diagnostic)
        rejected.output.contains(first)
        rejected.output.contains(second)
        interpreted.output.contains('Rejected literal contract before execution')
        output('example.LiteralService').bytes == previous
        !trap.exists()

        where:
        generation | target                 | diagnostic                                 | first                            | second
        '3'        | 'LiteralCollision'     | 'KEBAB_CASE external-name collision'         | 'example.LiteralCollision#URLValue' | 'example.LiteralCollision#urlValue'
        '4'        | 'LiteralCollision'     | 'KEBAB_CASE external-name collision'         | 'example.LiteralCollision#URLValue' | 'example.LiteralCollision#urlValue'
        '5'        | 'LiteralCollision'     | 'KEBAB_CASE external-name collision'         | 'example.LiteralCollision#URLValue' | 'example.LiteralCollision#urlValue'
        '3'        | 'LiteralWorker'        | '@Struct.Ignore'                              | 'example.LiteralWorker#workerId' | 'required construction key'
        '3'        | 'LiteralKeyCollision'  | 'construction key external-name collision'  | 'example.LiteralKeyCollision#key' | 'example.LiteralKeyCollision#value'
    }

    @Issue('959')
    def 'incomplete literal facts fail both source-free consumers atomically: #generation #kind'() {
        given:
        fixture(generation, false, false, false, false, false, true, true)
        new File(consumer, 'build.gradle').append('''
klumSchema.jsonSchema.rootTypes.set(['example.LiteralService'])
tasks.register('rejectedLiteralControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.LiteralControl'
    args('LiteralService')
}
''')
        run('generateKlumJsonSchema')
        byte[] previous = output('example.LiteralService').bytes
        rewriteNamingFacts('LiteralService', kind)

        when:
        def rejected = runner(consumer, 'generateKlumJsonSchema').buildAndFail()
        def interpreted = run('rejectedLiteralControl')

        then:
        rejected.output.contains('literal metadata')
        rejected.output.contains('example.LiteralService')
        rejected.output.contains('recompile and package')
        interpreted.output.contains('Rejected literal contract before execution')
        output('example.LiteralService').bytes == previous
        !trap.exists()

        where:
        generation | kind
        '3'        | 'literal-origin'
        '3'        | 'literal-rename'
        '3'        | 'literal-ignore'
    }


    @Issue('959')
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#type-naming-profiles')
    def 'resolve input naming from source-free hierarchy contracts with Groovy #generation'() {
        given:
        fixture(generation, false, false, false, false, false, true)
        new File(consumer, 'build.gradle').append("""
klumSchema.jsonSchema.rootTypes.set(['example.NamingBase', 'example.NamingService', 'example.NamingGrandchild', 'example.NamingRepeated', 'example.NamingIdentity', 'example.NamingKey', 'example.NamingLiteralKey', 'example.NamingKeyCollisionOwner'])
tasks.register('namingControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.NamingControl'
}
""")

        when:
        run('generateKlumJsonSchema')
        def service = document('example.NamingService').get('$defs').get('example.NamingService_DSL$Builder')
        def grandchild = document('example.NamingGrandchild').get('$defs').get('example.NamingGrandchild_DSL$Builder')
        def repeated = document('example.NamingRepeated').get('$defs').get('example.NamingRepeated_DSL$Builder')
        def base = document('example.NamingBase').get('$defs').get('example.NamingBase_DSL$Builder')
        def identity = document('example.NamingIdentity').get('$defs').get('example.NamingIdentity_DSL$Builder')
        def defs = document('example.NamingService').get('$defs')
        def key = document('example.NamingKey').get('$defs').get('example.NamingKey_DSL$Builder')

        then:
        service.properties.keySet() == ['display-name', 'displayName', 'service-name', 'url-value', 'xml2-parser', 'version2-url',
                'already_snake', 'Ångström-url', 'URL', 'empty-rename', 'neutral-ignore', 'configure-endpoint', 'literal-operation',
                'service-details', 'other-details', 'worker-instances', 'labels'] as Set
        service.additionalProperties == false
        !service.properties.containsKey('hidden-value')
        !service.properties.containsKey('ignored-operation')
        service.properties.URL.type == 'string'
        grandchild.properties.keySet() == service.properties.keySet() + 'final-name'
        repeated.properties.keySet() == service.properties.keySet() + 'equal-name'
        !base.properties.containsKey('displayName')
        !base.properties.containsKey('display-name')
        base.properties.keySet() == ['URL', 'emptyRename', 'neutralIgnore', 'literal-operation'] as Set
        identity.properties.keySet() == ['nativeName', 'URLValue'] as Set
        defs.get('example.NamingDetails_DSL$Builder').properties.keySet() == ['nativeName'] as Set
        defs.get('example.NamingWorker_DSL$Builder::keyless').properties.keySet() == ['image-name'] as Set
        service.properties.'service-details'.get('$ref') == service.properties.'other-details'.get('$ref')
        key.required == ['service-id']
        key.properties.keySet() == ['service-id', 'display-name'] as Set
        document('example.NamingLiteralKey').get('$defs').get('example.NamingLiteralKey_DSL$Builder').required == ['Service/ID']
        document('example.NamingKeyCollisionOwner').get('$defs').get('example.NamingKeyCollision_DSL$Builder::keyless').properties.keySet() == ['url-value'] as Set
        !trap.exists()
        !new File(consumer, 'src').exists()
        !new File(producer, 'api/src').exists()
        !new File(producer, 'schema/src').exists()
        !new File(producer, 'api/build/classes').exists()
        !new File(producer, 'schema/build/classes').exists()
        !new File(producer, 'owned/src').exists()
        !new File(producer, 'owned/build/classes').exists()

        when: 'the separately packaged interpreter checks the same exact names without compiler dependencies'
        def result = run('namingControl')

        then:
        result.output.contains('Naming interpreter/schema agreement passed')
        trap.text == 'executed'

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    def 'reject invalid profile contracts before schema replacement and input execution: #root with Groovy #generation'() {
        given:
        fixture(generation, false, false, false, false, false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.NamingService'])\n")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.NamingService').bytes
        new File(consumer, 'build.gradle').append("""
klumSchema.jsonSchema.rootTypes.add('example.$root')
tasks.register('rejectedNamingControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.NamingControl'
    args('$root')
}
""")

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema').buildAndFail()
        def interpreted = runner(consumer, 'rejectedNamingControl').build()

        then:
        failed.output.contains(diagnostic)
        failed.output.contains(first)
        failed.output.contains(second)
        interpreted.output.contains('Rejected naming contract before execution')
        output('example.NamingService').bytes == previous
        !output('example.' + root).exists()
        !trap.exists()

        where:
        generation | root                      | diagnostic                             | first                             | second
        '3'        | 'NamingConflict'          | 'Conflicting explicit input profiles'  | 'example.NamingService'            | 'example.NamingConflict'
        '4'        | 'NamingConflict'          | 'Conflicting explicit input profiles'  | 'example.NamingService'            | 'example.NamingConflict'
        '5'        | 'NamingIdentityConflict'  | 'Conflicting explicit input profiles'  | 'example.NamingIdentity'           | 'example.NamingIdentityConflict'
        '3'        | 'NamingCollision'         | 'KEBAB_CASE external-name collision'   | 'example.NamingCollision#URLValue' | 'example.NamingCollision#urlValue'
        '4'        | 'NamingCollision'         | 'KEBAB_CASE external-name collision'   | 'example.NamingCollision#URLValue' | 'example.NamingCollision#urlValue'
        '5'        | 'NamingCollision'         | 'KEBAB_CASE external-name collision'   | 'example.NamingCollision#URLValue' | 'example.NamingCollision#urlValue'
        '3'        | 'NamingKeyCollision'      | 'construction key external-name collision' | 'example.NamingKeyCollision#URLValue' | 'example.NamingKeyCollision#urlValue'
    }

    @Issue('959')
    def 'required naming metadata fails closed in both consumers: #target #kind'() {
        given:
        fixture(generation, false, false, false, false, false, true)
        new File(consumer, 'build.gradle').append("""
klumSchema.jsonSchema.rootTypes.set(['example.NamingService'])
tasks.register('rejectedNamingControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.NamingControl'
    args('NamingService')
}
""")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.NamingService').bytes
        rewriteNamingFacts(target, kind)

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema', '--refresh-dependencies').buildAndFail()
        def interpreted = runner(consumer, 'rejectedNamingControl').build()

        then:
        failed.output.contains('example.' + target)
        failed.output.contains(kind == 'native' ? 'Malformed UTF-16' : 'naming metadata')
        failed.output.contains('recompile and package')
        interpreted.output.contains('Rejected naming contract before execution')
        output('example.NamingService').bytes == previous
        !trap.exists()

        where:
        generation | target          | kind
        '5'        | 'NamingService' | 'profile'
        '3'        | 'NamingService' | 'explicit'
        '3'        | 'NamingService' | 'unknown'
        '3'        | 'NamingService' | 'inconsistent'
        '3'        | 'NamingService' | 'native'
        '3'        | 'NamingBase'    | 'profile'
        '3'        | 'NamingDetails' | 'profile'
    }


    private void rewriteNamingFacts(String target, String kind, String artifact = null) {
        String module = artifact ?: (target in ['NamingBase', 'NamingIdentity', 'NamingKeyBase', 'NamingLiteralKey'] ? 'api' :
                target in ['NamingDetails', 'NamingWorker'] ? 'owned' : 'schema')
        File jar = new File(repository, "fixture/$module/1.0/$module-1.0.jar")
        File replacement = new File(directory, 'naming-' + module + '.jar')
        new JarFile(jar).withCloseable { input ->
            new JarOutputStream(new FileOutputStream(replacement)).withCloseable { output ->
                input.entries().each { entry -> rewriteNamingEntry(input, output, entry, target, kind) }
            }
        }
        Files.move(replacement.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private static void rewriteNamingEntry(JarFile input, JarOutputStream output, JarEntry entry, String target, String kind) {
        byte[] bytes = input.getInputStream(entry).withCloseable { it.bytes }
        if (entry.name.endsWith('_DSL$Builder.class') && (target == '*' || entry.name == 'example/' + target + '_DSL$Builder.class')) {
            bytes = damageNamingContract(bytes, kind)
        }
        output.putNextEntry(new JarEntry(entry.name))
        output.write(bytes)
        output.closeEntry()
    }

    private static byte[] damageNamingContract(byte[] bytes, String kind) {
        def writer = new ClassWriter(0)
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                def downstream = super.visitAnnotation(descriptor, visible)
                if (!descriptor.endsWith('GeneratedInput$Schema;')) {
                    return downstream
                }
                namingSchemaMutation(downstream, kind)
            }
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                def downstream = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (kind.startsWith('examples-')) {
                    return exampleMutation(downstream, kind.substring('examples-'.length()))
                }
                namingNativeMutation(downstream, kind)
            }
        }, 0)
        writer.toByteArray()
    }

    private static MethodVisitor exampleMutation(MethodVisitor downstream, String kind) {
        new MethodVisitor(Opcodes.ASM9, downstream) {
            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                def visitor = super.visitAnnotation(descriptor, visible)
                if (!descriptor.endsWith('GeneratedInput$Field;') && !descriptor.endsWith('GeneratedInput$Operation;') && !descriptor.endsWith('GeneratedInput$Key;')) {
                    return visitor
                }
                new AnnotationVisitor(Opcodes.ASM9, visitor) {
                    @Override AnnotationVisitor visitArray(String member) {
                        if (member != 'examples') {
                            return super.visitArray(member)
                        }
                        if (kind == 'missing') {
                            return null
                        }
                        if (kind == 'scalar') {
                            super.visit(member, 'SECRET_PAYLOAD')
                            return null
                        }
                        if (kind == 'duplicate') {
                            super.visitArray(member).visitEnd()
                        }
                        def array = super.visitArray(member)
                        switch (kind) {
                            case 'element': array.visit(null, 1); break
                            case 'nested': array.visitArray(null).visitEnd(); break
                            case 'excessive': (1..257).each { array.visit(null, 'x' * 4096) }; break
                            default: array.visit(null, 'ordinary documentation')
                        }
                        array.visitEnd()
                        null
                    }
                }
            }
        }
    }

    private static AnnotationVisitor namingSchemaMutation(AnnotationVisitor downstream, String kind) {
        if (kind.startsWith('protocol-')) {
            return new AnnotationVisitor(Opcodes.ASM9, downstream) {
                @Override void visit(String member, Object value) {
                    if (member == 'version') {
                        if (kind == 'protocol-missing') { return }
                        value = ['protocol-older': 0, 'protocol-future': 2, 'protocol-malformed': '1'][kind]
                    }
                    super.visit(member, value)
                }
            }
        }

        new AnnotationVisitor(Opcodes.ASM9, downstream) {
            @Override void visit(String member, Object value) {
                if (kind == 'profile' && member == 'namingProfile') {
                    return
                }
                if (kind == 'explicit' && member == 'namingExplicit') {
                    return
                }
                if (kind == 'unknown' && member == 'namingProfile') {
                    value = 'FUTURE'
                }
                if (kind == 'inconsistent' && member == 'namingExplicit') {
                    value = false
                }
                super.visit(member, value)
            }
        }
    }

    private static MethodVisitor namingNativeMutation(MethodVisitor downstream, String kind) {
        if (kind.startsWith('literal-')) {
            return new MethodVisitor(Opcodes.ASM9, downstream) {
                @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    def visitor = super.visitAnnotation(descriptor, visible)
                    if (!['Field', 'Key', 'Operation'].any { descriptor.endsWith('GeneratedInput$' + it + ';') }) { return visitor }
                    new AnnotationVisitor(Opcodes.ASM9, visitor) {
                        @Override void visit(String member, Object value) {
                            if (kind == 'literal-origin' && member == 'nameOrigin'
                                    || kind == 'literal-rename' && member == 'rename'
                                    || kind == 'literal-ignore' && member == 'ignored') { return }
                            super.visit(member, value)
                        }
                    }
                }
            }
        }
        new MethodVisitor(Opcodes.ASM9, downstream) {
            @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                def visitor = super.visitAnnotation(annotation, visible)
                if (kind != 'native' || !(annotation.endsWith('GeneratedInput$Field;') || annotation.endsWith('GeneratedInput$Selection;'))) {
                    return visitor
                }
                new AnnotationVisitor(Opcodes.ASM9, visitor) {
                    @Override void visit(String member, Object value) {
                        if (value == 'serviceName' && member in ['value', 'field']) {
                            value = String.valueOf((char) 0xD800)
                        }
                        super.visit(member, value)
                    }
                }
            }
        }
    }

    @Issue('959')
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#field-constraint-projection')
    def 'choose field constraint projection without changing structural facts with Groovy #generation'() {
        given:
        fixture(generation, false, true, false, false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Projection'])\n")

        when:
        run('generateKlumJsonSchema')
        def schema = document('example.Projection')
        def root = schema.get('$defs').get('example.Projection_DSL$Builder')
        def properties = root.properties

        then:
        properties.projected.allOf == [[minimum: 1]]
        properties.implicit.findAll { key, value -> key != 'x-klum-constraint-source' } == properties.projected.findAll { key, value -> key != 'x-klum-constraint-source' }
        properties.'replica-count'.type == 'integer'
        properties.'replica-count'.minimum == -2147483648
        properties.'replica-count'.maximum == 2147483647
        !properties.'replica-count'.containsKey('allOf')
        properties.'replica-count'.description.startsWith('Desired replica count.')
        properties.'replica-count'.description.contains('Runtime constraint (not enforced by this schema; field projection policy TEXT_ONLY): @jakarta.validation.constraints.Min(value=1)')
        properties.'replica-count'.deprecated == true
        properties.omitted == [type: 'integer', minimum: -2147483648, maximum: 2147483647, description: 'Private capacity rule.', deprecated: true]
        properties.decimalText.type == 'number'
        !properties.decimalText.containsKey('exclusiveMinimum')
        properties.decimalText.description.contains('DecimalMin(inclusive=false, value="0.10")')
        properties.propagatedText.description.contains('@jakarta.validation.constraints.NotNull()')
        properties.propagatedOmit == [type: 'string', description: 'Required label.']
        properties.directText.description == properties.propagatedText.description
        properties.directOmit == properties.propagatedOmit
        properties.'inherited-percent'.description.contains('field projection policy TEXT_ONLY')
        !properties.'inherited-percent'.containsKey('allOf')
        schema.get('$defs').get('example.ProjectionChild_DSL$Builder').properties.amount == [type: 'number', description: 'Amount.']
        schema.get('$defs').get('example.ProjectionChild_DSL$Builder').properties.quantity.allOf == [[minimum: 2]]
        !properties.child.containsKey('x-klum-constraint-source')
        !properties.child.description.contains('Size')
        !properties.containsKey('hidden')
        !properties.containsKey('collision')
        !root.containsKey('required')
        !trap.exists()
        !new File(producer, 'api/src').exists()
        !new File(producer, 'schema/src').exists()

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    def 'field policy metadata fails closed before replacing output: #target #kind'() {
        given:
        fixture('3', false, true, false, false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Projection'])\n")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.Projection').bytes
        stripConstraintFacts(target, kind)

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema', '--refresh-dependencies').buildAndFail()

        then:
        failed.output.contains('example.' + target + '_DSL$Builder')
        failed.output.contains(diagnostic)
        failed.output.contains('recompile and package')
        output('example.Projection').bytes == previous
        !trap.exists()

        where:
        target            | kind                 | diagnostic
        'Projection'      | 'policy'             | 'Missing constraint projection policy'
        'ProjectionChild' | 'policy'             | 'Missing constraint projection policy'
        'Projection'      | 'unknown-policy'     | 'FUTURE'
        'ProjectionChild' | 'bad-decimal'        | 'Invalid decimal bound'
    }

    @Issue('959')
    def 'project supported numeric bounds and retain unsupported P2 descriptions and source deprecation from binaries with Groovy #generation'() {
        given:
        fixture(generation, false, true, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema { rootTypes.set(['example.P2']) }\n")

        when:
        def result = run('generateKlumJsonSchema')
        def schema = document('example.P2')
        def properties = schema.get('$defs').get('example.P2_DSL$Builder').properties
        def child = schema.get('$defs').get('example.P2Child_DSL$Builder')

        then:
        result.task(':generateKlumJsonSchema').outcome == TaskOutcome.SUCCESS
        properties.amount.exclusiveMinimum == -123G
        properties.amount.maximum == 1000G
        properties.amount.type == 'number'
        properties.words.description.contains('Size(max=3, min=2)')
        properties.distinct.type == 'array'
        properties.labels.type == 'object'
        properties.children.description.contains('Size(max=3, min=2)')
        properties.code.description.contains('CASE_INSENSITIVE')
        !properties.code.containsKey('pattern')
        properties.groupedDecimal.description.contains('groups=example.Later')
        properties.repeatedDecimal.description.contains('DecimalMin$List(value=[')
        properties.warningSize.description.contains('payload=example.Warning')
        properties.repeatedPattern.description.contains('Pattern$List(value=[')
        properties.composedPattern.description.contains('@example.P2Pattern()')
        !properties.composedPattern.containsKey('pattern')
        properties['inherited-size'].description.contains('Size(min=1)')
        properties['inherited-size']['x-klum-constraint-source'] == 'example.CapacityBase#inheritedWords'
        properties.keySet().containsAll(['old-child', 'old-operation', 'old-inherited'])
        !properties.containsKey('hidden')
        properties['old-child'].deprecated == true
        properties['old-operation'].deprecated == true
        properties['old-inherited'].deprecated == true
        !child.containsKey('deprecated')
        !trap.exists()
        !new File(producer, 'api/src').exists()
        !new File(producer, 'schema/src').exists()
        !new File(consumer, 'src').exists()
        publicDeprecationSites('schema', 'example/P2_DSL$Builder.class').contains('oldOperation')
        publicDeprecationSites('schema', 'example/P2_DSL$Builder.class').contains('getOldChild')
        publicDeprecationSites('api', 'example/CapacityBase_DSL$Builder.class').contains('getOldInherited')
        publicDeprecationSites('schema', 'example/P2_DSL$Builder.class').contains('getHidden')
        publicDeprecationSites('schema', 'example/P2_DSL$TemplateScope.class').containsAll(['Create', 'CreateFrom'])
        !properties.containsKey('Create')
        !properties.containsKey('CreateFrom')
        !properties.child.containsKey('deprecated')
        !schema.get('$defs').get('example.P2_DSL$Builder').containsKey('deprecated')

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#deprecation')
    def 'deprecated Schema members remain admitted with advisory metadata by default with Groovy #generation'() {
        given:
        fixture(generation, false, true, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.P2', 'example.DeprecatedFallbackKey', 'example.DeprecatedFieldOverlap', 'example.DeprecatedOperationOverlap'])\n")

        when:
        def result = run('generateKlumJsonSchema')
        def schema = document('example.P2')
        def root = schema.get('$defs').get('example.P2_DSL$Builder')
        def properties = root.properties
        def child = schema.get('$defs').get('example.P2Child_DSL$Builder')
        def keylessChild = schema.get('$defs').get('example.P2Child_DSL$Builder::keyless')
        def fallback = document('example.DeprecatedFallbackKey').get('$defs').get('example.DeprecatedFallbackKey_DSL$Builder')
        File validation = new File(System.getProperty('schemaValidationDirectory'), 'deprecation-' + generation)
        validation.mkdirs()
        ['P2', 'DeprecatedFallbackKey'].each { model ->
            new File(validation, model.toLowerCase() + '.schema.json').bytes = output('example.' + model).bytes
        }

        then:
        result.task(':generateKlumJsonSchema').outcome == TaskOutcome.SUCCESS
        properties.oldEndpoint.type == 'string'
        properties.oldEndpoint.deprecated == true
        properties.oldEndpoint.description.startsWith('Returns the oldEndpoint.')
        properties.endpoint.type == 'string'
        !properties.endpoint.containsKey('deprecated')
        properties['old-inherited'].deprecated == true
        properties['old-inherited-operation'].deprecated == true
        properties.directOperation == [type: 'string', deprecated: true]
        ['oldCount', 'oldTags', 'oldLabels', 'oldMode', 'oldEnabled', 'oldTagsOperation'].every { properties[it].deprecated == true }
        properties.oldTagsOperation.type == 'array'
        !properties.oldTagsOperation.get('items').containsKey('deprecated')
        properties['old-operation'].deprecated == true
        properties['old-child'].subMap(['$ref', 'deprecated']) == ['$ref': '#/$defs/example.P2Child_DSL%24Builder', deprecated: true]
        properties.child.subMap(['$ref', 'deprecated']) == ['$ref': '#/$defs/example.P2Child_DSL%24Builder']
        properties.children.deprecated == true
        properties.children.additionalProperties == ['$ref': '#/$defs/example.P2Child_DSL%24Builder%3A%3Akeyless']
        child.properties.name == [type: 'string', deprecated: true]
        child.required == ['name']
        schema.get('$defs').get('example.P2InheritedKey_DSL$Builder').properties.name == [type: 'string', deprecated: true]
        schema.get('$defs').get('example.P2InheritedKey_DSL$Builder').required == ['name']
        !child.containsKey('deprecated')
        !child.properties.value.containsKey('deprecated')
        !keylessChild.properties.containsKey('name')
        !keylessChild.containsKey('deprecated')
        fallback.properties['legacy-id'] == [type: 'string', deprecated: true]
        fallback.required == ['legacy-id']
        document('example.DeprecatedFieldOverlap').get('$defs').get('example.DeprecatedFieldOverlap_DSL$Builder').properties.key.deprecated == true
        !document('example.DeprecatedFieldOverlap').get('$defs').get('example.DeprecatedFieldOverlap_DSL$Builder').properties.setKey.containsKey('deprecated')
        !document('example.DeprecatedOperationOverlap').get('$defs').get('example.DeprecatedOperationOverlap_DSL$Builder').properties.key.containsKey('deprecated')
        document('example.DeprecatedOperationOverlap').get('$defs').get('example.DeprecatedOperationOverlap_DSL$Builder').properties.setKey.deprecated == true
        !root.containsKey('required')
        !root.containsKey('deprecated')
        !properties.keySet().intersect(['hidden', 'hiddenOperation', 'excludedLink', 'excludedState', 'unsupportedOperation', 'collision', 'mixed-collision', 'Create', 'CreateFrom'])
        schema.get('x-klum-unsupported').values().any { it.contains('collision') }
        publicDeprecationSites('schema', 'example/P2_DSL$TemplateScope.class').containsAll(['Create', 'CreateFrom'])
        !trap.exists()
        !new File(consumer, 'src').exists()
        !new File(producer, 'api/src').exists()
        !new File(producer, 'schema/src').exists()

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    def 'missing source deprecation facts reject current metadata with Groovy #generation'() {
        given:
        fixture(generation, false, true, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.P2', 'example.DeprecatedFallbackKey'])\n")

        run('generateKlumJsonSchema')
        byte[] previous = output('example.P2').bytes
        rewriteDeprecationFacts('schema', true)
        rewriteDeprecationFacts('api', true)

        when:
        def result = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        result.output.contains('Missing source deprecation fact')
        result.output.contains('recompile and package')
        output('example.P2').bytes == previous
        publicDeprecationSites('schema', 'example/P2_DSL$Builder.class').containsAll(['getOldEndpoint', 'oldOperation', 'child'])
        !trap.exists()

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    def 'deprecated generated selection does not override explicit nondeprecated source fact'() {
        given:
        fixture('3', false, true, true)
        rewriteDeprecationFacts('schema', false)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.P2'])\n")

        when:
        run('generateKlumJsonSchema')
        def properties = document('example.P2').get('$defs').get('example.P2_DSL$Builder').properties

        then:
        publicDeprecationSites('schema', 'example/P2_DSL$Builder.class').contains('child')
        !properties.child.containsKey('deprecated')
        properties['old-child'].deprecated == true
    }

    private Set<String> publicDeprecationSites(String artifact, String entry) {
        Set<String> sites = []
        new JarFile(new File(repository, "fixture/$artifact/1.0/$artifact-1.0.jar")).withCloseable { jar ->
            new ClassReader(jar.getInputStream(jar.getJarEntry(entry))).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    new MethodVisitor(Opcodes.ASM9) {
                        @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (annotation == 'Ljava/lang/Deprecated;') sites.add(name)
                            return null
                        }
                    }
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        }
        sites
    }

    @Issue('959')
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#decimal-constraints')
    def 'generate exact direct decimal authoring bounds by default from source-free binaries with Groovy #generation'() {
        given:
        fixture(generation, false, true, false, true)

        when:
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Price', 'example.SequencedPrice'])\n")
        def result = run('generateKlumJsonSchema')
        def schema = document('example.Price')
        def root = schema.get('$defs').get('example.Price_DSL$Builder')
        def properties = root.properties
        File validation = new File(System.getProperty('schemaValidationDirectory'), 'decimal-' + generation)
        validation.mkdirs()
        new File(validation, 'price.schema.json').bytes = output('example.Price').bytes

        then:
        result.task(':generateKlumJsonSchema').outcome == TaskOutcome.SUCCESS
        properties.amount.exclusiveMinimum == 0.10G
        properties.amount.maximum == 9007199254740993.0000000001G
        properties.amount.description.contains('Authored price.')
        properties.equal.minimum == 1G && properties.equal.maximum == 1G
        properties.zero.minimum == 0G && properties.zero.maximum == 0G
        properties.exponent.minimum == 2500G && properties.exponent.maximum == 3E+3G
        properties.tiny.minimum == 1E-1000G
        properties.'inherited-price'.exclusiveMinimum == -1.2300E+2G
        properties.'inherited-price'.'x-klum-constraint-source' == 'example.DecimalBase#inherited'
        ['boundedByte', 'boundedShort', 'boundedPrimitiveShort', 'boundedInt', 'boundedPrimitiveInt', 'boundedLong', 'boundedPrimitiveLong', 'boundedBigInteger'].every {
            properties[it].type == 'integer' && properties[it].exclusiveMinimum == -0.1G && numericBounds(properties[it], 'maximum').any { bound -> bound == 1.1G }
        }
        properties.units.type == 'integer'
        numericBounds(properties.units, 'minimum').any { bound -> bound == 2G } && properties.units.exclusiveMaximum == 3.1G
        properties.machine.minimum == -128 && properties.machine.maximum == 127
        properties.machine.exclusiveMinimum == -0.1G
        properties.tight.minimum == 2G && properties.tight.exclusiveMinimum == 1.0G
        properties.tie.exclusiveMinimum == 2G && properties.tie.minimum == 2G
        properties.child.'$ref'
        schema.get('$defs').get('example.DecimalChild_DSL$Builder').properties.amount.minimum == 0G
        properties.composed.minimum == 1G
        ['text', 'floating', 'doubleValue', 'grouped', 'payloadBound', 'repeated'].every {
            !properties[it].containsKey('exclusiveMinimum') && !properties[it].containsKey('minimum')
                    && properties[it].description.contains('Runtime constraint (not enforced by this schema')
        }
        properties.operation.type == 'number' && !properties.operation.containsKey('minimum')
        !root.containsKey('required')
        document('example.SequencedPrice').get('$defs').get('example.SequencedPrice_DSL$Builder').properties.amount.description.contains('group redefinition/sequence')
        !document('example.SequencedPrice').get('$defs').get('example.SequencedPrice_DSL$Builder').properties.amount.containsKey('maximum')
        !trap.exists()
        !new File(producer, 'schema/src').exists() && !new File(producer, 'api/src').exists()

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    def 'generation requires complete decimal facts on #target: #kind'() {
        given:
        fixture('3', false, true, false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema { rootTypes.set(['example.Price']) }\n")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.Price').bytes
        File manifest = new File(output('example.Price').parentFile, '.klum-json-schema-outputs')
        String previousManifest = manifest.text
        stripConstraintFacts(target, kind)

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema', '--refresh-dependencies').buildAndFail()

        then:
        failed.output.contains(target + '_DSL$Builder')
        failed.output.contains('recompile and package')
        failed.output.contains(reason)
        output('example.Price').bytes == previous
        manifest.text == previousManifest
        !trap.exists()

        where:
        target         | kind                    | reason
        'Price'        | 'decimalValue'          | 'missing direct decimal facts'
        'Price'        | 'inclusive'             | 'missing direct decimal facts'
    }

    @Issue('959')
    def 'decimal policy errors preserve previous output: #model'() {
        given:
        fixture('3', false, true, false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Price'])\n")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.Price').bytes
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema { rootTypes.set(['example.$model']) }\n")

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        failed.output.contains(reason)
        failed.output.contains('example.' + model + '#amount')
        failed.output.contains("external input 'amount'")
        output('example.Price').bytes == previous
        !output('example.' + model).exists()
        !trap.exists()

        where:
        model                | reason
        'MalformedDecimal'   | 'Invalid decimal bound'
        'Unrepresentable'    | 'Invalid decimal bound'
    }

    @Issue('959')
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#integral-constraints')
    def 'generate integral authoring bounds by default from binary Builder contracts with Groovy #generation'() {
        given:
        fixture(generation, false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Capacity'])\n")

        when:
        def result = run('generateKlumJsonSchema')
        def schema = document('example.Capacity')
        def root = schema.get('$defs').get('example.Capacity_DSL$Builder')
        File validation = new File(System.getProperty('schemaValidationDirectory'), 'integral-' + generation)
        validation.mkdirs()
        new File(validation, 'capacity.schema.json').bytes = output('example.Capacity').bytes

        then:
        result.task(':generateKlumJsonSchema').outcome == TaskOutcome.SUCCESS
        numericBounds(root.properties.percentage, 'minimum') == [-2147483648G, 0G]
        numericBounds(root.properties.percentage, 'maximum') == [2147483647G, 100G]
        root.properties.percentage.description.contains('Percentage of available capacity.')
        !root.containsKey('required')
        schema.description.contains('raw-input authoring')
        !trap.exists()
        !new File(producer, 'schema/src').exists()
        !new File(producer, 'api/src').exists()

        where:
        generation << ['3', '4', '5']
    }

    @Issue('959')
    def 'generation fails closed for #kind #target metadata and preserves previous outputs'() {
        given:
        fixture('3', false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Capacity'])\n")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.Capacity').bytes
        File manifest = new File(output('example.Capacity').parentFile, '.klum-json-schema-outputs')
        String previousManifest = manifest.text
        stripConstraintFacts(target, kind)

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema', '--refresh-dependencies').buildAndFail()

        then:
        failed.output.contains("root 'example.Capacity'")
        failed.output.contains('example.' + target + '_DSL$Builder')
        failed.output.contains(target == 'CapacityBase' ? 'source field #inherited' : 'source field #percentage')
        failed.output.contains(target == 'CapacityBase' ? "external input 'inherited-percent'" : "external input 'percentage'")
        failed.output.contains('recompile and package')
        failed.output.contains('Missing constraint facts')
        output('example.Capacity').bytes == previous
        manifest.text == previousManifest
        !trap.exists()

        where:
        target          | kind
        'Capacity'      | 'field'
    }

    @Issue('959')
    def 'unsupported complete field constraints retain runtime descriptions and effective group context'() {
        given:
        fixture('3', false, true)
        new File(consumer, 'build.gradle').append("""
klumSchema.jsonSchema {
    rootTypes.set(['example.Capacity', 'example.SequencedCapacity'])
}
""")

        when:
        run('generateKlumJsonSchema')
        def root = document('example.Capacity').get('$defs').get('example.Capacity_DSL$Builder')
        def sequenced = document('example.SequencedCapacity').get('$defs').get('example.SequencedCapacity_DSL$Builder')

        then:
        root.properties.grouped.minimum == -2147483648
        root.properties.explicitDefault.minimum == -2147483648
        root.properties.warning.minimum == -2147483648
        root.properties.composed.minimum == -2147483648
        root.properties.composed.allOf == [[maximum: 100], [minimum: 0]]
        root.properties.repeated.minimum == -2147483648
        root.properties.customRepeated.minimum == -2147483648
        root.properties.decimal.type == 'number'
        root.properties.decimal.minimum == 0.1G
        !root.properties.numericText.containsKey('minimum')
        root.properties.grouped.description.contains('groups=')
        root.properties.warning.description.contains('payload=')
        root.properties.composed.description.contains('example.Percentage')
        root.properties.repeated.description.contains('Min$List')
        root.properties.customRepeated.description.contains('example.Rules')
        root.properties.numericText.description.contains('Numeric text documentation.')
        ['grouped', 'explicitDefault', 'warning', 'composed', 'repeated', 'customRepeated', 'decimal', 'numericText', 'needed', 'requiredText'].every {
            root.properties[it].description.contains('Runtime constraint (not enforced by this schema')
        }
        !root.containsKey('required')
        sequenced.properties.'inherited-percent'.minimum == -2147483648
        sequenced.properties.'inherited-percent'.description.contains('group redefinition/sequence')
        !trap.exists()
    }

    @Issue('959')
    def 'direct integral bounds conjoin with machine ranges without projecting later construction behavior'() {
        given:
        fixture('3', false, true)
        new File(consumer, 'build.gradle').append("""
klumSchema.jsonSchema { rootTypes.set(['example.Capacity']) }
""")

        when:
        run('generateKlumJsonSchema')
        def root = document('example.Capacity').get('$defs').get('example.Capacity_DSL$Builder')
        def properties = root.properties

        then:
        ['byteValue', 'boxedByte', 'shortValue', 'boxedShort', 'intValue', 'percentage', 'longValue', 'boxedLong', 'large'].every {
            numericBounds(properties[it], 'minimum').any { bound -> bound == 0G } && numericBounds(properties[it], 'maximum').any { bound -> bound == 100G }
        }
        properties.machine.minimum == -128
        properties.machine.maximum == 127
        numericBounds(properties.machine, 'minimum') == [-128G, -1000G]
        numericBounds(properties.machine, 'maximum') == [127G, 1000G]
        properties.exact.minimum == -9223372036854775808G
        properties.exact.maximum == 9223372036854775807G
        numericBounds(properties.'inherited-percent', 'minimum').any { bound -> bound == 0G }
        properties.'inherited-percent'.'x-klum-constraint-source' == 'example.CapacityBase#inherited'
        !properties.containsKey('inherited')
        !properties.containsKey('hidden')
        !properties.percentage.containsKey('default')
        !properties.percentage.containsKey('const')
        properties.fromText.type == 'string'
        !properties.fromText.containsKey('minimum')
        !properties.fromText.containsKey('maximum')
        properties.label.type == 'string'
        properties.enabled.type == 'boolean'
        !properties.computed.containsKey('default')
        !root.containsKey('required')
        !trap.exists()
    }

    @Issue('959')
    def 'contradictory numeric declarations project without additional configuration'() {
        given:
        fixture('3', false, true, false, true)
        def models = ['Impossible', 'ImpossibleByte', 'EqualExclusive', 'UpperExclusive', 'BothExclusive',
                      'ReversedDecimal', 'NoInteger', 'ByteOverflow', 'MixedBounds', 'ExtremeBounds']
        def roots = models.collect { "'example.$it'" }.join(', ')
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set([$roots])\n")

        when:
        def result = run('generateKlumJsonSchema')
        File validation = new File(System.getProperty('schemaValidationDirectory'), 'numeric-contradictions')
        validation.mkdirs()
        models.each { new File(validation, it + '.schema.json').bytes = output('example.' + it).bytes }
        def impossible = document('example.Impossible').get('$defs').get('example.Impossible_DSL$Builder').properties.percent

        then:
        result.task(':generateKlumJsonSchema').outcome == TaskOutcome.SUCCESS
        numericBounds(impossible, 'minimum') == [-2147483648G, 100G]
        numericBounds(impossible, 'maximum') == [2147483647G, 50G]
        !trap.exists()
    }

    private static List numericBounds(Map schema, String keyword) {
        def fragments = [schema] + (schema.allOf ?: [])
        fragments.findAll { it.containsKey(keyword) }.collect { it[keyword] }
    }

    @Issue('959')
    def 'malformed nested constraint metadata fails closed without replacing outputs: #kind'() {
        given:
        fixture('3', false, true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.Capacity'])\n")
        run('generateKlumJsonSchema')
        byte[] previous = output('example.Capacity').bytes
        File manifest = new File(output('example.Capacity').parentFile, '.klum-json-schema-outputs')
        String previousManifest = manifest.text
        stripConstraintFacts('Capacity', kind)

        when:
        def failed = runner(consumer, 'generateKlumJsonSchema', '--refresh-dependencies').buildAndFail()

        then:
        failed.output.contains("root 'example.Capacity'")
        failed.output.contains('example.Capacity_DSL$Builder')
        failed.output.contains('recompile and package')
        failed.output.contains(reason)
        output('example.Capacity').bytes == previous
        manifest.text == previousManifest
        !trap.exists()

        where:
        kind        | reason
        'plain'     | 'Missing constraint eligibility'
        'value'     | 'Missing constraint eligibility'
        'group-value' | 'Missing direct integral bound'
        'primitive' | 'Unexpected constraint array item'
        'enum'      | 'Unexpected constraint array item'
        'array'     | 'Unexpected constraint array item'
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

    @Issue(['529', '959'])
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

    @Issue(['529', '959', '952'])
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

    @Issue('959')
    def 'missing isolated runtime cannot fall back to plugin or daemon runtime classes'() {
        given:
        fixture('3')
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.classpath.setFrom(configurations.schemaBinary)\n")

        when:
        def result = runner(consumer, 'generateKlumJsonSchema').buildAndFail()

        then:
        result.output.contains('Schema/API/runtime/annotation classpath')
        result.output.contains("Cannot generate JSON Schema for root 'example.Deployment'")
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

    private void patternCorruptionFixture() {
        synchronized (patternCache) {
            if (patternTrapPath == null) {
                fixture('3', false, false, false, false, false, false, false, false, true)
                patternTrapPath = trap.absolutePath
                copyTree(repository, patternCache)
                return
            }
            packagedPlugin = false
            initializeFixtureDirectories()
            copyTree(patternCache, repository)
            Set<String> rewritten = [] as Set
            ['api', 'owned', 'schema'].each { artifact ->
                specializeTrap(artifact, patternTrapPath, rewritten)
            }
            assert rewritten.containsAll(['example/Deployment.class', 'example/Release.class']):
                    'Every Pattern fixture initialization trap must be private to the scenario'
            writeConsumer('3', false, true)
        }
    }

    private void specializeTrap(String artifact, String originalTrapPath, Set<String> rewritten) {
        File jar = new File(repository, "fixture/$artifact/1.0/$artifact-1.0.jar")
        File replacement = new File(directory, "$artifact-trap.jar")
        int[] replacements = [0] as int[]
        new JarFile(jar).withCloseable { input ->
            new JarOutputStream(new FileOutputStream(replacement)).withCloseable { output ->
                input.entries().each { entry ->
                    byte[] bytes = specializeTrapEntry(input, entry, originalTrapPath, replacements, rewritten)
                    output.putNextEntry(new JarEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        jar.bytes = replacement.bytes
    }

    private byte[] specializeTrapEntry(JarFile input, JarEntry entry, String originalTrapPath, int[] replacements, Set<String> rewritten) {
        byte[] bytes = input.getInputStream(entry).withCloseable { it.bytes }
        if (!entry.name.endsWith('.class')) {
            return bytes
        }
        int before = replacements[0]
        bytes = specializeTrapClass(bytes, originalTrapPath, replacements)
        if (replacements[0] > before) {
            rewritten.add(entry.name)
        }
        bytes
    }

    private byte[] specializeTrapClass(byte[] bytes, String originalTrapPath, int[] replacements) {
        ClassWriter writer = new ClassWriter(0)
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override void visitLdcInsn(Object value) {
                        if (value == originalTrapPath) {
                            replacements[0]++
                            value = trap.absolutePath
                        }
                        super.visitLdcInsn(value)
                    }
                }
            }
        }, 0)
        writer.toByteArray()
    }

    private static void copyTree(File source, File destination) {
        source.eachFileRecurse { file ->
            File target = new File(destination, source.toPath().relativize(file.toPath()).toString())
            if (file.isDirectory()) {
                target.mkdirs()
            } else {
                target.parentFile.mkdirs()
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private void initializeFixtureDirectories() {
        producer = new File(directory, 'producer')
        consumer = new File(directory, 'consumer')
        repository = new File(directory, 'repository')
        trap = new File(directory, 'schema-executed')
        producer.mkdirs()
        consumer.mkdirs()
    }

    private void fixture(String generation, boolean defaults = false, boolean constraints = false, boolean p2 = false, boolean decimals = false, boolean projection = false, boolean naming = false, boolean literals = false, boolean examples = false, boolean patterns = false, boolean compositions = false) {
        packagedPlugin = generation == '5'
        initializeFixtureDirectories()
        String groovy = notation(System.getProperty("gdslGroovy$generation"))
        String compiler = classpath(System.getProperty('gdslCompilerClasspath'))
        new File(producer, 'settings.gradle').text = "include 'api', 'schema'" + (naming || patterns ? ", 'owned'" : '')
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
    @GeneratedInput.Schema(version=1, operationCount=0, groupRedefined=false, namingExplicit=false, namingProfile='IDENTITY') interface Builder<SELF extends Service> {}
    interface Factory { Service One() }
}
""")
        if (constraints) {
            new File(producer, 'build.gradle').append("\nsubprojects { dependencies { implementation files(${notation(System.getProperty('schemaValidationApi'))}) } }\n")
            writeSource('schema/src/main/groovy/example/Capacity.groovy', """
package example
import com.blackbuild.klum.ast.*
import jakarta.validation.constraints.*
import jakarta.validation.*
import com.fasterxml.jackson.annotation.*
import com.blackbuild.klum.ast.layer3.DefaultValues
import jakarta.validation.groups.Default as DefaultGroup
import java.lang.annotation.*
@DSL @Literals(percentage=75, label='filled', enabled=true) class Capacity extends CapacityBase {
    static { new File('${trap.absolutePath}').text = 'initialized'; throw new AssertionError('Schema initialization control') }
    /** Percentage of available capacity. */
    @Min(0L) @Max(100L) Integer percentage = 75
    @Min(0L) @Max(100L) byte byteValue
    @Min(0L) @Max(100L) Byte boxedByte
    @Min(0L) @Max(100L) short shortValue
    @Min(0L) @Max(100L) Short boxedShort
    @Min(0L) @Max(100L) int intValue
    @Min(0L) @Max(100L) long longValue
    @Min(0L) @Max(100L) Long boxedLong
    @Min(0L) @Max(100L) BigInteger large
    @Min(-1000L) @Max(1000L) byte machine
    @Min(Long.MIN_VALUE) @Max(Long.MAX_VALUE) BigInteger exact
    @JsonIgnore @Min(0L) Integer hidden
    @Size(min=1) String label
    Boolean enabled
    @Default(code={ throw new AssertionError('default executed') }) String computed
    @PostTree void repair() { percentage = 50 }
    @Builder.Method void fromText(String value) { percentage = Integer.valueOf(value) }
    CapacityChild child
    @Min(value=0L, groups=Later) Integer grouped
    @Min(value=0L, groups=DefaultGroup) Integer explicitDefault
    @Min(value=0L, payload=Warning) Integer warning
    @Percentage Integer composed
    @Min.List([@Min(0L), @Min(10L)]) Integer repeated
    @Rules([@Rule(0L), @Rule(10L)]) Integer customRepeated
    @Min(0L) @DecimalMin("0.1") BigDecimal decimal
    /** Numeric text documentation. */
    @Min(0L) @Pattern(regexp="[0-9]+") String numericText
    @NotNull String needed
    @Required String requiredText
}
interface Later {}
class Warning implements Payload { static { throw new AssertionError('payload initialized') } }
@Min(0L) @Max(100L) @Constraint(validatedBy=[])
@Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.ANNOTATION_TYPE])
@interface Percentage {
    String message() default 'percentage'
    Class[] groups() default []
    Class[] payload() default []
}
@Constraint(validatedBy=[RuleValidator])
@Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD])
@interface Rule {
    long value()
    String message() default 'rule'
    Class[] groups() default []
    Class[] payload() default []
}
@Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD])
@interface Rules { Rule[] value() }
class RuleValidator implements ConstraintValidator<Rule, Integer> {
    static { throw new AssertionError('validator initialized') }
    void initialize(Rule rule) { throw new AssertionError('validator initialize executed') }
    boolean isValid(Integer value, ConstraintValidatorContext context) { throw new AssertionError('validator executed') }
}
@DSL class Impossible { @JsonProperty('percent') @Min(100L) @Max(50L) Integer percentage }
@DSL class ImpossibleByte { @JsonProperty('percent') @Min(200L) byte percentage }
@DSL @GroupSequence([SequencedCapacity]) class SequencedCapacity extends CapacityBase {}
@DSL class CapacityChild { @Min(0L) @Max(100L) Integer percentage }
""")
        }
        if (p2) {
            writeSource('schema/src/main/groovy/example/P2.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.fasterxml.jackson.annotation.*
import jakarta.validation.constraints.*
import jakarta.validation.Constraint
import java.lang.annotation.*
@DSL class P2 extends CapacityBase {
    static { throw new AssertionError('P2 Model initialized') }
    @DecimalMin(value='-1.2300E+2', inclusive=false) @DecimalMax('1E+3') BigDecimal amount
    @DecimalMin('0.1') Integer integralDecimal
    @Size(min=2, max=3) List<String> words
    @Size(min=2, max=3) Set<String> distinct
    @Size(min=2, max=3) Map<String, String> labels
    @Deprecated @Size(min=2, max=3) Map<String, P2Child> children
    @Size(min=1, max=1) String text
    @Pattern(regexp='[A-Z]+', flags=Pattern.Flag.CASE_INSENSITIVE) String code
    @DecimalMin(value='0.1', groups=Later) BigDecimal groupedDecimal
    @DecimalMin.List([@DecimalMin('0.1'), @DecimalMin('0.2')]) BigDecimal repeatedDecimal
    @Size(min=1, payload=Warning) List<String> warningSize
    @Pattern.List([@Pattern(regexp='a'), @Pattern(regexp='b')]) String repeatedPattern
    @P2Pattern String composedPattern
    @Deprecated @JsonProperty('old-child') P2Child oldChild
    P2Child child
    P2InheritedKey inheritedChild
    @Deprecated String oldEndpoint
    @Deprecated int oldCount
    @Deprecated List<String> oldTags
    @Deprecated Map<String, String> oldLabels
    @Deprecated Mode oldMode
    @Deprecated Boolean oldEnabled
    @Deprecated @Builder.Method void oldTagsOperation(List<String> values) { }
    String endpoint
    @Deprecated @JsonIgnore String hidden
    @Deprecated @Field(FieldType.LINK) P2Child excludedLink
    @Deprecated transient String excludedState
    @Deprecated @JsonProperty('collision') String oldCollision
    @JsonProperty('collision') String newCollision
    @Deprecated @JsonProperty('mixed-collision') String mixedCollision
    @Builder.Method @JsonProperty('mixed-collision') void mixedOperation(String value) { }
    @Deprecated @Builder.Method @JsonIgnore void hiddenOperation(String value) { }
    @Deprecated @Builder.Method void unsupportedOperation(String first, String second) { }
    @Deprecated @Builder.Method void directOperation(String value) { throw new AssertionError('operation executed') }
    @Deprecated @Builder.Method @JsonProperty('old-operation') void oldOperation(String text) {
        throw new AssertionError('P2 operation executed')
    }
}
@Deprecated @DSL class P2Child { @Deprecated @Key String name; String value }
@DSL class P2InheritedKey extends P2Child { String extra }
@DSL class DeprecatedFieldOverlap {
    @Deprecated String key
    @Builder.Method void setKey(String value) { this.key = value }
}
@DSL class DeprecatedOperationOverlap {
    String key
    @Deprecated @Builder.Method void setKey(String value) { this.key = value }
}
@DSL class DeprecatedFallbackKey { @Deprecated @Key @JsonProperty('legacy-id') String key; String value }
@Constraint(validatedBy=[]) @Pattern(regexp='a')
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD])
@interface P2Pattern {
    String message() default 'p2'
    Class[] groups() default []
    Class[] payload() default []
}
""")
        }
        if (constraints) {
            writeSource('api/src/main/groovy/example/Literals.groovy', '''
package example
import java.lang.annotation.*
import com.blackbuild.klum.ast.layer3.DefaultValues
@Retention(RetentionPolicy.RUNTIME) @DefaultValues
@interface Literals { int percentage(); String label(); boolean enabled(); }
''')
            writeSource('api/src/main/groovy/example/CapacityBase.groovy', """
package example
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Builder
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.*
@DSL abstract class CapacityBase {
    @JsonProperty('inherited-percent') @Min(0L) @Max(100L) Integer inherited
}
""")
        }
        if (p2) {
            File base = new File(producer, 'api/src/main/groovy/example/CapacityBase.groovy')
            base.text = base.text.replace('@DSL abstract class CapacityBase {', """@DSL abstract class CapacityBase {
    @Deprecated @JsonProperty('old-inherited') String oldInherited
    @Deprecated @Builder.Method @JsonProperty('old-inherited-operation') void inheritedOperation(String value) {
        throw new AssertionError('inherited operation executed')
    }
    @JsonProperty('inherited-size') @Size(min=1) List<String> inheritedWords
""")
        }
        if (decimals) {
            writeSource('api/src/main/groovy/example/DecimalBase.groovy', """
package example
import com.blackbuild.klum.ast.DSL
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.*
@DSL abstract class DecimalBase {
    @JsonProperty('inherited-price') @DecimalMin(value='-1.2300E+2', inclusive=false) BigDecimal inherited
}
class DecimalConstants {
    public static final String HIGH = '9007199254740993.0000000001'
}
""")
            writeSource('schema/src/main/groovy/example/Price.groovy', """
package example
import com.blackbuild.klum.ast.*
import jakarta.validation.constraints.*
import jakarta.validation.Constraint
import jakarta.validation.GroupSequence
import java.lang.annotation.*
@DSL class Price extends DecimalBase {
    static { new File('${trap.absolutePath}').text = 'initialized'; throw new AssertionError('Model initialized') }
    /** Authored price. */
    @DecimalMin(value='0.10', inclusive=false) @DecimalMax(value=DecimalConstants.HIGH, inclusive=true) BigDecimal amount
    @DecimalMin('1.00') @DecimalMax('1E+0') BigDecimal equal
    @DecimalMin('-0.00') @DecimalMax('+0E+2') BigDecimal zero
    @DecimalMin('+2.50E+3') @DecimalMax('3E+3') BigDecimal exponent
    @DecimalMin('1E-1000') BigDecimal tiny
    ${[Byte: 'boundedByte', Short: 'boundedShort', short: 'boundedPrimitiveShort', Integer: 'boundedInt', int: 'boundedPrimitiveInt', Long: 'boundedLong', long: 'boundedPrimitiveLong', BigInteger: 'boundedBigInteger'].collect { type, name -> "@DecimalMin(value='-0.1', inclusive=false) @DecimalMax('1.1') $type $name" }.join('\n    ')}
    @Min(2L) @Max(4L) @DecimalMin('1.1') @DecimalMax(value='3.1', inclusive=false) Integer units
    @DecimalMin(value='-0.1', inclusive=false) byte machine
    @Min(2L) @DecimalMin(value='1.0', inclusive=false) BigInteger tight
    @Min(2L) @DecimalMin(value='2.00', inclusive=false) BigInteger tie
    @DecimalMin('1') String text
    @DecimalMin('1') float floating
    @DecimalMin('1') double doubleValue
    @DecimalMin(value='1', groups=Later) BigDecimal grouped
    @DecimalMin(value='1', payload=Warning) BigDecimal payloadBound
    @DecimalMin.List([@DecimalMin('1'), @DecimalMin('2')]) BigDecimal repeated
    @DecimalPolicy BigDecimal composed
    DecimalChild child
    @Builder.Method void operation(@DecimalMin('1') BigDecimal value) { throw new AssertionError('operation executed') }
}
@DSL @GroupSequence([SequencedPrice]) class SequencedPrice extends Price {}
@DSL class DecimalChild { @DecimalMin('0') BigDecimal amount }
@DecimalMin('1') @Constraint(validatedBy=[]) @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD])
@interface DecimalPolicy {
    String message() default 'decimal'
    Class[] groups() default []
    Class[] payload() default []
}
@DSL class EqualExclusive { @DecimalMin(value='1.00', inclusive=false) @DecimalMax('1E+0') BigDecimal amount }
@DSL class BothExclusive { @DecimalMin(value='1.00', inclusive=false) @DecimalMax(value='1E+0', inclusive=false) BigDecimal amount }
@DSL class UpperExclusive { @DecimalMin('1.00') @DecimalMax(value='1E+0', inclusive=false) BigDecimal amount }
@DSL class ReversedDecimal { @DecimalMin('2') @DecimalMax('1') BigDecimal amount }
@DSL class NoInteger { @DecimalMin('0.1') @DecimalMax('0.9') BigInteger amount }
@DSL class ByteOverflow { @DecimalMin(value='127.0', inclusive=false) byte amount }
@DSL class MixedBounds { @Min(2L) @DecimalMax(value='2.00', inclusive=false) Integer amount }
@DSL class ExtremeBounds {
    @DecimalMin('1E+1000000') @DecimalMax('-1E+1000000') BigDecimal huge
    @DecimalMin('1E-2147483647') @DecimalMax('-1E-2147483647') BigDecimal tiny
    @DecimalMin('-1E+1000000') @DecimalMax('1E+1000000') BigDecimal ordinary
}
@DSL class MalformedDecimal { @DecimalMin('no-number') BigDecimal amount }
@DSL class Unrepresentable { @DecimalMin('1E+2147483648') BigDecimal amount }
""")
        }
        if (patterns) {
            new File(producer, 'build.gradle').append("\nsubprojects { dependencies { implementation files(${notation(System.getProperty('schemaValidationApi'))}) } }\n")
            patternSources()
            if (compositions) {
                compositionSources()
            }
        }
        if (projection) projectionSources()
        if (naming || patterns) {
            if (naming) { namingSources() }
            if (literals) { literalSources() }
            if (examples) { exampleSources() }
            new File(producer, 'build.gradle').append("\nproject(':schema') { dependencies { implementation project(':owned') } }\n")
            runner(producer, ':owned:jar').build()
            publish('owned', 'owned/build/libs/owned.jar', '')
        }
        publishApi('Inherited region documentation.')
        runner(producer, ':schema:jar').build()
        publish('schema', 'schema/build/libs/schema.jar', '<dependencies><dependency><groupId>fixture</groupId><artifactId>api</artifactId><version>1.0</version></dependency>' +
                (naming || patterns ? '<dependency><groupId>fixture</groupId><artifactId>owned</artifactId><version>1.0</version></dependency>' : '') + '</dependencies>')
        new File(producer, 'schema/src').deleteDir()
        new File(producer, 'api/src').deleteDir()
        if (naming || patterns) {
            new File(producer, 'api/build/classes').deleteDir()
            new File(producer, 'schema/build/classes').deleteDir()
            new File(producer, 'owned/src').deleteDir()
            new File(producer, 'owned/build/classes').deleteDir()
        }
        writeConsumer(generation, defaults, patterns)
    }

    private void writeConsumer(String generation, boolean defaults, boolean patterns) {
        String groovy = notation(System.getProperty("gdslGroovy$generation"))
        String runtime = classpath(System.getProperty('gdslRuntimeClasspath'))
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
${patterns ? """
tasks.register('patternAdmissionControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.PatternAdmissionControl'
}
tasks.register('patternReadControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.PatternReadControl'
}
tasks.register('patternInitializeControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.PatternInitializeControl'
}
""" : ''}
tasks.register('initializeControl', JavaExec) {
    classpath = klumSchema.jsonSchema.classpath
    mainClass = 'example.InitializationControl'
}
"""
    }

    private void exampleSources() {
        writeSource('api/src/main/groovy/example/ExampleBase.groovy', '''
package example
import com.blackbuild.klum.ast.*
class ExampleTexts {
    static { throw new AssertionError('binary constant initialization trap') }
    public static final String VALUE = 'binary constant'
}
@DSL abstract class ExampleBase {
    @Struct.Examples(['inherited']) String displayName
    @Builder.Method @Struct.Examples(['base']) void configureBase(String value) { displayName = value }
}
''')
        writeSource('owned/src/main/groovy/example/ExampleChild.groovy', '''
package example
import com.blackbuild.klum.ast.*
@DSL abstract class ExampleKeyBase { @Key @Struct.Examples(['catalog']) String key }
@DSL class ExampleChild extends ExampleKeyBase {
    @Struct.Name('child.name') @Struct.Examples(['child']) String childName
    ExampleChild nested
}
''')
        writeSource('schema/src/main/groovy/example/ExampleService.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.fasterxml.jackson.annotation.*
@DSL class ExampleOwnedBase extends ExampleBase {
    @Struct.Examples(['inherited database']) ExampleChild inheritedChild
}
@DSL class ExampleExcluded { @Struct.Examples(['SECRET_PAYLOAD']) String value }
@DSL class ExampleUnrelated { @Struct.Examples(['SECRET_PAYLOAD']) String value }
@DSL @Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) class ExampleService extends ExampleOwnedBase {
    static { new File('${trap.absolutePath}').text = 'initialized'; throw new AssertionError('Schema initialization control') }
    @Struct.Examples([ExampleTexts.VALUE]) String binaryConstant
    @Deprecated @Struct.Name('service.name') @JsonProperty('service.name')
    @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT)
    @Struct.Examples(['Catalog', 'catalog.internal', 'Catalog']) String serviceName
    @Struct.Examples(['null', '{not JSON', '01', '[unclosed']) String varied
    @Struct.Examples([${DOCUMENTARY_TEXT.inspect()}]) String escaped
    @Struct.Examples(['42', 'many replicas']) int replicas
    @Struct.Examples(['true', 'enabled']) boolean enabled
    @Struct.Examples(['blue, green']) List<String> tags
    @Struct.Examples(['team: ops']) Map<String, String> labels
    @Struct.Examples(['primary database']) ExampleChild primary
    ExampleChild sibling
    @Field(key=Field.FieldName) @Struct.Examples(['fixed database']) ExampleChild fixed
    @Struct.Examples(['worker databases']) Map<String, ExampleChild> children
    @Struct.Ignore ExampleExcluded excludedTarget
    @Struct.Ignore @Struct.Examples(['hidden']) String secret
    @JsonIgnore @Struct.Examples(['jackson hidden']) String hidden
    @Field(FieldType.LINK) @Struct.Examples(['{}']) ExampleChild link
    @Builder.Method @Struct.Name('endpoint') @Struct.Examples(['API endpoint settings'])
    void endpoint(Map<String, String> input) { throw new AssertionError('operation executed') }
    @Builder.Method @Struct.Examples(['override']) void configure(String input) { throw new AssertionError('operation executed') }
    @Builder.Method @Struct.Examples(['true']) void unsupported(Object input) { throw new AssertionError('unsupported executed') }
    @PostCreate void created() { throw new AssertionError('lifecycle executed') }
}
class ExampleControl {
    static void main(String[] args) { Class.forName('example.ExampleService', true, ExampleControl.classLoader) }
}
""")
    }


    private void literalSources() {
        writeSource('owned/src/main/groovy/example/LiteralDetails.groovy', '''
package example
import com.blackbuild.klum.ast.*
@Struct.Naming(Struct.Naming.Profile.IDENTITY) @DSL class LiteralDetails { @Struct.Name('child/Name') String nativeName }
@DSL class LiteralWorker {
    @Key @Struct.Name('Worker/ID') @Struct.Ignore String workerId
    @Struct.Name('Image/Name') String imageName
}
''')
        writeSource('api/src/main/groovy/example/LiteralBase.groovy', '''
package example
import com.blackbuild.klum.ast.*
import com.fasterxml.jackson.annotation.*
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.KlumModelException
@DSL class LiteralBase {
    @Key @Struct.Name('Service/ID') @JsonProperty('Service/ID') String serviceId
    @Struct.Name('URL/exact') @JsonProperty('URL/exact') String URLValue
    String urlValue
    @Struct.Name('displayName') String literalName
    String displayName
    @Struct.Ignore @JsonIgnore(false) String hiddenValue
    @Struct.Name('ignored-renamed') @JsonIgnore String alsoHidden
    @Builder.Method @JsonProperty('set/Endpoint') @Struct.Name('set/Endpoint')
    void configureEndpoint(String value) { urlValue = value }
    @Builder.Method @Struct.Ignore void hiddenOperation(String value) { throw new AssertionError('ignored operation') }
}
@DSL class LiteralKeyFallback { @Key @Struct.Name('Fallback/ID') String key }
class LiteralControl {
    static void main(String[] args) {
        if (args.length) {
            def type = Class.forName('example.' + args[0], false, LiteralControl.classLoader)
            try {
                KlumStructuredInput.readRoot(type, [:], 'rejected.yaml')
                throw new AssertionError('invalid contract accepted')
            } catch (KlumModelException expected) {
                assert expected.message.contains('collision') || expected.message.contains('literal metadata') || expected.message.contains('@Struct.Ignore')
                println 'Rejected literal contract before execution'
            }
            return
        }
        def type = Class.forName('example.LiteralService', false, LiteralControl.classLoader)
        def result = KlumStructuredInput.readRoot(type, ['Service/ID': 'Service/Blue', 'URL/exact': 'URL', 'url-value': 'native',
                displayName: 'literal', 'display-name': 'base', 'set/Endpoint': 'operated', details: ['child/Name': 'child'],
                workers: ['Worker/Blue': ['Image/Name': 'worker:1']], labels: ['Team/Blue': 'ops'], 'items/exact': ['one', 'two']], 'literal.yaml')
        assert result.serviceId == 'Service/Blue' && result.URLValue == 'URL' && result.urlValue == 'operated'
        assert result.literalName == 'literal' && result.displayName == 'base'
        assert result.details.nativeName == 'child'
        assert result.workers['Worker/Blue'].workerId == 'Worker/Blue' && result.workers['Worker/Blue'].imageName == 'worker:1'
        assert result.labels == ['Team/Blue': 'ops'] && result.items == ['one', 'two']
        assert result.hiddenValue == null && result.alsoHidden == null
        ['serviceId', 'service-id', 'URLValue', 'urlValue', 'hidden-value', 'ignored-renamed', 'hidden-operation'].each { name ->
            try {
                KlumStructuredInput.readRoot(type, 'external', [(name): 'forbidden'], 'alias.yaml')
                throw new AssertionError('forbidden alias accepted: ' + name)
            } catch (KlumModelException expected) { assert expected.message.contains(name) }
        }
        def fallback = KlumStructuredInput.readRoot(LiteralKeyFallback, ['Fallback/ID': 'fallback'], 'key.yaml')
        assert fallback.key == 'fallback'
        println 'Literal interpreter/schema agreement passed'
    }
}
''')
        writeSource('schema/src/main/groovy/example/LiteralService.groovy', """
package example
import com.blackbuild.klum.ast.*
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) @DSL class LiteralService extends LiteralBase {
    static { new File('${trap.absolutePath}').text = 'initialized' }
    LiteralDetails details
    Map<String, LiteralWorker> workers
    Map<String, String> labels
    @Struct.Name('items/exact') List<String> items
}
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) @DSL class LiteralCollision {
    static { new File('${trap.absolutePath}').text = 'initialized'; throw new AssertionError('collision executed') }
    @Struct.Name('same') String URLValue
    @Struct.Name('same') String urlValue
    @Struct.Ignore @Struct.Name('same') String hidden
}
@DSL class LiteralKeyCollision { @Key @Struct.Name('Key/exact') String key; @Struct.Name('Key/exact') String value }
@DSL class LiteralKeyOwner { Map<String, LiteralKeyCollision> services }
""")
    }

    private void namingSources() {
        writeSource('owned/src/main/groovy/example/NamingDetails.groovy', '''
package example
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.Struct.Naming.Profile
@DSL class NamingDetails { String nativeName }
@Struct.Naming(Profile.KEBAB_CASE) @DSL class NamingWorker { @Key String workerId; String imageName }
''')
        writeSource('api/src/main/groovy/example/NamingBase.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.Struct.Naming.Profile
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.fasterxml.jackson.annotation.*
@DSL class NamingBase {
    String displayName
    @JsonProperty('displayName') String literalName
    @JsonProperty('URL') String endpointUrl
    @JsonProperty('') String emptyRename
    @JsonIgnore(false) String neutralIgnore
    @JsonIgnore @JsonProperty('hidden-literal') String hiddenValue
    @Builder.Method @JsonProperty('literal-operation') void literalOperation(String value) { neutralIgnore = value }
    @Builder.Method @JsonIgnore void ignoredOperation(String value) { throw new AssertionError('ignored operation') }
}
@Struct.Naming(Profile.IDENTITY) @DSL class NamingIdentity { String nativeName; String URLValue }
@DSL class NamingKeyBase { @Key String serviceId }
@Struct.Naming(Profile.KEBAB_CASE) @DSL class NamingLiteralKey { @Key @JsonProperty('Service/ID') String serviceId }
class NamingControl {
    static void main(String[] args) {
        if (args.length) {
            def type = Class.forName('example.' + args[0], false, NamingControl.classLoader)
            try {
                KlumStructuredInput.readRoot(type, (args.length > 1 ? [:] : args[0] == 'NamingKeyCollision' ? ['url-value': 'ambiguous'] : ['service-details': [nativeName: 'test'], 'worker-instances': [instance: ['image-name': 'test']]]), 'rejected.yaml')
                throw new AssertionError('invalid contract accepted')
            } catch (KlumModelException expected) {
                assert expected.message.contains('input profiles') || expected.message.contains('collision') || expected.message.contains('naming metadata') || expected.message.contains('metadata protocol') || expected.message.contains('Missing constraint') || expected.message.contains('Unknown constraint projection policy') || expected.message.contains('Invalid decimal bound') || expected.message.contains('missing direct decimal facts') || expected.message.contains('construction key') || expected.message.contains('Malformed UTF-16')
                println 'Rejected naming contract before execution'
            }
            return
        }
        def type = Class.forName('example.NamingService', false, NamingControl.classLoader)
        def input = ['display-name': 'Base', displayName: 'Literal', 'service-name': 'Catalog', 'url-value': 'url',
                'xml2-parser': 'xml', 'version2-url': 'v2', already_snake: 'snake', 'Ångström-url': 'unicode',
                URL: 'exact', 'empty-rename': 'empty', 'neutral-ignore': 'false is neutral',
                'service-details': [nativeName: 'first'], 'other-details': [nativeName: 'second'],
                'worker-instances': ['Worker/Blue': ['image-name': 'worker:1']], labels: ['Team/Blue': 'ops']]
        def result = KlumStructuredInput.readRoot(type, input, 'naming.yaml')
        assert result.displayName == 'Base' && result.literalName == 'Literal'
        assert result.serviceName == 'Catalog' && result.URLValue == 'url'
        assert result.XML2Parser == 'xml' && result.version2URL == 'v2'
        assert result.already_snake == 'snake' && result.ÅngströmURL == 'unicode'
        assert result.endpointUrl == 'exact' && result.emptyRename == 'empty'
        assert result.neutralIgnore == 'false is neutral' && result.hiddenValue == null
        assert result.serviceDetails.nativeName == 'first' && result.otherDetails.nativeName == 'second'
        assert result.workerInstances['Worker/Blue'].workerId == 'Worker/Blue'
        assert result.workerInstances['Worker/Blue'].imageName == 'worker:1'
        assert result.labels == ['Team/Blue': 'ops']
        def operated = KlumStructuredInput.readRoot(type, ['configure-endpoint': 'operation', 'literal-operation': 'literal'], 'operation.yaml')
        assert operated.serviceName == 'operation' && operated.neutralIgnore == 'literal'
        try {
            KlumStructuredInput.readRoot(type, [serviceName: 'alias'], 'alias.yaml')
            throw new AssertionError('native alias accepted')
        } catch (KlumModelException expected) { assert expected.message.contains('serviceName') }
        def keyed = KlumStructuredInput.readRoot(Class.forName('example.NamingKey', false, NamingControl.classLoader), ['service-id': 'Instance/Blue', 'display-name': 'Blue'], 'key.yaml')
        assert keyed.serviceId == 'Instance/Blue' && keyed.displayName == 'Blue'
        def literalKey = KlumStructuredInput.readRoot(NamingLiteralKey, ['Service/ID': 'Instance/Red'], 'key.yaml')
        assert literalKey.serviceId == 'Instance/Red'
        def keyless = KlumStructuredInput.readRoot(Class.forName('example.NamingKeyCollisionOwner', false, NamingControl.classLoader), [services: [instance: ['url-value': 'property']]], 'keyless.yaml')
        assert keyless.services.instance.URLValue == 'instance' && keyless.services.instance.urlValue == 'property'
        println 'Naming interpreter/schema agreement passed'
    }
}
""")
        writeSource('schema/src/main/groovy/example/NamingService.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.Struct.Naming.Profile
@Struct.Naming(Profile.KEBAB_CASE) @DSL class NamingService extends NamingBase {
    static { new File('${trap.absolutePath}').text = 'executed' }
    String serviceName
    String URLValue
    String XML2Parser
    String version2URL
    String already_snake
    String ÅngströmURL
    NamingDetails serviceDetails
    NamingDetails otherDetails
    Map<String, NamingWorker> workerInstances
    Map<String, String> labels
    @Builder.Method void configureEndpoint(String value) { serviceName = value }
}
@Struct.Naming(Profile.KEBAB_CASE) @DSL class NamingKey extends NamingKeyBase { String displayName }
@Struct.Naming(Profile.KEBAB_CASE) @DSL class NamingKeyCollision { @Key String URLValue; String urlValue }
@DSL class NamingKeyCollisionOwner { Map<String, NamingKeyCollision> services }
@DSL class NamingGrandchild extends NamingService { String finalName }
@Struct.Naming(Profile.KEBAB_CASE) @DSL class NamingRepeated extends NamingService { String equalName }
@Struct.Naming(Profile.IDENTITY) @DSL class NamingConflict extends NamingService {
    static { new File('${trap.absolutePath}').text = 'executed'; throw new AssertionError('invalid profile initialized') }
}
@Struct.Naming(Profile.KEBAB_CASE) @DSL class NamingIdentityConflict extends NamingIdentity {
    static { new File('${trap.absolutePath}').text = 'executed'; throw new AssertionError('invalid profile initialized') }
}
@Struct.Naming(Profile.KEBAB_CASE) @DSL class NamingCollision {
    static { new File('${trap.absolutePath}').text = 'executed'; throw new AssertionError('collision initialized') }
    String URLValue
    String urlValue
}
""")
    }

    private void compositionFixture(String generation) {
        fixture(generation, false, false, false, false, false, false, false, false, true, true)
    }

    private void compositionSources() {
        writeSource('api/src/main/groovy/org/hibernate/validator/constraints/ConstraintComposition.groovy', '''
package org.hibernate.validator.constraints
import java.lang.annotation.*
enum CompositionType { AND, OR, ALL_FALSE }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
@interface ConstraintComposition { CompositionType value() }
''')
        writeSource('api/src/main/groovy/example/CompositionRules.groovy', '''
package example
import com.blackbuild.klum.ast.*
import jakarta.validation.*
import jakarta.validation.constraints.*
import java.lang.annotation.*
import org.hibernate.validator.constraints.*
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.KlumModelException

@Constraint(validatedBy=CompositionValidator) @Min(1L) @Max(100L)
@Struct.ConstraintDescription(CompositionTexts.DESCRIPTION)
@Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.ANNOTATION_TYPE])
@interface TrafficPercentage {
    String message() default 'traffic'
    Class[] groups() default []
    Class[] payload() default []
}
@Constraint(validatedBy=[]) @Min(10L)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface AtLeastTen { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @Min(10L) @Max(5L)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Contradiction { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @DecimalMin(value='1.25', inclusive=false) @DecimalMax('9.75')
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface PriceRange { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @Pattern(regexp='[A-Z]{2}') @Email
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Code { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @Email
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface EmailOnly { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @Pattern(regexp='a', flags=Pattern.Flag.CASE_INSENSITIVE)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface FlaggedCode { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @Pattern(regexp='a') @Pattern.List([@Pattern(regexp='b')])
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface RepeatedCode { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @Pattern(regexp='a+')
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Unbounded { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@ConstraintComposition(CompositionType.OR) @Constraint(validatedBy=[]) @Min(1L) @Max(10L)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Either { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@ConstraintComposition(CompositionType.ALL_FALSE) @Constraint(validatedBy=[]) @Min(1L) @Max(10L)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Neither { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@ConstraintComposition(CompositionType.AND) @Constraint(validatedBy=[]) @Min(1L) @Max(10L)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Both { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @Min(1L)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface OverrideRange {
    @OverridesAttribute(constraint=Min, name='value') long value() default 5L
    String message() default ''; Class[] groups() default []; Class[] payload() default []
}
@Constraint(validatedBy=[]) @TrafficPercentage
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Nested { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @Min.List([@Min(1L), @Min(2L)])
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Repeated { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Constraint(validatedBy=[]) @Size(min=1)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface SizeOnly { String message() default ''; Class[] groups() default []; Class[] payload() default [] }
@Struct.ConstraintDescription('Must be valid Groovy code.')
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface ScriptText {}
@PropagateAnnotation(Required) @Struct.ConstraintDescription('Must be present.')
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface PresentText {}
interface Later {}
interface Warning extends Payload {}
class CompositionTexts {
    static { throw new AssertionError('description constant initialized') }
    public static final String DESCRIPTION = 'Percentage of traffic sent to this service.'
}
class CompositionReadControl {
    static void main(String[] args) {
        Class type = Class.forName('example.CompositionService', false, CompositionReadControl.classLoader)
        try {
            KlumStructuredInput.readRoot(type, [:], 'composition.yaml')
            throw new AssertionError('corrupt composition metadata accepted')
        } catch (KlumModelException expected) {
            assert expected.message.contains('CompositionService')
            assert expected.message.contains('recompile and package')
            println 'Rejected composition metadata before execution'
        }
    }
}
class CompositionInitializationControl {
    static void main(String[] args) { Class.forName(args[0], true, CompositionInitializationControl.classLoader) }
}
class CompositionValidator implements ConstraintValidator<TrafficPercentage, Integer> {
    static { throw new AssertionError('validator initialized') }
    boolean isValid(Integer value, ConstraintValidatorContext context) { throw new AssertionError('validator executed') }
}
@DSL class CompositionBase {
    /** Inherited prose. */
    @Deprecated @Struct.Name('Inherited/Range') @TrafficPercentage Integer inheritedRange
}
''')
        writeSource('schema/src/main/groovy/example/CompositionService.groovy', """
package example
import com.blackbuild.klum.ast.*
import jakarta.validation.constraints.*
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) @DSL class CompositionService extends CompositionBase {
    static { new File('${trap.absolutePath}').text = 'initialized'; throw new AssertionError('composition initialized') }
    @TrafficPercentage Integer range
    @Min(5L) @TrafficPercentage @AtLeastTen Integer multiple
    @Contradiction Integer contradiction
    @PriceRange BigDecimal decimal
    @Code String code
    @Pattern(regexp='A[A-Z]') @Code String intersectingPatterns
    @Pattern(regexp='[A-Z]{2}') @EmailOnly String directWithEmail
    @FlaggedCode String flaggedCode
    @RepeatedCode String repeatedCode
    @TrafficPercentage @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.TEXT_ONLY) Integer textOnly
    /** Retained prose. */
    @Struct.Examples(['explicit field example']) @TrafficPercentage @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT) Integer omitted
    @ScriptText String descriptionOnly
    @PresentText String propagationOnly
    @Both Integer explicitAnd
    @Either Integer or
    @Neither Integer allFalse
    @OverrideRange Integer overridden
    @Nested Integer nested
    @TrafficPercentage(groups=Later) Integer grouped
    @TrafficPercentage(payload=Warning) Integer payload
    @Repeated Integer repeated
    @SizeOnly String sizeOnly
    @Pattern(regexp='[A-Z]{2}') @Either String independentPattern
    @Unbounded String unsupportedPattern
    @Code Integer wrongType
    @Code List<String> container
}
""")
    }

    private void removeCompositionTypes() {
        File jar = new File(repository, 'fixture/api/1.0/api-1.0.jar')
        File replacement = new File(directory, 'without-composition-types.jar')
        new JarFile(jar).withCloseable { input ->
            new JarOutputStream(new FileOutputStream(replacement)).withCloseable { output ->
                input.entries().each { entry ->
                    copyNonCompositionEntry(input, output, entry)
                }
            }
        }
        jar.bytes = replacement.bytes
    }

    private static void copyNonCompositionEntry(JarFile input, JarOutputStream output, JarEntry entry) {
        String simple = entry.name.tokenize('/').last().tokenize('$').first().replace('.class', '')
        boolean domain = ['TrafficPercentage', 'AtLeastTen', 'Contradiction', 'PriceRange', 'Code', 'Unbounded',
            'EmailOnly', 'FlaggedCode', 'RepeatedCode', 'Either', 'Neither', 'Both', 'OverrideRange', 'Nested', 'Repeated', 'SizeOnly', 'ScriptText',
            'PresentText', 'CompositionValidator', 'CompositionTexts', 'Later', 'Warning'].contains(simple)
        if (domain || entry.name.startsWith('org/hibernate/')) {
            return
        }
        output.putNextEntry(new JarEntry(entry.name))
        output.write(input.getInputStream(entry).withCloseable { it.bytes })
        output.closeEntry()
    }

    private void patternSources() {
        List<String> acceptedPatterns = ['[A-Z]{2}[0-9]{4}', '^[A-Z]{2}[0-9]{4}$', '[a-z][a-z0-9-]{0,62}',
                'a?', 'a{0}', '[0-9]{0,3}', '[A-Za-z0-9_.-]{1,8}', 'release-[0-9]{1,3}', '[a]{2}[a]{0,2}',
                'literal_1', '[0-9]{255}', 'a{255}b{255}c{255}d{255}', '[.]', '[-]']
        String corpusFields = acceptedPatterns.withIndex().collect { String pattern, int index ->
            "    @Pattern(regexp='${pattern}') String sample${index}"
        }.join('\n')
        writeSource('owned/src/main/groovy/example/PatternChild.groovy', '''
package example
import com.blackbuild.klum.ast.*
import jakarta.validation.constraints.Pattern
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) @DSL class PatternChild { @Pattern(regexp='[A-Z]{2}') String childCode }
''')
        writeSource('api/src/main/groovy/example/PatternBase.groovy', """
package example
import com.blackbuild.klum.ast.*
import jakarta.validation.constraints.Pattern
@DSL class PatternBase {
    @Struct.Name('Inherited/Code') @Pattern(regexp='[A-Z]{2}') String inheritedCode
}
class PatternConstants { public static final String TOKEN = '[a-z][a-z0-9-]{0,62}' }
""")
        writeSource('api/src/main/groovy/example/PatternReadControl.groovy', '''
package example
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.KlumModelException
class PatternReadControl {
    static void main(String[] args) {
        Class type = Class.forName('example.Release', false, PatternReadControl.classLoader)
        try {
            KlumStructuredInput.readRoot(type, [:], 'pattern.yaml')
            throw new AssertionError('corrupt Pattern metadata accepted')
        } catch (KlumModelException expected) {
            assert expected.message.contains('Pattern')
            assert expected.message.contains('recompile and package')
            println 'Rejected Pattern metadata before execution'
        }
    }
}
class PatternAdmissionControl {
    static void main(String[] args) {
        try {
            Class.forName('com.blackbuild.klum.ast.runtime.internal.BuiltinConstraintCatalog', false, PatternAdmissionControl.classLoader)
            throw new AssertionError('plugin catalog leaked into interpreter classpath')
        } catch (ClassNotFoundException expected) { /* Plugin-only catalog must be absent. */ }
        Class type = Class.forName('example.PatternInputOnly', false, PatternAdmissionControl.classLoader)
        assert KlumStructuredInput.readRoot(type, [code: 'outside pattern'], 'pattern.yaml').code == 'outside pattern'
        assert KlumStructuredInput.readRoot(type, [:], 'pattern.yaml').code == null
        try {
            KlumStructuredInput.readRoot(type, [code: null], 'pattern.yaml')
            throw new AssertionError('null accepted')
        } catch (KlumModelException expected) { assert expected.message.contains('null') }
        println 'Pattern remains independent of interpreter admission'
    }
}
class PatternInitializeControl {
    static void main(String[] args) { Class.forName('example.Release', true, PatternInitializeControl.classLoader) }
}
''')
        writeSource('schema/src/main/groovy/example/Release.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.Struct.ConstraintProjection.Policy
import jakarta.validation.constraints.*
import jakarta.validation.groups.Default
import jakarta.validation.GroupSequence
import jakarta.validation.Payload
import groovy.transform.AnnotationCollector
import jakarta.validation.Constraint
import java.lang.annotation.*
import static example.PatternConstants.TOKEN
@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) @DSL class Release extends PatternBase {
    static { new File('${trap.absolutePath}').text = 'initialized'; throw new AssertionError('Pattern Schema initialized') }
    $corpusFields
    /** Release slug. */
    @Struct.Examples(['release-1', 'NOT VALID']) @Pattern(regexp=TOKEN) String slug
    @Pattern(regexp=TOKEN, flags=Pattern.Flag.COMMENTS) String constantFallback
    @Pattern(regexp='a', flags=Pattern.Flag.UNIX_LINES) String unix
    @Pattern(regexp='a', flags=Pattern.Flag.MULTILINE) String multiline
    @Pattern(regexp='a', flags=Pattern.Flag.DOTALL) String dotall
    @Pattern(regexp='a', flags=Pattern.Flag.UNICODE_CASE) String unicodeCase
    @Pattern(regexp='a', flags=Pattern.Flag.CANON_EQ) String canonical
    @Pattern(regexp='a', payload=Warning) String payload
    @ComposedPattern String composed
    @AliasedPattern String alias
    @Pattern(regexp='a\\n') String controls
    @Pattern(regexp='^a{0,2}\$', flags=[]) String optionalToken
    @Pattern(regexp='a', flags=Pattern.Flag.CASE_INSENSITIVE) String flagged
    @Pattern(regexp='a', groups=Default) String grouped
    @Pattern(regexp='a+') String unbounded
    @Pattern(regexp='[') String malformed
    @Pattern.List([@Pattern(regexp='a'), @Pattern(regexp='b')]) String repeated
    @Pattern.List([@Pattern(regexp='a')]) String singletonList
    @Pattern(regexp='a') @Pattern.List([@Pattern(regexp='b')]) String mixedList
    @Struct.Examples(['outside']) @Pattern(regexp='a') @Struct.ConstraintProjection(Policy.TEXT_ONLY) String textOnly
    /** Retained prose. */
    @Struct.Examples(['outside']) @Deprecated @Pattern(regexp='a') @Struct.ConstraintProjection(Policy.OMIT) String omitted
    @Struct.Ignore @Pattern(regexp='a') String excluded
    @Pattern(regexp='a') Integer wrongType
    @Pattern(regexp='a') List<String> container
    @Struct.ConstraintProjection(Policy.OMIT) PatternChild first
    PatternChild second
    @Builder.Method void configure(String value) { throw new AssertionError('operation executed') }
}
@DSL class PatternInputOnly { @Pattern(regexp='a') String code }
@DSL class PatternKey { @Key @Pattern(regexp='[A-Z]{2}') String name }
@Pattern(regexp='a') @AnnotationCollector @interface AliasedPattern {}
interface Warning extends Payload {}
@Constraint(validatedBy=[]) @Pattern(regexp='a')
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface ComposedPattern {
    String message() default ''
    Class[] groups() default []
    Class[] payload() default []
}
@GroupSequence([PatternGrouped]) @DSL class PatternGrouped { @Pattern(regexp='a') String value }
""")
    }

    private void projectionSources() {
        File base = new File(producer, 'api/src/main/groovy/example/CapacityBase.groovy')
        base.text = base.text.replace('import com.blackbuild.klum.ast.DSL', 'import com.blackbuild.klum.ast.*')
                .replace("@JsonProperty('inherited-percent')", "@Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.TEXT_ONLY) @JsonProperty('inherited-percent')")
        writeSource('api/src/main/groovy/example/ProjectionChild.groovy', '''
package example
import com.blackbuild.klum.ast.*
import jakarta.validation.constraints.*
@DSL class ProjectionChild {
    /** Amount. */
    @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT) @DecimalMin('0.1') BigDecimal amount
    @Min(2L) Integer quantity
}
''')
        writeSource('schema/src/main/groovy/example/Projection.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.Struct.ConstraintProjection.Policy
import com.fasterxml.jackson.annotation.*
import jakarta.validation.constraints.*
import java.lang.annotation.*
@DSL class Projection extends CapacityBase {
    static { new File('${trap.absolutePath}').text = 'initialized'; throw new AssertionError('Schema initialization control') }
    /** Projected replicas. */
    @Struct.ConstraintProjection @Min(1L) Integer projected
    /** Projected replicas. */
    @Min(1L) Integer implicit
    /** Desired replica count. */
    @Deprecated @JsonProperty('replica-count') @Min(1L)
    @Struct.ConstraintProjection(Policy.TEXT_ONLY) Integer replicas
    /** Private capacity rule. */
    @Deprecated @Min(1L) @Struct.ConstraintProjection(Policy.OMIT) Integer omitted
    @DecimalMin(value='0.10', inclusive=false) @Struct.ConstraintProjection(Policy.TEXT_ONLY) BigDecimal decimalText
    /** Required label. */
    @Needed @Struct.ConstraintProjection(Policy.TEXT_ONLY) String propagatedText
    /** Required label. */
    @Needed @Struct.ConstraintProjection(Policy.OMIT) String propagatedOmit
    /** Required label. */
    @NotNull @Struct.ConstraintProjection(Policy.TEXT_ONLY) String directText
    /** Required label. */
    @NotNull @Struct.ConstraintProjection(Policy.OMIT) String directOmit
    @JsonIgnore @Struct.ConstraintProjection(Policy.OMIT) @Min(1L) Integer hidden
    @Size(min=1) @Struct.ConstraintProjection(Policy.OMIT) ProjectionChild child
    @JsonProperty('collision') @Struct.ConstraintProjection(Policy.OMIT) String first
    @JsonProperty('collision') String second
}
@PropagateAnnotation(NotNull)
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Needed {}
""")
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

    private void rewriteDeprecationFacts(String artifact, boolean removeFacts) {
        File jar = new File(repository, "fixture/$artifact/1.0/$artifact-1.0.jar")
        File replacement = new File(directory, 'deprecation.jar')
        new JarFile(jar).withCloseable { input ->
            new JarOutputStream(new FileOutputStream(replacement)).withCloseable { output ->
                input.entries().each { entry ->
                    byte[] bytes = input.getInputStream(entry).withCloseable { it.bytes }
                    if (entry.name.endsWith('_DSL$Builder.class')) {
                        ClassWriter writer = new ClassWriter(0)
                        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                deprecationMutation(super.visitAnnotation(descriptor, visible), removeFacts)
                            }
                            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                        // Erase advisory deprecation only; operations also carry required literal facts.
                                        deprecationMutation(super.visitAnnotation(annotation, visible), removeFacts)
                                    }
                                    @Override void visitEnd() {
                                        // Synthetic compatibility alias on a selected overload, not source deprecation.
                                        if (name == 'child') super.visitAnnotation('Ljava/lang/Deprecated;', true).visitEnd()
                                        super.visitEnd()
                                    }
                                }
                            }
                        }, 0)
                        bytes = writer.toByteArray()
                    }
                    output.putNextEntry(new JarEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        jar.bytes = replacement.bytes
    }

    private static AnnotationVisitor deprecationMutation(AnnotationVisitor delegate, boolean removeFacts) {
        new AnnotationVisitor(Opcodes.ASM9, delegate) {
            @Override void visit(String name, Object value) {
                if (!removeFacts || name != 'sourceDeprecated') {
                    super.visit(name, value)
                }
            }
            @Override AnnotationVisitor visitAnnotation(String name, String descriptor) {
                deprecationMutation(super.visitAnnotation(name, descriptor), removeFacts)
            }
        }
    }

    // Corrupt only public facts, retaining the Model's real Bean annotations as a no-fallback control.
    private void stripConstraintFacts(String target, String kind, String selectedField = null) {
        String artifact = target == 'PatternChild' ? 'owned' : target in ['CapacityBase', 'DecimalBase', 'ProjectionChild', 'PatternBase'] ? 'api' : 'schema'
        File jar = new File(repository, "fixture/$artifact/1.0/$artifact-1.0.jar")
        File replacement = new File(directory, 'stripped.jar')
        new JarFile(jar).withCloseable { input ->
            new JarOutputStream(new FileOutputStream(replacement)).withCloseable { output ->
                input.entries().each { entry ->
                    byte[] bytes = input.getInputStream(entry).withCloseable { it.bytes }
                    if (entry.name == 'example/' + target + '_DSL$Builder.class') {
                        ClassReader reader = new ClassReader(bytes)
                        ClassWriter writer = new ClassWriter(0)
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
                                if (selectedField != null && name != 'get' + selectedField.capitalize()) { return delegate }
                                if (kind == 'group-value' && name != 'getGrouped') {
                                    return delegate
                                }
                                new MethodVisitor(Opcodes.ASM9, delegate) {
                                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                        AnnotationVisitor visitor = super.visitAnnotation(annotation, visible)
                                        if (!annotation.endsWith('GeneratedInput$Field;')) return visitor
                                        new AnnotationVisitor(Opcodes.ASM9, visitor) {
                                            @Override void visit(String member, Object value) {
                                                if (kind == 'count' && member == 'constraintCount') {
                                                    return
                                                }
                                                super.visit(member, value)
                                            }
                                            @Override void visitEnum(String member, String enumDescriptor, String value) {
                                                if (member == 'constraintProjection' && kind == 'policy') return
                                                super.visitEnum(member, enumDescriptor, member == 'constraintProjection' && kind == 'unknown-policy' ? 'FUTURE' : value)
                                            }
                                            @Override AnnotationVisitor visitArray(String member) {
                                                if (kind == 'field' && member == 'constraints') return null
                                                AnnotationVisitor array = super.visitArray(member)
                                                if (member != 'constraints') return array
                                                if (kind in ['primitive', 'enum', 'array']) {
                                                    if (kind == 'primitive') array.visit(null, 17)
                                                    if (kind == 'enum') array.visitEnum(null, 'Lexample/Mode;', 'SAFE')
                                                    if (kind == 'array') array.visitArray(null).visitEnd()
                                                    array.visitEnd()
                                                    return null
                                                }
                                                new AnnotationVisitor(Opcodes.ASM9, array) {
                                                    @Override void visitEnd() {
                                                        if (kind == 'duplicate-pattern') {
                                                            AnnotationVisitor duplicate = array.visitAnnotation(null, 'Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$Constraint;')
                                                            duplicate.visit('annotation', 'jakarta.validation.constraints.Pattern')
                                                            duplicate.visit('declaration', '@jakarta.validation.constraints.Pattern(regexp="a")')
                                                            duplicate.visit('origin', 'DIRECT')
                                                            duplicate.visit('owner', '')
                                                            duplicate.visit('description', '')
                                                            duplicate.visit('plain', true)
                                                            duplicate.visit('regexp', 'a')
                                                            duplicate.visit('patternFlagsEmpty', true)
                                                            duplicate.visitEnd()
                                                        }
                                                        super.visitEnd()
                                                    }
                                                    @Override AnnotationVisitor visitAnnotation(String elementName, String elementDescriptor) {
                                                        if (kind == 'stripped-rules') {
                                                            return null
                                                        }
                                                        AnnotationVisitor rule = super.visitAnnotation(elementName, elementDescriptor)
                                                        new AnnotationVisitor(Opcodes.ASM9, rule) {
                                                            @Override void visit(String attribute, Object value) {
                                                                if (kind in ['origin', 'owner', 'description'] && attribute == kind) {
                                                                    return
                                                                }
                                                                if (kind == 'bad-origin' && attribute == 'origin') { super.visit(attribute, 'FUTURE'); return }
                                                                if (kind == 'bad-owner' && attribute == 'owner' && value != '') { super.visit(attribute, '@missing.Rule()'); return }
                                                                if (kind == 'bad-description' && attribute == 'description') { super.visit(attribute, 17); return }
                                                                if (kind == 'duplicate-regexp' && attribute == 'regexp') { super.visit(attribute, value) }
                                                                if (kind == 'unknown-pattern' && attribute == 'regexp') { super.visit('futurePatternFact', value) }
                                                                if (kind in ['regexp', 'patternFlagsEmpty'] && attribute == kind) { return }
                                                                if (kind == 'bad-regexp' && attribute == 'regexp') { super.visit(attribute, 17); return }
                                                                if (kind == 'bad-flags' && attribute == 'patternFlagsEmpty') { super.visit(attribute, 'true'); return }
                                                                if (kind == 'bad-decimal' && attribute == 'decimalValue') {
                                                                    super.visit(attribute, 'malformed')
                                                                    return
                                                                }
                                                                if (kind in ['decimalValue', 'inclusive'] && attribute == kind) return
                                                                if (kind in ['plain', 'value'] && attribute == 'plain' || kind in ['value', 'group-value'] && attribute == 'value') return
                                                                super.visit(attribute, value)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }, 0)
                        bytes = writer.toByteArray()
                    }
                    output.putNextEntry(new JarEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        jar.bytes = replacement.bytes
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
        if (project == producer) {
            producerRunnerCalls++
        }
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
