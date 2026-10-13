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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import spock.lang.Issue

import javax.tools.ToolProvider

/** Characterization and isolated representation probes; no candidate is installed in KlumAST. */
@Issue('890')
class LifecycleCompositionInvestigationTest extends AbstractDSLSpec {
    def 'repeated field syntax creates before mutation and retains explicit input and inherited declarations'() {
        given:
        createSecondaryClass('''
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import com.blackbuild.klum.ast.runtime.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Mutator(phase = AutoCreate, handler = Bind)
            @interface FactBinding {}
            class Bind implements LifecycleParticipant.MutationHandler<FactBinding> {
                void mutate(LifecycleParticipant.MutationContext<FactBinding> c) {
                    assert c.getAnnotation(AutoCreate).present == (c.fieldName != 'absent')
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':bound')
                }
            }
            @DSL class Service {
                String value = 'default'
                @AutoCreate void childVisit() { value += ':child' }
            }
            @DSL class Base {
                @AutoCreate @FactBinding Service inherited
            }
            @DSL class Application extends Base {
                @AutoCreate({[value: 'annotation']}) @FactBinding Service created
                @AutoCreate @FactBinding Service existing
                @FactBinding Service absent
                String observed
                @AutoCreate void observe() { observed = created.value }
            }
        ''')

        when:
        def result = Application.Create.With { existing { value 'manual' } }

        then:
        result.created.value == 'annotation:bound:child'
        result.existing.value == 'manual:bound:child'
        result.inherited.value == 'default:bound:child'
        result.absent == null
        result.observed == 'annotation:bound'
    }

    def 'current public API rejects candidate syntax #kind'() {
        when:
        createSecondaryClass("""
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import com.blackbuild.klum.ast.runtime.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            $marker
            @interface Binding {}
            ${kind == 'meta' ? '' : '@DSL class Service {}\n@DSL class Application { @Binding Service service }'}
        """)

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains(diagnostic)

        where:
        kind       | marker                                                                                           | diagnostic
        'omission' | '@LifecycleParticipant.Creator(phase = AutoCreate)'                                               | 'handler'
        'sentinel' | '@LifecycleParticipant.Creator(phase = AutoCreate, handler = LifecycleParticipant.CreationHandler)' | 'public concrete'
        'meta'     | '@AutoCreate'                                                                                    | 'not allowed'
    }

    def 'default AutoCreate fails when a key or concrete type is needed #kind'() {
        given:
        createSecondaryClass("""
            import com.blackbuild.klum.ast.layer3.AutoCreate
            $declaration
            @DSL class Application { @AutoCreate Service service }
        """)

        when:
        Application.Create.One()

        then:
        Exception failure = thrown()
        causes(failure).contains(diagnostic)

        where:
        kind       | declaration                                  | diagnostic
        'keyed'    | '@DSL class Service { @Key String id }'            | "missing a 'key'"
        'abstract' | '@DSL abstract class Service {}'                  | "missing a 'type'"
        'defaultImpl' | '@DSL(defaultImpl=Concrete) abstract class Service {}\n@DSL class Concrete extends Service {}' | "missing a 'type'"
    }

    def 'isolated candidate representations recover one hop metadata without member forwarding'() {
        given:
        createSecondaryClass('''
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import com.blackbuild.klum.ast.runtime.LifecycleParticipant.CreationHandler
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
            @interface ProbeCreator {
                Class phase()
                Class handler() default CreationHandler
            }
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.ANNOTATION_TYPE])
            @interface ProbeAutoCreate { String key() default '$default' }
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.ANNOTATION_TYPE])
            @ProbeCreator(phase = AutoCreate)
            @interface Omitted {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @ProbeCreator(phase = AutoCreate, handler = CreationHandler)
            @interface Explicit {}
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.FIELD, ElementType.ANNOTATION_TYPE])
            @ProbeAutoCreate
            @interface Meta { String key() default 'domain-key' }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @Meta @interface Wrapped {}
            class Uses { @Omitted Object omitted; @Explicit Object explicit; @Meta(key='caller-key') Object meta; @Wrapped Object wrapped }
        ''')

        when:
        def omitted = Omitted.getAnnotation(ProbeCreator)
        def explicit = Explicit.getAnnotation(ProbeCreator)
        def uses = Uses.getDeclaredField('meta')
        def recovered = uses.declaredAnnotations.collect { it.annotationType().getAnnotation(ProbeAutoCreate) }.findAll()
        def wrapped = Uses.getDeclaredField('wrapped').declaredAnnotations.collect {
            it.annotationType().getAnnotation(ProbeAutoCreate)
        }.findAll()

        then:
        omitted == explicit
        recovered.size() == 1
        recovered.first().key() == '$default'
        uses.getAnnotation(Meta).key() == 'caller-key'
        wrapped.empty
        !uses.isAnnotationPresent(ProbeAutoCreate)
    }

