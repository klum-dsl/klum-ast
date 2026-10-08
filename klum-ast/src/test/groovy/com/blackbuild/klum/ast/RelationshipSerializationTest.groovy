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
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.KlumSchemaException
import com.blackbuild.klum.ast.runtime.internal.process.ConstructionSession
import com.blackbuild.klum.ast.runtime.internal.InternalKlumBuilder
import com.blackbuild.klum.ast.runtime.internal.KlumModelProxy
import com.blackbuild.klum.ast.runtime.internal.KlumTemplateProxy
import com.blackbuild.klum.ast.runtime.internal.SchemaRelationshipDeclaration
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import groovy.transform.CompileStatic
import spock.lang.Issue

import java.lang.annotation.Annotation
import java.lang.reflect.Field as ReflectionField

@Issue('856')
class RelationshipSerializationTest extends AbstractDSLSpec {
    def 'same-version ordinary serialization preserves cyclic LINK identity and inherited annotations'() {
        given:
        schema()
        def graph = create('Graph') {
            def a = first { value 'first' }
            def b = second { value 'second' }
            a.peer = b; b.peer = a
            related = [a, b, a]
        }

        when:
        def restored = roundTrip(graph)

        then:
        restored.first.peer.is(restored.second)
        restored.second.peer.is(restored.first)
        restored.related[0].is(restored.first)
        restored.related[2].is(restored.first)
        descriptor(restored.first) == descriptor(graph.first)
        descriptor(restored.first).declaringClass.is(getClass('GraphBase'))
        descriptor(restored.first).getAnnotation(getClass('Slot')).orElseThrow().value() == 'first'
        KlumObjectSupport.of(restored).structure.owningRelationship.empty
    }

    def 'serializing an isolated subtree retains its declaration without serializing its old owner instance'() {
        given:
        schema()
        def graph = create('Graph') { first { value 'isolated' } }

        when:
        def restored = roundTrip(graph.first, graph.class)

        then:
        restored.value == 'isolated'
        descriptor(restored) == descriptor(graph.first)
        descriptor(restored).getAnnotation(getClass('Slot')).orElseThrow().value() == 'first'
        KlumObjectSupport.of(restored).structure.directOwners.empty
    }

    def 'same-version Template serialization retains definition edges and replays into fresh recipient graphs'() {
        given:
        schema()
        def external = create('Graph') {
            def child = first { value 'external' }
            child.peer = child
        }
        def recipe = getClass('Graph').Create.Template.With {
            first { value 'recipe'; applyLater { result = value.toUpperCase() } }
            related = [external.first, external.first]
        }

        when:
        def restored = roundTrip(recipe)
        def recipientType = getClass('Recipient')
        def a = recipientType.Create.With { actual { copyFrom restored.first } }
        def b = recipientType.Create.With { actual { copyFrom restored.first } }
        def applied = getClass('Graph').Create.With { copyFrom restored }

        then:
        TemplateManager.isTemplate(restored)
        TemplateManager.isTemplate(restored.first)
        !TemplateManager.isTemplate(restored.related[0])
        restored.related[0].is(restored.related[1])
        restored.related[0].peer.is(restored.related[0])
        descriptor(restored.related[0]).name == 'first'
        applied.related[0].is(restored.related[0])
        applied.related[0].is(applied.related[1])
        RelationshipTemplateCopyTest.retained(restored) == null
        RelationshipTemplateCopyTest.retained(restored.first).declaringClass.is(getClass('GraphBase'))
        RelationshipTemplateCopyTest.retained(restored.first).name == 'first'
        a.actual.result == 'RECIPE'
        b.actual.result == 'RECIPE'
        !a.actual.is(b.actual)
        !a.actual.is(restored.first)
        descriptor(a.actual).name == 'actual'
        descriptor(a.actual).declaringClass.is(recipientType)
        descriptor(a.actual).getAnnotation(getClass('Slot')).orElseThrow().value() == 'recipient'
    }

    def 'a serialized Template subtree retains its definition record without retaining its old definition owner'() {
        given:
        schema()
        def recipe = getClass('Graph').Create.Template.With {
            first { value 'isolated'; applyLater { result = value.toUpperCase() } }
        }

        when:
        def restored = roundTrip(recipe.first, recipe.class)
        def result = create('Recipient') { actual { copyFrom restored } }

        then:
        TemplateManager.isTemplate(restored)
        RelationshipTemplateCopyTest.retained(restored).declaringClass.is(getClass('GraphBase'))
        RelationshipTemplateCopyTest.retained(restored).name == 'first'
        result.actual.result == 'ISOLATED'
        descriptor(result.actual).name == 'actual'
    }

