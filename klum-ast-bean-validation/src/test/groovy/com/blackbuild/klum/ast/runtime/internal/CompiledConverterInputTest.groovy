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

import com.blackbuild.klum.ast.ConstraintProjection
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.KlumModelException
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.Type
import groovy.transform.CompileStatic
import jakarta.validation.constraints.Pattern
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Issue
import spock.lang.See
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.TempDir

import java.lang.module.ModuleFinder
import java.nio.file.Path
import javax.tools.ToolProvider
import java.util.jar.JarFile
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@Issue('981')
class CompiledConverterInputTest extends Specification {
    private static final String DOMAIN = 'converterinput.PortNumber'
    private static final String MODEL = 'converterinput.Service'
    private static final String TRAP = 'klum.converter.981.executed'
    @TempDir File temporary
    private int serial

    def cleanup() { System.clearProperty(TRAP) }

    def 'exact paired #language #route selection survives binary inheritance without initialization'() {
        given:
        File domain = domainJar(language, route, '@Pattern(regexp="[0-9]{1,8}")', true)
        File schema = schemaJar(domain, route)
        File descendant = groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.DSL
            @DSL class Child extends Service { String label }
        ''', [schema, domain])
        def loader = binaryLoader([descendant, schema, domain])

        when:
        def model = Class.forName('converterinput.Child', false, loader)
        def contract = new InputContractResolver().read(model)
        def entry = contract.entries()['port-number']
        def facts = contract.constraintProfiles()[entry.contract()].converters()['port']
        def selected = entry.methods()[0].getDeclaredAnnotation(GeneratedInput.Converter)

        then:
        entry.payload().type() == String
        entry.methods().size() == 1
        selected.owner() == DOMAIN
        selected.name() == (route == 'constructor' ? '<init>' : route)
        selected.descriptor() == (route == 'constructor' ? '(Ljava/lang/String;)V' : '(Ljava/lang/String;)Lconverterinput/PortNumber;')
        selected.parameter() == 0
        facts.source() == DOMAIN + '#' + selected.name() + selected.descriptor() + ' parameter[0]'
        facts.artifact().contains(domain.parentFile.name)
        artifact(entry.contract()) == schema.toURI().toURL()
        facts.rules().size() == 1
        facts.rules()[0].regexp() == '[0-9]{1,8}'
        facts.rules()[0].origin() == 'PARAMETER'
        !facts.rules()[0].plain()
        facts.policy() == ConstraintProjection.Policy.PROJECT
        entry.documentation().contains('Port prose.')
        entry.examples() == ['1234']
        entry.sourceDeprecated()
        System.getProperty(TRAP) == null
        !new File(domain.parentFile, 'classes').exists()
        !new File(schema.parentFile, 'classes').exists()

        when: 'explicit initialization is a positive control outside inspection'
        Class.forName(DOMAIN, true, loader)

        then:
        thrown(ExceptionInInitializerError)
        System.getProperty(TRAP) == 'initialized'

        cleanup:
        loader?.close()

        where:
        [language, route] << [['Java', 'Groovy'], ['constructor', 'fromString', 'parse']].combinations()
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Converters.md#structured-string-input')
    def 'reads a separately compiled port converter without coercion or runtime Pattern enforcement'() {
        given:
        File domain = domainJar('Groovy', 'constructor', '@Pattern(regexp="[0-9]{1,8}")', false)
        File schema = schemaJar(domain, 'constructor', false)
        def loader = binaryLoader([schema, domain])
        Class<?> model = Class.forName(MODEL, false, loader)

        when:
        def port = KlumStructuredInput.readRoot(model, ['port-number': 'not digits'], 'ports.yaml')

        then:
        port.port.raw == 'not digits'

        when:
        def absent = KlumStructuredInput.readRoot(model, [:], 'ports.yaml')

        then:
        absent.port == null

        when:
        KlumStructuredInput.readRoot(model, ['port-number': 1234], 'ports.yaml')

        then:
        thrown(KlumModelException)

        when:
        KlumStructuredInput.readRoot(model, ['port-number': null], 'ports.yaml')

        then:
        thrown(KlumModelException)

        cleanup:
        loader?.close()
    }

    def 'effective collected parameter facts equal direct facts for #route and policy #policy'() {
        given:
        String annotation = '@Pattern(regexp="[0-9]{1,8}") @ConstraintProjection(ConstraintProjection.Policy.' + policy + ')'
        File direct = domainJar('Groovy', route, annotation, true)
        File collected = domainJar('Groovy', route, '@Digits @ConstraintProjection(ConstraintProjection.Policy.' + policy + ')', true,
                '@Pattern(regexp="[0-9]{1,8}") @groovy.transform.AnnotationCollector @interface Digits {}')

        when:
        def first = readFacts(direct, route)
        def second = readFacts(collected, route)

        then:
        first.rules() == second.rules()
        first.policy().name() == policy
        second.policy() == first.policy()
        System.getProperty(TRAP) == null

        where:
        [route, policy] << [['constructor', 'fromString'], ['PROJECT', 'TEXT_ONLY', 'OMIT']].combinations()
    }

    def 'complete unsupported Pattern facts remain distinct from absent: #annotation'() {
        given:
        File domain = domainJar('Java', 'fromString', annotation, true)

        when:
        def facts = readFacts(domain, 'fromString')

        then:
        facts.rules().size() == count
        if (count) assert facts.rules()[0].origin() == origin
        System.getProperty(TRAP) == null

        where:
        annotation                                                                 | count | origin
        ''                                                                         | 0     | null
        '@Pattern(regexp="[0-9]+")'                                                 | 1     | 'PARAMETER'
        '@Pattern(regexp="[0-9]{1,8}", flags=Pattern.Flag.CASE_INSENSITIVE)'          | 1     | 'PARAMETER'
        '@Pattern(regexp="[0-9]{1,8}", groups=PortNumber.class)'                     | 1     | 'PARAMETER_UNSUPPORTED'
        '@Pattern(regexp="[0-9]{1,8}", payload=PortNumber.class)'                    | 1     | 'PARAMETER_UNSUPPORTED'
        '@Pattern(regexp="[0-9]{1,8}") @Pattern(regexp="[A-Z]{1,8}")'                | 2     | 'PARAMETER_UNSUPPORTED'
    }

    def 'invalid regexp fails before #policy suppression and naming exclusion #ignored'() {
        given:
        File domain = domainJar('Java', 'fromString', '@Pattern(regexp="[") @ConstraintProjection(ConstraintProjection.Policy.' + policy + ')', true)
        File schema = schemaJar(domain, 'fromString', true, ignored ? '@InputIgnore' : '')
        def loader = binaryLoader([schema, domain])

        when:
        new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        def error = thrown(KlumModelException)
        error.message.contains('Unclosed character class')
        error.message.contains('parameter[0]')
        error.message.contains('artifact')
        error.message.contains('port-number') || ignored
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()

        where:
        [policy, ignored] << [['PROJECT', 'TEXT_ONLY', 'OMIT'], [false, true]].combinations()
    }

    def 'missing closed Pattern API fails even for a genuinely absent annotation'() {
        given:
        File domain = domainJar('Java', 'fromString', '', true)
        File schema = schemaJar(domain, 'fromString')
        def loader = new URLClassLoader([schema, domain]*.toURI()*.toURL() as URL[], getClass().classLoader) {
            @Override protected Class<?> loadClass(String name, boolean resolve) {
                if (name == 'jakarta.validation.constraints.Pattern') throw new ClassNotFoundException('Pattern API denied')
                super.loadClass(name, resolve)
            }
        }

        when:
        new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        def error = thrown(KlumModelException)
        error.message.contains('Pattern API denied')
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()
    }

    def 'selected exported domain signature is readable in a named module without opens'() {
        given:
        File original = domainJar('Java', 'fromString', '@Pattern(regexp="[0-9]{1,8}")', true)
        File classes = new File(temporary, 'named/classes')
        assert classes.mkdirs()
        new JarFile(original).withCloseable { jar ->
            jar.entries().each { item ->
                File target = new File(classes, item.name)
                target.parentFile.mkdirs()
                target.bytes = jar.getInputStream(item).bytes
            }
        }
        File descriptor = new File(classes.parentFile, 'module-info.java')
        descriptor.text = 'module converter.domain { requires jakarta.validation; exports converterinput; }'
        File api = new File(artifact(Pattern).toURI())
        assert ToolProvider.systemJavaCompiler.run(null, null, null, '--release', '17', '--module-path', api.path,
                '--patch-module', 'converter.domain=' + classes.path, '-d', classes.path, descriptor.path) == 0
        assert descriptor.delete()
        File domain = archive(classes)
        File schema = schemaJar(domain, 'fromString')
        def finder = ModuleFinder.of([domain.toPath(), api.toPath()] as Path[])
        def configuration = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(), ['converter.domain'] as Set)
        def layer = ModuleLayer.boot().defineModulesWithOneLoader(configuration, getClass().classLoader)
        def loader = new URLClassLoader([schema.toURI().toURL()] as URL[], layer.findLoader('converter.domain'))

        when:
        def contract = new InputContractResolver().read(Class.forName(MODEL, false, loader))
        def entry = contract.entries()['port-number']
        def facts = contract.constraintProfiles()[entry.contract()].converters()['port']

        then:
        facts.rules()[0].regexp() == '[0-9]{1,8}'
        moduleName(Class.forName(DOMAIN, false, loader)) == 'converter.domain'
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()
    }

    @CompileStatic
    private static String moduleName(Class<?> type) { type.module.name }

    def 'dynamic Groovy Class dispatch remains an intentional negative initialization control'() {
        given:
        File domain = domainJar('Java', 'fromString', '', true)
        def loader = binaryLoader([domain])
        Class<?> type = Class.forName(DOMAIN, false, loader)

        when:
        Throwable error = null
        try { type.getMethod('fromString', String) }
        catch (ExceptionInInitializerError caught) { error = caught }

        then:
        if (GroovySystem.version.startsWith('3.')) {
            assert error instanceof ExceptionInInitializerError
            assert System.getProperty(TRAP) == 'initialized'
        } else {
            assert error == null
            assert System.getProperty(TRAP) == null
        }

        cleanup:
        loader?.close()
    }

    def 'malformed paired facts fail closed before OMIT: #mutation'() {
        given:
        File domain = domainJar('Java', 'fromString', '@Pattern(regexp="[0-9]{1,8}") @ConstraintProjection(ConstraintProjection.Policy.OMIT)', true)
        File schema = schemaJar(domain, 'fromString')
        if (mutation.startsWith('domain-')) domain = rewrite(domain, 'converterinput/PortNumber.class', mutation)
        else schema = rewrite(schema, 'converterinput/Service_DSL$Builder.class', mutation)
        def loader = binaryLoader([schema, domain])

        when:
        new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(reason)
        failure.message.contains('recompile')
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()

        where:
        mutation                  | reason
        'association-strip'       | 'completeness mismatch'
        'association-owner'       | 'domain/index mismatch'
        'association-descriptor'  | 'descriptor/access/type mismatch'
        'association-index'       | 'malformed converter association'
        'association-missing'     | 'malformed converter association'
        'association-duplicate'   | 'duplicate converter fact'
        'completeness-strip'      | 'converter selection completeness'
        'selection-strip'         | 'lacks exact PROPERTY selection'
        'domain-regexp-strip'     | 'incomplete selected Pattern'
        'domain-regexp-type'      | 'incomplete selected Pattern'
        'domain-missing-group'    | 'converterinput.MissingGroup'
        'domain-invalid-flag'     | 'Invalid Pattern flag'
        'domain-policy'           | 'No enum constant'
        'domain-corrupt'          | 'compiled Builder input contract'
    }

    def 'stripped declaration Pattern has the same absence contract as a producer without one'() {
        given:
        File original = domainJar('Java', 'fromString', '@Pattern(regexp="[0-9]{1,8}")', true)
        File stripped = rewrite(original, 'converterinput/PortNumber.class', 'domain-pattern-strip')
        File absent = domainJar('Java', 'fromString', '', true)

        when:
        def first = readFacts(stripped, 'fromString')
        def second = readFacts(absent, 'fromString')

        then:
        first.rules().empty
        second.rules().empty
        System.getProperty(TRAP) == null
    }

    def 'rejects unsupported selection instead of accepting the first generated overload: #shape'() {
        given:
        File domain = javaJar("""
            package converterinput;
            public class PortNumber$generic {
                $constructors
                $methods
            }
        """)
        File schema = schemaJar(domain, route, true, fieldAnnotation)
        def loader = binaryLoader([schema, domain])

        when:
        def contract = new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        !contract.entries().containsKey('port-number')
        contract.rejections().any { it.source().endsWith('#port') }

        cleanup:
        loader?.close()

        where:
        shape               | generic | constructors                                      | methods                                                                                                                                    | route         | fieldAnnotation
        'two factories'     | ''      | 'private PortNumber() {}'                          | 'public static PortNumber fromString(String v) { return null; } public static PortNumber parse(String v) { return null; }'                 | 'fromString'  | ''
        'constructor pair'  | ''      | 'public PortNumber(String v) {}'                   | 'public static PortNumber fromString(String v) { return null; }'                                                                            | 'constructor' | ''
        'overloaded factory'| ''      | 'private PortNumber() {}'                          | 'public static PortNumber fromString(String v) { return null; } public static PortNumber fromString(int v) { return null; }'                | 'fromString'  | ''
        'overloaded ctor'   | ''      | 'public PortNumber(String v) {} public PortNumber(int v) {}' | ''                                                                                                                                 | 'constructor' | ''
        'generic domain'    | '<T>'   | 'public PortNumber(String v) {}'                   | ''                                                                                                                                         | 'constructor' | ''
        'generic method'    | ''      | 'private PortNumber() {}'                          | 'public static <T> PortNumber fromString(String v) { return null; }'                                                                        | 'fromString'  | ''
        'constructors off'  | ''      | 'public PortNumber(String v) {}'                   | ''                                                                                                                                         | 'fromString'  | ''
        'factory undiscovered' | ''   | 'private PortNumber() {}'                          | 'public static PortNumber decode(String v) { return null; }'                                                                                | 'discovery-defaults'  | ''
        'varargs'           | ''      | 'private PortNumber() {}'                          | 'public static PortNumber fromString(String... v) { return null; }'                                                                         | 'fromString'  | ''
        'closure collision' | ''      | 'public PortNumber(String v) {}'                   | ''                                                                                                                                         | 'constructor' | '@Field(converters=[{ String v -> new PortNumber(v) }])'
    }

    def 'authored Builder input competing with the field rejects converter admission'() {
        given:
        File domain = domainJar('Java', 'fromString', '', true)
        File schema = groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                @InputName('port-number') PortNumber port
                @Builder.Method void port(int raw) { throw new AssertionError('authored') }
            }
        ''', [domain])
        def loader = binaryLoader([schema, domain])

