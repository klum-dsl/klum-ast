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

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import javax.tools.ToolProvider
import java.lang.reflect.Modifier

import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC

@Issue("371")
class DslPropertyShadowingTest extends AbstractDSLSpec {

    def "rejects #kind instance storage with a #compilation ancestor"() {
        given:
        String parent = "@DSL class Service { $parentDeclaration }"
        String child = "@DSL class WebService extends Service { $childDeclaration }"
        String source
        int childLine
        if (compilation == 'binary') {
            createClass("package shadowing\n$parent")
            source = "package shadowing\n$child"
            childLine = 2
        } else if (compilation == 'child first') {
            source = "package shadowing\n$child\n$parent"
            childLine = 2
        } else {
            source = "package shadowing\n$parent\n$child"
            childLine = 3
        }

        when:
        createClass(source)

        then:
        def error = thrown(MultipleCompilationErrorsException)
        def diagnostic = error.errorCollector.errors.find {
            it instanceof SyntaxErrorMessage && it.cause.message.contains('shadows')
        }.cause
        diagnostic.message.contains("'shadowing.WebService.name' shadows 'shadowing.Service.name'")
        diagnostic.startLine == childLine
        diagnostic.startColumn == source.readLines()[childLine - 1].indexOf(childDeclaration) + 1

        where:
        [compilation, kind, parentDeclaration, childDeclaration] << [
                ['source', 'child first', 'binary'],
                [
                        ['property', "String name = 'ancestor'", "String name = 'subclass'"],
                        ['private field', 'private String name', 'private String name'],
                        ['protected field', 'protected String name', 'protected String name'],
                        ['collection', 'List<String> name', 'List<String> name'],
                        ['ignored field', '@Field(FieldType.IGNORED) String name', 'String name'],
                        ['transient field', '@Field(FieldType.TRANSIENT) String name', 'String name'],
                        ['different types', 'String name', 'Integer name'],
                        ['owner', '@Owner Object name', '@Owner Object name'],
                        ['default', "@Default(code = { 'ancestor' }) String name", "@Default(code = { 'subclass' }) String name"],
                        ['Builder only', '@Field(FieldType.BUILDER) String name', '@Field(FieldType.BUILDER) String name'],
                        ['Builder ancestor', '@Field(FieldType.BUILDER) String name', 'String name'],
                        ['Builder descendant', 'String name', '@Field(FieldType.BUILDER) String name']
                ]
        ].combinations().collect { mode, declarations -> [mode, *declarations] }
    }

    def "rejects the original split lifecycle storage scenario"() {
        when:
        createClass('''
            package shadowing
            @DSL class Service {
                String name = 'ancestor'
                String fromParent
                @PostTree void recordParent() { fromParent = name }
            }
            @DSL class WebService extends Service { String name = 'subclass' }
        ''')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("'shadowing.WebService.name' shadows 'shadowing.Service.name'")
    }

    def "rejects storage from a distant DSL ancestor"() {
        when:
        createClass('''
            package shadowing
            @DSL class Service { String name }
            @DSL class Backend extends Service { String protocol }
            @DSL class WebService extends Backend { String name }
        ''')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("'shadowing.WebService.name' shadows 'shadowing.Service.name'")
    }

    def "same-named static counters retain independent class state across compilation units"() {
        given:
        createClass('''
            package shadowing
            @DSL class Service {
                static int initializerCalls
                String parentValue = initializeParent()
                private static String initializeParent() {
                    initializerCalls++
                    'parent'
                }
            }
        ''')
        def parentClass = clazz
        def parentBuilderClass = builderClass
        createSecondaryClass('''
            package shadowing
            @DSL class WebService extends Service {
                static int initializerCalls
                String childValue = initializeChild()
                private static String initializeChild() {
                    initializerCalls++
                    'child'
                }
            }
        ''')

        when:
        instance = WebService.Create.One()

        then:
        instance.parentValue == 'parent'
        instance.childValue == 'child'
        parentClass.initializerCalls == 1
        WebService.initializerCalls == 1
        !parentBuilderClass.declaredFields.any { it.name == 'initializerCalls' }
        !getBuilderClass('shadowing.WebService').declaredFields.any { it.name == 'initializerCalls' }
    }

