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
import com.blackbuild.klum.ast.runtime.LifecycleCreationHandler
import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler
import com.blackbuild.klum.ast.runtime.LifecycleFieldContext
import groovy.transform.CompileStatic
import spock.lang.Issue

@Issue('867')
class LifecycleParticipantRoutesTest extends AbstractDSLSpec {
    def 'value-only Templates defer field participants to each recipient lifecycle in #phase'() {
        given:
        schema(phase)

        when:
        def template = Application.Create.Template.With { service { value 'recipe' } }
        def first = Application.Create.With { copyFrom template }
        def second = Application.Create.With { copyFrom template }

        then:
        template.service.value == 'recipe'
        first.service.value == 'recipe:service'
        second.service.value == 'recipe:service'
        !first.service.is(second.service)
        first.service.owner.is(first)
        second.service.owner.is(second)

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }

    def 'existing FromMap root dispatches participants once in #phase'() {
        given:
        schema(phase)

        when:
        def result = Application.Create.FromMap([service: [value: 'mapped']])

        then:
        result.service.value == 'mapped:service'
        result.service.owner.is(result)

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }

    def 'late-created children retain session and declaration authority without replay in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = $phase, handler = Supply)
            @LifecycleMutator(phase = $phase, handler = Inspect)
            @interface Supplied {}
            class Supply implements LifecycleCreationHandler<Supplied> {
                KlumBuilder<?> create(LifecycleFieldContext<Supplied> c) {
                    Service.Create.AsBuilder().FromMap([value: 'late'])
                }
            }
            class Inspect implements LifecycleMutationHandler<Supplied> {
                void mutate(LifecycleMutationContext<Supplied> c) {
                    def relationship = KlumBuilderSupport.of(c.targetBuilder).structure.owningRelationship.orElseThrow()
                    assert relationship.name == 'service'
                    assert relationship.declaringClass == Application
                    assert relationship.getAnnotation(Supplied).present
                    Service.Create.narrowBuilder(c.targetBuilder).value('claimed')
                }
            }
            @DSL class Service {
                String value
                boolean early
                @AutoCreate void markEarly() { early = true }
            }
            @DSL class Application { @Supplied Service service }
        """

        when:
        def result = Application.Create.One()

        then:
        result.service.value == 'claimed'
        !result.service.early

        where:
        phase << ['AutoLink', 'Default', 'PostTree']
    }

    def 'completed serialization excludes participant and Builder state while preserving cycles'() {
        given:
        schema('AutoLink')
        def completed = Application.Create.With {
            def child = service { value 'serialized' }
            child.peer(child)
        }
        def bytes = new ByteArrayOutputStream()

        when:
        new ParticipantCheckingOutput(bytes).withCloseable { it.writeObject(completed) }
        def restored = new SchemaInput(new ByteArrayInputStream(bytes.toByteArray()), loader).withCloseable { it.readObject() }

        then:
        restored.service.value == 'serialized:service'
        restored.service.peer.is(restored.service)
        restored.service.owner.is(restored)
    }

    def 'HANDLE probe exposes the completed LINK typed relationship read gap'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import groovy.transform.CompileStatic
            import org.codehaus.groovy.runtime.InvokerHelper
            @DSL class Facts { String value }
            @DSL class Service { Facts facts }
            @DSL class Application {
                @Field(FieldType.LINK) Service service
                boolean dynamicRead
                boolean typedReadMissing
                @AutoLink @CompileStatic void inspect() {
                    def wrapper = getService()
                    dynamicRead = InvokerHelper.getProperty(wrapper, 'facts') instanceof Facts
                    typedReadMissing = wrapper.getFacts() == null
                }
            }
        """
        def completed = Service.Create.With { facts { value 'completed' } }

        when:
        def result = Application.Create.With { service completed }

        then:
        result.dynamicRead
        result.typedReadMissing
        result.service.is(completed)
        result.service.facts.value == 'completed'
    }

    private void schema(String phase) {
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = $phase, handler = Configure,
                onSealed = LifecycleMutator.SealedPolicy.SKIP)
            @interface Configured {}
            class Configure implements LifecycleMutationHandler<Configured> {
                boolean used
                void mutate(LifecycleMutationContext<Configured> c) {
                    assert !used
                    used = true
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':' + c.fieldName)
                }
            }
            @DSL class Service {
                String value
                @Owner Application owner
                @Field(FieldType.LINK) Service peer
            }
            @DSL class Application { @Configured Service service }
        """
    }

    @CompileStatic
    private static class ParticipantCheckingOutput extends ObjectOutputStream {
        ParticipantCheckingOutput(OutputStream stream) {
            super(stream)
            enableReplaceObject(true)
        }
        @Override
        protected Object replaceObject(Object value) {
            if (value instanceof KlumBuilder || value instanceof LifecycleFieldContext
                || value instanceof LifecycleCreationHandler || value instanceof LifecycleMutationHandler) {
                throw new InvalidObjectException('Participant or Builder state entered completed serialization')
            }
            value
        }
    }

    @CompileStatic
    private static class SchemaInput extends ObjectInputStream {
        private final ClassLoader schemaLoader
        SchemaInput(InputStream stream, ClassLoader schemaLoader) {
            super(stream)
            this.schemaLoader = schemaLoader
        }
        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor) {
            try {
                schemaLoader.loadClass(descriptor.name)
            } catch (ClassNotFoundException ignored) {
                super.resolveClass(descriptor)
            }
        }
    }
}
