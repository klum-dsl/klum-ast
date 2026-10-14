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
package com.blackbuild.klum.ast

import com.blackbuild.annodocimal.generator.ProjectionPolicy
import com.blackbuild.annodocimal.generator.SourceProjector
import com.blackbuild.klum.ast.layer3.Cluster
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import javax.tools.ToolProvider


@Issue('890')
class AnnotationPropagationContractTest extends AbstractDSLSpec {
    def setup() {
        createSecondaryClass("""
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.layer3.*
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
            @PropagateAnnotation(Required)
            @interface Needed { String value() default 'domain message'; Validate.Level level() default Validate.Level.WARNING }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @PropagateAnnotation(Optional) @interface Ignored {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
            @PropagateAnnotation(Builder.Query) @interface ReadOnly {}
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
            @PropagateAnnotation(Cluster) @interface Group {}
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
            @PropagateAnnotation(AutoCreate) @interface Created {}
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD])
            @PropagateAnnotation(Cluster) @PropagateAnnotation(AutoCreate) @interface Managed {}
        """)
    }

    def 'Required and Optional lower once with defaults while direct customizations remain local'() {
        when:
        createSecondaryClass("""
            @Validate @DSL class Application {
                @Needed(value='not forwarded', level=Validate.Level.WARNING) Boolean required
                @Ignored String ignored
            }
            @Validate @DSL class Direct {
                @Required Boolean required
                @Optional String ignored
            }
            @DSL class Customized {
                @Required(value='custom message', level=Validate.Level.WARNING) String value
            }
            class Ordinary { @Required String required; @Optional String ignored; @Needed String inert }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        [Application, Direct, Ordinary].every { type ->
            type.getDeclaredField('required').getAnnotationsByType(Validate).size() == 1 &&
                    type.getDeclaredField('ignored').getAnnotationsByType(Validate).size() == 1 &&
                    type.getDeclaredField('ignored').getAnnotation(Validate).value() == Validate.Ignore
        }
        Application.getDeclaredField('required').getAnnotation(Validate).message() == ''
        Application.getDeclaredField('required').getAnnotation(Validate).level() == Validate.Level.ERROR
        Customized.getDeclaredField('value').getAnnotation(Validate).message() == 'custom message'
        Customized.getDeclaredField('value').getAnnotation(Validate).level() == Validate.Level.WARNING
        Customized.Create.One().value == null
        !Ordinary.getDeclaredField('inert').isAnnotationPresent(Required)
        Application.Create.With(required: false).required == Direct.Create.With(required: false).required

        when:
        Application.Create.One()

        then:
        def propagated = thrown(Exception)
        propagated.message.contains('required')

        when:
        Direct.Create.One()

        then:
        def direct = thrown(Exception)
        direct.class == propagated.class
        direct.message.contains('required')
    }

    def 'invalid #selected declarations retain the direct restriction: #member'() {
        when:
        createSecondaryClass("""
            import com.blackbuild.klum.ast.layer3.*
            @DSL abstract class Direct { ${member.replace('@Selected', '@' + selected)} }
        """)

        then:
        def direct = thrown(MultipleCompilationErrorsException)
        direct.message.contains(expected)

        when:
        createSecondaryClass("""
            import com.blackbuild.klum.ast.layer3.*
            @DSL abstract class Application { ${member.replace('@Selected', '@' + domain).replace('Direct summary', 'Application summary')} }
        """)

        then:
        def propagated = thrown(MultipleCompilationErrorsException)
        propagated.message.contains(expected)
        propagated.errorCollector.errorCount == direct.errorCollector.errorCount

        where:
        selected        | domain     | member                                                        | expected
        'Builder.Query' | 'ReadOnly' | '@Selected private String summary() { "x" }'                   | 'must be public'
        'Builder.Query' | 'ReadOnly' | '@Selected static String summary() { "x" }'                    | 'must be instance'
        'Builder.Query' | 'ReadOnly' | '@Selected void summary() {}'                                  | 'must return a value'
        'Builder.Query' | 'ReadOnly' | '@Selected abstract String summary()'                          | 'must declare an implementation'
        'Builder.Query' | 'ReadOnly' | '@Selected @Builder.Method String summary() { "x" }'           | 'mutually exclusive'
        'Builder.Query' | 'ReadOnly' | '@Selected @PostTree String summary() { "x" }'                  | 'lifecycle'
        'Builder.Query' | 'ReadOnly' | 'String name; @Selected String summary() { name = "changed" }' | 'must not'
        'Builder.Query' | 'ReadOnly' | '@Field(FieldType.BUILDER) String scratch; @Selected String summary() { scratch }' | 'Builder'
        'Builder.Query' | 'ReadOnly' | '@Selected Direct summary() { this }'                         | 'result must not contain'
        'Cluster'       | 'Group'    | '@Selected static Map<String, String> values'                  | 'must not be static'
        'Cluster'       | 'Group'    | '@Selected String getValues() { null }'                        | 'must return one of'
        'Cluster'       | 'Group'    | '@Selected Map getValues() { null }'                           | 'missing generics'
        'Cluster'       | 'Group'    | '@Selected Map<Integer, String> getValues() { null }'          | 'Map<String, x>'
        'Cluster'       | 'Group'    | '@Selected Map<String, ?> getValues() { null }'                | 'wildcard'
        'Cluster'       | 'Group'    | '@Selected Map<String, String> getValues(String x) { null }'   | 'parameterless'
        'Cluster'       | 'Group'    | '@Selected static Map<String, String> getValues() { null }'    | 'must not be static'
        'Cluster'       | 'Group'    | '@Selected Map<String, String> getValues() { [a:"b"] }'        | 'abstract, empty'
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Annotation-Propagation.md#inherited-and-generated-contracts')
    def 'abstract bases retain public Query and Cluster contracts through #binary inheritance'() {
        given:
        String bases = """
            import com.blackbuild.klum.ast.layer3.*
            @DSL class Service { String name }
            @DSL abstract class DirectBase {
                String name
                @Required Boolean enabled
                @AutoCreate Service primary
                @Cluster Map<String, Service> services
                @Cluster Map<String, Service> getSelected() { null }
                @Builder.Query String summary() { name.toUpperCase() }
            }
            @DSL abstract class ApplicationBase {
                String name
                @Needed Boolean enabled
                @Created Service primary
                @Group Map<String, Service> services
                @Group Map<String, Service> getSelected() { null }
                @ReadOnly String summary() { name.toUpperCase() }
            }
        """
        String children = """
            @DSL class Direct extends DirectBase { Service secondary }
            @DSL class Application extends ApplicationBase { Service secondary }
        """
        if (binary) {
            createSecondaryClass(bases)
            loader = new GroovyClassLoader(loader.parent, compilerConfiguration)
            loader.addClasspath(compilerConfiguration.targetDirectory.path)
            Thread.currentThread().contextClassLoader = loader
        }

        when:
        createSecondaryClass(binary ? children : bases + children)
        compileJavaConsumer()
        createSecondaryClass("""
            import groovy.transform.CompileStatic
            @CompileStatic class TypedConsumer {
                static String read(Application_DSL.Builder<Application> builder) { builder.summary() }
                static boolean accepts(Application_DSL.Factory factory, Object value) { factory.isModelOrBuilder(value) }
                static void configure(Application_DSL.Builder<Application> builder) {
                    builder.services { secondary { name 'typed' } }
                }
            }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        def consumer = loader.loadClass('TypedConsumer')
        def javaConsumer = loader.loadClass('JavaConsumer')
        def application = Application.Create.With {
            name 'orders'
            enabled false
            assert consumer.read(delegate) == 'ORDERS'
            assert javaConsumer.read(delegate) == 'ORDERS'
            name 'changed'
            assert summary() == 'CHANGED'
            consumer.configure(delegate)
        }
        application.summary() == 'CHANGED'
        application.primary != null
        application.services.secondary.name == 'typed'
        application.selected.secondary.is(application.secondary)
        consumer.accepts(Application.Create, application)
        javaConsumer.accepts(Application.Create, application)
        Application.Create.With {
            name 'java'
            enabled false
            services { javaConsumer.configure(delegate) }
        }.secondary.name == 'java'
        Direct.Create.With { name 'changed'; enabled false; services { secondary { name 'typed' } } }.summary() == application.summary()
        Application.getField('Create').type == loader.loadClass('Application_DSL$Factory')
        publicContracts('Application') == publicContracts('Direct')
        loader.loadClass('Application$Builder').getAnnotation(KlumGenerated).tags().contains(
                'dsl-support-interface:Application_DSL$Builder')
        loader.loadClass('Application$_Factory').getAnnotation(KlumGenerated).tags().contains(
                'dsl-support-interface:Application_DSL$Factory')
        loader.loadClass('Application_DSL$Builder').isAssignableFrom(loader.loadClass('Application$Builder'))
        loader.loadClass('Application_DSL$Factory').isAssignableFrom(loader.loadClass('Application$_Factory'))
        !loader.loadClass('Application_DSL').declaredClasses*.simpleName.any { it.contains('Impl') }
        !ApplicationBase.declaredFields*.name.contains('services')
        ApplicationBase.getDeclaredMethod('getServices').getAnnotationsByType(Cluster).size() == 1
        ApplicationBase.getDeclaredMethod('getServices').declaredAnnotations*.annotationType()*.simpleName.contains('Group')
        ApplicationBase.getDeclaredField('enabled').getAnnotationsByType(Validate).size() == 1
        mirror('Application') == mirror('Direct')
        mirror('ApplicationBase') == mirror('DirectBase')
        loader.loadClass('ApplicationBase_DSL$Builder').getDeclaredMethod('summary')
                .getAnnotationsByType(Builder.Query).size() == 1
        loader.loadClass('ApplicationBase$Builder').getDeclaredMethod('summary')
                .getAnnotationsByType(Builder.Query).size() == 1
        ApplicationBase.getDeclaredMethod('summary').getAnnotationsByType(Builder.Query).size() == 1

        where:
        binary << [false, true]
    }

    def 'overridden queries use their declaring Schema annotation (#binary)'() {
        given:
        String bases = """
            @DSL abstract class DirectBase { @Required String name; @Builder.Query String summary() { name } }
            @DSL abstract class ApplicationBase { @Needed String name; @ReadOnly String summary() { name } }
        """
        if (binary) {
            createSecondaryClass(bases)
            loader = new GroovyClassLoader(loader.parent, compilerConfiguration)
            loader.addClasspath(compilerConfiguration.targetDirectory.path)
            Thread.currentThread().contextClassLoader = loader
        }

        when:
        createSecondaryClass((binary ? '' : bases) + """
            @DSL class Direct extends DirectBase {
                @Override @Builder.Query String summary() { 'child' }
            }
            @DSL class Application extends ApplicationBase {
                @Override @ReadOnly String summary() { 'child' }
            }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        Application.Create.With(name: 'value').summary() == Direct.Create.With(name: 'value').summary()
        Application.Create.With { name 'value'; assert summary() == 'child' }.summary() == 'child'
        publicContracts('Application') == publicContracts('Direct')

        where:
        binary << [false, true]
    }

    def 'nested DSL eligibility stays independent of enclosing and sibling declarations'() {
        when:
        createSecondaryClass("""
            class Enclosing {
                @Needed String inert
                @DSL static class Application { @Needed String name; @ReadOnly String summary() { name } }
                @DSL static class Direct { @Required String name; @Builder.Query String summary() { name } }
                class Ordinary { @Needed boolean inert }
            }
            @DSL interface Contract { @Group Map<String, String> values = null }
            class Sibling { @ReadOnly void inert() {} }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        def application = loader.loadClass('Enclosing$Application')
        def direct = loader.loadClass('Enclosing$Direct')
        application.Create.With(name: 'nested').summary() == direct.Create.With(name: 'nested').summary()
        !Enclosing.getDeclaredField('inert').isAnnotationPresent(Required)
        !loader.loadClass('Enclosing$Ordinary').getDeclaredField('inert').isAnnotationPresent(Required)
        !Contract.getDeclaredField('values').isAnnotationPresent(Cluster)
        !Sibling.getDeclaredMethod('inert').isAnnotationPresent(Builder.Query)
    }

    def 'nonstatic inner DSL declarations retain conversion and public Query parity'() {
        when:
        createSecondaryClass("""
            class Enclosing {
                @DSL class Direct { @Required String name; @Builder.Query String summary() { name } }
                @DSL class Application { @Needed String name; @ReadOnly String summary() { name } }
            }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        loader.loadClass('Enclosing$Application').getDeclaredField('name').getAnnotationsByType(Validate).size() == 1
        publicContracts('Enclosing$Application') == publicContracts('Enclosing$Direct')
    }

    def 'field shadowing retains the ordinary hierarchy diagnostic (#binary)'() {
        given:
        String bases = """
            @DSL abstract class DirectBase { @Required String name }
            @DSL abstract class ApplicationBase { @Needed String name }
        """
        if (binary) {
            createSecondaryClass(bases)
            loader = new GroovyClassLoader(loader.parent, compilerConfiguration)
            loader.addClasspath(compilerConfiguration.targetDirectory.path)
            Thread.currentThread().contextClassLoader = loader
        }

        when:
        createSecondaryClass((binary ? '' : bases) + """
            @DSL class Direct extends DirectBase { @Optional String name }
            @DSL class Application extends ApplicationBase { @Ignored String name }
        """)

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("'Direct.name' shadows 'DirectBase.name'")
        error.message.contains("'Application.name' shadows 'ApplicationBase.name'")
        error.errorCollector.errorCount == 2

        where:
        binary << [false, true]
    }

    def 'non DSL subclasses retain the existing same-unit hierarchy diagnostic'() {
        when:
        createSecondaryClass("""
            @DSL abstract class Base { @Needed String name }
            class Ordinary extends Base { @Needed boolean inert }
        """)

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Non-DSL subclasses cannot extend DSL Objects')
        !error.message.contains('Effective annotation origin')
    }

    def 'Required boolean lowering preserves the existing direct behavior'() {
        when:
        createSecondaryClass("""
            @DSL class Direct { @Required boolean enabled }
            @DSL class Application { @Needed boolean enabled }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        [Direct, Application].every { it.getDeclaredField('enabled').getAnnotationsByType(Validate).size() == 1 }
        Application.Create.With(enabled: true).enabled == Direct.Create.With(enabled: true).enabled

        when: 'direct Validate still enforces its primitive boolean restriction'
        createSecondaryClass('@DSL class Invalid { @Validate boolean enabled }')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("Validation is not valid on 'boolean' fields")
    }

    private void compileJavaConsumer() {
        File source = new File(tempFolder.root, 'JavaConsumer.java')
        source.text = """
            import java.util.Map;
            public final class JavaConsumer {
                public static String read(Application_DSL.Builder<Application> builder) {
                    return builder.summary();
                }
                public static boolean accepts(Application_DSL.Factory factory, Object value) {
                    return factory.isModelOrBuilder(value);
                }
                public static void configure(
                        Application_DSL.Builder.ClusterFactory_services cluster) {
                    cluster.secondary(Map.of("name", "java"));
                }
            }
        """
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)
        assert ToolProvider.systemJavaCompiler.run(null, null, null, '--release', '17',
                '-classpath', classpath, '-d', compilerConfiguration.targetDirectory.absolutePath, source.path) == 0
        loader.addClasspath(compilerConfiguration.targetDirectory.path)
    }

    private Set<String> publicContracts(String schema) {
        Class namespace = loader.loadClass(schema + '_DSL')
        contractTypes(namespace).collectMany { type ->
            type.methods.findAll { !it.synthetic }.collect { method ->
                normalize(type.simpleName + ':' + method.toGenericString(), schema)
            }
        } as Set<String>
    }

    private static List<Class> contractTypes(Class type) {
        [type] + type.declaredClasses.collectMany { contractTypes(it) }
    }

    private String mirror(String schema) {
        File root = new File(compilerConfiguration.targetDirectory, 'mirrors-' + schema)
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(
                new File(compilerConfiguration.targetDirectory, schema + '_DSL.class').toPath(), root.toPath())
        File source = new File(root, schema + '_DSL.java')
        File classes = new File(root, 'compiled')
        classes.mkdirs()
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)
        assert ToolProvider.systemJavaCompiler.run(null, null, null, '--release', '17',
                '-classpath', classpath, '-d', classes.path, source.path) == 0
        if (schema == 'ApplicationBase') {
            assert source.text.count('@ReadOnly') == 1
        }
        // The retained domain is expected output. Compare the remaining contract, ignoring JavaPoet wrapping/prose.
        normalize(source.text, schema).replace('@ReadOnly', '')
                .replaceAll(/(?s)\/\*.*?\*\//, '').replaceAll(/\s+/, '')
    }

    private static String normalize(String value, String schema) {
        value.replace(schema.replace('Base', '') + 'Base', 'SchemaBase').replace(schema, 'Schema')
    }
}