    def 'Java candidate shape supports omission and one hop while repeatable domains require explicit expansion'() {
        given:
        File source = scriptFile('Shapes.java', '''
            import java.lang.annotation.*;
            public class Shapes {
                public interface Handler<A extends Annotation> {}
                @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
                public @interface Creator { Class<? extends Handler> handler() default Handler.class; }
                @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
                public @interface Auto { String key() default "$default"; }
                @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @Creator
                public @interface Omitted {}
                @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @Creator(handler=Handler.class)
                public @interface Explicit {}
                @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @Auto
                @Repeatable(Many.class) public @interface Meta { String key() default "domain-key"; }
                @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
                public @interface Many { Meta[] value(); }
                public static class Uses { @Meta(key="caller-key") Object one; @Meta @Meta Object repeated; }
            }
        ''')

        when:
        int exitCode = ToolProvider.systemJavaCompiler.run(null, null, null, '-d', tempFolder.root.path, source.path)

        then:
        exitCode == 0

        when:
        def javaLoader = new URLClassLoader([tempFolder.root.toURI().toURL()] as URL[], loader)
        def creator = javaLoader.loadClass('Shapes$Creator')
        def auto = javaLoader.loadClass('Shapes$Auto')
        def meta = javaLoader.loadClass('Shapes$Meta')
        def uses = javaLoader.loadClass('Shapes$Uses')
        def omitted = javaLoader.loadClass('Shapes$Omitted').getAnnotation(creator)
        def explicit = javaLoader.loadClass('Shapes$Explicit').getAnnotation(creator)
        def one = uses.getDeclaredField('one')
        def repeated = uses.getDeclaredField('repeated')

        then:
        omitted.handler() == explicit.handler()
        one.getAnnotation(meta).key() == 'caller-key'
        one.declaredAnnotations.first().annotationType().getAnnotation(auto).key() == '$default'
        repeated.getAnnotationsByType(meta).size() == 2
        repeated.declaredAnnotations.every { it.annotationType().getAnnotation(auto) == null }

        cleanup:
        javaLoader?.close()
    }

    private static String causes(Throwable failure) {
        List<String> messages = []
        for (Throwable current = failure; current != null; current = current.cause) {
            messages.add(current.message ?: '')
        }
        messages.join('\n')
    }

    def 'early additive propagation leaves actual domain annotation and standard field annotations at runtime'() {
        given:
        installPropagationProbe()
        createSecondaryClass('''
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import com.blackbuild.klum.ast.runtime.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
            @interface Propagate { Class<? extends Annotation>[] value() }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Marker { String value() default 'marker-default' }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @Propagate([AutoCreate, Marker])
            @LifecycleParticipant.Mutator(phase = AutoCreate, handler = Bind)
            @interface FactBinding { String label() default 'domain-default' }
            class Bind implements LifecycleParticipant.MutationHandler<FactBinding> {
                void mutate(LifecycleParticipant.MutationContext<FactBinding> c) {
                    assert c.getAnnotation(AutoCreate).present
                    assert c.getAnnotation(Marker).get().value() == 'marker-default'
                    Service.Create.narrowBuilder(c.targetBuilder).value(c.annotation.label())
                }
            }
            @DSL class Service { String value }
            @DSL class Application { @FactBinding(label='domain-member') Service service }
        ''')

        when:
        def result = Application.Create.One()
        def field = Application.getDeclaredField('service')

        then:
        result.service.value == 'domain-member'
        field.getAnnotation(FactBinding).label() == 'domain-member'
        field.declaredAnnotations*.annotationType()*.simpleName.containsAll(['FactBinding', 'AutoCreate', 'Marker'])
    }

