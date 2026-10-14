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
import com.blackbuild.klum.cast.compiler.EffectiveDeclarationContributor
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.Tag
import spock.lang.See

import javax.tools.ToolProvider

/** Exercises the shipped contributor through ordinary Groovy compilation. */
@Issue('890')
class AnnotationPropagationTest extends AbstractDSLSpec {
    private File fixtureRoot

    def setup() {
        assert System.getProperty('java.specification.version') == '17'
        fixtureRoot = tempFolder.newFolder('compiled-fixture')
        def config = new CompilerConfiguration(compilerConfiguration)
        config.targetDirectory = fixtureRoot
        def bootstrap = new GroovyClassLoader(loader, config)
        bootstrap.parseClass(new File('src/test/fixtures/annotation-propagation/ForeignTransform.groovy'))
        bootstrap.close()
        loader.addClasspath(fixtureRoot.path)
        assert ServiceLoader.load(EffectiveDeclarationContributor, loader)*.class*.simpleName == ['AnnotationPropagationContributor']
    }

    private void domain(String selected, String name = 'Domain') {
        createSecondaryClass("""
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.layer3.*
            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.FIELD, ElementType.METHOD, ElementType.TYPE])
            @PropagateAnnotation($selected) @interface $name {}
        """)
    }

    def 'Required is collected once and retains ordinary validation defaults'() {
        given:
        domain('Required')

        when:
        createSecondaryClass('''
            @DSL class Direct { @Required String name; @Optional String other }
            @DSL class Customized { @Required(value='direct message', level=Validate.Level.WARNING) String name }
            class Ordinary { @Required String name; @Domain String inert }
            @DSL class Application { @Domain String name; @Optional String other }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
        [Direct, Application].every { type ->
            def annotations = type.getDeclaredField('name').getAnnotationsByType(Validate)
            annotations.size() == 1 && annotations[0].message() == '' && annotations[0].level() == Validate.Level.ERROR &&
                    type.getDeclaredField('other').getAnnotationsByType(Validate).size() == 1
        }
        Customized.getDeclaredField('name').getAnnotation(Validate).message() == 'direct message'
        Customized.getDeclaredField('name').getAnnotation(Validate).level() == Validate.Level.WARNING
        Ordinary.getDeclaredField('name').getAnnotationsByType(Validate).size() == 1
        !Ordinary.getDeclaredField('inert').isAnnotationPresent(Required)
        Application.Create.With { name 'valid' }.name == Direct.Create.With { name 'valid' }.name

        when:
        Application.Create.One()

        then:
        def contributed = thrown(Exception)
        contributed.message.contains('name')

        when:
        Direct.Create.One()

        then:
        def direct = thrown(Exception)
        direct.class == contributed.class
        direct.message.contains('name')
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Annotation-Propagation.md#default-only-composition')
    def 'AutoCreate keeps retained DefaultValues members and normal lifecycle behavior'() {
        given:
        createSecondaryClass('''
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import com.blackbuild.klum.ast.layer3.DefaultValues
            @DefaultValues @PropagateAnnotation(AutoCreate)
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Configured { String name() default '' }
            @DefaultValues
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface DirectDefaults { String name() default '' }
        ''')

        when:
        createSecondaryClass('''
            import com.blackbuild.klum.ast.layer3.AutoCreate
            @DSL class Service { String name }
            @DSL class Direct { @AutoCreate @DirectDefaults(name='orders') Service child }
            @DSL class Application { @Configured(name='orders') Service child }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
        Application.Create.One().child.name == Direct.Create.One().child.name
        Application.Create.One().child.name == 'orders'
        Application.Create.With { child { name 'supplied' } }.child.name == 'supplied'
        Application.getDeclaredField('child').declaredAnnotations*.annotationType()*.simpleName.containsAll(['Configured', 'AutoCreate'])
    }

    def 'Builder Query keeps construction and model behavior public signatures and source mirrors'() {
        given:
        domain('Builder.Query')

        when:
        createSecondaryClass('''
            @DSL class Direct {
                String name
                @Builder.Query String summary() { name.toUpperCase() }
            }
            @DSL class Application {
                String name
                @Domain String summary() { name.toUpperCase() }
            }
        ''')
        createSecondaryClass('''
            import groovy.transform.CompileStatic
            @CompileStatic class TypedConsumer {
                static String read(Application_DSL.Builder<Application> builder) { builder.summary() }
                static boolean accepts(Application_DSL.Factory factory, Object value) { factory.isModelOrBuilder(value) }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
        def typedConsumer = loader.loadClass('TypedConsumer')
        Application.Create.With {
            name 'orders'
            assert summary() == 'ORDERS'
            assert typedConsumer.read(delegate) == 'ORDERS'
        }.summary() == Direct.Create.With { name 'orders'; assert summary() == 'ORDERS' }.summary()
        signatures(loader.loadClass('Application_DSL$Builder'), 'Application') == signatures(loader.loadClass('Direct_DSL$Builder'), 'Direct')
        signatures(loader.loadClass('Application_DSL$Factory'), 'Application') == signatures(loader.loadClass('Direct_DSL$Factory'), 'Direct')
        typedConsumer.accepts(Application.Create, Application.Create.One())
        mirror('Application').contains('String summary()')
        mirror('Direct').contains('String summary()')
    }

    def 'Cluster field conversion method projection and cluster factories match direct controls'() {
        given:
        domain('Cluster')

        when:
        createSecondaryClass('''
            import com.blackbuild.klum.ast.layer3.Cluster
            @DSL class Service { String value }
            @DSL class Direct {
                Service first
                @Cluster Map<String, Service> services
                @Cluster Map<String, Service> getSelected() { null }
            }
            @DSL class Application {
                Service first
                @Domain Map<String, Service> services
                @Domain Map<String, Service> getSelected() { null }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
        Application.Create.With { services { first { value 'one' } } }.services.first.value == 'one'
        Application.Create.With { selected { first { value 'two' } } }.selected.first.value == 'two'
        Direct.Create.With { services { first { value 'one' } } }.selected.first.value == 'one'
        !Application.declaredFields*.name.contains('services')
        Application.getDeclaredMethod('getServices').isAnnotationPresent(Cluster)
        signatures(loader.loadClass('Application_DSL$Builder'), 'Application') == signatures(loader.loadClass('Direct_DSL$Builder'), 'Direct')
        signatures(loader.loadClass('Application_DSL$Factory'), 'Application') == signatures(loader.loadClass('Direct_DSL$Factory'), 'Direct')
        mirror('Application').contains('services(')
        mirror('Application').contains('selected(')
    }

    def 'separately compiled foreign local transform runs once for fields methods and classes'() {
        given:
        domain('ForeignTrigger')

        when:
        createSecondaryClass('''
            @DSL @Domain class Application {
                @Domain String field
                @Domain String operation() { 'ordinary' }
            }
            @DSL @ForeignTrigger class Direct {
                @ForeignTrigger String field
                @ForeignTrigger String operation() { 'ordinary' }
            }
            class Ordinary { @Domain String copied; @ForeignTrigger String direct }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
        def model = Application.Create.One()
        model.foreign_Application() == 'generated'
        model.foreign_field() == 'generated'
        model.foreign_operation() == 'generated'
        Direct.Create.One().foreign_field() == 'generated'
        Ordinary.newInstance().foreign_direct() == 'generated'
        !Ordinary.declaredMethods*.name.contains('foreign_copied')
    }

    def 'KlumCast validates introduced and existing annotations with origin and no manual engine'() {
        given:
        domain('Default', 'DomainDefault')
        domain('Field', 'DomainField')

        when:
        createSecondaryClass('@DSL class InvalidDefault { @DomainDefault String value }')

        then:
        def effective = thrown(MultipleCompilationErrorsException)
        effective.message.contains('Effective annotation origin: @DomainDefault requests @com.blackbuild.klum.ast.Default')
        effective.message.contains('field') && effective.message.contains('delegate')

        when:
        createSecondaryClass('@DSL class InvalidCoannotation { @Key @DomainField String value }')

        then:
        def conflict = thrown(MultipleCompilationErrorsException)
        conflict.message.contains('Key') && conflict.message.contains('Field')
        conflict.message.contains('Effective declaration context: @DomainField requests @com.blackbuild.klum.ast.Field')
        !conflict.message.contains('Effective annotation origin:')
        conflict.errorCollector.errorCount == 1

        when:
        createSecondaryClass('@DSL class DirectInvalid { @Key @Field String value }')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def 'Java container and Groovy repeated requests work through binary inheritance and inert controls'() {
        given:
        compileJavaDomain()
        createSecondaryClass('''
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.layer3.AutoCreate
            @PropagateAnnotation(AutoCreate) @PropagateAnnotation(Required)
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface GroovyRequests {}
        ''')
        createSecondaryClass('''
            @DSL class Service { String name }
            @DSL abstract class Base { @JavaRequests Service inherited }
        ''')
        // Consume the emitted parent in a fresh loader: no in-memory source AST inheritance.
        loader = new GroovyClassLoader(loader.parent, compilerConfiguration)
        loader.addClasspath(fixtureRoot.path)
        loader.addClasspath(compilerConfiguration.targetDirectory.path)
        Thread.currentThread().contextClassLoader = loader

        when:
        createSecondaryClass('''
            @DSL class Application extends Base { @GroovyRequests Service local }
            class Ordinary { @JavaRequests Object inert }
            @DSL class NoContribution { String value }
            @DSL interface Contract { @JavaRequests Object inert = null }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
        Application.Create.One().inherited != null
        Application.Create.One().local != null
        !Ordinary.getDeclaredField('inert').declaredAnnotations*.annotationType()*.simpleName.contains('AutoCreate')
        NoContribution.Create.One().value == null
    }

    private void compileJavaDomain() {
        File source = new File(fixtureRoot, 'JavaRequests.java')
        source.text = '''
            import java.lang.annotation.*;
            import com.blackbuild.klum.ast.Required;
            import com.blackbuild.klum.ast.PropagateAnnotation;
            import com.blackbuild.klum.ast.layer3.AutoCreate;
            @PropagateAnnotation.List({@PropagateAnnotation(AutoCreate.class), @PropagateAnnotation(Required.class)})
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            public @interface JavaRequests {}
        '''
        String classpath = [fixtureRoot.path, Required.protectionDomain.codeSource.location.toURI().path,
                GroovySystem.protectionDomain.codeSource.location.toURI().path].join(File.pathSeparator)
        assert ToolProvider.systemJavaCompiler.run(null, null, null, '--release', '17', '-classpath', classpath,
                '-d', fixtureRoot.path, source.path) == 0
    }

    private String mirror(String schema) {
        File root = new File(tempFolder.root, "mirrors-$schema")
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(
                new File(compilerConfiguration.targetDirectory, "${schema}_DSL.class").toPath(), root.toPath())
        new File(root, "${schema}_DSL.java").text
    }

    private static Set<String> signatures(Class type, String schema) {
        type.declaredMethods.findAll { !it.synthetic }.collect { method ->
            "$method.name:${method.genericReturnType.typeName}:${method.genericParameterTypes*.typeName.join(',')}"
                    .replace(schema, 'Schema')
        } as Set
    }
}
