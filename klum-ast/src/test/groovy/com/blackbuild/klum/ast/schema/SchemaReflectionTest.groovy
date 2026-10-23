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
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.FieldVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchemaReplacementProbe
import spock.lang.Issue

import java.lang.module.ModuleFinder
import java.nio.file.Path
import spock.lang.Requires
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.JarFile

/** SCHEMA-R measures compiled public contracts; does not normalize or generate a schema. */
@Issue('529')
class SchemaReflectionTest extends AbstractDSLSpec {
    private static final String ROOT = 'schemar.schema.Application_DSL$Builder'
    private static final String DOC = AnnoDoc.name
    private static final String GENERATED = 'com.blackbuild.klum.ast.KlumGenerated'
    private static final String METHOD = 'com.blackbuild.klum.ast.Builder$Method'
    private static final String API = '''
        package schemar.api
        import com.blackbuild.klum.ast.DSL
        class Traps {
            static String hit(String point) {
                if (System.getProperty('schema.r.control') != null && System.getProperty('schema.r.control') != point) return 'provided'
                System.setProperty('schema.r.executed', point)
                throw new AssertionError('SCHEMA-R: ' + point)
            }
        }
        @DSL abstract class ApplicationApi {
            static { Traps.hit('API initialization') }
            /** Region from the binary API. */
            String region
        }
    '''
    private static final String SCHEMA = '''
        package schemar.schema
        import com.blackbuild.klum.ast.*
        import com.blackbuild.klum.ast.layer3.Cluster
        import com.blackbuild.klum.ast.runtime.KlumFactory
        import com.blackbuild.annodocimal.annotations.AnnoDoc
        import com.fasterxml.jackson.annotation.*
        import java.lang.annotation.*
        import schemar.api.ApplicationApi
        import schemar.api.Traps
        enum Mode { SAFE, FAST; static { Traps.hit('enum initialization') } }
        @Retention(RetentionPolicy.RUNTIME) @interface Fixed {}
        @DSL class Component {
            static { Traps.hit('child initialization') }
            @Key String name
            String value
        }
        @DSL class Application extends ApplicationApi {
            static { Traps.hit('Schema initialization') }
            @JsonProperty('service-name') String serviceName
            @JsonIgnore String secret
            @Field(FieldType.PROTECTED) String reserved
            @JsonIgnore(false) @JsonProperty('') String unchanged
            int port
            Mode mode
            /** Values use a custom population method. */
            @Field(members='addValue') NavigableSet<String> values
            List<String> tags
            Map<String, List<String>> labels
            Component child
            Map<String, Component> components
            List<Component> children
            @Field(FieldType.LINK) Component linked
            @JsonProperty('main-slot') @Fixed Component primary
            @Cluster(value=Fixed, fixedKeys=true) Map<String, Component> getSlots() {}
            @Field(converters=[{ String text -> Traps.hit('converter closure'); new URI(text) }]) URI uri
            @Default(code={ Traps.hit('default closure') }) String computed
            String supplied = Traps.hit('initializer')
            @Builder.Method void values(Iterable<String> input) { Traps.hit('plural override') }
            /** Configure the endpoint using a single data map.
             * @param settings literal data keys
             */
            @Builder.Method @JsonProperty('connect') void endpoint(Map<String, List<String>> settings) { Traps.hit('operation') }
            @Builder.Method @JsonIgnore void hidden(String value) { Traps.hit('ignored operation') }
            @Builder.Query String status() { Traps.hit('query') }
            @PostCreate void created() { Traps.hit('lifecycle') }
            static class Factory extends KlumFactory.Unkeyed<Application> {
                static { Traps.hit('factory initialization') }
                protected Factory() { super(Application); Traps.hit('factory constructor') }
            }
        }
    '''
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

