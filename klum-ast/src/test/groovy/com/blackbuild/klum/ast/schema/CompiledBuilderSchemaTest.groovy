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
package com.blackbuild.klum.ast.schema

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.compiler.internal.ast.StructuredInputContractFixtures
import com.blackbuild.klum.ast.runtime.KlumModelException
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.FieldVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchemaReplacementProbe
import spock.lang.Issue

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** Strict fact comparison, including expected gaps; passing this probe does not mean WIRE replacement parity. */
@Issue('529')
class CompiledBuilderSchemaTest extends AbstractDSLSpec {
    private static final String TRAP = "static { System.setProperty('wire.schema.executed', 'initialized'); throw new AssertionError('Schema executed') }"
    private static final List<String> NAMING = ['''
        package com.fasterxml.jackson.annotation
        import java.lang.annotation.*
        @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
        @interface JsonProperty { String value() default '' }
    ''', '''
        package com.fasterxml.jackson.annotation
        import java.lang.annotation.*
        @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
        @interface JsonIgnore { boolean value() default true }
    ''']

    def 'every admitted WIRE datum is either equal or an exact reported enrichment gap'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING)
        def binaries = isolated([jar])
        def oracle = carrier(jar, 'admission.Parent')

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'admission.Parent_DSL$Builder', 'admission.Parent_DSL$Factory')
        def comparison = compare(schema, oracle)
        File report = new File("build/reports/wire-schema-parity-${GroovySystem.version}.tsv")
        report.parentFile.mkdirs()
        report.text = renderMismatches(comparison) + '\n'
        def fields = oracle.entries.findAll { it.role != 'OPERATION' }*.source
        def operations = oracle.entries.findAll { it.role == 'OPERATION' }*.source
        def expected = [:]
        fields.each { source ->
            ['admission', 'externalName', 'nameOrigin', 'role', 'source', 'populationSelection',
             'mapKeyType', 'excludedChildKeyMember', 'fixedChildKey'].each { expected[source + ':' + it] = 'UNAVAILABLE' }
        }
        operations.each { expected[it + ':admission'] = 'ELIGIBLE' }
        oracle.rejections.each { expected[it.source + ':rejection'] = 'UNAVAILABLE' }

        then:
        comparison.mismatches.collectEntries { [(it.path): it.actual] } == expected
        comparison.mismatches.every { it.expected != it.actual }
        comparison.mismatches.size() == 77
        renderMismatches(comparison) == getClass().getResource('/wire-schema-probe/admission-gaps.tsv').text.trim()
        comparison.equalities.containsAll(['model', 'builder'])
        comparison.transportNormalizations.any { it.wire.contains('[Ljava.lang.Class;') && it.contract.contains('java.lang.Class[]') }
        comparison.equalities.count { it.endsWith(':payload') } == oracle.entries.size()
        comparison.equalities.count { it.contains(':descriptor:') } >= oracle.entries.size()
        schema.methods.find { it.name == 'inheritedOperation' }.owner == 'admission.Base_DSL$Builder'
        schema.methods.find { it.name == 'region' && it.parameters == ['admission.Region'] }.enumNames == ['EU', 'US']
        schema.factories.any { it.name == 'One' && it.returns == 'admission.Parent' }
        schema.methods.findAll { it.name == 'copyFrom' }.every { it.eligibility == 'UNKNOWN' }
        System.getProperty('wire.schema.executed') == null
        !binaries.resolved.any { it.endsWith('$WirePlan') || it == 'admission.Parent$Builder' || it == 'admission.Parent$Factory' }

        when: 'a consumer requires complete parity'
        requireParity(comparison)

        then:
        def incomplete = thrown(AssertionError)
        incomplete.message.contains('admission.Base#region:externalName')
        incomplete.message.contains('UNAVAILABLE')
        incomplete.message.contains('renamed')

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'binary replacement facade loads with every carrier removed and sources deleted across API and Schema artifacts'() {
        given:
        String source = StructuredInputContractFixtures.ADMISSION
        int split = source.indexOf('            @DSL class Child')
        String imports = source.substring(0, source.indexOf('            enum Region'))
        def api = compileJar([trapped(source.substring(0, split))] + NAMING)
        def schemaJar = compileJar([trapped(imports + source.substring(split))], [api])
        def apiWithoutCarrier = withoutCarriers(api)
        def schemaWithoutCarrier = withoutCarriers(schemaJar)
        def binaries = isolated([apiWithoutCarrier, schemaWithoutCarrier])

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'admission.Parent_DSL$Builder', 'admission.Parent_DSL$Factory')
        def local = carrier(schemaJar, 'admission.Parent')
        def parent = carrier(api, 'admission.Base')
        def comparison = compare(schema, [model: local.model, builder: local.builder,
                entries: (parent.entries + local.entries).unique { it.source }, rejections: parent.rejections + local.rejections])
        replacementParity(binaries, [model: local.model, builder: local.builder,
                entries: (parent.entries + local.entries).unique { it.source }.sort { it.externalName }, rejections: parent.rejections + local.rejections])

        then:
        schema.parents.contains('admission.Base_DSL$Builder')
        comparison.equalities.contains('admission.Base#inheritedOperation(java.lang.String):void:payload')
        comparison.mismatches.any { it.path == 'admission.Base#region:externalName' && it.expected == 'renamed' }
        System.getProperty('wire.schema.executed') == null
        binaries.resolved.contains('admission.Parent')
        !binaries.resolved.any { it.endsWith('$WirePlan') }

        when: 'the non-initialization control demonstrates the trap is live'
        Class.forName('admission.Parent', true, binaries)

        then:
        thrown(AssertionError)
        System.getProperty('wire.schema.executed') == 'initialized'

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'unsupported and excluded WIRE records remain visible rather than disappearing from the comparison'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.REJECTIONS)] + NAMING)
        def binaries = isolated([jar])
        def oracle = carrier(jar, 'rejection.Parent')

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'rejection.Parent_DSL$Builder', 'rejection.Parent_DSL$Factory')
        def comparison = compare(schema, oracle)

        then:
        oracle.entries.empty
        oracle.rejections.size() == 11
        comparison.mismatches*.path as Set == oracle.rejections.findAll { !it.source.contains('(') || it.reason.startsWith('external-name collision') }
                .collect { it.source + ':rejection' } as Set
        schema.methods.find { it.name == 'reset' }.reason == '@Builder.Method must declare exactly one payload'
        schema.methods.find { it.name == 'replace' }.eligibility == 'EXCLUDED'
        schema.methods.find { it.name == 'raw' }.reason == '@Builder.Method payload is outside the admitted grammar'
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'population override exposes matching types without pretending to know the selected field method'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.POPULATION)])
        def binaries = isolated([jar])
        def oracle = carrier(jar, 'shadowedpopulation.Configuration')

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'shadowedpopulation.Configuration_DSL$Builder', 'shadowedpopulation.Configuration_DSL$Factory')
        def comparison = compare(schema, oracle)

        then:
        oracle.entries.size() == 1
        oracle.entries.first().operation == 'addValue'
        schema.methods.find { it.name == 'addValue' }.eligibility == 'UNKNOWN'
        schema.methods.find { it.name == 'values' && it.parameters == ['java.lang.Iterable<java.lang.String>'] }.eligibility == 'EXCLUDED'
        comparison.equalities.contains('shadowedpopulation.Configuration#values:payload')
        comparison.mismatches.find { it.path.endsWith(':populationSelection') }.expected == 'addValue'
        comparison.equalities.contains('shadowedpopulation.Configuration#values(java.lang.Iterable):void:rejection')
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'comparison fails loudly for an unlisted mismatch or carrier signature drift'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING)
        def binaries = isolated([jar])
        def schema = CompiledBuilderSchema.read(binaries, 'admission.Parent_DSL$Builder', 'admission.Parent_DSL$Factory')
        def oracle = carrier(jar, 'admission.Parent')
        oracle.entries.find { it.role == 'OPERATION' }.descriptors = ['invented(java.lang.String):void']

        when:
        compare(schema, oracle)

        then:
        def failure = thrown(AssertionError)
        failure.message.contains('invented(java.lang.String):void')

        cleanup:
        binaries?.close()
    }

    def 'exact public annotation bytes preserve docs and naming without initializing unrelated annotation enums or defaults'() {
        given:
        def jar = compileJar(['''
            package safeannotations
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*
            import com.fasterxml.jackson.annotation.JsonProperty
            import com.fasterxml.jackson.annotation.JsonIgnore
            enum Policy { SAFE; static { System.setProperty('wire.schema.executed', 'enum'); throw new AssertionError('Enum executed') } }
            @Retention(RetentionPolicy.RUNTIME) @interface Rule { Policy value() }
            @DSL class Configuration {
                static { System.setProperty('wire.schema.executed', 'model'); throw new AssertionError('Model executed') }
                @Default(code={ throw new AssertionError('default executed') }) String computed
                String initialized = { throw new AssertionError('initializer executed') }()
                /** Exact operation documentation. */
                @Builder.Method @Rule(Policy.SAFE) @JsonProperty('configure-input')
                void configure(Map<String, List<String>> input) { throw new AssertionError('operation executed') }
                @Builder.Method @JsonIgnore void hidden(String input) {}
                @Builder.Query String query() { 'result' }
            }
        '''] + NAMING)
        def binaries = isolated([jar])

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'safeannotations.Configuration_DSL$Builder', 'safeannotations.Configuration_DSL$Factory')
        replacementParity(binaries, carrier(jar, 'safeannotations.Configuration'))
        def configure = schema.methods.find { it.name == 'configure' }

        then:
        configure.documentation.contains('Exact operation documentation.')
        configure.externalName == 'configure-input'
        configure.nameOrigin == 'JSON_PROPERTY'
        configure.payload == 'MAP:java.util.Map:java.lang.String[LIST:java.util.List[SCALAR:java.lang.String]]'
        configure.parameters == ['java.util.Map<java.lang.String, java.util.List<java.lang.String>>']
        schema.methods.find { it.name == 'hidden' }.eligibility == 'EXCLUDED'
        schema.methods.find { it.name == 'query' }.eligibility == 'EXCLUDED'
        schema.methods.find { it.name == 'computed' }.eligibility == 'UNKNOWN'
        schema.methods.find { it.name == 'computed' }.defaultStatus == 'UNAVAILABLE_WITHOUT_EXECUTION'
        System.getProperty('wire.schema.executed') == null
        !binaries.resolved.contains('safeannotations.Policy')

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'custom Number rejection agrees for the authored operation and exposes all invisible field rejections'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.NUMERIC_REJECTIONS)])
        def binaries = isolated([jar])
        def oracle = carrier(jar, 'numericboundary.Configuration')

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'numericboundary.Configuration_DSL$Builder', 'numericboundary.Configuration_DSL$Factory')
        def comparison = compare(schema, oracle)

        then:
        comparison.mismatches*.path == ['numericboundary.Configuration#budget:rejection',
                'numericboundary.Configuration#budgets:rejection', 'numericboundary.Configuration#limits:rejection']
        comparison.equalities.contains('numericboundary.Configuration#allocate(numericboundary.OpaqueNumber):void:rejection')
        schema.methods.find { it.name == 'allocate' }.eligibility == 'EXCLUDED'
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'fixed native keys child key exclusions LINK and keyMapping are explicit enrichment gaps'() {
        given:
        def jar = compileJar([trapped('''
            package fixedschema
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.layer3.Cluster
            import com.fasterxml.jackson.annotation.JsonProperty
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @interface Managed {}
            @DSL class Entry { @Key String name; String route; String value }
            @DSL class Configuration {
                @Managed @JsonProperty('main-slot') Entry primary
                @Cluster(value=Managed, fixedKeys=true) Map<String, Entry> getSlots() {}
                @Field(FieldType.LINK) Entry linked
                @Field(keyMapping={ it.route }) Map<String, Entry> derived
                Map<String, Entry> entries
            }
        ''')] + NAMING)
        def binaries = isolated([jar])
        def oracle = carrier(jar, 'fixedschema.Configuration')

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'fixedschema.Configuration_DSL$Builder', 'fixedschema.Configuration_DSL$Factory')
        def child = CompiledBuilderSchema.read(binaries, 'fixedschema.Entry_DSL$Builder', 'fixedschema.Entry_DSL$Factory')
        def comparison = compare(schema, oracle)
        def cluster = CompiledBuilderSchema.readScope(binaries, 'fixedschema.Configuration_DSL$Builder$ClusterFactory_slots')
        replacementParity(binaries, oracle)
        def actualScope = CompiledBuilderSchemaReplacementProbe.scope(Class.forName(
                'fixedschema.Configuration_DSL$Builder$ClusterFactory_slots', false, binaries))

        then:
        actualScope as Set == cluster.collect { [owner: it.owner, name: it.name, parameters: it.parameters, returns: it.returns, documentation: it.documentation ?: ''] } as Set
        comparison.mismatches.find { it.path == 'fixedschema.Configuration#primary:fixedChildKey' } ==
                [path: 'fixedschema.Configuration#primary:fixedChildKey', expected: 'primary', actual: 'UNAVAILABLE']
        comparison.mismatches.find { it.path == 'fixedschema.Configuration#primary:externalName' }.expected == 'main-slot'
        comparison.mismatches.find { it.path == 'fixedschema.Configuration#entries:excludedChildKeyMember' }.expected == 'name'
        comparison.mismatches*.path.containsAll(['fixedschema.Configuration#derived:rejection', 'fixedschema.Configuration#linked:rejection'])
        comparison.equalities.contains('fixedschema.Configuration#entries:payload')
        child.methods.any { it.name == 'getName' }
        !child.methods.any { it.name == 'name' }
        cluster.any { it.name == 'primary' && it.returns.contains('Entry_DSL$Builder') }
        schema.factories.every { it.eligibility == 'EXCLUDED' }
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'missing inherited contracts or exact public byte resources fail with root context and silent traps'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING)
        def missingParent = new SchemaReflectionProbe.IsolatedLoader((runtimeFiles() + [jar])*.toURI()*.toURL() as URL[], 'admission.Base_DSL$Builder')
        def missingBytes = new URLClassLoader((runtimeFiles() + [jar])*.toURI()*.toURL() as URL[], ClassLoader.platformClassLoader) {
            @Override InputStream getResourceAsStream(String name) {
                name == 'admission/Parent_DSL$Builder.class' ? null : super.getResourceAsStream(name)
            }
        }

        when:
        CompiledBuilderSchema.read(missingParent, 'admission.Parent_DSL$Builder', 'admission.Parent_DSL$Factory')

        then:
        def linkage = thrown(IllegalStateException)
        linkage.message.contains('admission.Parent_DSL$Builder')
        linkage.message.contains('complete API/Schema/runtime classpath')

        when:
        CompiledBuilderSchema.read(missingBytes, 'admission.Parent_DSL$Builder', 'admission.Parent_DSL$Factory')

        then:
        def resource = thrown(IllegalStateException)
        resource.message.contains('exact contract resources')
        System.getProperty('wire.schema.executed') == null

        cleanup:
        missingParent?.close()
        missingBytes?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'field container #declaration preserves its exact generic shape and selected population signature'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.collection(declaration))])
        def binaries = isolated([jar])
        def oracle = carrier(jar, 'collectioncontracts.Configuration')

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'collectioncontracts.Configuration_DSL$Builder', 'collectioncontracts.Configuration_DSL$Factory')
        def comparison = compare(schema, oracle)

        then:
        comparison.equalities.contains('collectioncontracts.Configuration#values:payload')
        comparison.equalities.any { it.startsWith('collectioncontracts.Configuration#values:descriptor:addValue(') }
        comparison.mismatches.find { it.path.endsWith(':populationSelection') }.expected == 'addValue'
        comparison.mismatches.size() == 9
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        declaration << ['List<String>', 'Set<String>', 'SortedSet<String>', 'NavigableSet<String>',
                        'EnumSet<Mode>', 'Map<String, String>', 'SortedMap<String, String>', 'NavigableMap<String, String>']
    }

    def 'unsupported whole-operation #target agrees with the WIRE rejection instead of field-container admission'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.operationArgument(target))])
        def binaries = isolated([jar])
        def oracle = carrier(jar, 'restrictedarguments.Configuration')

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'restrictedarguments.Configuration_DSL$Builder', 'restrictedarguments.Configuration_DSL$Factory')
        def comparison = compare(schema, oracle)

        then:
        comparison.mismatches.size() == 9
        comparison.equalities.count { it.endsWith(':rejection') } == 1
        schema.methods.find { it.name == 'consume' }.eligibility == 'EXCLUDED'
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        target << ['Set<String>', 'SortedSet<String>', 'NavigableSet<String>', 'EnumSet<Mode>',
                   'Collection<String>', 'ArrayList<String>', 'LinkedHashMap<String, String>', 'TreeMap<String, String>']
    }

    def 'payload #damage drift is reported even when every selected signature still exists'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING)
        def binaries = isolated([jar])
        def schema = CompiledBuilderSchema.read(binaries, 'admission.Parent_DSL$Builder', 'admission.Parent_DSL$Factory')
        def oracle = carrier(jar, 'admission.Parent')
        def entry = oracle.entries.find { it.externalName == (damage == 'kind' ? 'renamed' : 'child') }
        entry.payload[damage == 'kind' ? 'kind' : 'builder'] = damage == 'kind' ? 'SCALAR' : 'admission.Wrong_DSL$Builder'

        when:
        def comparison = compare(schema, oracle)

        then:
        comparison.mismatches.any { it.path == entry.source + ':payloadFacts' && it.expected != it.actual }
        comparison.equalities.any { it.startsWith(entry.source + ':descriptor:') }

        cleanup:
        binaries?.close()

        where:
        damage << ['kind', 'nested Builder']
    }

    def 'unsupported nested payload #target preserves compiler rejection precedence'() {
        given:
        String source = StructuredInputContractFixtures.NUMERIC_REJECTIONS.replace(
                '@Builder.Method void allocate(OpaqueNumber value) { budget = value }',
                "@Builder.Method void consume($target value) { }")
        def jar = compileJar([trapped(source)])
        def binaries = isolated([jar])
        def oracle = carrier(jar, 'numericboundary.Configuration')

        when:
        def schema = CompiledBuilderSchema.read(binaries, 'numericboundary.Configuration_DSL$Builder', 'numericboundary.Configuration_DSL$Factory')
        def comparison = compare(schema, oracle)
        def rejection = oracle.rejections.find { it.source.contains('#consume(') }

        then:
        schema.methods.find { it.name == 'consume' }.reason == rejection.reason
        comparison.equalities.contains(rejection.source + ':rejection')
        comparison.mismatches*.path == ['numericboundary.Configuration#budget:rejection',
                'numericboundary.Configuration#budgets:rejection', 'numericboundary.Configuration#limits:rejection']
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        target << ['Set<OpaqueNumber>', 'Map<Integer, OpaqueNumber>', 'List<Set<OpaqueNumber>>',
                   'Map<String, SortedSet<OpaqueNumber>>', 'Map<Integer, Set<String>>']
    }

    def 'production replacement has complete carrier parity with no carrier packaged: #model'() {
        given:
        def jar = compileJar([trapped(source)] + NAMING)
        def binaries = isolated([withoutCarriers(jar)])
        def oracle = carrier(jar, model)
        oracle.entries.each { it.descriptors = it.descriptors.collect { canonicalDescriptor(it) }.sort() }

        when:
        def actual = CompiledBuilderSchemaReplacementProbe.read(
                Class.forName(model + '_DSL$Builder', false, binaries), Class.forName(model + '_DSL$Factory', false, binaries))

        then:
        actual.model == oracle.model
        actual.builder == oracle.builder
        actual.entries == oracle.entries
        actual.rejections as Set == oracle.rejections as Set
        actual.defaults == 'UNAVAILABLE_WITHOUT_EXECUTION'
        System.getProperty('wire.schema.executed') == null
        !binaries.resolved.any { it.endsWith('$WirePlan') || it.endsWith('$Builder') && !it.contains('_DSL') }

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        model                                  | source
        'admission.Parent'                     | StructuredInputContractFixtures.ADMISSION
        'rejection.Parent'                     | StructuredInputContractFixtures.REJECTIONS
        'numericboundary.Configuration'        | StructuredInputContractFixtures.NUMERIC_REJECTIONS
        'shadowedpopulation.Configuration'     | StructuredInputContractFixtures.POPULATION
        'collectioncontracts.Configuration'    | StructuredInputContractFixtures.collection('List<String>')
        'collectioncontracts.Configuration'    | StructuredInputContractFixtures.collection('Set<String>')
        'collectioncontracts.Configuration'    | StructuredInputContractFixtures.collection('SortedSet<String>')
        'collectioncontracts.Configuration'    | StructuredInputContractFixtures.collection('NavigableSet<String>')
        'collectioncontracts.Configuration'    | StructuredInputContractFixtures.collection('EnumSet<Mode>')
        'collectioncontracts.Configuration'    | StructuredInputContractFixtures.collection('Map<String, String>')
        'collectioncontracts.Configuration'    | StructuredInputContractFixtures.collection('SortedMap<String, String>')
        'collectioncontracts.Configuration'    | StructuredInputContractFixtures.collection('NavigableMap<String, String>')
        'restrictedarguments.Configuration'    | StructuredInputContractFixtures.operationArgument('Collection<String>')
        'restrictedarguments.Configuration'    | StructuredInputContractFixtures.operationArgument('String[]')
        'restrictedarguments.Configuration'    | StructuredInputContractFixtures.operationArgument('int[]')
        'restrictedarguments.Configuration'    | StructuredInputContractFixtures.operationArgument('List')
        'restrictedarguments.Configuration'    | StructuredInputContractFixtures.operationArgument('Map')
        'restrictedarguments.Configuration'    | StructuredInputContractFixtures.operationArgument('Object')
        'rejectedparent.Configuration'         | '''
            package rejectedparent
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Base {
                @JsonProperty('shared') String first
                @JsonProperty('shared') String second
            }
            @DSL class Configuration extends Base { @JsonProperty('shared') String available }
        '''
        'unresolvedinput.Configuration'         | '''
            package unresolvedinput
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Builder
            @DSL class Base<T> { T value; @Builder.Method void consume(T input) {} }
            @DSL class Configuration extends Base<String> {}
        '''
        'restrictedarguments.Configuration'    | StructuredInputContractFixtures.operationArgument('Map<Integer, Set<String>>')
    }

    def 'otherwise invisible protected declarations preserve their exact exclusion categories'() {
        given:
        def jar = compileJar([trapped('''
            package invisiblefields
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonIgnore
            @DSL class Configuration {
                @Field(FieldType.PROTECTED) String reserved
                @Field(FieldType.PROTECTED) char marker
                @Field(FieldType.PROTECTED) Number budget
                @JsonIgnore @Field(FieldType.PROTECTED) String ignored
                @Field(FieldType.PROTECTED) List<Integer> values
            }
        ''')] + NAMING)
        def binaries = isolated([withoutCarriers(jar)])

        when:
        replacementParity(binaries, carrier(jar, 'invisiblefields.Configuration'))

        then:
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'production replacement rejects #damage enrichment without execution'() {
        given:
        def jar = compileJar([trapped('''
            package damagedenrichment
            import com.blackbuild.klum.ast.DSL
            @DSL class Configuration { String title }
        ''')])
        def damaged = rewriteContract(jar, 'damagedenrichment/Configuration_DSL$Builder.class', damage)
        def binaries = isolated([damaged])

        when:
        CompiledBuilderSchemaReplacementProbe.read(
                Class.forName('damagedenrichment.Configuration_DSL$Builder', false, binaries),
                Class.forName('damagedenrichment.Configuration_DSL$Factory', false, binaries))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('damagedenrichment.Configuration_DSL$Builder')
        failure.message.contains('recompile and package the Schema')
        failure.cause != null
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        damage << ['schema marker', 'selection', 'source', 'role', 'fixed property key', 'exclusion']
    }

    def 'directory contracts with unescaped spaces retain exact resource origins'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING)
        File directory = unpackContract(jar)
        URL directoryUrl = new URL('file:' + directory.absolutePath + '/')
        def urls = (runtimeFiles()*.toURI()*.toURL() + [directoryUrl]) as URL[]
        def binaries = new URLClassLoader(urls, ClassLoader.platformClassLoader)

        when:
        replacementParity(binaries, carrier(jar, 'admission.Parent'))

        then:
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'production replacement rejects a shadow resource or missing dependency without ambient fallback: #damage'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING)
        File other = unpackContract(compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING))
        def urls = (runtimeFiles() + [jar])*.toURI()*.toURL() as URL[]
        String contractResource = 'admission/Parent_DSL$Builder.class'
        String resourceDamage = damage
        def binaries = damage == 'protocol' ? new SchemaReflectionProbe.IsolatedLoader(urls,
                'com.blackbuild.klum.ast.runtime.generated.GeneratedInput$Field') : new URLClassLoader(urls, ClassLoader.platformClassLoader) {
            @Override URL getResource(String name) {
                if (name == contractResource && resourceDamage == 'missing resource') return null
                name == contractResource && resourceDamage == 'shadow resource' ?
                        new URL('file:' + other.absolutePath + '/' + name) : super.getResource(name)
            }
            @Override InputStream getResourceAsStream(String name) {
                name == contractResource && resourceDamage == 'missing resource' ? null : super.getResourceAsStream(name)
            }
        }

        when:
        CompiledBuilderSchemaReplacementProbe.read(Class.forName('admission.Parent_DSL$Builder', false, binaries),
                Class.forName('admission.Parent_DSL$Factory', false, binaries))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('complete API/runtime classpath')
        failure.cause.message.contains(damage == 'shadow resource' ? 'origin' : damage == 'protocol' ? 'GeneratedInput$Field' : 'resource')
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        damage << ['shadow resource', 'missing resource', 'protocol']
    }

    def 'older compiled artifact requires enrichment recompilation without a legacy fallback'() {
        given:
        def original = new File(getClass().getResource('/structured-input-938/wire-plan-b-938.jar').toURI())
        def binaries = isolated([original])

        when:
        CompiledBuilderSchemaReplacementProbe.read(Class.forName('original938.Original_DSL$Builder', false, binaries),
                Class.forName('original938.Original_DSL$Factory', false, binaries))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile and package the Schema')
        failure.message.contains('Missing generated input enrichment')

        cleanup:
        binaries?.close()
    }

    def 'diamond parent paths resolve one declaring inventory without duplicate admission'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING)
        def diamond = rewriteContract(jar, 'admission/Parent_DSL$Builder.class', 'diamond')
        def binaries = isolated([diamond])

        when:
        replacementParity(binaries, carrier(jar, 'admission.Parent'))

        then:
        System.getProperty('wire.schema.executed') == null
        binaries.resolved.contains('admission.Diamond_DSL$Builder')

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    private static File unpackContract(File jar) {
        File directory = new File(jar.parentFile, 'schema fixtures')
        new JarFile(jar).withCloseable { archive ->
            archive.entries().findAll { !it.directory }.each { entry ->
                File target = new File(directory, entry.name)
                target.parentFile.mkdirs()
                target.bytes = archive.getInputStream(entry).bytes
            }
        }
        directory
    }

    private File rewriteContract(File jar, String contract, String damage) {
        File output = new File(jar.parentFile, 'damaged.jar')
        new JarFile(jar).withCloseable { input ->
            new JarOutputStream(new FileOutputStream(output)).withCloseable { archive ->
                input.entries().each { entry ->
                    if (entry.name.endsWith('$WirePlan.class')) return
                    byte[] bytes = input.getInputStream(entry).bytes
                    if (entry.name == contract) {
                        def writer = new ClassWriter(0)
                        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                                if (damage == 'diamond') {
                                    interfaces = (interfaces.toList() + 'admission/Diamond_DSL$Builder') as String[]
                                    signature += 'Ladmission/Diamond_DSL$Builder<TSELF;>;'
                                }
                                super.visit(version, access, name, signature, parent, interfaces)
                            }
                            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                if (damage == 'schema marker' && descriptor.endsWith('GeneratedInput$Schema;')) return null
                                super.visitAnnotation(descriptor, visible)
                            }
                            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                        if (damage == 'selection' && annotation.endsWith('GeneratedInput$Selection;')) return null
                                        def delegate = super.visitAnnotation(annotation, visible)
                                        new AnnotationVisitor(Opcodes.ASM9, delegate) {
                                            @Override void visit(String member, Object value) {
                                                if (damage == 'source' && annotation.endsWith('GeneratedInput$Field;') && member == 'value') value = 'forged'
                                                if (damage == 'role' && annotation.endsWith('GeneratedInput$Selection;') && member == 'role') value = 'OPERATION'
                                                super.visit(member, value)
                                            }
                                            @Override void visitEnd() {
                                                if (annotation.endsWith('GeneratedInput$Field;')) {
                                                    if (damage == 'fixed property key') super.visit('fixedKey', true)
                                                    if (damage == 'exclusion') super.visit('exclusion', 'UNKNOWN')
                                                }
                                                super.visitEnd()
                                            }
                                        }
                                    }
                                }
                            }
                        }, 0)
                        bytes = writer.toByteArray()
                    }
                    archive.putNextEntry(new JarEntry(entry.name))
                    archive.write(bytes)
                    archive.closeEntry()
                }
                if (damage == 'diamond') {
                    def writer = new ClassWriter(0)
                    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                            'admission/Diamond_DSL$Builder', '<SELF:Ladmission/Base;>Ljava/lang/Object;Ladmission/Base_DSL$Builder<TSELF;>;',
                            'java/lang/Object', ['admission/Base_DSL$Builder'] as String[])
                    writer.visitAnnotation('Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$Schema;', true).visitEnd()
                    writer.visitEnd()
                    archive.putNextEntry(new JarEntry('admission/Diamond_DSL$Builder.class'))
                    archive.write(writer.toByteArray())
                    archive.closeEntry()
                }
            }
        }
        output
    }

    private static void replacementParity(ClassLoader binaries, Map oracle) {
        def actual = CompiledBuilderSchemaReplacementProbe.read(
                Class.forName(oracle.builder, false, binaries), Class.forName(oracle.model + '_DSL$Factory', false, binaries))
        def expected = oracle.entries.collect { it + [descriptors: it.descriptors.collect { canonicalDescriptor(it) }.sort()] }
        assert actual.entries == expected
        assert actual.rejections as Set == oracle.rejections as Set
        assert actual.defaults == 'UNAVAILABLE_WITHOUT_EXECUTION'
        def observed = CompiledBuilderSchema.read(binaries, oracle.builder, oracle.model + '_DSL$Factory')
        def factory = CompiledBuilderSchemaReplacementProbe.scope(Class.forName(oracle.model + '_DSL$Factory', false, binaries))
        assert factory as Set == observed.factories.collect {
            [owner: it.owner, name: it.name, parameters: it.parameters, returns: it.returns, documentation: it.documentation ?: '']
        } as Set
        oracle.entries.each { entry ->
            def declaration
            if (entry.role == 'OPERATION') declaration = observed.methods.find {
                it.owner == entry.owner && it.descriptor == canonicalDescriptor(entry.descriptors.first())
            }
            else {
                String field = entry.source.substring(entry.source.indexOf('#') + 1)
                String getter = 'get' + field.substring(0, 1).toUpperCase(Locale.ROOT) + field.substring(1)
                declaration = observed.methods.find { it.owner == entry.owner && it.name == getter && it.parameters.empty }
            }
            assert declaration != null
            assert actual.documentation[entry.source] == declaration.documentation
        }
    }

    private static String renderMismatches(Map comparison) {
        (['fact\texpected\tcontract'] + comparison.mismatches.collect { mismatch ->
            [mismatch.path, mismatch.expected, mismatch.actual].collect { it == null ? '<absent>' : it.toString() }.join('\t')
        }).join('\n')
    }

    private static void requireParity(Map comparison) {
        assert comparison.mismatches.empty: renderMismatches(comparison)
    }

    private static Map compare(CompiledBuilderSchema schema, Map oracle) {
        def equalities = []
        def mismatches = []
        def transportNormalizations = []
        def check = { String path, expected, actual ->
            if (expected == actual) equalities << path
            else mismatches << [path: path, expected: expected, actual: actual]
        }
        check('model', oracle.model, schema.model)
        check('builder', oracle.builder, schema.builder)
        oracle.entries.each { entry ->
            def selected = entry.descriptors.collect { descriptor ->
                String canonical = canonicalDescriptor(descriptor)
                if (canonical != descriptor) transportNormalizations << [wire: descriptor, contract: canonical]
                def method = schema.methods.find { it.owner == entry.owner && it.descriptor == canonical }
                assert method != null: "Missing public contract ${entry.owner} $descriptor"
                check(entry.source + ':descriptor:' + descriptor, canonical, method.descriptor)
                method
            }
            def declaration = selected.first()
            boolean operation = entry.role == 'OPERATION'
            check(entry.source + ':admission', 'ADMITTED', operation ? declaration.eligibility : 'UNAVAILABLE')
            ['externalName', 'nameOrigin', 'source'].each { fact ->
                check(entry.source + ':' + fact, entry[fact], operation ? declaration[fact] : 'UNAVAILABLE')
            }
            check(entry.source + ':role', entry.role, operation ? declaration.category : 'UNAVAILABLE')
            check(entry.source + ':populationSelection', entry.operation, operation ? declaration.name : 'UNAVAILABLE')
            ['mapKeyType', 'excludedChildKeyMember', 'fixedChildKey'].each { fact ->
                check(entry.source + ':' + fact, entry[fact], operation ? null : 'UNAVAILABLE')
            }
            if (operation) {
                check(entry.source + ':payload', payloadText(entry.payload), declaration.payload)
                check(entry.source + ':payloadFacts', entry.payload, shapeFacts(declaration.parameterShapes.first()))
            }
            else {
                // Oracle supplies this association only in the comparison, never in the facade.
                String field = entry.source.substring(entry.source.indexOf('#') + 1)
                String getterName = 'get' + field.substring(0, 1).toUpperCase(Locale.ROOT) + field.substring(1)
                def getter = schema.methods.find { it.name == getterName && it.parameters.empty }
                assert getter != null: "Missing oracle-associated getter $getterName"
                String expected = genericType(entry.payload)
                if (entry.role == 'KEYED_OWNED_MAP') expected = "java.util.Map<java.lang.String, $expected>"
                check(entry.source + ':payload', expected, getter.returns)
                def actualShape = entry.role == 'KEYED_OWNED_MAP' ? getter.returnShape.item : getter.returnShape
                check(entry.source + ':payloadFacts', entry.payload, shapeFacts(actualShape))
            }
        }
        oracle.rejections.each { rejection ->
            def declaration = schema.methods.find { it.source == rejection.source }
            check(rejection.source + ':rejection', rejection.reason,
                    declaration?.eligibility == 'EXCLUDED' ? declaration.reason : 'UNAVAILABLE')
        }
        [equalities: equalities, mismatches: mismatches, transportNormalizations: transportNormalizations]
    }

    private static Map shapeFacts(CompiledBuilderSchema.Shape shape) {
        [kind: shape.kind, type: shape.type, builder: shape.builder, keyType: shape.keyType,
         item: shape.item == null ? null : shapeFacts(shape.item)]
    }

    /** WIRE records both ClassNode array encodings. Only transport spelling changes; all types remain compared. */
    private static String canonicalDescriptor(String descriptor) {
        def normalize = { String name ->
            if (!name.startsWith('[')) return name
            int dimensions = name.takeWhile { it == '[' }.length()
            String base = name.substring(dimensions)
            String component = base.startsWith('L') ? base.substring(1, base.length() - 1) :
                    [Z: 'boolean', B: 'byte', S: 'short', I: 'int', J: 'long', F: 'float', D: 'double', C: 'char'][base]
            assert component != null: "Unknown WIRE array encoding $name"
            component + '[]' * dimensions
        }
        int start = descriptor.indexOf('(')
        int end = descriptor.indexOf('):')
        String parameters = descriptor.substring(start + 1, end)
        descriptor.substring(0, start) + '(' + (parameters ? parameters.split(', ').collect(normalize).join(', ') : '') +
                '):' + normalize(descriptor.substring(end + 2))
    }

    private static String genericType(Map payload) {
        if (payload.kind == 'NESTED_OBJECT') return payload.builder + '<' + payload.type + '>'
        if (payload.kind == 'LIST') return payload.type + '<' + genericType(payload.item) + '>'
        if (payload.kind == 'MAP') return payload.type + '<' + payload.keyType + ', ' + genericType(payload.item) + '>'
        payload.type
    }

    private static String payloadText(Map payload) {
        payload.kind + ':' + payload.type + (payload.keyType ? ':' + payload.keyType : '') +
                (payload.item ? '[' + payloadText(payload.item) + ']' : '')
    }

    private static String trapped(String source) {
        source.replaceAll(/(@DSL(?: abstract)? class [\w]+(?: extends [\w]+)? \{)/) { it[0] + TRAP + '\n' }
                .replace('enum Region { EU, US }', 'enum Region { EU, US; ' + TRAP + ' }')
    }

    private SchemaReflectionProbe.IsolatedLoader isolated(List<File> jars) {
        new SchemaReflectionProbe.IsolatedLoader((runtimeFiles() + jars)*.toURI()*.toURL() as URL[], '')
    }

    private static List<File> runtimeFiles() {
        [new File(System.getProperty('klumRuntimeJar')), new File(System.getProperty('klumAnnotationsJar')),
         new File(GroovyObject.protectionDomain.codeSource.location.toURI()), new File(AnnoDoc.protectionDomain.codeSource.location.toURI())]
    }

    private File compileJar(List<String> sources, List<File> dependencies = []) {
        File directory = tempFolder.newFolder()
        File classes = new File(directory, 'classes')
        def configuration = new CompilerConfiguration(compilerConfiguration)
        configuration.targetDirectory = classes
        new GroovyClassLoader(getClass().classLoader, configuration).withCloseable { compiler ->
            dependencies.each { compiler.addClasspath(it.path) }
            def unit = new CompilationUnit(configuration, null, compiler)
            sources.eachWithIndex { source, index -> unit.addSource("Fixture${index}.groovy", source) }
            unit.compile()
        }
        File jar = new File(directory, 'contracts.jar')
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { archive ->
            classes.eachFileRecurse { file ->
                if (file.file) {
                    assert file.name.endsWith('.class')
                    archive.putNextEntry(new JarEntry(classes.toPath().relativize(file.toPath()).toString()))
                    archive.write(file.bytes)
                    archive.closeEntry()
                }
            }
        }
        assert classes.deleteDir()
        jar
    }

    private File withoutCarriers(File jar) {
        File output = new File(jar.parentFile, 'without-carriers.jar')
        new JarFile(jar).withCloseable { input ->
            new JarOutputStream(new FileOutputStream(output)).withCloseable { archive ->
                input.entries().each { entry ->
                    if (!entry.name.endsWith('$WirePlan.class')) {
                        archive.putNextEntry(new JarEntry(entry.name))
                        archive.write(input.getInputStream(entry).bytes)
                        archive.closeEntry()
                    }
                }
            }
        }
        output
    }

    /** Legacy transport is an oracle only. It never enters CompiledBuilderSchema. */
    private static Map carrier(File jar, String model) {
        String data
        new JarFile(jar).withCloseable { archive ->
            def entry = archive.getJarEntry(model.replace('.', '/') + '_DSL$WirePlan.class')
            assert entry != null: "Missing oracle for $model"
            new ClassReader(archive.getInputStream(entry).bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if (name == 'DATA') data = value
                    null
                }
            }, ClassReader.SKIP_CODE)
        }
        def lines = data.readLines()
        assert lines.remove(0) == 'WIRE-PLAN/B'
        Map result = [entries: [], rejections: []]
        lines.each { line ->
            def tokens = line.split('\\|', -1)
            switch (tokens[0]) {
                case 'model':
                    assert tokens.length == 3
                    result.model = decode(tokens[1]); result.builder = decode(tokens[2]); break
                case 'entry':
                    assert tokens.length == 12
                    result.entries << [externalName: decode(tokens[1]), role: tokens[2], source: decode(tokens[3]),
                            owner: decode(tokens[4]), operation: decode(tokens[5]), descriptors: decode(tokens[6]).split(/,(?=[^ ]+\()/).toList(),
                            nameOrigin: tokens[7], mapKeyType: decode(tokens[8]), excludedChildKeyMember: decode(tokens[9]),
                            payload: parsePayload(tokens[10]), fixedChildKey: decode(tokens[11])]; break
                case 'rejection':
                    assert tokens.length == 3
                    result.rejections << [source: decode(tokens[1]), reason: decode(tokens[2])]; break
                default: throw new AssertionError("Unknown oracle record $line")
            }
        }
        result
    }

    private static Map parsePayload(String text) {
        assert text.startsWith('[') && text.endsWith(']')
        def parts = text.substring(1, text.length() - 1).split(',', 5)
        assert parts.length in [4, 5]
        [kind: parts[0], type: decode(parts[1]), builder: decode(parts[2]), keyType: decode(parts[3]),
         item: parts.length == 5 ? parsePayload(parts[4]) : null]
    }

    private static String decode(String token) {
        token == '-' ? null : new String(Base64.urlDecoder.decode(token), 'UTF-8')
    }
}
