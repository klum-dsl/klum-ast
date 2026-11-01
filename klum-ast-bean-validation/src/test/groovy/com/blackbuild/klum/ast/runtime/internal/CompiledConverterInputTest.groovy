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

import com.blackbuild.klum.ast.Struct
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
        facts.policy() == Struct.ConstraintProjection.Policy.PROJECT
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

    def 'normal and structured dispatch execute the recorded #language #route route inherited=#inherited'() {
        given:
        String source = """
            package converterinput;
            import jakarta.validation.constraints.Pattern;
            public class PortNumber {
                public final String raw;
                public PortNumber(@Pattern(regexp="[0-9]{1,8}") String raw) {
                    this.raw = raw;
                    System.setProperty("$TRAP", "constructor:" + raw);
                }
                public static PortNumber fromString(@Pattern(regexp="[0-9]{1,8}") String raw) {
                    return new PortNumber("fromString:" + raw);
                }
                public static PortNumber parse(@Pattern(regexp="[0-9]{1,8}") String raw) {
                    return new PortNumber("parse:" + raw);
                }
            }
        """
        File domain = language == 'Java' ? javaJar(source) : groovyJar(source, [])
        File schema = groovyJar("""
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                @Struct.Name('port-number')
                @Converters(includeConstructors=${route == 'constructor'},
                        includeMethods=['${route == 'constructor' ? 'noFactories' : route}'])
                PortNumber port
            }
        """, [domain])
        File descendant = inherited ? groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.DSL
            @DSL class Child extends Service { String label }
        ''', [schema, domain]) : null
        def loader = binaryLoader([descendant, schema, domain].findAll { it != null })
        Class<?> model = Class.forName(inherited ? 'converterinput.Child' : MODEL, false, loader)
        String expected = route == 'constructor' ? '1234' : route + ':1234'

        when:
        def entry = new InputContractResolver().read(model).entries()['port-number']
        def selected = entry.methods()[0].getDeclaredAnnotation(GeneratedInput.Converter)

        then:
        selected.owner() == DOMAIN
        selected.name() == (route == 'constructor' ? '<init>' : route)
        selected.descriptor() == (route == 'constructor' ? '(Ljava/lang/String;)V' : '(Ljava/lang/String;)Lconverterinput/PortNumber;')
        selected.parameter() == 0
        System.getProperty(TRAP) == null

        when: 'ordinary Builder dispatch uses the same String argument'
        def ordinary = model.Create.With { port '1234' }

        then:
        ordinary.port.raw == expected
        System.getProperty(TRAP) == 'constructor:' + expected

        when: 'structured construction must execute that same route, not a disabled peer or direct constructor'
        System.clearProperty(TRAP)
        def structured = KlumStructuredInput.readRoot(model, ['port-number': '1234'], 'ports.yaml')

        then:
        structured.port.raw == expected
        System.getProperty(TRAP) == 'constructor:' + expected

        cleanup:
        loader?.close()

        where:
        [language, route, inherited] << [['Java', 'Groovy'], ['constructor', 'fromString', 'parse'], [false, true]].combinations()
    }

    @Issue('952')
    def 'a mark on a colliding converter source cannot manufacture a public selection'() {
        given:
        File domain = javaJar("""
            package converterinput;
            import com.blackbuild.klum.ast.Struct;
            public class PortNumber {
                private PortNumber() {}
                public static PortNumber fromString(String raw) { throw new AssertionError("first converter"); }
                @Struct.Operation
                public static PortNumber parse(String raw) { throw new AssertionError("marked converter"); }
            }
        """)
        File schema = schemaJar(domain, 'fromString', false)
        def loader = binaryLoader([schema, domain])
        Class<?> model = Class.forName(MODEL, false, loader)

        when:
        def contract = new InputContractResolver().read(model)

        then:
        !contract.entries().containsKey('port-number')
        contract.rejections().any { it.reason().contains('unsupported converter selection') }

        when:
        KlumStructuredInput.readRoot(model, ['port-number': '1234'], 'collision.yaml')

        then:
        thrown(KlumModelException)

        cleanup:
        loader?.close()
    }

    def 'an authored binary descendant override rejects inherited converter input: kebab=#kebab ignored=#ignored grandchild=#grandchild'() {
        given:
        File domain = domainJar('Java', 'fromString', '@Pattern(regexp="[0-9]{1,8}")', false)
        File schema = schemaJar(domain, 'fromString', false)
        File descendant = groovyJar("""
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL ${kebab ? '@Struct.Naming(Struct.Naming.Profile.KEBAB_CASE)' : ''} class Child extends Service {
                @Builder.Method @Struct.Name('other') ${ignored ? '@Struct.Ignore' : ''}
                PortNumber port(String raw) {
                    System.setProperty("$TRAP", "authored:" + raw)
                    return null
                }
            }
        """, [schema, domain])
        File leaf = grandchild ? groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.DSL
            @DSL class Leaf extends Child { String label }
        ''', [descendant, schema, domain]) : null
        def loader = binaryLoader([leaf, descendant, schema, domain].findAll { it != null })
        Class<?> model = Class.forName(grandchild ? 'converterinput.Leaf' : 'converterinput.Child', false, loader)

        when:
        def contract = new InputContractResolver().read(model)

        then:
        !contract.entries().containsKey('port-number')
        contract.rejections().any { it.source().endsWith('Service#port') && it.reason().contains('overridden') }
        System.getProperty(TRAP) == null

        when:
        KlumStructuredInput.readRoot(model, ['port-number': '1234'], 'ports.yaml')

        then:
        def rejection = thrown(KlumModelException)
        rejection.message.contains('overridden')
        System.getProperty(TRAP) == null

        when: 'ordinary Builder override dispatch remains unchanged'
        model.Create.With { port '1234' }

        then:
        System.getProperty(TRAP) == 'authored:1234'

        cleanup:
        loader?.close()

        where:
        [kebab, ignored, grandchild] << [[false, true], [false, true], [false, true]].combinations()
    }

    def 'an excluded overridden converter does not collide with an inherited construction key'() {
        given:
        File domain = domainJar('Java', 'fromString', '', true)
        File schema = groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                @Key @Struct.Name('port-number') String id
                @Struct.Name('port-number') PortNumber port
            }
        ''', [domain])
        File descendant = groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL class Child extends Service {
                @Builder.Method @Struct.Ignore
                PortNumber port(String raw) { throw new AssertionError('authored override executed') }
            }
        ''', [schema, domain])
        def loader = binaryLoader([descendant, schema, domain])
        Class<?> model = Class.forName('converterinput.Child', false, loader)

        when:
        def contract = new InputContractResolver().read(model)
        def result = KlumStructuredInput.readRoot(model, ['port-number': 'service-id'], 'ports.yaml')

        then:
        !contract.entries().containsKey('port-number')
        contract.keyCollisions().empty
        result.id == 'service-id'
        result.port == null
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()
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
        String annotation = '@Pattern(regexp="[0-9]{1,8}") @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.' + policy + ')'
        File direct = domainJar('Groovy', route, annotation, true)
        File collected = domainJar('Groovy', route, '@Digits @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.' + policy + ')', true,
                '@Pattern(regexp="[0-9]{1,8}") @AnnotationCollector @interface Digits {}')

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
        if (count) {
            assert facts.rules()[0].origin() == origin
        }
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
        File domain = domainJar('Java', 'fromString', '@Pattern(regexp="[") @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.' + policy + ')', true)
        File schema = schemaJar(domain, 'fromString', true, ignored ? '@Struct.Ignore' : '')
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
                if (name == 'jakarta.validation.constraints.Pattern') {
                    throw new ClassNotFoundException('Pattern API denied')
                }
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
        File domain = domainJar('Java', 'fromString', '@Pattern(regexp="[0-9]{1,8}") @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.OMIT)', true)
        File schema = schemaJar(domain, 'fromString')
        if (mutation.startsWith('domain-')) {
            domain = rewrite(domain, 'converterinput/PortNumber.class', mutation)
        } else {
            schema = rewrite(schema, 'converterinput/Service_DSL$Builder.class', mutation)
        }
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
        'domain-pattern-duplicate' | 'Duplicate or non-runtime Pattern declaration'
        'domain-container-invisible' | 'Duplicate or non-runtime Pattern container'
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

    def 'known domain group-redefinition identity disables hard eligibility without resolving providers: #marker'() {
        given:
        File original = domainJar('Java', 'fromString', '@Pattern(regexp="[0-9]{1,8}")', true)
        File domain = rewrite(original, 'converterinput/PortNumber.class', marker)

        when:
        def facts = readFacts(domain, 'fromString')

        then:
        facts.rules().size() == 1
        facts.rules()[0].origin() == 'PARAMETER_UNSUPPORTED'
        !facts.rules()[0].eligible()
        System.getProperty(TRAP) == null

        where:
        marker << ['group-jakarta', 'group-javax', 'group-provider']
    }

    def 'Pattern container API denial fails before #policy suppression'() {
        given:
        File domain = domainJar('Java', 'fromString', '@Pattern(regexp="[0-9]{1,8}") @Pattern(regexp="[A-Z]{1,8}") @Struct.ConstraintProjection(Struct.ConstraintProjection.Policy.' + policy + ')', true)
        File schema = schemaJar(domain, 'fromString')
        def loader = new URLClassLoader([schema, domain]*.toURI()*.toURL() as URL[], getClass().classLoader) {
            @Override protected Class<?> loadClass(String name, boolean resolve) {
                if (name == 'jakarta.validation.constraints.Pattern$List') {
                    throw new ClassNotFoundException('container API denied')
                }
                super.loadClass(name, resolve)
            }
        }

        when:
        new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('container API denied')
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()

        where:
        policy << ['PROJECT', 'TEXT_ONLY', 'OMIT']
    }

    @Issue('952')
    def 'external adapter projection admits while an undiscovered inherited factory remains excluded: #external'() {
        given:
        File domain = groovyJar('''
            package converterinput
            class Parent {
                static PortNumber fromString(String raw) { throw new AssertionError('inherited converter') }
            }
            class PortNumber extends Parent { }
            class ExternalFactory {
                static PortNumber fromString(String raw) { throw new AssertionError('external converter') }
            }
        ''', [])
        File schema = groovyJar("""
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                ${external ? '@Converters([ExternalFactory])' : ''}
                PortNumber port
            }
        """, [domain])
        def loader = binaryLoader([schema, domain])

        when:
        def contract = new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        contract.entries().containsKey('port') == external
        contract.constraintProfiles().values().collectMany { it.converters().values() }.every { it.rules().isEmpty() }

        cleanup:
        loader?.close()

        where:
        external << [false, true]
    }

    def 'default-generated binary overloads are rejected'() {
        given:
        File domain = groovyJar('''
            package converterinput
            class PortNumber {
                static PortNumber fromString(String raw, int count = 1) { throw new AssertionError('converter') }
            }
        ''', [])
        File schema = schemaJar(domain, 'fromString')
        def loader = binaryLoader([schema, domain])

        when:
        def contract = new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        !contract.entries().containsKey('port-number')
        contract.rejections().any { it.source().endsWith('#port') }

        cleanup:
        loader?.close()
    }

    def 'synthetic or bridge binary routes are rejected: #mutation'() {
        given:
        File original = domainJar('Java', 'fromString', '', true)
        File domain = rewrite(original, 'converterinput/PortNumber.class', mutation)
        File schema = schemaJar(domain, 'fromString')
        def loader = binaryLoader([schema, domain])

        when:
        def contract = new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        !contract.entries().containsKey('port-number')
        contract.rejections().any { it.source().endsWith('#port') }
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()

        where:
        mutation << ['domain-synthetic', 'domain-bridge']
    }

    @Issue('952')
    def 'separately compiled external adapter uses the same String Builder projection at runtime'() {
        given:
        File domain = groovyJar('''
            package converterinput
            class PortNumber { String raw }
            class Adapter {
                static PortNumber fromString(String value) { new PortNumber(raw: 'adapter:' + value) }
            }
        ''', [])
        File schema = groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL class Service { @Converters([Adapter]) PortNumber port }
        ''', [domain])
        def loader = binaryLoader([schema, domain])
        def model = Class.forName(MODEL, false, loader)

        when:
        def result = KlumStructuredInput.readRoot(model, [port: '1234'], 'adapter.yaml')
        def direct = model.Create.With { port '1234' }
        def contract = new InputContractResolver().read(model)

        then:
        result.port.raw == 'adapter:1234'
        result.port.raw == direct.port.raw
        contract.entries().port.source() == MODEL + '#port'
        contract.constraintProfiles().values().collectMany { it.converters().values() }.every { it.rules().isEmpty() }
        !new File(domain.parentFile, 'classes').exists()
        !new File(schema.parentFile, 'classes').exists()

        cleanup:
        loader?.close()
    }

    @Issue('952')
    def 'same-compilation domain source admits its projected String without C1 facts'() {
        given:
        File schema = groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.*
            class PortNumber { PortNumber(String raw) {} }
            @DSL class Service { @Converters(includeConstructors=true) PortNumber port }
        ''', [])
        def loader = binaryLoader([schema])

        when:
        def contract = new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        contract.entries().port.payload().type() == String
        contract.constraintProfiles().values().collectMany { it.converters().values() }.every { it.rules().isEmpty() }

        cleanup:
        loader?.close()
    }

    @Issue('952')
    def 'unrelated authored overload does not remove the exact converter projection'() {
        given:
        File domain = domainJar('Java', 'fromString', '', true)
        File schema = groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                @Struct.Name('port-number') PortNumber port
                @Builder.Method void port(int raw) { throw new AssertionError('authored') }
            }
        ''', [domain])
        def loader = binaryLoader([schema, domain])

        when:
        def contract = new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        contract.entries()['port-number'].payload().type() == String
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()
    }

    def 'selected domain cannot fall back from missing or shadow resources: #damage profile=#profile'() {
        given:
        File domain = domainJar('Java', 'fromString', '', true)
        File schema = profile ? groovyJar('''
            package converterinput
            import com.blackbuild.klum.ast.*
            @DSL @Struct.Naming(Struct.Naming.Profile.KEBAB_CASE) class Service { PortNumber portNumber }
        ''', [domain]) : schemaJar(domain, 'fromString')
        File shadow = new File(temporary, 'shadow.class')
        shadow.bytes = [0, 0, 0, 0] as byte[]
        String selectedDamage = damage
        def loader = new URLClassLoader([schema, domain]*.toURI()*.toURL() as URL[], getClass().classLoader) {
            @Override protected Class<?> loadClass(String name, boolean resolve) {
                if (selectedDamage == 'missing' && name == DOMAIN) {
                    throw new ClassNotFoundException('domain denied')
                }
                super.loadClass(name, resolve)
            }
            @Override URL getResource(String name) {
                if (selectedDamage == 'shadow' && name == 'converterinput/PortNumber.class') {
                    return shadow.toURI().toURL()
                }
                super.getResource(name)
            }
        }

        when:
        new InputContractResolver().read(Class.forName(MODEL, false, loader))

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(damage == 'shadow' ? 'resource origin disagrees' : 'PortNumber')
        failure.message.contains(profile ? 'portNumber' : 'port')
        failure.message.contains("external input 'port-number'")
        failure.message.contains('Service_DSL$Builder')
        failure.message.contains('artifact')
        failure.message.contains('converterinput.PortNumber#fromString(Ljava/lang/String;)Lconverterinput/PortNumber;')
        failure.message.contains('parameter[0]')
        System.getProperty(TRAP) == null

        cleanup:
        loader?.close()

        where:
        [damage, profile] << [['missing', 'shadow'], [false, true]].combinations()
    }

    def 'deprecated public #route remains eligible without confusing its attribute with access flags'() {
        given:
        String executable = route == 'constructor'
                ? '@Deprecated public PortNumber(@Pattern(regexp="[0-9]{1,8}") String value) { throw new AssertionError("constructor"); }'
                : 'private PortNumber() {} @Deprecated public static PortNumber fromString(@Pattern(regexp="[0-9]{1,8}") String value) { throw new AssertionError("converter"); }'
        File domain = javaJar("""
            package converterinput;
            import jakarta.validation.constraints.Pattern;
            public class PortNumber { $executable }
        """)

        when:
        def facts = readFacts(domain, route)

        then:
        facts.rules()[0].eligible()
        facts.rules()[0].regexp() == '[0-9]{1,8}'

        where:
        route << ['constructor', 'fromString']
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
                    copyEntry(input, output, entry, resource, mutation)
                }
            }
        }
        target
    }

    private static void copyEntry(JarFile input, JarOutputStream output, JarEntry entry, String resource, String mutation) {
        byte[] bytes = input.getInputStream(entry).bytes
        if (entry.name == resource) {
            bytes = mutatedClass(bytes, mutation)
        }
        output.putNextEntry(new JarEntry(entry.name))
        output.write(bytes)
        output.closeEntry()
    }

    private static byte[] mutatedClass(byte[] bytes, String mutation) {
        if (mutation == 'domain-corrupt') {
            return [0, 0, 0, 0] as byte[]
        }
        def writer = new ClassWriter(0)
        new ClassReader(bytes).accept(new FixtureClassMutation(writer, mutation), 0)
        writer.toByteArray()
    }

    /** Class-level markers and executable access flags are independent of annotation member damage. */
    private static class FixtureClassMutation extends ClassVisitor {
        private final String mutation

        FixtureClassMutation(ClassVisitor delegate, String mutation) {
            super(Opcodes.ASM9, delegate)
            this.mutation = mutation
        }

        @Override void visitEnd() {
            addGroupMarker()
            super.visitEnd()
        }

        private void addGroupMarker() {
            def markers = ['group-jakarta': 'Ljakarta/validation/GroupSequence;',
                           'group-javax': 'Ljavax/validation/GroupSequence;',
                           'group-provider': 'Lorg/hibernate/validator/group/GroupSequenceProvider;']
            String descriptor = markers[mutation]
            if (descriptor == null) {
                return
            }
            def annotation = super.visitAnnotation(descriptor, true)
            if (mutation == 'group-provider') {
                // Deliberately unavailable: the opaque marker must not resolve or execute a provider.
                annotation.visit('value', Type.getType('Lconverterinput/UnavailableProvider;'))
            } else {
                def values = annotation.visitArray('value')
                values.visit(null, Type.getType('Lconverterinput/PortNumber;'))
                values.visitEnd()
            }
            annotation.visitEnd()
        }

        @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name == 'fromString') {
                access = mutatedAccess(access)
            }
            def method = super.visitMethod(access, name, descriptor, signature, exceptions)
            new FixtureMethodMutation(method, mutation)
        }

        private int mutatedAccess(int access) {
            switch (mutation) {
                case 'domain-synthetic': return access | Opcodes.ACC_SYNTHETIC
                case 'domain-bridge': return access | Opcodes.ACC_BRIDGE
                default: return access
            }
        }
    }

    /** Method annotations carry Builder associations; parameter annotations carry the domain constraints. */
    private static class FixtureMethodMutation extends MethodVisitor {
        private static final String PATTERN_DESCRIPTOR = 'Ljakarta/validation/constraints/Pattern;'
        private final String mutation

        FixtureMethodMutation(MethodVisitor delegate, String mutation) {
            super(Opcodes.ASM9, delegate)
            this.mutation = mutation
        }

        @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
            boolean converter = annotation.endsWith('GeneratedInput$Converter;')
            boolean selection = annotation.endsWith('GeneratedInput$Selection;')
            if (converter && mutation == 'association-strip' || selection && mutation == 'selection-strip') {
                return null
            }
            def original = super.visitAnnotation(annotation, visible)
            if (converter) {
                return new ConverterAssociationMutation(original, mutation)
            }
            if (annotation.endsWith('GeneratedInput$Field;') && mutation == 'completeness-strip') {
                return withoutConverterCount(original)
            }
            original
        }

        private static AnnotationVisitor withoutConverterCount(AnnotationVisitor original) {
            new AnnotationVisitor(Opcodes.ASM9, original) {
                @Override void visit(String key, Object value) {
                    if (key != 'converterCount') {
                        super.visit(key, value)
                    }
                }
            }
        }

        @Override AnnotationVisitor visitParameterAnnotation(int index, String annotation, boolean visible) {
            if (annotation == PATTERN_DESCRIPTOR) {
                if (mutation == 'domain-pattern-strip') {
                    return null
                }
                addExtraPatternAnnotations(index, visible)
            }
            def original = super.visitParameterAnnotation(index, annotation, visible)
            new ParameterMemberMutation(original, mutation, annotation == PATTERN_DESCRIPTOR)
        }

        private void addExtraPatternAnnotations(int index, boolean visible) {
            if (mutation == 'domain-pattern-duplicate') {
                def duplicate = super.visitParameterAnnotation(index, PATTERN_DESCRIPTOR, visible)
                duplicate.visit('regexp', '[0-9]{1,8}')
                duplicate.visitEnd()
            }
            if (mutation == 'domain-container-invisible') {
                def container = super.visitParameterAnnotation(index, 'Ljakarta/validation/constraints/Pattern$List;', false)
                container.visitArray('value').visitEnd()
                container.visitEnd()
            }
        }
    }

    private static class ConverterAssociationMutation extends AnnotationVisitor {
        private final String mutation

        ConverterAssociationMutation(AnnotationVisitor delegate, String mutation) {
            super(Opcodes.ASM9, delegate)
            this.mutation = mutation
        }

        @Override void visit(String key, Object value) {
            if (mutation == 'association-missing' && key == 'owner') {
                return
            }
            Object changed = mutatedValue(key, value)
            super.visit(key, changed)
            if (mutation == 'association-duplicate' && key == 'owner') {
                super.visit(key, changed)
            }
        }

        private Object mutatedValue(String key, Object value) {
            switch (mutation) {
                case 'association-owner': return key == 'owner' ? 'converterinput.Wrong' : value
                case 'association-descriptor': return key == 'descriptor' ? '(Ljava/lang/String;)V' : value
                case 'association-index': return key == 'parameter' ? 1 : value
                default: return value
            }
        }
    }

    private static class ParameterMemberMutation extends AnnotationVisitor {
        private final String mutation
        private final boolean pattern

        ParameterMemberMutation(AnnotationVisitor delegate, String mutation, boolean pattern) {
            super(Opcodes.ASM9, delegate)
            this.mutation = mutation
            this.pattern = pattern
        }

        @Override void visit(String key, Object value) {
            if (mutation == 'domain-regexp-strip' && key == 'regexp') {
                return
            }
            if (mutation == 'domain-regexp-type' && key == 'regexp') {
                value = 42
            }
            super.visit(key, value)
        }

        @Override void visitEnum(String key, String type, String value) {
            if (mutation == 'domain-policy') {
                value = 'INVALID'
            }
            super.visitEnum(key, type, value)
        }

        @Override void visitEnd() {
            if (pattern) {
                addInvalidPatternMembers()
            }
            super.visitEnd()
        }

        private void addInvalidPatternMembers() {
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
        String initialization = ''
        if (trap) {
            initialization = """
                static {
                    System.setProperty("$TRAP", "initialized");
                    if (Boolean.parseBoolean("true")) {
                        throw new IllegalStateException("domain initialized");
                    }
                }
            """
        }
        String source = """
            package converterinput;
            import jakarta.validation.constraints.Pattern;
            import jakarta.validation.Payload;
            import com.blackbuild.klum.ast.Struct;
            ${language == 'Groovy' ? 'import groovy.transform.AnnotationCollector;' : ''}
            $extra
            public class PortNumber implements Payload {
                public final String raw;
                $initialization
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
                @Struct.Examples(['1234'])
                @Deprecated
                @Struct.Name('port-number')
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
        String classpath = [Pattern, Struct.ConstraintProjection].collect { new File(artifact(it).toURI()).path }.join(File.pathSeparator)
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