    def 'isolated binary reflection exposes contracts docs and the precise provenance gaps'() {
        given:
        def artifacts = fixture()
        def binaries = isolated(artifacts)

        when:
        def root = SchemaReflectionProbe.inspect(binaries, ROOT)
        def factory = SchemaReflectionProbe.inspect(binaries, 'schemar.schema.Application_DSL$Factory')
        def child = SchemaReflectionProbe.inspect(binaries, 'schemar.schema.Component_DSL$Builder')
        def collection = SchemaReflectionProbe.inspect(binaries, ROOT + '$CollectionFactory_components')
        def cluster = SchemaReflectionProbe.inspect(binaries, ROOT + '$ClusterFactory_slots')
        def plan = carrier(artifacts.schema, 'schemar.schema.Application')
        def entries = plan.findAll { it[0] == 'entry' }.collectEntries { [(decode(it[1])): it] }
        def methods = root.methods
        new File(compilerConfiguration.targetDirectory, 'reflection.txt').text = [root, factory, child, binaries.resolved, plan].join('\n')

        then:
        collection.methods.any { it.return.contains('Component_DSL$Builder') }
        cluster.methods.any { it.name == 'primary' }
        root.bounds.contains('schemar.schema.Application')
        root.parents.any { it.contains('schemar.api.ApplicationApi_DSL$Builder') }
        factory.methods.any { it.name == 'One' && it.return == 'schemar.schema.Application' }
        methods.find { it.name == 'region' && it.parameters == ['java.lang.String'] }.owner == 'schemar.api.ApplicationApi_DSL$Builder'
        SchemaReflectionProbe.exactDoc(binaries, ROOT, 'region').contains('Region from the binary API.')
        methods.find { it.name == 'endpoint' }.annotations[DOC].value.contains('single data map')
        methods.find { it.name == 'endpoint' }.annotations[METHOD] != null
        methods.find { it.name == 'endpoint' }.annotations['com.fasterxml.jackson.annotation.JsonProperty'].value == 'connect'
        methods.find { it.name == 'endpoint' }.parameters == ['java.util.Map<java.lang.String, java.util.List<java.lang.String>>']
        !methods.find { it.name == 'addValue' && it.parameters == ['java.lang.String'] }.annotations.containsKey(GENERATED)
        root.annotations[GENERATED].tags.contains('dsl-support-role:Builder')
        methods.find { it.name == 'values' && it.parameters == ['java.lang.Iterable<java.lang.String>'] }.annotations.containsKey(METHOD)
        methods.find { it.name == 'child' && it.parameters.contains('groovy.lang.Closure') && it.return == 'schemar.schema.Component_DSL$Builder<schemar.schema.Component>' }
        methods.find { it.name == 'components' && it.parameters.any { it.contains('java.util.Map') } }
        methods.any { it.parameterAnnotations.flatten().contains('groovy.lang.DelegatesTo') }
        SchemaReflectionProbe.enumNames(binaries, 'schemar.schema.Mode') == ['SAFE', 'FAST']
        !methods.find { it.name == 'serviceName' && it.parameters == ['java.lang.String'] }.annotations.containsKey('com.fasterxml.jackson.annotation.JsonProperty')
        !child.methods.any { it.name == 'name' }
        entries['service-name'][7] == 'JSON_PROPERTY'
        entries['unchanged'][7] == 'IDENTITY'
        entries['values'][2] == 'PROPERTY'
        methods.find { it.name == 'getValues' }.return == 'java.util.NavigableSet<java.lang.String>'
        methods.find { it.name == 'getLabels' }.return == 'java.util.Map<java.lang.String, java.util.List<java.lang.String>>'
        !methods.find { it.name == 'computed' && it.parameters == ['java.lang.String'] }.annotations.containsKey('com.blackbuild.klum.ast.Default')
        !methods.find { it.name == 'getSupplied' }.annotations.containsKey('com.blackbuild.klum.ast.Default')
        decode(entries['values'][5]) == 'addValue'
        decode(entries['values'][6]) == methods.find { it.name == 'addValue' && it.parameters == ['java.lang.String'] }.descriptor
        decode(entries['connect'][6]) == methods.find { it.name == 'endpoint' }.descriptor
        entries['connect'][2] == 'OPERATION'
        decode(entries['components'][9]) == 'name'
        decode(entries['main-slot'][11]) == 'primary'
        plan.any { it[0] == 'rejection' && decode(it[1]).endsWith('#secret') }
        plan.any { it[0] == 'rejection' && decode(it[1]).endsWith('#children') }
        plan.any { it[0] == 'rejection' && decode(it[1]).endsWith('#reserved') && decode(it[2]).contains('absent') }
        !methods.any { it.name == 'reserved' }
        System.getProperty('schema.r.executed') == null
        !binaries.resolved.any { it.startsWith('schemar.') && it.contains('$') && !it.contains('_DSL$') }
        binaries.resolved.containsAll(['schemar.schema.Application', 'schemar.api.ApplicationApi', 'schemar.schema.Component', 'schemar.schema.Mode'])

        cleanup:
        binaries?.close()
        System.clearProperty('schema.r.executed')
    }