    def "inherited instance storage supports ordinary getter overrides"() {
        given:
        createClass('''
            package shadowing
            @DSL class Service { String name }
            @DSL class WebService extends Service {
                @Override String getName() { 'web:' + super.getName() }
            }
        ''')

        when:
        instance = WebService.Create.With { name 'frontend' }

        then:
        instance.name == 'web:frontend'
    }

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Inheritance.md#instance-storage-names")
    def "configures inherited storage without redeclaring it"() {
        given:
        createClass('''
            package shadowing
            @DSL class Service {
                String name
                @Default(code = { 'https' }) String protocol
            }
            @DSL class WebService extends Service { Integer port }
        ''')

        when:
        instance = WebService.Create.With {
            name 'frontend'
            port 443
        }

        then:
        instance.name == 'frontend'
        instance.protocol == 'https'
        instance.port == 443
    }

    def "inherits owner storage and preserves Factory and Template behavior"() {
        given:
        createClass('''
            package shadowing
            @DSL class Container { WebService service }
            @DSL class Service {
                @Owner Object container
                String name
            }
            @DSL class WebService extends Service { Integer port }
        ''')
        def template = WebService.Create.Template.With(name: 'frontend')

        when:
        instance = Container.Create.With {
            service {
                copyFrom template
                port 443
            }
        }

        then:
        instance.service.container.is(instance)
        instance.service.name == 'frontend'
        instance.service.port == 443
    }

    def "instance property implements an abstract getter and ordinary methods override"() {
        given:
        createClass('''
            package shadowing
            @DSL abstract class Service {
                abstract String getName()
                String description() { 'service' }
            }
            @DSL class WebService extends Service {
                String name
                @Override String description() { 'web service' }
            }
        ''')

        when:
        instance = WebService.Create.With { name 'frontend' }

        then:
        instance.name == 'frontend'
        instance.description() == 'web service'
    }

    def "Java ancestor fields are outside the DSL storage rule"() {
        given:
        File outputDirectory = compilerConfiguration.targetDirectory
        File javaSource = new File(outputDirectory, 'JavaService.java')
        javaSource.text = '''
            package shadowing;
            public class JavaService { protected String name; }
        '''
        assert ToolProvider.systemJavaCompiler.run(null, null, null,
                '-d', outputDirectory.absolutePath, javaSource.absolutePath) == 0
        loader.addClasspath(outputDirectory.absolutePath)

        when:
        createClass('''
            package shadowing
            @DSL class WebService extends JavaService { String name }
        ''')
        instance = WebService.Create.With { name 'frontend' }

        then:
        notThrown(MultipleCompilationErrorsException)
        instance.name == 'frontend'
    }

    def "KlumGenerated #generatedDeclaration #kind storage still conflicts with a #compilation ancestor"() {
        given:
        String marker = "@KlumGenerated(generator = 'test')"
        String parent = """@DSL class Service {
            ${generatedDeclaration in ['ancestor', 'both'] ? marker : ''} $parentDeclaration
            String fromParent
            @PostTree void recordParent() { fromParent = name }
        }"""
        String child = """@DSL class WebService extends Service {
            ${generatedDeclaration in ['descendant', 'both'] ? marker : ''} $childDeclaration
        }"""
        String source
        if (compilation == 'binary') {
            createClass("package shadowing\n$parent")
            source = "package shadowing\n$child"
        } else {
            source = "package shadowing\n${compilation == 'child first' ? child : parent}\n${compilation == 'child first' ? parent : child}"
        }

        when:
        createClass(source)

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("'shadowing.WebService.name' shadows 'shadowing.Service.name'")

        where:
        [compilation, generatedDeclaration, kind, parentDeclaration, childDeclaration] << [
                ['source', 'child first', 'binary'],
                ['ancestor', 'descendant', 'both'],
                [
                        ['ordinary', "String name = 'ancestor'", "String name = 'descendant'"],
                        ['default', "@Default(code = { 'ancestor-default' }) String name", "@Default(code = { 'descendant-default' }) String name"],
                        ['owner', '@Owner Object name', '@Owner Object name'],
                        ['Builder only', '@Field(FieldType.BUILDER) String name', '@Field(FieldType.BUILDER) String name']
                ]
        ].combinations().collect { mode, generated, declarations -> [mode, generated, *declarations] }
    }

    def "JVM synthetic flag does not exempt projected instance storage with a #compilation ancestor"() {
        given:
        markSyntheticField('name')
        String parent = "@DSL class Service { String name = 'ancestor' }"
        String child = "@DSL class WebService extends Service { String name = 'descendant' }"
        if (compilation == 'binary') {
            createClass("package shadowing\n$parent")
        }

        when:
        createClass("package shadowing\n${compilation == 'binary' ? '' : parent}\n$child")

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("'shadowing.WebService.name' shadows 'shadowing.Service.name'")

        where:
        compilation << ['source', 'binary']
    }

