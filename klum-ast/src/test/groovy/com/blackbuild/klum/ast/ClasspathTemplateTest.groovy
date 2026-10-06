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
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag
import spock.lang.Unroll

import javax.tools.ToolProvider

@Issue('833')
class ClasspathTemplateTest extends AbstractDSLSpec {

    File marker

    def setup() {
        createClass '''
            package recipes

            @DSL class Environment {
                @Key String name
                String region
                String identifier
                int lifecycleCalls
                Server primary
                @Field(FieldType.LINK) Server shared

                @PostCreate void recordCreation() { lifecycleCalls++ }
                @PostApply void recordConfiguration() { lifecycleCalls++ }
                @Validate void validateRegion() { assert region != 'invalid' }
            }

            @DSL class Server {
                String value
                String identifier
                int lifecycleCalls
                @Owner Environment environment

                @PostCreate void recordCreation() { lifecycleCalls++ }
                @PostApply void recordConfiguration() { lifecycleCalls++ }
            }

            class ReturnedModel {
                static Environment value
            }
        '''
        def root = tempFolder.newFolder('classpath')
        marker = new File(root, 'META-INF/klum-model/recipes.Environment.properties')
        marker.parentFile.mkdirs()
        loader.addURL(root.toURI().toURL())
        marker.text = 'model-class=recipes.DefaultEnvironment'
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#loading-templates-from-the-classpath')
    def "loads a classpath recipe and replays it into fresh environments"() {
        given:
        createSecondaryClass '''
            package recipes
            import groovy.transform.BaseScript
            @BaseScript(DelegatingScript) import groovy.util.DelegatingScript

            region 'eu-central'
            primary {
                value 'server'
                applyLater { identifier value.toUpperCase() }
            }
            applyLater { identifier name.toUpperCase() }
        ''', 'DefaultEnvironment.groovy'

        when:
        def defaults = Environment.Create.Template.FromClasspath()
        def catalog = Environment.Template.With(defaults) {
            Environment.Create.With('catalog') { }
        }
        def billing = Environment.Template.With(defaults) {
            Environment.Create.With('billing') { }
        }

        then:
        defaults.name == null
        defaults.region == 'eu-central'
        defaults.identifier == null
        defaults.lifecycleCalls == 0
        defaults.primary.identifier == null
        defaults.primary.lifecycleCalls == 0
        TemplateManager.isTemplate(defaults)
        TemplateManager.isTemplate(defaults.primary)
        catalog.identifier == 'CATALOG'
        billing.identifier == 'BILLING'
        catalog.primary.identifier == 'SERVER'
        catalog.primary.environment.is(catalog)
        catalog.lifecycleCalls == 2
        catalog.primary.lifecycleCalls == 2
        !catalog.primary.is(billing.primary)
        !catalog.primary.is(defaults.primary)
        !TemplateManager.isTemplate(catalog)
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#ordinary-scripts-produce-value-snapshots')
    def "snapshots an ordinary script after its lifecycle without retaining deferred actions"() {
        given:
        createSecondaryClass '''
            package recipes
            def sharedServer = Server.Create.With(value: 'shared')
            ReturnedModel.value = Environment.Create.With('seed') {
                region 'eu-central'
                primary {
                    value 'server'
                    applyLater { identifier value.toUpperCase() }
                }
                shared sharedServer
                applyLater { identifier name.toUpperCase() }
            }
            ReturnedModel.value
        ''', 'DefaultEnvironment.groovy'

        when:
        def defaults = Environment.Create.Template.FromClasspath(loader)
        def catalog = Environment.Template.With(defaults) {
            Environment.Create.With('catalog') { }
        }
        def source = ReturnedModel.value

        then:
        source.name == 'seed'
        source.identifier == 'SEED'
        source.lifecycleCalls == 2
        defaults.name == null
        defaults.identifier == 'SEED'
        defaults.lifecycleCalls == 2
        defaults.primary.lifecycleCalls == 2
        TemplateManager.isTemplate(defaults)
        TemplateManager.isTemplate(defaults.primary)
        !defaults.is(source)
        !defaults.primary.is(source.primary)
        defaults.primary.environment == null
        source.primary.environment.is(source)
        catalog.primary.environment.is(catalog)
        defaults.shared.is(source.shared)
        !TemplateManager.isTemplate(defaults.shared)
        !TemplateManager.isTemplate(source)
        catalog.name == 'catalog'
        catalog.identifier == 'SEED'
        catalog.lifecycleCalls == 4
        catalog.primary.lifecycleCalls == 4
        catalog.shared.is(source.shared)
    }

    def "snapshot conversion retains normal defaults from the active Template scope"() {
        given:
        createSecondaryClass '''
            package recipes
            Environment.Create.With('seed', region: 'eu-central')
        ''', 'DefaultEnvironment.groovy'
        def scopedDefaults = Environment.Create.Template.With {
            applyLater { identifier name.toUpperCase() }
        }

        when:
        def defaults = Environment.Template.With(scopedDefaults) {
            Environment.Create.Template.FromClasspath(loader)
        }
        def catalog = Environment.Template.With(defaults) {
            Environment.Create.With('catalog') { }
        }

        then:
        defaults.identifier == 'SEED'
        catalog.identifier == 'CATALOG'
        TemplateManager.isTemplate(defaults)
    }

    def "preserves a Template explicitly returned by an ordinary script"() {
        given:
        createSecondaryClass '''
            package recipes
            ReturnedModel.value = Environment.Create.Template.With {
                region 'eu-central'
                applyLater { identifier name.toUpperCase() }
            }
            ReturnedModel.value
        ''', 'DefaultEnvironment.groovy'

        when:
        def defaults = Environment.Create.Template.FromClasspath()
        def catalog = Environment.Template.With(defaults) {
            Environment.Create.With('catalog') { }
        }

        then:
        defaults.is(ReturnedModel.value)
        defaults.lifecycleCalls == 0
        defaults.identifier == null
        catalog.identifier == 'CATALOG'
        catalog.lifecycleCalls == 2
    }

    def "explicit loader resolves marker and script independently of thread context loader"() {
        given:
        createSecondaryClass '''
            package recipes
            import groovy.transform.BaseScript
            @BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            region 'eu-central'
        ''', 'DefaultEnvironment.groovy'
        Thread.currentThread().contextClassLoader = oldLoader

        when:
        def defaults = Environment.Create.Template.FromClasspath(loader)

        then:
        defaults.region == 'eu-central'
        TemplateManager.isTemplate(defaults)

        when:
        Environment.Create.Template.FromClasspath()

        then:
        def error = thrown(KlumModelException)
        error.message.contains('File META-INF/klum-model/recipes.Environment.properties not found in classpath.')
    }

    @Unroll
    def "classpath marker failure retains root diagnostics: #scenario"() {
        given:
        if (content == null) marker.delete()
        else marker.text = content

        when:
        Environment.Create.Template.FromClasspath(loader)

        then:
        def templateError = thrown(KlumModelException)
        templateError.message.contains(message)

        when:
        Environment.Create.FromClasspath(loader)

        then:
        def modelError = thrown(KlumModelException)
        modelError.message == templateError.message
        modelError.cause?.class == templateError.cause?.class

        where:
        scenario         | content                       | message
        'missing marker' | null                          | 'File META-INF/klum-model/recipes.Environment.properties not found in classpath.'
        'missing entry'  | 'other=value'                 | "No entry 'model-class' found in META-INF/klum-model/recipes.Environment.properties"
        'missing class'  | 'model-class=recipes.Missing' | "Class 'recipes.Missing' defined in META-INF/klum-model/recipes.Environment.properties does not exist"
    }

    def "marker IO failures retain the root diagnostic and cause"() {
        given:
        def failingLoader = new ClassLoader(loader) {
            @Override InputStream getResourceAsStream(String name) {
                new InputStream() {
                    @Override int read() { throw new IOException('unreadable marker') }
                }
            }
        }

        when:
        Environment.Create.Template.FromClasspath(failingLoader)

        then:
        def error = thrown(KlumModelException)
        error.message.contains('Error while reading marker properties.')
        error.cause instanceof IOException
        error.cause.message == 'unreadable marker'
    }

    def "delegating classpath Templates skip validation until ordinary application"() {
        given:
        createSecondaryClass '''
            package recipes
            import groovy.transform.BaseScript
            @BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            region 'invalid'
        ''', 'DefaultEnvironment.groovy'

        when:
        def defaults = Environment.Create.Template.FromClasspath()

        then:
        defaults.region == 'invalid'
        defaults.lifecycleCalls == 0
        TemplateManager.isTemplate(defaults)

        when:
        Environment.Template.With(defaults) {
            Environment.Create.With('catalog') { }
        }

        then:
        thrown(KlumValidationException)
    }

    @Unroll
    def "script failures keep their diagnostic and cause: #scenario"() {
        given:
        createSecondaryClass script, 'DefaultEnvironment.groovy'

        when:
        Environment.Create.Template.FromClasspath(loader)

        then:
        def error = thrown(KlumModelException)
        error.message.contains('Could not read model from recipes.DefaultEnvironment')
        error.cause.message.contains(causeMessage)

        where:
        scenario        | script                                                              | causeMessage
        'wrong result'  | 'package recipes; 42'                                               | 'did not return an instance of recipes.Environment'
        'script throws' | "package recipes; throw new IllegalStateException('broken recipe')" | 'broken recipe'
        'validation'    | "package recipes; Environment.Create.With('seed', region: 'invalid')" | 'invalid'
    }

    def "rejects an unrelated Model through the normal requested-root contract before snapshotting"() {
        given:
        createSecondaryClass '''
            package recipes
            @DSL class Unrelated { String value }
            class UnrelatedResult { static Unrelated value }
        '''
        createSecondaryClass '''
            package recipes
            UnrelatedResult.value = Unrelated.Create.With {
                value 'wrong'
            }
            UnrelatedResult.value
        ''', 'DefaultEnvironment.groovy'
        def snapshot = null

        when:
        snapshot = Environment.Create.Template.FromClasspath(loader)

        then:
        def templateError = thrown(KlumModelException)
        templateError.message.contains('Could not read model from recipes.DefaultEnvironment')
        templateError.cause instanceof KlumModelException
        templateError.cause.message.startsWith('Script recipes.DefaultEnvironment did not return an instance of recipes.Environment at ')
        snapshot == null

        and: 'the incompatible ordinary result remains a completed Model with no Template identity'
        def templateSource = UnrelatedResult.value
        templateSource.class == Unrelated
        templateSource.value == 'wrong'
        !TemplateManager.isTemplate(templateSource)

        when:
        Environment.Create.FromClasspath(loader)

        then: 'ordinary and Template loading enforce the same requested-root result-type validation'
        def modelError = thrown(KlumModelException)
        modelError.message == templateError.message
        modelError.cause.class == templateError.cause.class
        modelError.cause.message == templateError.cause.message
        UnrelatedResult.value.class == Unrelated
        UnrelatedResult.value.value == 'wrong'
        !TemplateManager.isTemplate(UnrelatedResult.value)
        !TemplateManager.isTemplate(templateSource)
    }

    def "snapshots the concrete subtype returned for an abstract root"() {
        given:
        createClass '''
            package recipes
            @DSL abstract class BaseEnvironment { String region }
            @DSL class ConcreteEnvironment extends BaseEnvironment { String detail }
        '''
        def baseMarker = new File(marker.parentFile, 'recipes.BaseEnvironment.properties')
        baseMarker.text = 'model-class=recipes.ConcreteDefaults'
        createSecondaryClass '''
            package recipes
            ConcreteEnvironment.Create.With(region: 'eu-central', detail: 'concrete')
        ''', 'ConcreteDefaults.groovy'

        when:
        def defaults = BaseEnvironment.Create.Template.FromClasspath(loader)

        then:
        defaults.class == ConcreteEnvironment
        defaults.detail == 'concrete'
        TemplateManager.isTemplate(defaults)
    }

    def "Java static Groovy and source mirrors expose truthful classpath Template signatures"() {
        given:
        createSecondaryClass '''
            package recipes
            import groovy.transform.BaseScript
            @BaseScript(DelegatingScript) import groovy.util.DelegatingScript
            region 'eu-central'
        ''', 'DefaultEnvironment.groovy'
        def source = new File(tempFolder.root, 'recipes/JavaClasspathConsumer.java')
        source.parentFile.mkdirs()
        source.text = '''
            package recipes;
            public class JavaClasspathConsumer {
                public static Environment load() {
                    Environment_DSL.Factory.Template factory = Environment.Create.Template;
                    return factory.FromClasspath();
                }
                public static Environment load(ClassLoader loader) {
                    return Environment.Create.Template.FromClasspath(loader);
                }
            }
        '''
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)

        when:
        int result = ToolProvider.systemJavaCompiler.run(null, null, null, '-classpath', classpath,
                '-d', compilerConfiguration.targetDirectory.absolutePath, source.absolutePath)
        def javaLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], loader)
        def javaConsumer = javaLoader.loadClass('recipes.JavaClasspathConsumer')
        def staticConsumer = createSecondaryClass '''
            package recipes
            import groovy.transform.CompileStatic
            @CompileStatic class StaticClasspathConsumer {
                static Environment load() {
                    Environment_DSL.Factory.Template factory = Environment.Create.Template
                    factory.FromClasspath()
                }
                static Environment load(ClassLoader loader) {
                    Environment.Create.Template.FromClasspath(loader)
                }
            }
        '''
        def mirrorRoot = tempFolder.newFolder('mirrors')
        def namespace = new File(compilerConfiguration.targetDirectory, 'recipes/Environment_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespace.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'recipes/Environment_DSL.java').text
        def publicFactory = getClass('recipes.Environment_DSL$Factory$Template')
        def mirrorClasses = tempFolder.newFolder('mirror-classes')
        int mirrorResult = ToolProvider.systemJavaCompiler.run(null, null, null, '-classpath', classpath,
                '-d', mirrorClasses.absolutePath, new File(mirrorRoot, 'recipes/Environment_DSL.java').absolutePath)

        then:
        result == 0
        mirrorResult == 0
        publicFactory.getMethod('FromClasspath').returnType == Environment
        publicFactory.getMethod('FromClasspath', ClassLoader).returnType == Environment
        TemplateManager.isTemplate(javaConsumer.getMethod('load').invoke(null))
        TemplateManager.isTemplate(javaConsumer.getMethod('load', ClassLoader).invoke(null, loader))
        TemplateManager.isTemplate(staticConsumer.load())
        TemplateManager.isTemplate(staticConsumer.load(loader))
        mirror.contains('Environment FromClasspath()')
        mirror.contains('Environment FromClasspath(ClassLoader loader)')
        mirror.contains('value-only Template snapshot')
        mirror.contains('META-INF/klum-model/recipes.Environment.properties')
    }
}
