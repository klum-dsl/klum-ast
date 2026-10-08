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

import com.blackbuild.klum.ast.runtime.KlumBuilder
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.KlumSchemaRelationship
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import javax.tools.ToolProvider
import java.lang.reflect.Modifier

@Issue('856')
class OwningRelationshipTracerTest extends AbstractDSLSpec {

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Structure.md#owning-schema-declarations')
    def 'inherited AUTO_LINK selects a completed provider through consumer-owned annotations'() {
        given: 'runtime annotations and base Schemas are binary inputs to the leaf and consumer'
        annotation('Binding', 'String value();')
        annotation('Source', 'String value();')
        annotation('DefaultSource', 'Class<?> value();')
        loader.addClasspath(compilerConfiguration.targetDirectory.absolutePath)
        createNonDslClass(('''
            package tracer
            import groovy.transform.CompileStatic
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
            import com.blackbuild.klum.ast.runtime.KlumObjectSupport

            @DSL class Facts {
                String topic
                int postTreeRuns
                @PostTree void count() { postTreeRuns++ }
                @Validate void counted() { Policy.validations++ }
            }
            @DSL class ProviderBase {
                @Source('primary') @DefaultSource(Facts) Facts primary
                @Source('secondary') Facts secondary
            }
            @DSL class ReceiverBase {
                @Field(FieldType.LINK) Facts facts
                @AutoLink void bindFacts() {
                    if (facts != null) return
                    def binding = KlumBuilderSupport.of(this).structure
                        .getOwningRelationshipAnnotation(Binding).orElseThrow()
                    facts Policy.choose(binding.value(), Facts)
                }
            }
            // Deliberately consumer-owned policy; KlumAST supplies declaration lookup only.
            class Policy {
                static List<Facts> candidates
                static int validations
                static Facts choose(String binding, Class<?> requestedType) {
                    candidates.find { candidate ->
                        def structure = KlumObjectSupport.of(candidate).structure
                        if (binding) return structure.getOwningRelationshipAnnotation(Source)
                            .map { it.value() == binding }.orElse(false)
                        structure.getOwningRelationshipAnnotation(DefaultSource)
                            .map { it.value() == requestedType }.orElse(false)
                    }
                }
            }
        ''').replace('@DSL class ReceiverBase', (staticWriter ? '@CompileStatic ' : '') + '@DSL class ReceiverBase'))
        // Reload class files in a fresh loader: no source or in-memory base Schema is available.
        def binaryLoader = new GroovyClassLoader(oldLoader, new CompilerConfiguration(compilerConfiguration))
        binaryLoader.addClasspath(compilerConfiguration.targetDirectory.absolutePath)
        loader = binaryLoader
        classPool.clear()
        createNonDslClass("""
            package tracer
            import com.blackbuild.klum.ast.DSL
            @DSL class Provider extends ProviderBase {}
            @DSL class Receiver extends ReceiverBase {}
            @DSL class ApplicationBase { @Binding('$binding') Receiver receiver }
            @DSL class Application extends ApplicationBase {}
        """)
        def providerType = getClass('Provider')
        def applicationType = getClass('Application')
        def policy = getClass('tracer.Policy')
        def provider = providerType.Create.With {
            primary { topic 'main' }
            secondary { topic 'alternative' }
        }
        policy.candidates = [provider.primary, provider.secondary]
        def expected = explicitOverride || !binding ? provider.primary : provider.secondary
        def writer = createSecondaryClass("""
            package tracer
            import com.blackbuild.klum.ast.runtime.KlumObjectSupport
            import groovy.transform.CompileStatic
            ${staticWriter ? '@CompileStatic' : ''}
            class Writer {
                static Application write(Facts override) {
                    Application.Create.With {
                        receiver { if (override != null) facts override }
                    }
                }
                static String declaration(Facts facts) {
                    KlumObjectSupport.of(facts).structure.owningRelationship.orElseThrow().name
                }
            }
        """)

        when:
        instance = writer.write(explicitOverride ? provider.primary : null)

        then:
        instance.receiver.facts.is(expected)
        writer.declaration(instance.receiver.facts) == (expected.is(provider.primary) ? 'primary' : 'secondary')
        KlumObjectSupport.of(instance.receiver).structure.owningRelationship.orElseThrow().declaringClass.is(getClass('ApplicationBase'))
        KlumObjectSupport.of(expected).structure.owningRelationship.orElseThrow().declaringClass.is(getClass('tracer.ProviderBase'))
        KlumObjectSupport.of(expected).structure.directOwners.empty
        expected.postTreeRuns == 1
        policy.validations == 2
        !instance.receiver.hasProperty('binding')
        KlumBuilder.declaredMethods.length == 0

        where:
        binding     | explicitOverride | staticWriter
        'secondary' | false            | false
        ''          | false            | false
        'secondary' | true             | false
        'secondary' | false            | true
        ''          | false            | true
        'secondary' | true             | true
    }

