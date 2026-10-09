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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.internal.ClosureHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException
import spock.lang.Issue

import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.net.URLClassLoader

@Issue('799')
class ConstraintCallbackProbeTest extends AbstractDSLSpec {

    def 'a meta-annotation closure retains its authored Model parameter and concrete annotation'() {
        given:
        compilerConfiguration.addCompilationCustomizers(new ProbeSignatureCheck())
        createNonDslClass '''
            package probe

            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Field
            import com.blackbuild.klum.ast.FieldType
            import com.blackbuild.klum.ast.Validate
            import com.blackbuild.klum.ast.runtime.KlumBuilder
            import org.codehaus.groovy.runtime.InvokerHelper
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.ANNOTATION_TYPE)
            @interface ProbeRule { Class<?> value() }

            @ProbeRule({ Bounds bounds, Pool pool -> assert pool.slots >= bounds.minimum() })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds { int minimum() }

            @ProbeRule({ BroadBounds bounds, Object pool -> assert pool != null })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface BroadBounds {}

            @ProbeRule({ NarrowBounds bounds, KafkaPool pool -> assert pool.slots > 0 })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface NarrowBounds {}

            @DSL abstract class Pool {
                private static int VALIDATIONS
                int slots

                @Validate void countValidation() { VALIDATIONS++ }
                static int validationCount() { VALIDATIONS }
            }
            @DSL class KafkaPool extends Pool {}

            @DSL class Plan {
                static final List<String> CALLBACKS = []

                @Bounds(minimum = 5) Pool owned
                @Bounds(minimum = 5) @Field(FieldType.LINK) Pool external
                @Bounds(minimum = 5) @Field(FieldType.LINK) Pool sameRoot
                @Bounds(minimum = 5) @Field(FieldType.OPTIONAL_LINK) Pool optional
                @BroadBounds Pool broad

                @Validate
                void probeCompletedFields() {
                    assert !(this instanceof KlumBuilder)
                    ['owned', 'external', 'sameRoot', 'optional'].each { String name ->
                        def field = Plan.class.getDeclaredField(name)
                        field.accessible = true
                        def value = field.get(this)
                        if (value == null) return
                        def domain = field.annotations.find { it.annotationType().isAnnotationPresent(ProbeRule) }
                        def marker = domain.annotationType().getAnnotation(ProbeRule)
                        assert Closure.isAssignableFrom(marker.value())
                        Closure callback = (Closure) InvokerHelper.invokeConstructorOf(marker.value(), [null, null] as Object[])
                        callback.call(domain, value)
                        CALLBACKS << name
                    }
                }
            }
        '''

        Class<?> planType = getClass('probe.Plan')
        Class<?> poolType = getClass('probe.Pool')
        Class<?> boundsType = getClass('probe.Bounds')
        Annotation marker = boundsType.getAnnotation(getClass('probe.ProbeRule'))
        Class<?> callbackType = marker.value()
        Closure callback = instantiateCallback(callbackType)

        expect:
        marker != null
        getClass('probe.ProbeRule').getDeclaredMethod('value').genericReturnType.typeName == 'java.lang.Class<?>'
        callback.parameterTypes.toList() == [boundsType, poolType]
        callbackType.module == boundsType.module
        planType.getDeclaredField('owned').getAnnotation(boundsType).minimum() == 5
        acceptsDeclaredTarget(callbackType, planType.getDeclaredField('owned'))
        acceptsDeclaredTarget(callbackType, planType.getDeclaredField('external'))
        acceptsDeclaredTarget(callbackType, planType.getDeclaredField('optional'))
        acceptsDeclaredTarget(getClass('probe.BroadBounds').getAnnotation(getClass('probe.ProbeRule')).value(),
                planType.getDeclaredField('broad'))
        !acceptsDeclaredTarget(getClass('probe.NarrowBounds').getAnnotation(getClass('probe.ProbeRule')).value(),
                planType.getDeclaredField('owned'))
        !acceptsDeclaredTarget(getClass('probe.NarrowBounds').getAnnotation(getClass('probe.ProbeRule')).value(),
                planType.getDeclaredField('external'))

        when:
        Class<?> kafkaType = getClass('probe.KafkaPool')
        def external = kafkaType.Create.With { slots 7 }
        int externalValidationCount = poolType.validationCount()
        def targetIssuesBefore = KlumObjectSupport.of(external).validation.result.issues.toList()
        String externalModelPathBefore = KlumObjectSupport.of(external).modelPath
        def plan = planType.Create.With {
            owned(kafkaType) { slots 8 }
            delegate.external = external
            delegate.sameRoot = delegate.owned
        }

        then:
        plan.owned.class == kafkaType
        plan.external.is(external)
        plan.sameRoot.is(plan.owned)
        plan.optional == null
        !external.is(plan.owned)
        planType.CALLBACKS == ['owned', 'external', 'sameRoot']
        externalValidationCount == 1
        poolType.validationCount() == externalValidationCount + 1
        ['owned', 'external', 'sameRoot'].every { String name ->
            Field field = planType.getDeclaredField(name)
            Annotation bounds = field.getAnnotation(boundsType)
            field.accessible = true
            callback.call(bounds, field.get(plan)) == null
        }
        KlumObjectSupport.of(external).validation.result.issues.toList() == targetIssuesBefore
        KlumObjectSupport.of(external).modelPath == externalModelPathBefore

        when: 'the optional relationship resolves to an external completed Model'
        def externalOptional = planType.Create.With {
            delegate.optional = external
        }

        then:
        externalOptional.optional.is(external)
        planType.CALLBACKS == ['owned', 'external', 'sameRoot', 'optional']
        poolType.validationCount() == externalValidationCount + 1
        KlumObjectSupport.of(external).modelPath == externalModelPathBefore
        KlumObjectSupport.of(external).validation.result.issues.toList() == targetIssuesBefore
        callback.call(planType.getDeclaredField('optional').getAnnotation(boundsType), externalOptional.optional) == null

        when: 'the optional relationship resolves to a same-session owned Model'
        def ownedOptional = planType.Create.With {
            owned(kafkaType) { slots 9 }
            delegate.optional = delegate.owned
        }

        then:
        ownedOptional.optional.is(ownedOptional.owned)
        planType.CALLBACKS == ['owned', 'external', 'sameRoot', 'optional', 'owned', 'optional']
        poolType.validationCount() == externalValidationCount + 2
        callback.call(planType.getDeclaredField('optional').getAnnotation(boundsType), ownedOptional.optional) == null

        and: 'the compiled schema can be loaded without the source GroovyClassLoader'
        URLClassLoader binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], oldLoader)
        Class<?> binaryPlan = binaryLoader.loadClass('probe.Plan')
        Class<?> binaryBounds = binaryLoader.loadClass('probe.Bounds')
        Annotation binaryMarker = binaryBounds.getAnnotation(binaryLoader.loadClass('probe.ProbeRule'))
        Closure binaryCallback = instantiateCallback(binaryMarker.value())
        binaryPlan.classLoader.is(binaryLoader)
        binaryBounds.classLoader.is(binaryLoader)
        binaryCallback.class.classLoader.is(binaryLoader)
        binaryLoader.loadClass('probe.ProbeRule').getDeclaredMethod('value').genericReturnType.typeName == 'java.lang.Class<?>'
        binaryCallback.parameterTypes.toList() == [binaryBounds, binaryLoader.loadClass('probe.Pool')]
        binaryPlan.getDeclaredField('external').getAnnotation(binaryBounds).minimum() == 5
        binaryCallback.call(binaryPlan.getDeclaredField('external').getAnnotation(binaryBounds),
                binaryLoader.loadClass('probe.KafkaPool').Create.With { slots 7 }) == null
    }

    def 'the test-only schema guard rejects a callback narrower than owned or LINK declared targets'() {
        given:
        compilerConfiguration.addCompilationCustomizers(new ProbeSignatureCheck())

        when:
        createNonDslClass """
            package probe

            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Field
            import com.blackbuild.klum.ast.FieldType
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.ANNOTATION_TYPE)
            @interface ProbeRule { Class<?> value() }

            @ProbeRule({ NarrowBounds bounds, KafkaPool pool -> assert pool.slots > 0 })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface NarrowBounds {}

            @DSL abstract class Pool { int slots }
            @DSL class KafkaPool extends Pool {}

            @DSL class Plan {
                @NarrowBounds ${linkAnnotation} Pool target
            }
        """

        then:
        MultipleCompilationErrorsException error = thrown()
        error.errorCollector.errors.any { it instanceof SyntaxErrorMessage &&
                it.cause.message.contains('Callback target probe.KafkaPool cannot accept declared relationship probe.Pool') }

        where:
        linkAnnotation << ['', '@Field(FieldType.LINK)']
    }

    def 'a separately compiled schema consumer rejects the same narrower callback'() {
        given:
        createNonDslClass '''
            package probe

            import com.blackbuild.klum.ast.DSL
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.ANNOTATION_TYPE)
            @interface ProbeRule { Class<?> value() }

            @ProbeRule({ NarrowBounds bounds, KafkaPool pool -> assert pool.slots > 0 })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface NarrowBounds {}

            @DSL abstract class Pool { int slots }
            @DSL class KafkaPool extends Pool {}
        '''
        URLClassLoader binaryParent = new URLClassLoader(
                [compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], oldLoader)
        CompilerConfiguration consumerConfiguration = new CompilerConfiguration()
        consumerConfiguration.targetDirectory = tempFolder.newFolder('binary-consumer')
        consumerConfiguration.addCompilationCustomizers(new ProbeSignatureCheck())
        GroovyClassLoader consumerLoader = new GroovyClassLoader(binaryParent, consumerConfiguration)

        when:
        consumerLoader.parseClass("""
            package consumer

            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Field
            import com.blackbuild.klum.ast.FieldType
            import probe.NarrowBounds
            import probe.Pool

            @DSL class Plan {
                @NarrowBounds ${linkAnnotation} Pool target
            }
        """)

        then:
        MultipleCompilationErrorsException error = thrown()
        error.errorCollector.errors.any { it instanceof SyntaxErrorMessage &&
                it.cause.message.contains('Callback target probe.KafkaPool cannot accept declared relationship probe.Pool') }

        where:
        linkAnnotation << ['', '@Field(FieldType.LINK)']
    }

    private static boolean acceptsDeclaredTarget(Class<?> callbackType, Field field) {
        Class<?>[] parameters = instantiateCallback(callbackType).parameterTypes
        parameters.length == 2 && parameters[1].isAssignableFrom(field.type)
    }

    private static Closure instantiateCallback(Class<?> callbackType) {
        assert Closure.isAssignableFrom(callbackType)
        ClosureHelper.createClosureInstance((Class<? extends Closure>) callbackType)
    }

    /** S0-only stand-in for the planned Schema compiler check; it contributes no production API. */
    private static class ProbeSignatureCheck extends CompilationCustomizer {
        ProbeSignatureCheck() { super(CompilePhase.SEMANTIC_ANALYSIS) }

        @Override
        void call(SourceUnit source, GeneratorContext context, ClassNode owner) {
            owner.fields.each { field ->
                field.annotations.each { domain ->
                    if (!domain.classNode.name.startsWith('probe.')) return
                    def marker = domain.classNode.annotations.find { it.classNode.name == 'probe.ProbeRule' }
                    def expression = marker?.getMember('value')
                    ClassNode target
                    if (expression instanceof ClosureExpression) {
                        if (expression.parameters.length != 2) return
                        target = expression.parameters[1].type
                    } else {
                        Class<?> annotationType = domain.classNode.typeClass
                        Class<?> markerType = annotationType.classLoader.loadClass('probe.ProbeRule')
                        Annotation binaryMarker = annotationType.getAnnotation(markerType)
                        if (binaryMarker == null) return
                        Class<?>[] parameters = instantiateCallback(binaryMarker.value()).parameterTypes
                        if (parameters.length != 2) return
                        target = ClassHelper.make(parameters[1])
                    }
                    ClassNode declared = field.type
                    if (target != declared && !declared.isDerivedFrom(target) && !declared.implementsInterface(target))
                        source.addError(new SyntaxException(
                                "Callback target ${target.name} cannot accept declared relationship ${declared.name}",
                                field.lineNumber, field.columnNumber))
                }
            }
        }
    }
}
