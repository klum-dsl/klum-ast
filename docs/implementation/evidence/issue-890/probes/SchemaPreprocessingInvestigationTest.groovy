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
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import javax.tools.ToolProvider
import java.util.concurrent.TimeUnit

/** Isolated service-discovered global passes. Deliberately not a production implementation. */
@Issue('890')
class SchemaPreprocessingInvestigationTest extends AbstractDSLSpec {
    private static final String CAST = 'com.blackbuild.klum.cast.validation.KlumCastTransformation'
    private File schedulingRoot

    private void install(Map options = [:]) {
        boolean castFirst = options.get('castFirst', true)
        boolean validate = options.get('validate', false)
        boolean coordinate = options.get('coordinate', false)
        boolean revisit = options.get('revisit', false)
        File root = tempFolder.newFolder('scheduling')
        schedulingRoot = root
        def config = new CompilerConfiguration(compilerConfiguration)
        config.targetDirectory = root
        def bootstrap = new GroovyClassLoader(loader, config)
        String fixture = new File(System.getProperty('issue890.evidence'), 'fixtures/SchedulingTransforms.groovy').text
        bootstrap.parseClass(fixture.replace('@VALIDATE@', "$validate").replace('@COORDINATE@', "$coordinate").replace('@REVISIT@', "$revisit"))
        bootstrap.close()
        compilerConfiguration.disabledGlobalASTTransformations = [CAST] as Set
        File service = new File(root, 'META-INF/services/org.codehaus.groovy.transform.ASTTransformation')
        service.parentFile.mkdirs()
        service.text = (castFirst ? ['CastSchedulingProbe', 'SchemaSchedulingProbe'] :
                ['SchemaSchedulingProbe', 'CastSchedulingProbe']).plus('LoweringAuditProbe').join('\n') + '\n'
        loader.addClasspath(root.path)
        createSecondaryClass('''
            import java.lang.annotation.*
            import org.codehaus.groovy.transform.GroovyASTTransformationClass
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
            @Repeatable(ProbePropagate.List)
            @interface ProbePropagate {
                Class<? extends Annotation> value()
                @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
                @interface List { ProbePropagate[] value() }
            }
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD, ElementType.TYPE])
            @GroovyASTTransformationClass('ForeignSchedulingProbe')
            @interface ForeignMarker {}
        ''')
    }

    private List<String> events() { loader.loadClass('PreprocessingTrace').events }

    private void domain(String selected, String name = 'Domain', boolean java = false) {
        String selectedName = selected.startsWith('com.') ? selected.tokenize('.').last() : selected
        String selectedImport = selected.startsWith('com.') ? "import $selected" : ''
        if (!java) {
            createSecondaryClass("""
                import java.lang.annotation.*
                $selectedImport
                @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.METHOD, ElementType.TYPE])
                @ProbePropagate($selectedName) @interface $name {}
            """)
            return
        }
        compileJavaDomain(name, "@ProbePropagate(${selectedName}.class)")
    }

    private void compileJavaDomain(String name, String requests) {
        File root = tempFolder.newFolder("java-$name")
        File source = new File(root, "${name}.java")
        source.text = """
            import java.lang.annotation.*;
            import com.blackbuild.klum.ast.*;
            import com.blackbuild.klum.ast.layer3.*;
            @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
            $requests public @interface $name {}
        """
        String classpath = [compilerConfiguration.targetDirectory.path,
                Cluster.protectionDomain.codeSource.location.toURI().path,
                GroovySystem.protectionDomain.codeSource.location.toURI().path].join(File.pathSeparator)
        assert ToolProvider.systemJavaCompiler.run(null, null, null, '-classpath', classpath,
                '-d', root.path, source.path) == 0
        loader.addClasspath(root.path)
    }

    def 'global propagation before KlumCast preserves the effective Default constraint'() {
        given:
        install(castFirst: false)
        domain('Default')
        events().clear()

        when:
        createSecondaryClass('@DSL class Application { @Domain String value }')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('field') && failure.message.contains('delegate')
        events().findAll { it in ['preprocess', 'cast'] } == ['preprocess', 'cast']
    }