    def 'Java and static Groovy consumers preserve exact generic Optional descriptors'() {
        given:
        annotation('Binding', 'String value();')
        loader.addClasspath(compilerConfiguration.targetDirectory.absolutePath)
        createClass '''
            package tracer
            @DSL class Receiver {}
            @DSL class Application { @Binding('primary') Receiver receiver }
        '''
        compileJava('JavaMetadataConsumer', '''
            package tracer;
            import java.util.Optional;
            import com.blackbuild.klum.ast.runtime.KlumBuilder;
            import com.blackbuild.klum.ast.runtime.KlumBuilderSupport;
            import com.blackbuild.klum.ast.runtime.KlumObjectSupport;
            import com.blackbuild.klum.ast.runtime.KlumSchemaRelationship;
            public class JavaMetadataConsumer {
                public static Optional<Binding> live(KlumBuilder<Receiver> builder) {
                    KlumBuilderSupport<Receiver> support = KlumBuilderSupport.of(builder);
                    KlumBuilderSupport.Structure<Receiver> structure = support.getStructure();
                    Optional<KlumSchemaRelationship> declaration = structure.getOwningRelationship();
                    return structure.getOwningRelationshipAnnotation(Binding.class);
                }
                public static Optional<Binding> completed(Receiver model) {
                    KlumObjectSupport<Receiver> support = KlumObjectSupport.of(model);
                    KlumObjectSupport.Structure<Receiver> structure = support.getStructure();
                    Optional<KlumSchemaRelationship> declaration = structure.getOwningRelationship();
                    return declaration.flatMap(value -> value.getAnnotation(Binding.class));
                }
            }
        ''')
        def javaConsumer = loader.loadClass('tracer.JavaMetadataConsumer')
        def staticConsumer = createSecondaryClass '''
            package tracer
            import groovy.transform.CompileStatic
            import com.blackbuild.klum.ast.runtime.*
            import java.util.Optional
            @CompileStatic class StaticMetadataConsumer {
                static Optional<Binding> live(KlumBuilder<Receiver> builder) {
                    KlumBuilderSupport<Receiver> support = KlumBuilderSupport.of(builder)
                    KlumBuilderSupport.Structure<Receiver> structure = support.structure
                    Optional<KlumSchemaRelationship> declaration = structure.owningRelationship
                    structure.getOwningRelationshipAnnotation(Binding)
                }
                static Optional<Binding> completed(Receiver model) {
                    KlumObjectSupport<Receiver> support = KlumObjectSupport.of(model)
                    KlumObjectSupport.Structure<Receiver> structure = support.structure
                    Optional<KlumSchemaRelationship> declaration = structure.owningRelationship
                    declaration.flatMap { it.getAnnotation(Binding) }
                }
            }
        '''
        def receiverType = getClass('Receiver')
        def applicationType = getClass('Application')
        List<Object> liveBindings = []
        def receiver
        BuilderRelationshipLifetimeTest.observe(16) {
            liveBindings << javaConsumer.live(receiver).orElseThrow()
            liveBindings << staticConsumer.live(receiver).orElseThrow()
        }

        when:
        instance = applicationType.Create.With { receiver = delegate.receiver {} }

        then:
        liveBindings*.value() == ['primary', 'primary']
        javaConsumer.completed(instance.receiver).orElseThrow().value() == 'primary'
        staticConsumer.completed(instance.receiver).orElseThrow().value() == 'primary'
        [KlumBuilderSupport, KlumBuilderSupport.Structure, KlumSchemaRelationship].every { type ->
            Modifier.isFinal(type.modifiers) && type.constructors.length == 0 && !Serializable.isAssignableFrom(type)
        }
    }

    private void annotation(String name, String members) {
        compileJava(name, """
            package tracer;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            public @interface $name { $members }
        """)
    }

    private void compileJava(String name, String source) {
        def sourceFile = new File(tempFolder.root, "${name}.java")
        sourceFile.text = source.stripIndent()
        def errors = new ByteArrayOutputStream()
        def classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath].join(File.pathSeparator)
        int result = ToolProvider.systemJavaCompiler.run(null, null, errors,
            '--release', '17', '-classpath', classpath,
            '-d', compilerConfiguration.targetDirectory.absolutePath, sourceFile.absolutePath)
        assert result == 0: errors.toString()
    }
}
