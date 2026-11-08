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
package com.blackbuild.klum.ast.runtime.internal

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.Struct
import com.blackbuild.klum.ast.runtime.KlumModelException
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Issue('1055')
class StructRequiredTest extends AbstractDSLSpec {
    def 'presence marker leaves omitted construction defaults Templates and lifecycle unchanged: #marked'() {
        given:
        createClass """
            package presencecontrol
            @DSL class Service {
                static List<String> events = []
                ${marked ? '@Struct.Required' : ''} String initialized = 'local'
                ${marked ? '@Struct.Required' : ''} @Default(code={ 'computed' }) String defaulted
                ${marked ? '@Struct.Required' : ''} String recipe
                ${marked ? '@Struct.Required' : ''} boolean enabled
                ${marked ? '@Struct.Required' : ''} int count
                @Builder.Method void touch(String value) { events << value }
                @PostCreate void created() { events << 'create' }
                @PostApply void applied() { events << 'apply' }
                @PostTree void tree() { events << 'tree' }
            }
        """
        def binaries = binaries()
        def service = binaries.loadClass('presencecontrol.Service')

        when:
        def imported = service.Create.FromStruct([:])
        def importEvents = service.events.toList()
        service.events.clear()
        def ordinary = service.Create.With { }
        def ordinaryEvents = service.events.toList()
        service.events.clear()
        def applied = service.Create.With { applyStruct([:]) }
        def applyEvents = service.events.toList()
        service.events.clear()
        def template = service.Create.Template.FromStruct([recipe: 'template'])
        def templateEvents = service.events.toList()
        def recipient
        service.Template.With(template) { recipient = service.Create.FromStruct([:]) }

        then:
        [imported, ordinary, applied, recipient].every { it.initialized == 'local' && it.defaulted == 'computed' && !it.enabled && it.count == 0 }
        recipient.recipe == 'template'
        importEvents == ['create', 'apply', 'tree']
        ordinaryEvents == importEvents
        applyEvents == ['create', 'apply', 'apply', 'tree']
        templateEvents.empty
        service.events == importEvents

        when:
        def supplied = service.Create.FromStruct([initialized: '', enabled: false, count: 0])
        service.events.clear()
        service.Create.FromStruct([touch: 'before-null', initialized: null, recipe: 'unreached'], 'presence.yaml')

        then:
        supplied.initialized == ''
        !supplied.enabled
        supplied.count == 0
        def failure = thrown(KlumModelException)
        failure.message.contains('presence.yaml')
        failure.message.contains('/initialized')
        service.events == ['create', 'before-null']

        cleanup:
        binaries?.close()

        where:
        marked << [false, true]
    }

    def 'marker supports fields and domain annotations with runtime documentary retention'() {
        when:
        def marker = Class.forName(Struct.name + '$Required')

        then:
        marker.getAnnotation(Target).value().toList() == [ElementType.FIELD, ElementType.ANNOTATION_TYPE]
        marker.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        marker.isAnnotationPresent(Documented)
        marker.declaredMethods.length == 0
    }

    def 'marked field facts survive exact accessor association and immutable normalization'() {
        given:
        createClass '''
            package presence
            @DSL class Service {
                static { throw new AssertionError('must not initialize') }
                @Struct.Required String displayName
                @Struct.Required String eMail
                @Struct.Required String key
                @Builder.Method @Struct.Name('setter-exact') void setKey(String value) { key = value }
                String optional
                @Builder.Method void configure(String value) {}
            }
        '''
        def binaries = binaries()
        def type = Class.forName('presence.Service', false, binaries)
        def builder = Class.forName('presence.Service_DSL$Builder', false, binaries)

        when:
        def facts = new BuilderContractReader().metadata(builder)
        def schema = new InputContractResolver().read(type)

        then:
        facts.methods().find { signature, fact -> fact.field() == 'displayName' }.key.startsWith('getDisplayName(')
        facts.methods().find { signature, fact -> fact.field() == 'key' }.key.startsWith('setKey(')
        facts.methods().values().find { it.field() == 'displayName' }.rawInputRequired()
        schema.entries().displayName.rawInputRequired()
        schema.entries().eMail.rawInputRequired()
        schema.entries().key.rawInputRequired()
        schema.entries().displayName.source() == 'presence.Service#displayName'
        !schema.entries().optional.rawInputRequired()
        !schema.entries().configure.rawInputRequired()
        !schema.entries().get('setter-exact').rawInputRequired()

        when:
        schema.entries().clear()

        then:
        thrown(UnsupportedOperationException)

        cleanup:
        binaries?.close()
    }