    def 'cross-artifact name collision is invisible without original field naming provenance'() {
        given:
        def artifacts = fixture('''
            package schemar.schema
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonProperty
            import schemar.api.ApplicationApi
            @DSL class Collision extends ApplicationApi {
                @JsonProperty('region') String local
            }
        ''')
        def binaries = isolated(artifacts)

        when:
        def root = SchemaReflectionProbe.inspect(binaries, 'schemar.schema.Collision_DSL$Builder')
        def childEntries = carrier(artifacts.schema, 'schemar.schema.Collision').findAll { it[0] == 'entry' }
        def parentEntries = carrier(artifacts.api, 'schemar.api.ApplicationApi').findAll { it[0] == 'entry' }

        then:
        childEntries.find { decode(it[1]) == 'region' }[7] == 'JSON_PROPERTY'
        parentEntries.find { decode(it[1]) == 'region' }[7] == 'IDENTITY'
        root.methods.any { it.name == 'region' }
        !root.methods.find { it.name == 'local' }.annotations.containsKey('com.fasterxml.jackson.annotation.JsonProperty')
        System.getProperty('schema.r.executed') == null

        cleanup:
        binaries?.close()
    }

    def 'missing dependency and annotation classes do not become empty metadata success'() {
        given:
        def artifacts = fixture()
        def missingApi = isolated(artifacts, 'schemar.api.ApplicationApi_DSL$Builder')
        def missingDoc = isolated(artifacts, DOC)

        when:
        SchemaReflectionProbe.inspect(missingApi, ROOT)

        then:
        def linkage = thrown(IllegalStateException)
        linkage.message.contains(ROOT)
        linkage.message.contains('complete resolved')
        System.getProperty('schema.r.executed') == null

        when: 'mandatory protocol classes are resolved explicitly rather than accepting silently dropped annotations'
        def partial = SchemaReflectionProbe.inspect(missingDoc, ROOT)
        SchemaReflectionProbe.exactDoc(missingDoc, ROOT, 'region')

        then:
        def missingProtocol = thrown(ClassNotFoundException)
        missingProtocol.message.contains('AnnoDoc annotation protocol dependency')
        !partial.annotations.containsKey(DOC)
        System.getProperty('schema.r.executed') == null

        cleanup:
        missingApi?.close()
        missingDoc?.close()
    }

    def 'separate loaders retain dependency versions and exact annotation identity'() {
        given:
        def first = fixture()
        def secondApi = compileJar('api-changed', [API.replace('Region from the binary API.', 'Updated binary documentation.')])
        def a = isolated(first)
        def b = isolated([api: secondApi, schema: first.schema])

        when:
        def left = Class.forName(ROOT, false, a)
        def right = Class.forName(ROOT, false, b)

        then:
        left != right
        !left.isAssignableFrom(right)
        SchemaReflectionProbe.exactDoc(a, ROOT, 'region').contains('Region from the binary API.')
        SchemaReflectionProbe.exactDoc(b, ROOT, 'region').contains('Updated binary documentation.')
        Class.forName(DOC, false, a) != AnnoDoc
        System.getProperty('schema.r.executed') == null

        when:
        SchemaReflectionProbe.requireRootIdentity(a, right)

        then:
        def mismatch = thrown(IllegalArgumentException)
        mismatch.message.contains(ROOT)
        mismatch.message.contains('reload the root through the inspected resolved classpath')
        System.getProperty('schema.r.executed') == null

        cleanup:
        a?.close()
        b?.close()
    }

