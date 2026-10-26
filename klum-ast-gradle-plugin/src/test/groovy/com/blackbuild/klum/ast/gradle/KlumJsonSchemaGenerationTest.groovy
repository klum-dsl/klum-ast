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
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
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
        'Projection'      | 'projection-version' | 'projection metadata version 0'
        'CapacityBase'    | 'projection-version' | 'projection metadata version 0'
        'ProjectionChild' | 'projection-version' | 'projection metadata version 0'
        'Projection'      | 'future-projection'  | 'projection metadata version 2'
        'Projection'      | 'policy'             | 'missing constraint projection policy'
        'ProjectionChild' | 'policy'             | 'missing constraint projection policy'
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
    def 'missing source deprecation facts preserve legacy structural generation with Groovy #generation'() {
        given:
        fixture(generation, false, true, true)
        rewriteDeprecationFacts('schema', true)
        rewriteDeprecationFacts('api', true)
        new File(consumer, 'build.gradle').append("\nklumSchema.jsonSchema.rootTypes.set(['example.P2', 'example.DeprecatedFallbackKey'])\n")

        when:
        def result = run('generateKlumJsonSchema')
        def schema = document('example.P2')
        def properties = schema.get('$defs').get('example.P2_DSL$Builder').properties

        then:
        result.task(':generateKlumJsonSchema').outcome == TaskOutcome.SUCCESS
        properties.keySet().containsAll(['oldEndpoint', 'endpoint', 'old-inherited', 'old-inherited-operation', 'old-child', 'old-operation', 'directOperation'])
        !output('example.P2').text.contains('"deprecated"')
        !output('example.DeprecatedFallbackKey').text.contains('"deprecated"')
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
        ['text', 'floating', 'doubleValue', 'grouped', 'payloadBound', 'repeated', 'composed'].every {
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
        'Price'        | 'decimal-version'       | 'decimal metadata version 0'
        'DecimalBase'  | 'decimal-version'       | 'decimal metadata version 0'
        'DecimalChild' | 'decimal-version'       | 'decimal metadata version 0'
        'Price'        | 'future-decimal'        | 'decimal metadata version 2'
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
        failed.output.contains(target == 'CapacityBase' ? 'example.CapacityBase#inherited' : 'example.' + target + '#percentage')
        failed.output.contains(target == 'CapacityBase' ? "external input 'inherited-percent'" : "external input 'percentage'")
        failed.output.contains('recompile and package')
        failed.output.contains(kind == 'field' ? 'missing constraint facts' : 'constraint metadata version')
        output('example.Capacity').bytes == previous
        manifest.text == previousManifest
        !trap.exists()

        where:
        target          | kind
        'Capacity'      | 'version'
        'CapacityBase'  | 'version'
        'CapacityChild' | 'version'
        'Capacity'      | 'future-version'
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

    @Issue(['954', '959'])
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

    @Issue(['529', '959'])
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

    private void fixture(String generation, boolean defaults = false, boolean constraints = false, boolean p2 = false, boolean decimals = false, boolean projection = false) {
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
import com.blackbuild.klum.ast.ConstraintProjection
import java.util.function.Consumer
class Legacy {}
class Legacy_DSL {
    @GeneratedInput.Schema(key = 'name') interface Builder<SELF extends Legacy> {}
    interface Factory { Legacy One(String key) }
}
class LegacyHolder {}
class LegacyHolder_DSL {
    @GeneratedInput.Schema(constraintVersion=1, decimalVersion=1, projectionVersion=1, groupRedefined=false) interface Builder<SELF extends LegacyHolder> {
        @GeneratedInput.Field(value='child', constraints=[], constraintProjection=ConstraintProjection.Policy.PROJECT) Legacy_DSL.Builder getChild()
        @GeneratedInput.Selection(field = 'child', role = 'OWNED_OBJECT') void child(Consumer input)
    }
    interface Factory { LegacyHolder One() }
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
        if (projection) projectionSources()
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

    private void projectionSources() {
        File base = new File(producer, 'api/src/main/groovy/example/CapacityBase.groovy')
        base.text = base.text.replace('import com.blackbuild.klum.ast.DSL', 'import com.blackbuild.klum.ast.*')
                .replace("@JsonProperty('inherited-percent')", "@ConstraintProjection(ConstraintProjection.Policy.TEXT_ONLY) @JsonProperty('inherited-percent')")
        writeSource('api/src/main/groovy/example/ProjectionChild.groovy', '''
package example
import com.blackbuild.klum.ast.*
import jakarta.validation.constraints.*
@DSL class ProjectionChild {
    /** Amount. */
    @ConstraintProjection(ConstraintProjection.Policy.OMIT) @DecimalMin('0.1') BigDecimal amount
    @Min(2L) Integer quantity
}
''')
        writeSource('schema/src/main/groovy/example/Projection.groovy', """
package example
import com.blackbuild.klum.ast.*
import com.blackbuild.klum.ast.ConstraintProjection.Policy
import com.fasterxml.jackson.annotation.*
import jakarta.validation.constraints.*
import java.lang.annotation.*
@DSL class Projection extends CapacityBase {
    static { new File('${trap.absolutePath}').text = 'initialized'; throw new AssertionError('Schema initialization control') }
    /** Projected replicas. */
    @ConstraintProjection @Min(1L) Integer projected
    /** Projected replicas. */
    @Min(1L) Integer implicit
    /** Desired replica count. */
    @Deprecated @JsonProperty('replica-count') @Min(1L)
    @ConstraintProjection(Policy.TEXT_ONLY) Integer replicas
    /** Private capacity rule. */
    @Deprecated @Min(1L) @ConstraintProjection(Policy.OMIT) Integer omitted
    @DecimalMin(value='0.10', inclusive=false) @ConstraintProjection(Policy.TEXT_ONLY) BigDecimal decimalText
    /** Required label. */
    @Needed @ConstraintProjection(Policy.TEXT_ONLY) String propagatedText
    /** Required label. */
    @Needed @ConstraintProjection(Policy.OMIT) String propagatedOmit
    /** Required label. */
    @NotNull @ConstraintProjection(Policy.TEXT_ONLY) String directText
    /** Required label. */
    @NotNull @ConstraintProjection(Policy.OMIT) String directOmit
    @JsonIgnore @ConstraintProjection(Policy.OMIT) @Min(1L) Integer hidden
    @Size(min=1) @ConstraintProjection(Policy.OMIT) ProjectionChild child
    @JsonProperty('collision') @ConstraintProjection(Policy.OMIT) String first
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

    private void rewriteDeprecationFacts(String artifact, boolean legacy) {
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
                                optionalDeprecation(super.visitAnnotation(descriptor, visible), legacy)
                            }
                            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                        if (legacy && annotation.endsWith('GeneratedInput$Operation;')) return null
                                        optionalDeprecation(super.visitAnnotation(annotation, visible), legacy)
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

    private static AnnotationVisitor optionalDeprecation(AnnotationVisitor delegate, boolean legacy) {
        new AnnotationVisitor(Opcodes.ASM9, delegate) {
            @Override void visit(String name, Object value) {
                if (!legacy || name != 'sourceDeprecated') super.visit(name, value)
            }
            @Override AnnotationVisitor visitAnnotation(String name, String descriptor) {
                optionalDeprecation(super.visitAnnotation(name, descriptor), legacy)
            }
        }
    }

    // Corrupt only public facts, retaining the Model's real Bean annotations as a no-fallback control.
    private void stripConstraintFacts(String target, String kind) {
        String artifact = target in ['CapacityBase', 'DecimalBase', 'ProjectionChild'] ? 'api' : 'schema'
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
                            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                AnnotationVisitor delegate = super.visitAnnotation(descriptor, visible)
                                if (!descriptor.endsWith('GeneratedInput$Schema;')) return delegate
                                new AnnotationVisitor(Opcodes.ASM9, delegate) {
                                    @Override void visit(String name, Object value) {
                                        if (name == 'projectionVersion' && kind in ['projection-version', 'future-projection']) {
                                            if (kind == 'future-projection') super.visit(name, 2)
                                        } else if (name == 'decimalVersion' && kind in ['decimal-version', 'future-decimal']) {
                                            if (kind == 'future-decimal') {
                                                super.visit(name, 2)
                                            }
                                        } else if (name != 'constraintVersion' || !(kind in ['version', 'future-version'])) {
                                            super.visit(name, value)
                                        } else if (kind == 'future-version') {
                                            super.visit(name, 2)
                                        }
                                    }
                                }
                            }
                            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
                                if (kind in ['version', 'future-version'] || kind == 'group-value' && name != 'getGrouped') return delegate
                                new MethodVisitor(Opcodes.ASM9, delegate) {
                                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                        AnnotationVisitor visitor = super.visitAnnotation(annotation, visible)
                                        if (!annotation.endsWith('GeneratedInput$Field;')) return visitor
                                        new AnnotationVisitor(Opcodes.ASM9, visitor) {
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
                                                    @Override AnnotationVisitor visitAnnotation(String elementName, String elementDescriptor) {
                                                        AnnotationVisitor rule = super.visitAnnotation(elementName, elementDescriptor)
                                                        new AnnotationVisitor(Opcodes.ASM9, rule) {
                                                            @Override void visit(String attribute, Object value) {
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