    def 'readable same-version absent metadata is empty while an explicit corrupt declaration remains an error'() {
        given:
        schema()
        def graph = create('Graph') { first { value 'no-record' }; second { value 'bad-record' } }
        def companionField = graph.first.class.getDeclaredField('$state')
        companionField.accessible = true
        def companion = companionField.get(graph.first)
        def declarationField = companion.class.getDeclaredField('owningRelationship')
        declarationField.accessible = true
        declarationField.set(companion, null)
        def declaration = RelationshipTemplateCopyTest.retained(graph.second)
        def nameField = declaration.class.getDeclaredField('name')
        nameField.accessible = true
        nameField.set(declaration, 'removed')

        when:
        def restored = roundTrip(graph)

        then:
        KlumObjectSupport.of(restored.first).structure.owningRelationship.empty
        KlumObjectSupport.of(restored.first).structure.getOwningRelationshipAnnotation(getClass('Slot')).empty

        when:
        descriptor(restored.second)

        then:
        def error = thrown(KlumSchemaException)
        error.message.contains('metadata.GraphBase.removed')
    }

    def 'serialized companion fields contain only declaration identity plus existing ordinary or recipe state'() {
        expect:
        ObjectStreamClass.lookup(SchemaRelationshipDeclaration).fields*.name == ['declaringClass', 'name']
        ObjectStreamClass.lookup(SchemaRelationshipDeclaration).serialVersionUID == 1L
        ObjectStreamClass.lookup(KlumModelProxy).fields*.name ==
            ['breadcrumbPath', 'executedValidators', 'metadata', 'model', 'modelPath', 'owningRelationship']
        ObjectStreamClass.lookup(KlumTemplateProxy).fields*.name ==
            ['breadcrumbPath', 'modelPath', 'object', 'owningRelationship', 'recipeState']

        cleanup:
        [KlumModelProxy, KlumTemplateProxy, InternalKlumBuilder.ModelState, SchemaRelationshipDeclaration].each { type ->
            def form = ObjectStreamClass.lookup(type)
            println "RM2 serialized form ${type.name}: UID=${form.serialVersionUID}; fields=${form.fields*.name}"
        }
    }

    private Object roundTrip(Object model, Class<?> forbiddenOwner = null) {
        def bytes = new ByteArrayOutputStream()
        new MetadataCheckingOutput(bytes, forbiddenOwner).withCloseable { it.writeObject(model) }
        new SchemaInput(new ByteArrayInputStream(bytes.toByteArray()), loader).withCloseable { it.readObject() }
    }

    @CompileStatic
    private static class MetadataCheckingOutput extends ObjectOutputStream {
        private final Class<?> forbiddenOwner

        MetadataCheckingOutput(OutputStream stream, Class<?> forbiddenOwner) {
            super(stream)
            this.forbiddenOwner = forbiddenOwner
            enableReplaceObject(true)
        }

        @Override
        protected Object replaceObject(Object value) {
            if (value instanceof KlumBuilder || value instanceof ConstructionSession
                || value instanceof ReflectionField || value instanceof Optional || value instanceof Annotation
                || (forbiddenOwner != null && forbiddenOwner.isInstance(value)))
                throw new InvalidObjectException('Metadata serialized forbidden construction, reflection, or owner state')
            return value
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
        protected Class<?> resolveClass(ObjectStreamClass description) {
            try { schemaLoader.loadClass(description.name) }
            catch (ClassNotFoundException ignored) { super.resolveClass(description) }
        }
    }

    private static Object descriptor(Object model) {
        KlumObjectSupport.of(model).structure.owningRelationship.orElseThrow()
    }

    private void schema() {
        createNonDslClass '''
            package metadata
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Slot { String value() }
            @DSL class Node {
                String value
                String result
                @Field(FieldType.LINK) Node peer
            }
            @DSL class GraphBase { @Slot('first') Node first; @Slot('second') Node second }
            @DSL class Graph extends GraphBase { @Field(FieldType.LINK) List<Node> related }
            @DSL class Recipient { @Slot('recipient') Node actual }
        '''
    }
}