    def 'positive initialization controls catch Schema API child enum and factory execution'() {
        given:
        def artifacts = fixture()

        when:
        def trapped = ['schemar.api.ApplicationApi', 'schemar.schema.Application', 'schemar.schema.Component',
                       'schemar.schema.Mode', 'schemar.schema.Application$Factory'].collect { name ->
            isolated(artifacts).withCloseable { binaries ->
                try {
                    Class.forName(name, true, binaries)
                    assert false: "Missing initialization trap for $name"
                } catch (ExceptionInInitializerError | AssertionError failure) {
                    assert System.getProperty('schema.r.executed') != null
                    assert (failure.cause ?: failure).message.contains('SCHEMA-R:')
                    System.clearProperty('schema.r.executed')
                    name
                }
            }
        }

        then:
        trapped.size() == 5
    }

    def 'execution controls independently reach constructors initializers lifecycle defaults and converters'() {
        given:
        def artifacts = fixture()

        when:
        def trapped = ['factory constructor', 'initializer', 'lifecycle', 'default closure', 'converter closure'].collect { target ->
            System.setProperty('schema.r.control', target)
            isolated(artifacts).withCloseable { binaries ->
                try {
                    SchemaReflectionProbe.constructControl(binaries, target == 'converter closure')
                    assert false: "Missing execution trap for $target"
                } catch (ReflectiveOperationException | AssertionError | ExceptionInInitializerError failure) {
                    assert System.getProperty('schema.r.executed') == target: (failure.cause ?: failure)
                    target
                } finally {
                    System.clearProperty('schema.r.executed')
                }
            }
        }

        then:
        trapped.size() == 5

        cleanup:
        System.clearProperty('schema.r.control')
        System.clearProperty('schema.r.executed')
    }

    def 'even selecting AnnoDoc parses unrelated enum-valued annotations and executes the user enum'() {
        given:
        def artifacts = fixture('''
            package schemar.schema
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.*
            import com.blackbuild.annodocimal.annotations.AnnoDoc
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.METHOD])
            @interface Policy { Mode value() }
            @DSL class Unsafe {
                /** Safe prose beside an unsafe annotation. */
                @Builder.Method @Policy(Mode.SAFE) void configure(String text) {}
            }
        ''')
        def binaries = isolated(artifacts)

        when:
        SchemaReflectionProbe.exactDoc(binaries, 'schemar.schema.Unsafe_DSL$Builder', 'configure')

        then:
        thrown(AssertionError)
        System.getProperty('schema.r.executed') == 'enum initialization'

        cleanup:
        binaries?.close()
        System.clearProperty('schema.r.executed')
    }

    @Requires(value = { !GroovySystem.version.startsWith('3.') }, reason = 'Named runtime requires org.apache.groovy; qualify this path in Groovy 4/5 lanes.')
    def 'public exported contracts and AnnoDoc are accessible in a named layer without opens or workaround flags'() {
        given:
        def artifacts = fixture('', true)
        def paths = (runtimeFiles() + [artifacts.api, artifacts.schema])*.toPath()
        def finder = ModuleFinder.of(paths as Path[])
        def configuration = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(), ['schemar.schema'] as Set)
        def layer = ModuleLayer.boot().defineModulesWithOneLoader(configuration, ClassLoader.platformClassLoader)
        def binaries = layer.findLoader('schemar.schema')

        when:
        def root = SchemaReflectionProbe.inspect(binaries, ROOT)
        def factory = SchemaReflectionProbe.inspect(binaries, 'schemar.schema.Application_DSL$Factory')
        def inheritedDoc = SchemaReflectionProbe.exactDoc(binaries, ROOT, 'region')
        def replacement = CompiledBuilderSchemaReplacementProbe.read(Class.forName(ROOT, false, binaries),
                Class.forName('schemar.schema.Application_DSL$Factory', false, binaries))