    def 'direct and domain-contributed markers capture the same presence fact without runtime enforcement'() {
        given:
        createClass '''
            package contributedpresence
            import java.lang.annotation.*
            @DSL class Service {
                @Struct.Required String directName = 'fallback'
                @AuthorValue String domainName
                @NestedValue @Struct.Name('external-name') String nestedName
                @PropagatedValue String propagatedName
                @CycleA String optionalName
                @AuthorValue @Builder.Method void configure(String value) {}
            }
            @Struct.Required
            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.METHOD])
            @interface AuthorValue {}
            @AuthorValue
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface NestedValue {}
            @PropagateAnnotation(Struct.Required)
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface PropagatedValue {}
            @CycleB
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.ANNOTATION_TYPE])
            @interface CycleA {}
            @CycleA
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
            @interface CycleB {}
        '''
        def binaries = binaries()
        def type = Class.forName('contributedpresence.Service', false, binaries)
        def builder = Class.forName('contributedpresence.Service_DSL$Builder', false, binaries)

        when:
        def facts = new BuilderContractReader().metadata(builder)
        def schema = new InputContractResolver().read(type)
        def imported = type.Create.FromStruct([:])
        def configured = type.Create.With {}

        then:
        facts.methods().values().findAll { it.rawInputRequired() }*.field().toSet() ==
                ['directName', 'domainName', 'nestedName', 'propagatedName'] as Set
        schema.entries().findAll { name, entry -> entry.rawInputRequired() }.keySet() ==
                ['directName', 'domainName', 'external-name', 'propagatedName'] as Set
        schema.entries().get('external-name').source() == 'contributedpresence.Service#nestedName'
        !schema.entries().configure.rawInputRequired()
        !schema.entries().optionalName.rawInputRequired()
        imported.directName == configured.directName
        imported.directName == 'fallback'
        imported.domainName == null

        cleanup:
        binaries?.close()
    }

    def 'domain-contributed presence cannot override field exclusion'() {
        when:
        createClass '''
            import java.lang.annotation.*
            @DSL class Service { @AuthorValue @Struct.Ignore String value }
            @Struct.Required
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface AuthorValue {}
        '''

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Struct.Required')
        failure.message.contains('Service#value')
    }