    def 'KlumCast first silently misses the effective Default constraint while direct use fails'() {
        given:
        install()
        domain('Default')
        events().clear()

        when:
        createSecondaryClass('@DSL class Application { @Domain String value }')

        then:
        Application.getDeclaredField('value').isAnnotationPresent(Default)
        events().take(2) == ['cast', 'preprocess']

        when:
        createSecondaryClass('@DSL class Direct { @Default String value }')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def 'engine validation after copying catches effective constraints in either global order (#castFirst, #java)'() {
        given:
        install(castFirst: castFirst, validate: true)
        domain('com.blackbuild.klum.ast.Default', 'DomainDefault', java)

        when:
        createSecondaryClass('@DSL class Application { @DomainDefault String value }')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('@DomainDefault propagates @com.blackbuild.klum.ast.Default')
        failure.message.contains('value')
        failure.message.contains('field') && failure.message.contains('delegate')
        failure.message.contains('line 1, column')

        where:
        castFirst | java
        true      | false
        false     | false
        true      | true
        false     | true
    }

    def 'Cluster shape and placement checks match direct failures (#declaration)'() {
        given:
        install(validate: true)
        domain('com.blackbuild.klum.ast.layer3.Cluster')

        when:
        createSecondaryClass("import com.blackbuild.klum.ast.layer3.Cluster; @DSL class Direct { @Cluster $declaration }")

        then:
        thrown(MultipleCompilationErrorsException)

        when:
        createSecondaryClass("@DSL class Application { @Domain $declaration }")

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('@Domain propagates @com.blackbuild.klum.ast.layer3.Cluster')

        where:
        declaration << ['String value', 'Map values', 'String getValues() { null }']
    }

    def 'class Cluster requires configuration and wrong Required placement retains target legality'() {
        given:
        install(validate: true)
        domain('com.blackbuild.klum.ast.layer3.Cluster', 'DomainCluster')
        domain('Required', 'DomainRequired')

        when:
        createSecondaryClass('@DomainCluster @DSL class Application {}')

        then:
        def cluster = thrown(MultipleCompilationErrorsException)
        cluster.message.contains('@DomainCluster propagates')
        cluster.message.contains('bounded')

        when:
        createSecondaryClass('@DSL class Wrong { @DomainRequired String value() { "" } }')

        then:
        def required = thrown(MultipleCompilationErrorsException)
        required.message.contains('Required')
        required.message.contains('METHOD') || required.message.contains('method')
    }

    def 'DSL eligibility follows existing transformation boundaries in a mixed source unit'() {
        given:
        install(validate: true)
        domain('com.blackbuild.klum.ast.layer3.AutoCreate')
        domain('Builder.Query', 'DomainQuery')
        events().clear()

        when:
        createSecondaryClass('''
            @DSL class Service {}
            @DSL abstract class Base { @Domain Service inherited }
            @DSL class Application extends Base { @Domain Service local }
            class Ordinary { @Domain Object field; @DomainQuery String query() { 'ordinary' } }
            @DSL interface Contract { @Domain Object value = null }
            class Holder {
                @DSL static class Nested { @Domain Service child }
                static class Plain { @Domain Object field }
            }
        ''')

        then:
        def result = Application.Create.One()
        result.inherited != null && result.local != null
        Base.getDeclaredField('inherited').declaredAnnotations*.annotationType()*.simpleName.count('AutoCreate') == 1
        !Ordinary.getDeclaredField('field').declaredAnnotations*.annotationType()*.simpleName.contains('AutoCreate')
        !Ordinary.getDeclaredMethod('query').declaredAnnotations*.annotationType()*.simpleName.contains('Query')
        !Contract.getDeclaredField('value').declaredAnnotations*.annotationType()*.simpleName.contains('AutoCreate')
        loader.loadClass('Holder$Nested').Create.One().child != null
        !loader.loadClass('Holder$Plain').getDeclaredField('field').declaredAnnotations*.annotationType()*.simpleName.contains('AutoCreate')
        events().count { it == 'copy:com.blackbuild.klum.ast.layer3.AutoCreate:inherited' } == 1
        !events().contains('eligible:Ordinary') && !events().contains('eligible:Contract')
    }

    def 'foreign local transformation executes once for direct and propagated DSL uses only (#castFirst)'() {
        given:
        install(castFirst: castFirst, validate: true)
        domain('ForeignMarker')
        events().clear()

        when:
        createSecondaryClass('''
            @DSL class Application { @Domain String copied; @ForeignMarker String direct }
            class Ordinary { @Domain String copied; @ForeignMarker String direct }
        ''')

        then:
        Application.Create.One().foreign_copied() == 'generated'
        Application.Create.One().foreign_direct() == 'generated'
        Ordinary.newInstance().foreign_direct() == 'generated'
        !Ordinary.declaredMethods*.name.contains('foreign_copied')
        events().count { it == 'foreign:copied' } == 1
        events().count { it == 'foreign:direct' } == 2
        events().indexOf('preprocess') < events().indexOf('foreign:copied')

        where:
        castFirst << [true, false]
    }

    def 'unchanged Required Optional and foreign behavior without propagation'() {
        given:
        install(validate: true)

        when:
        createSecondaryClass('''
            @DSL class Application { @Required String name; @Optional String other }
            class Ordinary { @Required String name; @Optional String other; @ForeignMarker String direct }
        ''')

        then:
        Application.Create.With { name 'valid' }.name == 'valid'
        [Application, Ordinary].every { type ->
            type.getDeclaredField('name').declaredAnnotations*.annotationType()*.simpleName.count('Validate') == 1 &&
                    type.getDeclaredField('other').declaredAnnotations*.annotationType()*.simpleName.count('Validate') == 1
        }
        Ordinary.newInstance().foreign_direct() == 'generated'
        !events().any { it.startsWith('copy:') }
    }

    def 'coordinator can invoke real converters in fixed order but unchanged local registrations duplicate lowering'() {
        given:
        install(validate: true, coordinate: true)
        events().clear()

        when:
        def failure = compileFailure('@DSL class Application { @Optional String other; @Required String name }')

        then:
        GroovySystem.version.startsWith('3.') ? failure == null : failure.message.contains('duplicate annotation')
        events().findAll { it in ['required', 'optional', 'lowered'] } == ['required', 'optional', 'lowered']
        events().containsAll(['validate-count:name:2', 'validate-count:other:2'])
    }

    def 'lowering before independently scheduled KlumCast introduces a false coannotation conflict'() {
        given:
        install(castFirst: false, validate: true, coordinate: true)

        when:
        createSecondaryClass('@DSL class Application { @Required String name }')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Not allowed to use')
        failure.message.contains('Required') && failure.message.contains('Validate')
    }

    def 'propagation of DSL is rejected inside a Schema and ignored on an ordinary class'() {
        given:
        install(validate: true)
        domain('DSL')

        when:
        createSecondaryClass('@Domain class Ordinary {}')

        then:
        !Ordinary.declaredFields*.name.contains('Create')

        when:
        createSecondaryClass('@Domain @DSL class Application {}')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Propagation of @DSL is unsupported')
    }

    def 'valid propagated Cluster Query Required Optional and factory contracts match direct use (#castFirst)'() {
        given:
        install(castFirst: castFirst, validate: true)
        domain('com.blackbuild.klum.ast.layer3.Cluster', 'DomainCluster')
        domain('Builder.Query', 'DomainQuery')
        domain('Required', 'DomainRequired')
        domain('Optional', 'DomainOptional')
        createSecondaryClass('''
            import com.blackbuild.klum.ast.layer3.Cluster
            @DSL class Service { String value }
            @DSL class Direct {
                @Required String name
                @Optional String other
                Service first
                @Cluster Map<String, Service> services
                @Cluster Map<String, Service> getSelected() { null }
                @Builder.Query String summary() { name.toUpperCase() }
            }
            @DSL class Application {
                @DomainRequired String name
                @DomainOptional String other
                Service first
                @DomainCluster Map<String, Service> services
                @DomainCluster Map<String, Service> getSelected() { null }
                @DomainQuery String summary() { name.toUpperCase() }
            }
        ''')

        when:
        def result = Application.Create.With {
            name 'app'
            services { first { value 'one' } }
            assert summary() == 'APP'
        }

        then:
        result.summary() == 'APP'
        result.services.first.value == 'one' && result.selected.first.value == 'one'
        signatures(loader.loadClass('Application_DSL$Builder'), 'Application') == signatures(loader.loadClass('Direct_DSL$Builder'), 'Direct')
        signatures(Application.Create.class, 'Application') == signatures(Direct.Create.class, 'Direct')
        !events().contains('validate-count:name:2')
        mirror('Application').contains('String summary()')
        mirror('Direct').contains('String summary()')
        mirror('Application').contains('services(')
        mirror('Application').contains('selected(')

        where:
        castFirst << [true, false]
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

    def 'valid repeatable requests and explicit container retain domain defaults (#container)'() {
        given:
        install(validate: true)
        String requests = container ? '@ProbePropagate.List([@ProbePropagate(AutoCreate), @ProbePropagate(Required)])' :
                '@ProbePropagate(AutoCreate) @ProbePropagate(Required)'
        createSecondaryClass("""
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.layer3.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @DefaultValues
            $requests
            @interface Configured { String name() default '' }
        """)

        when:
        createSecondaryClass('''
            @DSL class Service { String name }
            @DSL class Application { @Configured(name='orders') Service service }
        ''')

        then:
        Application.Create.One().service.name == 'orders'
        Configured.getAnnotationsByType(ProbePropagate).size() == 2
        Application.getDeclaredField('service').declaredAnnotations*.annotationType()*.simpleName.containsAll(
                ['Configured', 'AutoCreate', 'Required', 'Validate'])

        where:
        container << [false, true]
    }

    def 'validating only new copies cannot recheck a previously validated coannotation'() {
        given:
        install(validate: true)
        domain('Field')

        when:
        createSecondaryClass('@DSL class Application { @Key @Domain String value }')

        then:
        Application != null

        when:
        createSecondaryClass('@DSL class Direct { @Key @Field String value }')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def 'revalidating the complete effective declaration restores coannotation correctness (#castFirst, #annotations)'() {
        given:
        install(castFirst: castFirst, validate: true, revisit: true)
        domain('Validate')

        when:
        createSecondaryClass("@DSL class Application { $annotations String value }")

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Not allowed to use')
        failure.message.contains('@Domain propagates @com.blackbuild.klum.ast.Validate')

        where:
        castFirst | annotations
        true      | '@Required @Domain'
        false     | '@Required @Domain'
        true      | '@Domain @Required'
        false     | '@Domain @Required'
    }

    def 'precompiled base declarations are not expanded again'() {
        given:
        install(validate: true)
        domain('Optional')
        createSecondaryClass('''
            @DSL abstract class Base { @Domain String inherited }
        ''')
        events().clear()

        when:
        createSecondaryClass('''
            @DSL class Application extends Base {}
        ''')

        then:
        Application.Create.With { inherited 'value' }.inherited == 'value'
        !events().any { it.endsWith(':inherited') }
    }

    def 'nonstatic inner Schemas follow existing DSL eligibility'() {
        given:
        install(validate: true)
        domain('com.blackbuild.klum.ast.layer3.AutoCreate')

        when:
        createSecondaryClass('''
            @DSL class Service {}
            class Outer { @DSL class Inner { @Domain Service child } }
        ''')

        then:
        events().contains('eligible:Outer$Inner')
        loader.loadClass('Outer$Inner').getDeclaredField('child').declaredAnnotations*.annotationType()*.simpleName.contains('AutoCreate')
    }

    def 'Java repeatable requests and explicit containers are consumed by Groovy (#container)'() {
        given:
        install(validate: true)
        String requests = '@ProbePropagate(AutoCreate.class), @ProbePropagate(Required.class)'
        compileJavaDomain('JavaDomain', container ? "@ProbePropagate.List({$requests})" : requests.replace(', ', ' '))

        when:
        createSecondaryClass('''
            @DSL class Service {}
            @DSL class Application { @JavaDomain Service child }
        ''')

        then:
        Application.Create.One().child != null
        loader.loadClass('JavaDomain').getAnnotationsByType(ProbePropagate).size() == 2
        Application.getDeclaredField('child').declaredAnnotations*.annotationType()*.simpleName.containsAll(
                ['JavaDomain', 'AutoCreate', 'Required', 'Validate'])

        where:
        container << [false, true]
    }

    def 'unannotated subclasses retain the existing DSL hierarchy error without propagation'() {
        given:
        install(validate: true)
        domain('Optional')
        events().clear()

        when:
        createSecondaryClass('''
            @DSL abstract class Base {}
            class OrdinaryChild extends Base { @Domain String own }
        ''')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('Non-DSL subclasses cannot extend DSL Objects')
        !events().contains('eligible:OrdinaryChild')
        !events().any { it.endsWith(':own') }
    }

    def 'default-only propagation guards missing members across compiler versions'() {
        given:
        install(validate: true)
        createSecondaryClass('''
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface NeedsConfiguration { String value() }
        ''')
        domain('NeedsConfiguration')

        when:
        def directFailure = compileFailure('@DSL class Direct { @NeedsConfiguration String field }')

        then:
        GroovySystem.version.startsWith('5.') ? directFailure.message.contains('No explicit/default value') : directFailure == null

        when:
        createSecondaryClass('@DSL class Application { @Domain String field }')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('NeedsConfiguration') && failure.message.contains('value')
    }

    def 'validated domain annotations still require separate compilation'() {
        given:
        install(validate: true)

        when:
        createSecondaryClass('''
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.layer3.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @DefaultValues
            @ProbePropagate(AutoCreate)
            @interface SameSource { String name() default '' }
            @DSL class Service { String name }
            @DSL class Application { @SameSource Service field }
        ''')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('already been compiled')
    }

    def 'temporary service overlay characterizes the named compiler encapsulation boundary (#named)'() {
        given:
        install(validate: true, revisit: true)
        domain('com.blackbuild.klum.ast.Default', 'DomainDefault')
        List<String> jars = ['klumAnnotationsJar', 'klumRuntimeJar', 'klumCompilerJar'].collect { System.getProperty(it) }
        jars.addAll(System.getProperty('java.class.path').split(File.pathSeparator).findAll { entry ->
            ['groovy-3.', 'groovy-4.', 'groovy-5.', 'anno-docimal-annotations-', 'anno-docimal-ast-',
             'anno-docimal-global-ast-', 'klum-cast-annotations-', 'klum-cast-compile-', 'klum-cast-spi-',
             'jspecify-'].any { new File(entry).name.startsWith(it) }
        })
        String dependencies = jars.unique().join(File.pathSeparator)
        String probePath = [schedulingRoot.absolutePath, compilerConfiguration.targetDirectory.absolutePath].join(File.pathSeparator)
        File script = scriptFile('module-probe.groovy', '''
            import org.codehaus.groovy.control.*
            def config = new CompilerConfiguration()
            config.disabledGlobalASTTransformations = ['com.blackbuild.klum.cast.validation.KlumCastTransformation'] as Set
            def loader = new GroovyClassLoader(Thread.currentThread().contextClassLoader, config)
            try {
                loader.parseClass('import com.blackbuild.klum.ast.*; @DSL class Application { @DomainDefault String value }')
                assert false : 'constraint bypassed'
            } catch (MultipleCompilationErrorsException expected) {
                assert expected.errorCollector.errors.size() == 1
                assert expected.errorCollector.errors.first().cause.message.contains('@DomainDefault propagates')
            }
            println 'effective-validation=true'
        ''')
        File engineScript = scriptFile('engine-module-probe.groovy', '''
            import com.blackbuild.klum.cast.validation.ValidationHandler
            import org.codehaus.groovy.ast.*
            import org.codehaus.groovy.classgen.GeneratorContext
            import org.codehaus.groovy.control.*
            import org.codehaus.groovy.control.customizers.CompilationCustomizer
            def diagnostics = []
            def config = new CompilerConfiguration()
            config.disabledGlobalASTTransformations = ['com.blackbuild.klum.cast.validation.KlumCastTransformation'] as Set
            config.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.SEMANTIC_ANALYSIS) {
                void call(SourceUnit source, GeneratorContext context, ClassNode schema) {
                    def field = schema.getDeclaredField('value')
                    if (field != null) {
                        diagnostics.addAll(ValidationHandler.validateAnnotation(field.annotations.first(), field))
                    }
                }
            })
            def loader = new GroovyClassLoader(Thread.currentThread().contextClassLoader, config)
            loader.parseClass('import com.blackbuild.klum.ast.*; @DSL class Application { @Default String value }')
            assert diagnostics.any { it.message.contains('field') && it.message.contains('delegate') }
            println 'engine-validation=true'
        ''')
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path]
        command.addAll(named ? ['--module-path', dependencies, '--add-modules', 'ALL-MODULE-PATH',
                                '--class-path', probePath, '-m', 'org.apache.groovy/groovy.ui.GroovyMain'] :
                ['--class-path', dependencies + File.pathSeparator + probePath, 'groovy.ui.GroovyMain'])

        when:
        def result = execute(command + [script.path])
        // Remove the external overlay for the independent named-engine access check.
        def engineResult = execute(command.collect {
            it.replace(probePath, compilerConfiguration.targetDirectory.absolutePath)
        } + [engineScript.path])

        then:
        named ? result.exit != 0 && result.output.contains('IllegalAccessError') && result.output.contains('DslAstHelper') :
                result.exit == 0 && result.output.contains('effective-validation=true')
        engineResult.exit == 0 && engineResult.output.contains('engine-validation=true')

        where:
        // Groovy 3 is classpath-only; both named compiler lanes use normal module access.
        named << (GroovySystem.version.startsWith('3.') ? [false] : [true])
    }

    private MultipleCompilationErrorsException compileFailure(String source) {
        try { createSecondaryClass(source); return null }
        catch (MultipleCompilationErrorsException failure) { return failure }
    }

    private static Map execute(List<String> command) {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start()
        StringBuilder output = new StringBuilder()
        Thread reader = Thread.start { process.inputStream.withReader { output.append(it.text) } }
        boolean completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(5000)
        assert completed : output.toString()
        [exit: process.exitValue(), output: output.toString()]
    }
}