        then:
        replacement.builder == ROOT
        replacement.entries.any { it.externalName == 'service-name' }
        replacement.rejections.any { it.source.endsWith('#reserved') }
        root.module == 'schemar.schema'
        factory.module == 'schemar.schema'
        inheritedDoc.contains('Region from the binary API.')
        System.getProperty('schema.r.executed') == null
        !layer.findModule('schemar.schema').get().isOpen('schemar.schema')
    }

    def 'replacement preserves authored ignore on a shadowed public projection: #ignored'() {
        given:
        def source = SCHEMA.replace('@Builder.Method void values(Iterable<String> input)',
                "@Builder.Method @JsonIgnore($ignored) void values(Iterable<String> input)")
        def artifacts = fixture('', false, source)
        def binaries = isolated(artifacts)
        String sourceMember = 'schemar.schema.Application#values(java.lang.Iterable):void'
        def original = carrier(artifacts.schema, 'schemar.schema.Application').find { it[0] == 'rejection' && decode(it[1]) == sourceMember }
        assert original != null

        when:
        def replacement = CompiledBuilderSchemaReplacementProbe.read(Class.forName(ROOT, false, binaries),
                Class.forName('schemar.schema.Application_DSL$Factory', false, binaries))

        then:
        replacement.rejections.find { it.source == sourceMember }.reason == decode(original[2])
        System.getProperty('schema.r.executed') == null

        cleanup:
        binaries?.close()
        System.clearProperty('schema.r.executed')

        where:
        ignored << [true, false]
    }

    private Map fixture(String extra = '', boolean named = false, String schemaSource = SCHEMA) {
        File api = compileJar('api', [API], [], named ? 'schemar.api' : '')
        File schema = compileJar('schema', [schemaSource] + NAMING + (extra ? [extra] : []), [api], named ? 'schemar.schema' : '')
        [api: api, schema: schema]
    }

    private SchemaReflectionProbe.IsolatedLoader isolated(Map artifacts, String denied = '') {
        new SchemaReflectionProbe.IsolatedLoader((runtimeFiles() + [artifacts.api, artifacts.schema])*.toURI()*.toURL() as URL[], denied)
    }

    private static List<File> runtimeFiles() {
        [new File(System.getProperty('klumRuntimeJar')), new File(System.getProperty('klumAnnotationsJar')),
         new File(GroovyObject.protectionDomain.codeSource.location.toURI()), new File(AnnoDoc.protectionDomain.codeSource.location.toURI())]
    }

    private File compileJar(String name, List<String> sources, List<File> dependencies = [], String module = '') {
        File directory = tempFolder.newFolder(name + UUID.randomUUID())
        File classes = new File(directory, 'classes')
        def configuration = new CompilerConfiguration(compilerConfiguration)
        configuration.targetDirectory = classes
        def compiler = new GroovyClassLoader(getClass().classLoader, configuration)
        dependencies.each { compiler.addClasspath(it.path) }
        try {
            def unit = new CompilationUnit(configuration, null, compiler)
            sources.eachWithIndex { source, index -> unit.addSource("Fixture${index}.groovy", source) }
            unit.compile()
        } finally {
            compiler.close()
        }
        if (module) {
            File descriptor = new File(directory, 'module-info.java')
            String declarations = module == 'schemar.api' ? 'exports schemar.api;' :
                    'requires schemar.api; exports schemar.schema; exports com.fasterxml.jackson.annotation;'
            descriptor.text = """module $module {
                requires com.blackbuild.klum.ast.runtime;
                requires com.blackbuild.klum.ast.annotations;
                requires com.blackbuild.annodocimal.annotations;
                requires org.apache.groovy;
                $declarations
            }"""
            def process = new ProcessBuilder(new File(System.getProperty('java.home'), 'bin/javac').path,
                    '--release', '17', '--module-path', (runtimeFiles() + dependencies)*.path.join(File.pathSeparator),
                    '-d', classes.path, descriptor.path).redirectErrorStream(true).start()
            String diagnostics = process.inputStream.text
            assert process.waitFor() == 0: diagnostics
        }
        File jar = new File(directory, "${name}.jar")
        // Packaging compiler output is not discovery: the reflective consumer receives only explicit contract names.
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

    private static List<List<String>> carrier(File jar, String model) {
        String data
        new JarFile(jar).withCloseable { archive ->
            def bytes = archive.getInputStream(archive.getJarEntry(model.replace('.', '/') + '_DSL$WirePlan.class')).bytes
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if (name == 'DATA') data = value
                    null
                }
            }, ClassReader.SKIP_CODE)
        }
        data.readLines().collect { it.split('\\|', -1).toList() }
    }

    private static String decode(String token) {
        token == '-' ? null : new String(Base64.urlDecoder.decode(token), 'UTF-8')
    }
}