    def "#category implementation fields are invisible to DSL storage with a #compilation ancestor"() {
        given:
        String implementationName = '$implementationState'
        String marker = category in ['KlumGenerated', 'both'] ? "@KlumGenerated(generator = 'test')" : ''
        if (category in ['JVM synthetic', 'both']) {
            markSyntheticField(implementationName)
        }
        String parent = """@DSL class Service {
            $marker private String $implementationName = 'ancestor-implementation'
            String name
            @Default(code = { 'https' }) String protocol
            @Owner Container container
            String seenName
            String seenProtocol
            String seenContainerName
            boolean seenOwner
            @PostTree void recordParent() {
                seenName = name
                seenProtocol = protocol
                seenContainerName = container.name
                seenOwner = container != null
            }
        }
        @DSL class Container {
            String name
            Service service
        }
        """
        String child = """@DSL class WebService extends Service {
            $marker private String $implementationName = 'descendant-implementation'
            Integer port
            String seenChildName
            @PostTree void recordChild() { seenChildName = name }
        }
        """
        if (compilation == 'binary') {
            createClass("package shadowing\n$parent")
        }
        createClass("package shadowing\n${compilation == 'binary' ? '' : parent}\n$child")
        List builderTypes = [getBuilderClass('shadowing.Service'), getBuilderClass('shadowing.WebService')]
        List publicBuilderTypes = [getClass('shadowing.Service_DSL$Builder'), getClass('shadowing.WebService_DSL$Builder')]
        List modelTypes = [Service, WebService]
        def webServiceType = WebService
        def parentName = builderTypes.first().getDeclaredField('name')
        parentName.accessible = true
        def modelName = Service.getDeclaredField('name')
        modelName.accessible = true
        String configuredStorage

        when:
        instance = Container.Create.With {
            name 'root'
            service(webServiceType) {
                name 'frontend'
                port 443
                configuredStorage = parentName.get(delegate)
            }
        }

        then: 'implementation fields never become Builder slots or configuration operations'
        notThrown(MultipleCompilationErrorsException)
        builderTypes.every { type ->
            !type.declaredFields.any { it.name in [implementationName, '$state', 'Create', 'Template'] }
        }
        (builderTypes + publicBuilderTypes).every { type ->
            !type.declaredMethods.any { it.name in [implementationName, 'implementationState', 'getImplementationState', 'setImplementationState'] }
        }
        builderTypes.collectMany { it.declaredFields.toList() }.count { it.name == 'name' } == 1
        modelTypes.collectMany { it.declaredFields.toList() }.count { it.name == 'name' } == 1
        publicBuilderTypes.collectMany { it.declaredMethods.toList() }.count { it.name == 'name' && it.parameterCount == 1 } == 1

        and: 'the configured slot, materialized value, defaults, owners and both callbacks agree'
        configuredStorage == 'frontend'
        instance.service.name == configuredStorage
        modelName.get(instance.service) == configuredStorage
        instance.service.seenName == configuredStorage
        instance.service.seenChildName == configuredStorage
        instance.service.protocol == 'https'
        instance.service.seenProtocol == instance.service.protocol
        instance.service.container.is(instance)
        instance.service.seenContainerName == instance.service.container.name
        instance.service.seenContainerName == 'root'
        instance.service.seenOwner

        and: 'same-named implementation state remains private Model state'
        modelTypes.collect { type ->
            def field = type.getDeclaredField(implementationName)
            field.accessible = true
            field.get(instance.service)
        } == ['ancestor-implementation', 'descendant-implementation']
        modelTypes.every { type ->
            ['Create', 'Template'].every { name ->
                def field = type.getDeclaredField(name)
                Modifier.isStatic(field.modifiers) && field.isAnnotationPresent(KlumGenerated)
            }
        }
        Service.getDeclaredField('$state').synthetic

        where:
        [compilation, category] << [['source', 'binary'], ['KlumGenerated', 'JVM synthetic', 'both']].combinations()
    }

    private void markSyntheticField(String name) {
        compilerConfiguration.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.SEMANTIC_ANALYSIS) {
            @Override
            void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
                def field = classNode.getDeclaredField(name)
                if (field != null) {
                    field.modifiers |= ACC_SYNTHETIC
                }
            }
        })
    }
}