    private void installPropagationProbe() {
        // Experimental compiler customizer, not a registered KlumAST transform or public API.
        compilerConfiguration.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.SEMANTIC_ANALYSIS) {
            @Override
            void call(SourceUnit source, GeneratorContext context, ClassNode schema) {
                schema.fields.each { field ->
                    field.annotations.toList().each { use ->
                        use.classNode.annotations.findAll { it.classNode.name == 'Propagate' }.each { marker ->
                            def value = marker.getMember('value')
                            def entries = value instanceof ListExpression ? value.expressions : [value]
                            entries.each { ClassExpression entry -> field.addAnnotation(new AnnotationNode(entry.type)) }
                        }
                    }
                }
            }
        })
    }

    def 'adding Required before DSL canonicalization is too late for its local transform discovery'() {
        given:
        installPropagationProbe()

        when:
        createSecondaryClass('''
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
            @interface Propagate { Class<? extends Annotation>[] value() }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @Propagate([Required]) @interface Mandatory {}
            @DSL class Application { @Mandatory String value }
        ''')
        def annotations = Application.getDeclaredField('value').declaredAnnotations*.annotationType()*.simpleName

        then:
        annotations.contains('Required')
        !annotations.contains('Validate')
    }

    def 'existing Groovy collector can bundle AutoCreate with a separate retained domain annotation'() {
        given:
        createSecondaryClass('''
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import com.blackbuild.klum.ast.runtime.*
            import groovy.transform.AnnotationCollector
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleParticipant.Mutator(phase = AutoCreate, handler = Bind)
            @interface FactBinding { String label() default 'domain-default' }
            class Bind implements LifecycleParticipant.MutationHandler<FactBinding> {
                void mutate(LifecycleParticipant.MutationContext<FactBinding> c) {
                    assert c.getAnnotation(AutoCreate).present
                    Service.Create.narrowBuilder(c.targetBuilder).value(c.annotation.label())
                }
            }
            @AnnotationCollector([AutoCreate, FactBinding])
            @interface StructuralFactBinding {}
            @DSL class Service { String value }
            @DSL class Application { @StructuralFactBinding(label='collector-member') Service service }
        ''')

        when:
        def result = Application.Create.One()
        def annotations = Application.getDeclaredField('service').declaredAnnotations*.annotationType()*.simpleName

        then:
        result.service.value == 'collector-member'
        annotations.containsAll(['FactBinding', 'AutoCreate'])
        !annotations.contains('StructuralFactBinding')
    }

    def 'one retained annotation drives propagated creation external mutation and existing default values'() {
        given:
        installPropagationProbe()
        // DefaultValues uses KlumCast validation, so compile the domain annotation before its Schema use.
        createSecondaryClass('''
            import com.blackbuild.klum.ast.layer3.*
            import com.blackbuild.klum.ast.runtime.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
            @interface Propagate { Class<? extends Annotation>[] value() }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @DefaultValues
            @Propagate([AutoCreate])
            @LifecycleParticipant.Mutator(phase = AutoLink, handler = Bind)
            @interface Configured { String name() default '' }
            class Bind implements LifecycleParticipant.MutationHandler<Configured> {
                void mutate(LifecycleParticipant.MutationContext<Configured> c) {
                    c.targetBuilder.binding(c.annotation.name() ?: 'unnamed')
                }
            }
        ''')
        createSecondaryClass('''
            @DSL class Service { String name; String binding }
            @DSL class Application {
                @Configured(name='annotation-name') Service created
                @Configured(name='annotation-name') Service supplied
                @Configured Service omitted
            }
        ''')

        when:
        def result = Application.Create.With { supplied { name 'manual-name' } }

        then:
        result.created.name == 'annotation-name'
        result.created.binding == 'annotation-name'
        result.supplied.name == 'manual-name'
        result.supplied.binding == 'annotation-name'
        result.omitted.name == null
        result.omitted.binding == 'unnamed'
        Application.getDeclaredField('created').getAnnotation(Configured).name() == 'annotation-name'
    }
}