    def 'separately compiled inherited presence survives descendant naming profiles'() {
        given:
        def parentDirectory = tempFolder.newFolder('parent')
        def configuration = new CompilerConfiguration(compilerConfiguration)
        configuration.targetDirectory = parentDirectory
        def parentLoader = new GroovyClassLoader(getClass().classLoader, configuration)
        def parent = new CompilationUnit(configuration, null, parentLoader)
        parent.addSource('Base.groovy', '''
            package inheritedpresence
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*
            @Struct.Required
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.ANNOTATION_TYPE])
            @interface AuthorValue {}
            @AuthorValue
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface NestedValue {}
            @DSL abstract class Base {
                static { throw new AssertionError('parent initialization') }
                @AuthorValue String displayName
                @NestedValue @Struct.Name('exact-name') String explicitName
                String optionalName
            }
        ''')
        parent.compile()
        loader.addClasspath(parentDirectory.absolutePath)
        def child = new CompilationUnit(compilerConfiguration, null, loader)
        child.addSource('Service.groovy', '''
            package inheritedpresence
            import com.blackbuild.klum.ast.*
            @DSL @Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)
            class Service extends Base { @NestedValue boolean publicAccess }
        ''')
        child.compile()
        def binaries = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL(),
                parentDirectory.toURI().toURL()] as URL[], getClass().classLoader)

        when:
        def schema = new InputContractResolver().read(Class.forName('inheritedpresence.Service', false, binaries))

        then:
        schema.entries().keySet() == ['display-name', 'exact-name', 'optional-name', 'public-access'] as Set
        schema.entries().findAll { name, entry -> entry.rawInputRequired() }.keySet() ==
                ['display-name', 'exact-name', 'public-access'] as Set
        schema.entries().get('display-name').source() == 'inheritedpresence.Base#displayName'
        schema.entries().get('display-name').contract().name == 'inheritedpresence.Base_DSL$Builder'

        cleanup:
        binaries?.close()
        parentLoader?.close()
    }

    def 'effective Jackson exclusion cannot carry presence'() {
        when:
        createClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonIgnore { boolean value() default true }
            @DSL class Service { @Struct.Required @JsonIgnore String value }
        '''

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Struct.Required')
        failure.message.contains('Service#value')
    }

    def 'rejects marker on a non-admitted declaration: #declaration'() {
        when:
        createClass("@DSL class Service { $declaration }")

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Struct.Required')
        failure.message.contains('Service#value')

        where:
        declaration << [
                '@Struct.Required @Struct.Ignore String value',
                '@Struct.Required @Owner Object value',
                '@Struct.Required transient String value',
                '@Struct.Required @Key String value',
                '@Struct.Required Object value',
                '@Struct.Required static String value',
                '@Struct.Required @Field(FieldType.IGNORED) String value'
        ]
    }

    def 'field marker cannot target a method parameter or type: #source'() {
        when:
        createClass(source)

        then:
        thrown(MultipleCompilationErrorsException)

        where:
        source << [
                '@DSL class Service { @Struct.Required @Builder.Method void configure(String value) {} }',
                '@DSL class Service { @Builder.Method void configure(@Struct.Required String value) {} }',
                '@Struct.Required @DSL class Service {}'
        ]
    }

    def 'optional byte fact normalizes absent and explicit false identically: #supplied'() {
        given:
        createClass '@DSL class Service { String value }'
        rewriteField('Service', 'value', supplied == 'absent' ? [] : [false])
        def binaries = binaries()

        when:
        def schema = new InputContractResolver().read(Class.forName('Service', false, binaries))

        then:
        !schema.entries().value.rawInputRequired()

        cleanup:
        binaries?.close()

        where:
        supplied << ['absent', 'false']
    }

    def 'malformed or contradictory supplied facts fail closed: #damage'() {
        given:
        createClass '''
            @DSL class Service {
                static { throw new AssertionError('must not initialize') }
                String value
                @Struct.Ignore String ignored
            }
        '''
        rewriteField('Service', damage == 'ignored' ? 'ignored' : 'value', values, damage)
        def binaries = binaries()

        when:
        new InputContractResolver().read(Class.forName('Service', false, binaries))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('rawInputRequired')
        failure.message.contains('Service')
        failure.message.contains('recompile')

        cleanup:
        binaries?.close()

        where:
        damage      | values
        'wrong type'| ['true']
        'duplicate' | [true, false]
        'ignored'   | [true]
        'excluded'  | [true]
        'wrong accessor' | [true]
    }

    def 'required field displaced by an authored operation rejects instead of marking the operation'() {
        given:
        createClass '''
            @DSL class Service {
                @Struct.Required @Struct.Name('endpoint') String value
                @Struct.Operation @Builder.Method void endpoint(String value) {}
            }
        '''
        def binaries = binaries()

        when:
        new InputContractResolver().read(Class.forName('Service', false, binaries))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('rawInputRequired')
        failure.message.contains('Service#value')
        failure.message.contains('selected field')

        cleanup:
        binaries?.close()
    }

    private URLClassLoader binaries() {
        new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
    }

    private void rewriteField(String type, String field, List<Object> values, String damage = '') {
        def contract = new File(compilerConfiguration.targetDirectory, type + '_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(contract.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                def target = super.visitMethod(access, damage == 'wrong accessor' && name == 'getValue' ? 'getOther' : name,
                        descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, target) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        def delegate = super.visitAnnotation(annotation, visible)
                        if (!annotation.endsWith('GeneratedInput$Field;')) {
                            return delegate
                        }
                        new AnnotationVisitor(Opcodes.ASM9, delegate) {
                            boolean selected
                            @Override void visit(String member, Object value) {
                                if (member == 'value') {
                                    selected = value == field
                                }
                                if (selected && member == 'rawInputRequired') {
                                    return
                                }
                                super.visit(member, value)
                            }
                            @Override void visitEnd() {
                                if (selected) {
                                    for (Object supplied : values) {
                                        super.visit('rawInputRequired', supplied)
                                    }
                                    if (damage == 'excluded') {
                                        super.visit('exclusion', 'NO_PAYLOAD')
                                    }
                                }
                                super.visitEnd()
                            }
                        }
                    }
                }
            }
        }, 0)
        contract.bytes = writer.toByteArray()
    }
}
