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
import com.blackbuild.klum.ast.compiler.internal.ast.GeneratedDslSupport
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.compiler.internal.ast.StructuredInputContractFixtures
import com.blackbuild.klum.ast.runtime.KlumModelException
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchemaReplacementProbe
import spock.lang.Issue

import java.lang.reflect.ParameterizedType
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** Final compiled-contract acceptance without a legacy oracle or expected-gap allowlist. */
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


    @Issue('959')
    def 'D7 binary probe folds explicit type choices onto public Builder contracts'() {
        given:
        def api = compileJar(['''
            package profiletransport
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.TYPE])
            @interface Choice { String value() }
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.TYPE])
            @interface Facts { int version(); boolean explicit(); String profile() }
            @DSL class Base { String displayName }
            @Choice('IDENTITY') @DSL class Locked { String nativeName }
            @DSL class Owned { String ownName }
        '''], [], true)
        def child = compileJar(['''
            package profiletransport
            import com.blackbuild.klum.ast.*
            @Choice('KEBAB_CASE') @DSL class Child extends Base { Owned first; Owned second }
            @Choice('KEBAB_CASE') @DSL class Conflict extends Locked { String localName }
        '''], [api], true)
        def descendants = compileJar(['''
            package profiletransport
            import com.blackbuild.klum.ast.*
            @DSL class Grandchild extends Child { String finalName }
            @Choice('KEBAB_CASE') @DSL class Repeated extends Child { String equalName }
            @Choice('IDENTITY') @DSL class Different extends Child { String changedName }
        '''], [api, child], true)
        def binaries = isolated([api, child, descendants])

        when:
        def base = rawNamingFacts(api, 'profiletransport/Base_DSL$Builder.class').profile
        def locked = rawNamingFacts(api, 'profiletransport/Locked_DSL$Builder.class').profile
        def selected = rawNamingFacts(child, 'profiletransport/Child_DSL$Builder.class').profile
        def absent = rawNamingFacts(descendants, 'profiletransport/Grandchild_DSL$Builder.class').profile
        def equal = rawNamingFacts(descendants, 'profiletransport/Repeated_DSL$Builder.class').profile
        def different = rawNamingFacts(descendants, 'profiletransport/Different_DSL$Builder.class').profile

        then:
        base == [version: 1, explicit: false, profile: 'IDENTITY']
        locked == [version: 1, explicit: true, profile: 'IDENTITY']
        selected == [version: 1, explicit: true, profile: 'KEBAB_CASE']
        absent == base
        equal == selected
        different == locked
        rawNamingFacts(child, 'profiletransport/Conflict_DSL$Builder.class').profile == selected
        rawNamingFacts(api, 'profiletransport/Owned_DSL$Builder.class').profile == base
        Class.forName('profiletransport.Grandchild_DSL$Builder', false, binaries).interfaces*.name.contains('profiletransport.Child_DSL$Builder')
        !binaries.resolved.contains('profiletransport.Choice')

        cleanup:
        binaries?.close()
    }

    @Issue('959')
    def 'D7 binary probe retains native candidates before identity collision pruning'() {
        given:
        def api = compileJar(['''
            package namingprobe
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.*
            @DSL class Base {
                static { System.setProperty('wire.schema.executed', 'initialized'); throw new AssertionError('probe trap') }
                @Key String serviceId
                String displayName
                @JsonProperty('displayName') String literalName
                @JsonIgnore String hiddenValue
                @Builder.Method @JsonProperty('URL') void configureURL(String value) {}
            }
            @DSL class Owned { String nativeName }
        '''] + NAMING)
        def child = compileJar(['''
            package namingprobe
            import com.blackbuild.klum.ast.*
            @DSL class Child extends Base { String localName; Owned first; Owned second }
        '''], [api])
        def grandchild = compileJar(['''
            package namingprobe
            import com.blackbuild.klum.ast.*
            @DSL class Grandchild extends Child { String finalName }
        '''], [api, child])
        def binaries = isolated([api, child, grandchild])
        def builder = Class.forName('namingprobe.Grandchild_DSL$Builder', false, binaries)

        when:
        def identity = CompiledBuilderSchemaReplacementProbe.read(builder,
                Class.forName('namingprobe.Grandchild_DSL$Factory', false, binaries))
        def declarations = rawNamingFacts(api, 'namingprobe/Base_DSL$Builder.class')

        then: 'identity collisions prune the resolved map but public facts retain both candidates and exact overrides'
        !identity.entries*.externalName.contains('displayName')
        identity.rejections.count { it.reason.contains('collision') } == 2
        declarations.fields.displayName.value == 'displayName'
        declarations.fields.literalName.rename == 'displayName'
        declarations.fields.hiddenValue.ignored == true
        declarations.selections.contains('displayName')
        declarations.selections.contains('literalName')
        declarations.key.value == 'serviceId'
        declarations.operations.configureURL.rename == 'URL'
        builder.interfaces*.name.contains('namingprobe.Child_DSL$Builder')
        builder.interfaces.find { it.name == 'namingprobe.Child_DSL$Builder' }.interfaces*.name.contains('namingprobe.Base_DSL$Builder')
        !binaries.resolved.any { it.endsWith('$WirePlan') || it.endsWith('$Builder') && !it.contains('_DSL') }
        System.getProperty('wire.schema.executed') == null

        when: 'a separate positive control initializes the binary producer'
        Class.forName('namingprobe.Base', true, binaries)

        then:
        thrown(AssertionError)
        System.getProperty('wire.schema.executed') == 'initialized'

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    @Issue('959')
    def 'first descendant profile preserves non-naming ancestor exclusions: #profile'() {
        given:
        def api = compileJar([trapped('''
            package namingexclusions
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.*
            @DSL abstract class AbstractChild {}
            @DSL class Base {
                String validName
                String displayName
                @JsonProperty('displayName') String literalName
                @JsonIgnore @JsonProperty('display-name') String hiddenValue
                @Field(FieldType.PROTECTED) String internalValue
                char unsupportedValue
                AbstractChild abstractChild
                @Builder.Method void raw(Map values) { throw new AssertionError('authored operation') }
                @Builder.Method void reset() { throw new AssertionError('authored operation') }
            }
        ''')] + NAMING)
        def child = compileJar(["""
            package namingexclusions
            import com.blackbuild.klum.ast.*
            $annotation
            @DSL class Child extends Base {}
        """], [api])
        def binaries = isolated([api, child])

        when:
        def ancestor = CompiledBuilderSchemaReplacementProbe.read(
                Class.forName('namingexclusions.Base_DSL$Builder', false, binaries),
                Class.forName('namingexclusions.Base_DSL$Factory', false, binaries))
        def descendant = CompiledBuilderSchemaReplacementProbe.read(
                Class.forName('namingexclusions.Child_DSL$Builder', false, binaries),
                Class.forName('namingexclusions.Child_DSL$Factory', false, binaries))
        def exclusions = ancestor.rejections.findAll { !it.reason.contains('collision') }

        then: 'only naming-collision sources can recover; structural, unsupported and ignored sources stay rejected'
        ancestor.entries*.externalName == ['validName']
        ancestor.rejections.count { it.reason.contains('collision') } == 2
        exclusions*.source as Set == [
                'namingexclusions.Base#abstractChild', 'namingexclusions.Base#hiddenValue',
                'namingexclusions.Base#internalValue', 'namingexclusions.Base#unsupportedValue',
                'namingexclusions.Base#raw(java.util.Map):void', 'namingexclusions.Base#reset():void'
        ] as Set
        descendant.rejections.findAll { !it.reason.contains('collision') } == exclusions
        descendant.rejections.count { it.reason.contains('collision') } == (profile == 'KEBAB_CASE' ? 0 : 2)
        descendant.entries.collectEntries { [(it.externalName): it.source] } == expected.collectEntries { name, member ->
            [(name): "namingexclusions.Base#$member".toString()]
        }
        System.getProperty('wire.schema.executed') == null

        when: 'the same source-free producer is explicitly initialized as a positive control'
        Class.forName('namingexclusions.Base', true, binaries)

        then:
        thrown(AssertionError)
        System.getProperty('wire.schema.executed') == 'initialized'

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        profile      | annotation                                    | expected
        'absent'     | ''                                            | [validName: 'validName']
        'IDENTITY'   | '@InputNaming(InputNaming.Profile.IDENTITY)'   | [validName: 'validName']
        'KEBAB_CASE' | '@InputNaming(InputNaming.Profile.KEBAB_CASE)' | ['valid-name': 'validName', 'display-name': 'displayName', displayName: 'literalName']
    }

    @Issue('959')
    def 'literal folding preserves source-free fields keys and operations across artifacts'() {
        given:
        def api = compileJar(['''
            package literalbinary
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.*
            @DSL class Base {
                static { System.setProperty('wire.schema.executed', 'initialized'); throw new AssertionError('literal trap') }
                @Key @InputName('Service/ID') @JsonProperty('Service/ID') String serviceId
                String displayName
                @InputName('displayName') String literalName
                @InputIgnore @JsonIgnore(false) String hiddenValue
                @Builder.Method @JsonProperty('Configure/URL') @InputName('Configure/URL')
                void configureEndpoint(String value) { throw new AssertionError('operation executed') }
                @Builder.Method @InputIgnore void hiddenOperation(String value) { throw new AssertionError('ignored executed') }
                @InputName('unsupported') Thread unsupported
            }
        '''] + NAMING)
        def child = compileJar(['''
            package literalbinary
            import com.blackbuild.klum.ast.*
            @InputNaming(InputNaming.Profile.KEBAB_CASE) @DSL class Child extends Base {
                @InputName('URL/exact') String URLValue
                String urlValue
            }
        '''], [api])
        def binaries = isolated([api, child])

        when:
        def plan = CompiledBuilderSchemaReplacementProbe.read(Class.forName('literalbinary.Child_DSL$Builder', false, binaries),
                Class.forName('literalbinary.Child_DSL$Factory', false, binaries))

        then:
        plan.entries*.externalName as Set == ['display-name', 'displayName', 'URL/exact', 'url-value', 'Configure/URL'] as Set
        plan.entries.find { it.externalName == 'displayName' }.source == 'literalbinary.Base#literalName'
        plan.entries.find { it.externalName == 'Configure/URL' }.source.startsWith('literalbinary.Base#configureEndpoint(')
        plan.rejections.find { it.source == 'literalbinary.Base#hiddenValue' }.reason == 'excluded by @InputIgnore'
        plan.rejections.find { it.source.contains('#hiddenOperation(') }.reason == 'excluded by @InputIgnore'
        plan.rejections.any { it.source == 'literalbinary.Base#unsupported' }
        rawNamingFacts(api, 'literalbinary/Base_DSL$Builder.class').key.rename == 'Service/ID'
        !binaries.resolved.any { it.startsWith('com.blackbuild.klum.ast.compiler.') || it.startsWith('com.fasterxml.jackson.') }
        System.getProperty('wire.schema.executed') == null

        when: 'the independent positive control proves the static trap is live'
        Class.forName('literalbinary.Base', true, binaries)

        then:
        thrown(AssertionError)
        System.getProperty('wire.schema.executed') == 'initialized'

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    @Issue('959')
    def 'literal decoder rejects incomplete facts before initialization: #damage'() {
        given:
        def jar = compileJar([trapped('''
            package damagedliteral
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                @Key @InputName('Key/ID') String serviceId
                @InputName('exact/URL') String value
                @Builder.Method @InputIgnore void configure(String value) {}
            }
        ''')])
        def damaged = rewriteContract(jar, 'damagedliteral/Configuration_DSL$Builder.class', damage)
        def binaries = isolated([damaged])

        when:
        CompiledBuilderSchemaReplacementProbe.read(Class.forName('damagedliteral.Configuration_DSL$Builder', false, binaries),
                Class.forName('damagedliteral.Configuration_DSL$Factory', false, binaries))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('literal')
        failure.message.contains('damagedliteral.Configuration')
        failure.message.contains('recompile and package')
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        damage << ['literal schema version', 'literal schema future', 'literal schema type',
                   'literal Field origin', 'literal Field unknown', 'literal Field rename', 'literal Field ignored',
                   'literal Key origin', 'literal Key rename', 'literal Key ignored',
                   'literal Operation origin', 'literal Operation rename', 'literal Operation ignored',
                   'literal Operation inconsistent']
    }

    private static Map rawNamingFacts(File jar, String contract) {
        def result = [fields: [:], selections: [], key: [:], operations: [:], profile: [:]]
        new JarFile(jar).withCloseable { archive ->
            new ClassReader(archive.getInputStream(archive.getJarEntry(contract))).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    if (annotation != 'Lprofiletransport/Facts;') {
                        return null
                    }
                    new AnnotationVisitor(Opcodes.ASM9) {
                        @Override void visit(String member, Object value) { result.profile[member] = value }
                    }
                }
                @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    new MethodVisitor(Opcodes.ASM9) {
                        @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            def values = [:]
                            if (annotation.endsWith('GeneratedInput$Field;')) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override void visit(String member, Object value) { values[member] = value }
                                    @Override void visitEnd() { result.fields[values.value] = values }
                                }
                            }
                            if (annotation.endsWith('GeneratedInput$Key;')) {
                                values = result.key
                            } else if (annotation.endsWith('GeneratedInput$Selection;')) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override void visit(String member, Object value) {
                                        if (member == 'field') {
                                            result.selections.add(value)
                                        }
                                    }
                                }
                            } else if (annotation == 'Lcom/fasterxml/jackson/annotation/JsonProperty;') {
                                values = result.operations.computeIfAbsent(name) { [:] }
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override void visit(String member, Object value) {
                                        if (member == 'value') {
                                            values.rename = value
                                        }
                                    }
                                }
                            }
                            new AnnotationVisitor(Opcodes.ASM9) {
                                @Override void visit(String member, Object value) { values[member] = value }
                            }
                        }
                    }
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        }
        result
    }

    def 'compiled public contract matches every expected fact: #fixture'() {
        given:
        def jar = compileJar([trapped(source)] + NAMING)
        def binaries = isolated([jar])

        when:
        def actual = assertContract(binaries, fixture)

        then:
        actual.defaults == 'UNAVAILABLE_WITHOUT_EXECUTION'
        System.getProperty('wire.schema.executed') == null
        !binaries.resolved.any { it.endsWith('$WirePlan') || it.endsWith('$Builder') && !it.contains('_DSL') }

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        fixture              | source
        'admission'          | StructuredInputContractFixtures.ADMISSION
        'rejection'          | StructuredInputContractFixtures.REJECTIONS
        'numericboundary'    | StructuredInputContractFixtures.NUMERIC_REJECTIONS
        'shadowedpopulation' | StructuredInputContractFixtures.POPULATION
        'rejectedparent'     | '''
            package rejectedparent
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Base {
                @JsonProperty('shared') String first
                @JsonProperty('shared') String second
            }
            @DSL class Configuration extends Base { @JsonProperty('shared') String available }
        '''
        'unresolvedinput' | '''
            package unresolvedinput
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Builder
            @DSL class Base<T> { T value; @Builder.Method void consume(T input) {} }
            @DSL class Configuration extends Base<String> {}
        '''
    }

    def 'separate API and Schema artifacts preserve inherited facts after compiler output deletion'() {
        given:
        String source = StructuredInputContractFixtures.ADMISSION
        int split = source.indexOf('            @DSL class Child')
        String imports = source.substring(0, source.indexOf('            enum Region'))
        def api = compileJar([trapped(source.substring(0, split))] + NAMING)
        def schema = compileJar([trapped(imports + source.substring(split))], [api])
        def binaries = isolated([api, schema])

        when:
        def actual = assertContract(binaries, 'admission')

        then:
        actual.entries.find { it.operation == 'inheritedOperation' }.owner == 'admission.Base_DSL$Builder'
        SchemaReflectionProbe.enumNames(binaries, 'admission.Region') == ['EU', 'US']
        CompiledBuilderSchemaReplacementProbe.scope(Class.forName('admission.Parent_DSL$Factory', false, binaries))
                .any { it.name == 'One' && it.returns == 'admission.Parent' }
        System.getProperty('wire.schema.executed') == null

        when: 'the initialization control proves the trap is live'
        Class.forName('admission.Parent', true, binaries)

        then:
        thrown(AssertionError)
        System.getProperty('wire.schema.executed') == 'initialized'

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    @Issue('957')
    def 'raw binary child contracts normalize without admitting raw generic or abstract targets'() {
        given:
        def api = compileJar(['''
            package binaryowned
            import com.blackbuild.klum.ast.*
            @DSL class Facts { String host }
            @DSL class Workload { @Key String name; String image }
            @DSL class GenericFacts<T> { T value }
            @DSL abstract class AbstractFacts { String host }
        '''])
        def schema = compileJar(['''
            package binaryowned
            import com.blackbuild.klum.ast.*
            @DSL class Base { Facts inherited; Map<String, Workload> inheritedWorkloads }
            @DSL class Deployment extends Base {
                Facts facts
                Map<String, Workload> workloads
                LocalFacts local
                GenericFacts<String> genericFacts
                AbstractFacts abstractFacts
                @Builder.Method void consumeBuilder(Facts_DSL.Builder value) {}
            }
            @DSL class LocalFacts { String host }
        '''], [api])
        def binaries = isolated([api, schema])
        def builder = Class.forName('binaryowned.Deployment_DSL$Builder', false, binaries)

        when:
        def actual = CompiledBuilderSchemaReplacementProbe.read(builder,
                Class.forName('binaryowned.Deployment_DSL$Factory', false, binaries))

        then:
        builder.getMethod('getFacts').genericReturnType instanceof Class
        builder.getMethod('getLocal').genericReturnType instanceof ParameterizedType
        actual.entries.collectEntries { [(it.externalName): it.role] } == [
                facts: 'OWNED_OBJECT', inherited: 'OWNED_OBJECT', local: 'OWNED_OBJECT',
                workloads: 'KEYED_OWNED_MAP', inheritedWorkloads: 'KEYED_OWNED_MAP']
        actual.entries.findAll { it.role == 'KEYED_OWNED_MAP' }.every {
            it.payload.builder == 'binaryowned.Workload_DSL$Builder' && it.excludedChildKeyMember == 'name'
        }
        actual.rejections*.source as Set == ['binaryowned.Deployment#genericFacts',
                'binaryowned.Deployment#abstractFacts', 'binaryowned.Deployment#consumeBuilder(binaryowned.Facts_DSL$Builder):void'] as Set

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
        def actual = assertContract(binaries, 'safeannotations')
        def configure = actual.entries.find { it.operation == 'configure' }

        then:
        actual.documentation[configure.source].contains('Exact operation documentation.')
        configure.externalName == 'configure-input'
        configure.payload.item.item.type == 'java.lang.String'
        !actual.entries.any { it.operation in ['hidden', 'query'] }
        System.getProperty('wire.schema.executed') == null
        !binaries.resolved.contains('safeannotations.Policy')

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'fixed native keys child key exclusions LINK and keyMapping retain final contract facts'() {
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

        when:
        def actual = assertContract(binaries, 'fixedschema')
        def child = CompiledBuilderSchemaReplacementProbe.scope(Class.forName('fixedschema.Entry_DSL$Builder', false, binaries))
        def cluster = CompiledBuilderSchemaReplacementProbe.scope(Class.forName('fixedschema.Configuration_DSL$Builder$ClusterFactory_slots', false, binaries))

        then:
        actual.entries.find { it.externalName == 'main-slot' }.fixedChildKey == 'primary'
        actual.entries.find { it.externalName == 'entries' }.excludedChildKeyMember == 'name'
        child.any { it.name == 'getName' }
        !child.any { it.name == 'name' }
        cluster.any { it.name == 'primary' && it.returns.contains('Entry_DSL$Builder') }
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'field container #declaration preserves its exact generic shape and selected population signature'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.collection(declaration))])
        def binaries = isolated([jar])

        when:
        def actual = assertContract(binaries, 'collectioncontracts-' + declaration.tokenize('<')[0])

        then:
        actual.entries.first().operation == 'addValue'
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        declaration << ['List<String>', 'Set<String>', 'SortedSet<String>', 'NavigableSet<String>',
                        'EnumSet<Mode>', 'Map<String, String>', 'SortedMap<String, String>', 'NavigableMap<String, String>']
    }

    def 'unsupported whole-operation #target preserves exact rejection and independent field admission'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.operationArgument(target))])
        def binaries = isolated([jar])

        when:
        String variant = target == 'String[]' ? 'String-array' : target == 'int[]' ? 'int-array' : target.tokenize('<')[0]
        if (target == 'Map<Integer, Set<String>>') {
            variant += '-nested-set'
        }
        def actual = assertContract(binaries, 'restrictedarguments-' + variant)

        then:
        actual.rejections.size() == 1
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        target << ['Set<String>', 'SortedSet<String>', 'NavigableSet<String>', 'EnumSet<Mode>', 'Collection<String>',
                   'ArrayList<String>', 'LinkedHashMap<String, String>', 'TreeMap<String, String>', 'String[]', 'int[]',
                   'List', 'Map', 'Object', 'Map<Integer, Set<String>>']
    }

    def 'unsupported nested payload #target preserves compiler rejection precedence'() {
        given:
        String source = StructuredInputContractFixtures.NUMERIC_REJECTIONS.replace(
                '@Builder.Method void allocate(OpaqueNumber value) { budget = value }',
                "@Builder.Method void consume($target value) { }")
        def jar = compileJar([trapped(source)])
        def binaries = isolated([jar])

        when:
        String variant = target.tokenize('<')[0] + (target == 'Map<Integer, Set<String>>' ? '-container' : '')
        def actual = assertContract(binaries, 'numericboundary-consume-' + variant)

        then:
        actual.entries.empty
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        target << ['Set<OpaqueNumber>', 'Map<Integer, OpaqueNumber>', 'List<Set<OpaqueNumber>>',
                   'Map<String, SortedSet<OpaqueNumber>>', 'Map<Integer, Set<String>>']
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
        def binaries = isolated([jar])

        when:
        assertContract(binaries, 'invisiblefields')

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

    @Issue('959')
    def 'naming decoder rejects damaged binary facts before initialization: #damage'() {
        given:
        def jar = compileJar([trapped('''
            package damagednaming
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.InputNaming
            @InputNaming(InputNaming.Profile.KEBAB_CASE)
            @DSL class Configuration { String value }
        ''')])
        def damaged = rewriteContract(jar, 'damagednaming/Configuration_DSL$Builder.class', damage)
        def binaries = isolated([damaged])

        when:
        CompiledBuilderSchemaReplacementProbe.read(Class.forName('damagednaming.Configuration_DSL$Builder', false, binaries),
                Class.forName('damagednaming.Configuration_DSL$Factory', false, binaries))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(diagnostic)
        failure.message.contains('damagednaming.Configuration')
        failure.message.contains('recompile and package')
        System.getProperty('wire.schema.executed') == null

        when: 'the independent initialization control proves the producer trap is live'
        Class.forName('damagednaming.Configuration', true, binaries)

        then:
        thrown(AssertionError)
        System.getProperty('wire.schema.executed') == 'initialized'

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')

        where:
        damage                | diagnostic
        'naming version'      | 'naming metadata version 0'
        'naming future'       | 'naming metadata version 2'
        'naming explicit'     | 'naming metadata'
        'naming profile'      | 'naming metadata'
        'naming unknown'      | 'naming metadata'
        'naming inconsistent' | 'naming metadata'
        'naming duplicate'    | 'Duplicate schema member namingProfile'
        'naming type'         | 'Unknown schema member namingExplicit'
        'naming enum'         | 'Unexpected schema enum namingProfile'
        'naming native'       | 'Malformed UTF-16'
        'naming low'          | 'Malformed UTF-16'
        'naming broken pair'  | 'Malformed UTF-16'
    }

    @Issue('959')
    def 'selected profile requires declared absence provenance from a legacy binary ancestor'() {
        given:
        def api = compileJar([trapped('''
            package legacyprofile
            import com.blackbuild.klum.ast.DSL
            @DSL class Base { String inheritedName }
        ''')])
        def child = compileJar([trapped('''
            package legacyprofile
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.InputNaming
            @InputNaming(InputNaming.Profile.KEBAB_CASE)
            @DSL class Configuration extends Base { String localName }
        ''')], [api])
        def legacy = rewriteContract(api, 'legacyprofile/Base_DSL$Builder.class', 'naming legacy')
        def binaries = isolated([legacy, child])

        when:
        CompiledBuilderSchemaReplacementProbe.read(Class.forName('legacyprofile.Configuration_DSL$Builder', false, binaries),
                Class.forName('legacyprofile.Configuration_DSL$Factory', false, binaries))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Selected input profile requires naming metadata version 1')
        failure.message.contains('legacyprofile.Base')
        failure.message.contains('artifact')
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'directory contracts with unescaped spaces retain exact resource origins'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING)
        File directory = unpackContract(jar)
        URL directoryUrl = new URL('file:' + directory.absolutePath + '/')
        def urls = (runtimeFiles()*.toURI()*.toURL() + [directoryUrl]) as URL[]
        def binaries = new URLClassLoader(urls, ClassLoader.platformClassLoader)

        when:
        assertContract(binaries, 'admission')

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
                if (name == contractResource && resourceDamage == 'missing resource') {
                    return null
                }
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

    @Issue('959')
    def 'profile resolution deduplicates native candidates through a source-free diamond'() {
        given:
        def source = StructuredInputContractFixtures.ADMISSION.replace('import com.blackbuild.klum.ast.DSL',
                'import com.blackbuild.klum.ast.DSL\nimport com.blackbuild.klum.ast.InputNaming').replace('@DSL class Parent extends Base',
                '@InputNaming(InputNaming.Profile.KEBAB_CASE) @DSL class Parent extends Base')
        def jar = compileJar([trapped(source)] + NAMING)
        def diamond = rewriteContract(jar, 'admission/Parent_DSL$Builder.class', 'profile diamond')
        def binaries = isolated([diamond])

        when:
        def actual = CompiledBuilderSchemaReplacementProbe.read(Class.forName('admission.Parent_DSL$Builder', false, binaries),
                Class.forName('admission.Parent_DSL$Factory', false, binaries))

        then:
        actual.entries.count { it.externalName == 'inherited-label' } == 1
        actual.entries.count { it.externalName == 'inherited-operation' } == 1
        actual.entries*.externalName.contains('renamed')
        !actual.entries*.externalName.contains('secret')
        System.getProperty('wire.schema.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('wire.schema.executed')
    }

    def 'diamond parent paths resolve one declaring inventory without duplicate admission'() {
        given:
        def jar = compileJar([trapped(StructuredInputContractFixtures.ADMISSION)] + NAMING)
        def diamond = rewriteContract(jar, 'admission/Parent_DSL$Builder.class', 'diamond')
        def binaries = isolated([diamond])

        when:
        assertContract(binaries, 'admission')

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
                    rewriteEntry(input, archive, entry, contract, damage)
                }
                if (damage in ['diamond', 'profile diamond']) {
                    appendDiamond(archive, damage == 'profile diamond')
                }
            }
        }
        output
    }

    private static void appendDiamond(JarOutputStream archive, boolean profiled) {
        def writer = new ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                'admission/Diamond_DSL$Builder', '<SELF:Ladmission/Base;>Ljava/lang/Object;Ladmission/Base_DSL$Builder<TSELF;>;',
                'java/lang/Object', ['admission/Base_DSL$Builder'] as String[])
        def schema = writer.visitAnnotation('Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$Schema;', true)
        if (profiled) {
            schema.visit('namingVersion', 1)
            schema.visit('namingExplicit', false)
            schema.visit('namingProfile', 'IDENTITY')
        }
        schema.visitEnd()
        writer.visitEnd()
        archive.putNextEntry(new JarEntry('admission/Diamond_DSL$Builder.class'))
        archive.write(writer.toByteArray())
        archive.closeEntry()
    }

    private static void rewriteEntry(JarFile input, JarOutputStream archive, JarEntry entry, String contract, String damage) {
        byte[] bytes = input.getInputStream(entry).bytes
        if (entry.name == contract) {
            bytes = damageContract(bytes, damage)
        }
        archive.putNextEntry(new JarEntry(entry.name))
        archive.write(bytes)
        archive.closeEntry()
    }

    private static byte[] damageContract(byte[] bytes, String damage) {
        def writer = new ClassWriter(0)
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                if (damage in ['diamond', 'profile diamond']) {
                    interfaces = (interfaces.toList() + 'admission/Diamond_DSL$Builder') as String[]
                    signature += 'Ladmission/Diamond_DSL$Builder<TSELF;>;'
                }
                super.visit(version, access, name, signature, parent, interfaces)
            }

            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (damage == 'schema marker' && descriptor.endsWith('GeneratedInput$Schema;')) {
                    return null
                }
                def downstream = super.visitAnnotation(descriptor, visible)
                if (damage.startsWith('literal ') && descriptor.endsWith('GeneratedInput$Schema;')) {
                    return damageLiteral(downstream, descriptor, damage)
                }
                if (!damage.startsWith('naming ') || !descriptor.endsWith('GeneratedInput$Schema;')) {
                    return downstream
                }
                new AnnotationVisitor(Opcodes.ASM9, downstream) {
                    @Override void visit(String member, Object value) {
                        if (damage == 'naming legacy' && member.startsWith('naming')) {
                            return
                        }
                        if (damage == 'naming version' && member == 'namingVersion') {
                            return
                        }
                        if (damage == 'naming explicit' && member == 'namingExplicit') {
                            return
                        }
                        if (damage == 'naming profile' && member == 'namingProfile') {
                            return
                        }
                        if (damage == 'naming future' && member == 'namingVersion') {
                            value = 2
                        }
                        if (damage == 'naming unknown' && member == 'namingProfile') {
                            value = 'FUTURE'
                        }
                        if (damage == 'naming inconsistent' && member == 'namingExplicit') {
                            value = false
                        }
                        if (damage == 'naming type' && member == 'namingExplicit') {
                            value = 'true'
                        }
                        if (damage == 'naming enum' && member == 'namingProfile') {
                            super.visitEnum(member, 'Lunavailable/UserEnum;', 'KEBAB_CASE')
                            return
                        }
                        super.visit(member, value)
                    }
                    @Override void visitEnd() {
                        if (damage == 'naming duplicate') {
                            super.visit('namingProfile', 'IDENTITY')
                        }
                        super.visitEnd()
                    }
                }
            }

            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                damageMethod(super.visitMethod(access, name, descriptor, signature, exceptions), damage)
            }
        }, 0)
        writer.toByteArray()
    }

    private static MethodVisitor damageMethod(MethodVisitor visitor, String damage) {
        new MethodVisitor(Opcodes.ASM9, visitor) {
            @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                if (damage == 'selection' && annotation.endsWith('GeneratedInput$Selection;')) {
                    return null
                }
                damageAnnotation(super.visitAnnotation(annotation, visible), annotation, damage)
            }
        }
    }

    private static AnnotationVisitor damageLiteral(AnnotationVisitor visitor, String annotation, String damage) {
        def parts = damage.split(' ')
        boolean selected = annotation.endsWith('GeneratedInput$' + (parts[1] == 'schema' ? 'Schema' : parts[1]) + ';')
        if (!selected) {
            return visitor
        }
        new AnnotationVisitor(Opcodes.ASM9, visitor) {
            @Override void visit(String member, Object value) {
                if (member == 'literalVersion') {
                    if (parts[2] == 'version') { return }
                    value = parts[2] == 'future' ? 2 : '1'
                }
                if (member == 'nameOrigin' && parts[2] == 'origin'
                        || member == 'rename' && parts[2] == 'rename'
                        || member == 'ignored' && parts[2] == 'ignored') {
                    return
                }
                if (member == 'nameOrigin' && parts[2] == 'unknown') {
                    value = 'FUTURE'
                }
                if (member == 'ignored' && parts[2] == 'inconsistent') {
                    value = false
                }
                super.visit(member, value)
            }
        }
    }

    private static AnnotationVisitor damageAnnotation(AnnotationVisitor visitor, String annotation, String damage) {
        if (damage.startsWith('literal ')) {
            return damageLiteral(visitor, annotation, damage)
        }
        new AnnotationVisitor(Opcodes.ASM9, visitor) {
            @Override void visit(String member, Object value) {
                if (damage in ['naming native', 'naming low', 'naming broken pair'] && value == 'value'
                        && (annotation.endsWith('GeneratedInput$Field;') && member == 'value'
                        || annotation.endsWith('GeneratedInput$Selection;') && member == 'field')) {
                    value = damage == 'naming low' ? String.valueOf((char) 0xDC00) : String.valueOf((char) 0xD800)
                    if (damage == 'naming broken pair') {
                        value += 'x'
                    }
                }
                if (damage == 'source' && annotation.endsWith('GeneratedInput$Field;') && member == 'value') {
                    value = 'forged'
                }
                if (damage == 'role' && annotation.endsWith('GeneratedInput$Selection;') && member == 'role') {
                    value = 'OPERATION'
                }
                super.visit(member, value)
            }

            @Override void visitEnd() {
                if (annotation.endsWith('GeneratedInput$Field;')) {
                    if (damage == 'fixed property key') {
                        super.visit('fixedKey', true)
                    }
                    if (damage == 'exclusion') {
                        super.visit('exclusion', 'UNKNOWN')
                    }
                }
                super.visitEnd()
            }
        }
    }

    private static Map assertContract(ClassLoader binaries, String fixture) {
        def expected = CompiledInputExpectedContracts.contract(fixture)
        def actual = CompiledBuilderSchemaReplacementProbe.read(Class.forName(expected.builder, false, binaries),
                Class.forName(expected.model + '_DSL$Factory', false, binaries))
        assert actual.model == expected.model
        assert actual.builder == expected.builder
        assert actual.entries == expected.entries
        assert actual.rejections as Set == expected.rejections as Set
        assert actual.defaults == 'UNAVAILABLE_WITHOUT_EXECUTION'
        actual.entries.each { entry ->
            def scope = CompiledBuilderSchemaReplacementProbe.scope(Class.forName(entry.owner, false, binaries))
            String field = entry.source.substring(entry.source.indexOf('#') + 1)
            String name = entry.role == 'OPERATION' ? entry.operation : 'get' + field.substring(0, 1).toUpperCase(Locale.ROOT) + field.substring(1)
            def declaration = scope.find { it.owner == entry.owner && it.name == name &&
                    (entry.role == 'OPERATION' ? entry.descriptors.contains(it.name + '(' + it.parameters.collect { type -> type.replaceAll(/<.*>/, '') }.join(', ') + '):' + it.returns) : it.parameters.empty) }
            assert declaration != null
            assert actual.documentation[entry.source] == (declaration.documentation ?: null)
        }
        actual
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

    private static CompilationCustomizer profileTransportProbe() {
        new CompilationCustomizer(CompilePhase.INSTRUCTION_SELECTION) {
            @Override void call(SourceUnit source, GeneratorContext context, ClassNode type) {
                foldProfileProbeFacts(source, type)
            }
        }
    }

    private static void foldProfileProbeFacts(SourceUnit source, ClassNode type) {
        if (!type.annotations.any { it.classNode.name == 'com.blackbuild.klum.ast.DSL' }) {
            return
        }
        def builder = GeneratedDslSupport.of(type).builderInterface.redirect()
        def choice = type.annotations.find { it.classNode.name == 'profiletransport.Choice' }
        def factType = type.module.classes.find { it.name == 'profiletransport.Facts' }
        if (factType == null) {
            factType = ClassHelper.make(Class.forName('profiletransport.Facts', false, source.classLoader))
        }
        def fact = new AnnotationNode(factType)
        fact.addMember('version', new ConstantExpression(1))
        fact.addMember('explicit', new ConstantExpression(choice != null))
        fact.addMember('profile', new ConstantExpression(choice == null ? 'IDENTITY' : choice.getMember('value').value))
        fact.runtimeRetention = true
        builder.addAnnotation(fact)
    }

    private File compileJar(List<String> sources, List<File> dependencies = [], boolean profileProbe = false) {
        File directory = tempFolder.newFolder()
        File classes = new File(directory, 'classes')
        def configuration = new CompilerConfiguration(compilerConfiguration)
        configuration.targetDirectory = classes
        if (profileProbe) {
            configuration.addCompilationCustomizers(profileTransportProbe())
        }
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
                    assert !file.name.endsWith('$WirePlan.class')
                    archive.putNextEntry(new JarEntry(classes.toPath().relativize(file.toPath()).toString()))
                    archive.write(file.bytes)
                    archive.closeEntry()
                }
            }
        }
        assert classes.deleteDir()
        jar
    }

}