        when:
        def contract = new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        !contract.entries().containsKey('port-number')
        contract.rejections().any { it.source().endsWith('#port') && it.reason().contains('one supported public String route') }
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()
    }

    def 'selected domain cannot fall back from missing or shadow resources: #damage'() {
        given:
        File domain = domainJar('Java', 'fromString', '', true)
        File schema = schemaJar(domain, 'fromString')
        File shadow = new File(temporary, 'shadow.class')
        shadow.bytes = [0, 0, 0, 0] as byte[]
        String selectedDamage = damage
        def loader = new URLClassLoader([schema, domain]*.toURI()*.toURL() as URL[], getClass().classLoader) {
            @Override protected Class<?> loadClass(String name, boolean resolve) {
                if (selectedDamage == 'missing' && name == DOMAIN) throw new ClassNotFoundException('domain denied')
                super.loadClass(name, resolve)
            }
            @Override URL getResource(String name) {
                if (selectedDamage == 'shadow' && name == 'converterinput/PortNumber.class') return shadow.toURI().toURL()
                super.getResource(name)
            }
        }

        when:
        new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(damage == 'shadow' ? 'resource origin disagrees' : 'PortNumber')
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()

        where:
        damage << ['missing', 'shadow']
    }

    def 'an existing discovered annotation factory needs no preferred method name'() {
        given:
        File domain = javaJar("""
            package converterinput;
            import com.blackbuild.klum.ast.Converter;
            public class PortNumber {
                private PortNumber() {}
                @Converter public static PortNumber decode(String value) { throw new AssertionError("converter"); }
            }
        """)

        when:
        def facts = readFacts(domain, 'decode')

        then:
        facts.source().contains('#decode(Ljava/lang/String;)Lconverterinput/PortNumber;')
    }

    private File rewrite(File jar, String resource, String mutation) {
        File target = new File(temporary, "mutated-${serial++}.jar")
        new JarFile(jar).withCloseable { input ->
            new JarOutputStream(new FileOutputStream(target)).withCloseable { output ->
                input.entries().each { entry ->
                    byte[] bytes = input.getInputStream(entry).bytes
                    if (entry.name == resource) bytes = mutation == 'domain-corrupt' ? [0, 0, 0, 0] as byte[] : mutate(bytes, mutation)
                    output.putNextEntry(new JarEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        target
    }

    private static byte[] mutate(byte[] bytes, String mutation) {
        def writer = new ClassWriter(0)
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                def method = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, method) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (annotation.endsWith('GeneratedInput$Converter;') && mutation == 'association-strip') return null
                        if (annotation.endsWith('GeneratedInput$Selection;') && mutation == 'selection-strip') return null
                        def delegate = super.visitAnnotation(annotation, visible)
                        if (annotation.endsWith('GeneratedInput$Converter;')) {
                            return new AnnotationVisitor(Opcodes.ASM9, delegate) {
                                @Override void visit(String key, Object value) {
                                    if (mutation == 'association-missing' && key == 'owner') return
                                    if (mutation == 'association-owner' && key == 'owner') value = 'converterinput.Wrong'
                                    if (mutation == 'association-descriptor' && key == 'descriptor') value = '(Ljava/lang/String;)V'
                                    if (mutation == 'association-index' && key == 'parameter') value = 1
                                    super.visit(key, value)
                                    if (mutation == 'association-duplicate' && key == 'owner') super.visit(key, value)
                                }
                            }
                        }
                        if (annotation.endsWith('GeneratedInput$Field;') && mutation == 'completeness-strip') {
                            return new AnnotationVisitor(Opcodes.ASM9, delegate) {
                                @Override void visit(String key, Object value) { if (key != 'converterCount') super.visit(key, value) }
                            }
                        }
                        delegate
                    }
                    @Override AnnotationVisitor visitParameterAnnotation(int index, String annotation, boolean visible) {
                        if (annotation == 'Ljakarta/validation/constraints/Pattern;' && mutation == 'domain-pattern-strip') return null
                        def delegate = super.visitParameterAnnotation(index, annotation, visible)
                        new AnnotationVisitor(Opcodes.ASM9, delegate) {
                            @Override void visit(String key, Object value) {
                                if (mutation == 'domain-regexp-strip' && key == 'regexp') return
                                super.visit(key, mutation == 'domain-regexp-type' && key == 'regexp' ? 42 : value)
                            }
                            @Override void visitEnum(String key, String type, String value) {
                                super.visitEnum(key, type, mutation == 'domain-policy' ? 'INVALID' : value)
                            }
                            @Override void visitEnd() {
                                if (annotation == 'Ljakarta/validation/constraints/Pattern;') {
                                    if (mutation == 'domain-missing-group') {
                                        def groups = super.visitArray('groups')
                                        groups.visit(null, Type.getType('Lconverterinput/MissingGroup;'))
                                        groups.visitEnd()
                                    }
                                    if (mutation == 'domain-invalid-flag') {
                                        def flags = super.visitArray('flags')
                                        flags.visitEnum(null, 'Ljakarta/validation/constraints/Pattern$Flag;', 'INVALID')
                                        flags.visitEnd()
                                    }
                                }
                                super.visitEnd()
                            }
                        }
                    }
                }
            }
        }, 0)
        writer.toByteArray()
    }

    private CompiledBuilderSchema.ConverterConstraints readFacts(File domain, String route) {
        File schema = schemaJar(domain, route)
        binaryLoader([schema, domain]).withCloseable { loader ->
            def contract = new InputContractResolver().read(Class.forName(MODEL, false, loader))
            def entry = contract.entries()['port-number']
            contract.constraintProfiles()[entry.contract()].converters()['port']
        }
    }

    @CompileStatic
    private static URL artifact(Class<?> type) { type.protectionDomain.codeSource.location }

    private URLClassLoader binaryLoader(List<File> files) {
        new URLClassLoader(files*.toURI()*.toURL() as URL[], getClass().classLoader)
    }

    private File domainJar(String language, String route, String annotation, boolean trap, String extra = '') {
        String executable = route == 'constructor'
                ? "public PortNumber($annotation String raw) { this.raw = raw; }"
                : "public static PortNumber $route($annotation String raw) { return new PortNumber(raw); }"
        String constructor = route == 'constructor' ? '' : 'private PortNumber(String raw) { this.raw = raw; }'
        String source = """
            package converterinput;
            import jakarta.validation.constraints.Pattern;
            import jakarta.validation.Payload;
            import com.blackbuild.klum.ast.ConstraintProjection;
            $extra
            public class PortNumber implements Payload {
                public final String raw;
                ${trap ? 'static { System.setProperty("' + TRAP + '", "initialized"); if (Boolean.parseBoolean("true")) throw new IllegalStateException("domain initialized"); }' : ''}
                $constructor
                $executable
            }
        """
        language == 'Java' ? javaJar(source) : groovyJar(source, [])
    }

    private File schemaJar(File domain, String route, boolean trap = true, String fieldAnnotation = '') {
        groovyJar("""
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                ${trap ? 'static { throw new AssertionError("Schema initialized") }' : ''}
                /** Port prose.
                 * @example 1234
                 */
                @InputExamples(['1234'])
                @Deprecated
                @InputName('port-number')
                ${route == 'discovery-defaults' ? '' : '@Converters(includeConstructors=' + (route == 'constructor') + ')'}
                $fieldAnnotation
                PortNumber port
            }
        """, [domain])
    }

    private File javaJar(String source) {
        File directory = new File(temporary, "java-${serial++}")
        File classes = new File(directory, 'classes')
        assert classes.mkdirs()
        File input = new File(directory, 'PortNumber.java')
        input.text = source
        String classpath = [Pattern, ConstraintProjection].collect { new File(artifact(it).toURI()).path }.join(File.pathSeparator)
        assert ToolProvider.systemJavaCompiler.run(null, null, null, '--release', '17', '-classpath', classpath,
                '-d', classes.path, input.path) == 0
        assert input.delete()
        archive(classes)
    }

    private File groovyJar(String source, List<File> dependencies) {
        File directory = new File(temporary, "groovy-${serial++}")
        File classes = new File(directory, 'classes')
        def configuration = new CompilerConfiguration(targetDirectory: classes)
        configuration.optimizationOptions.groovydoc = true
        new GroovyClassLoader(getClass().classLoader, configuration).withCloseable { compiler ->
            dependencies.each { compiler.addClasspath(it.path) }
            def unit = new CompilationUnit(configuration, null, compiler)
            unit.addSource('Fixture.groovy', source)
            unit.compile()
        }
        archive(classes)
    }

    private static File archive(File classes) {
        File jar = new File(classes.parentFile, 'fixture.jar')
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { output ->
            classes.eachFileRecurse { file ->
                if (file.file) {
                    output.putNextEntry(new JarEntry(classes.toPath().relativize(file.toPath()).toString()))
                    output.write(file.bytes)
                    output.closeEntry()
                }
            }
        }
        assert classes.deleteDir()
        jar
    }
}
