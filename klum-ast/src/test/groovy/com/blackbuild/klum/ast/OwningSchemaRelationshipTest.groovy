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

import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.KlumSchemaException
import spock.lang.Issue

@Issue('856')
class OwningSchemaRelationshipTest extends AbstractDSLSpec {

    def 'completed provider exposes its inherited owning declaration without an Owner value'() {
        given:
        createNonDslClass """
            package metadata
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Source { String value() }
            @DSL class Facts { String value; $owners }
            @DSL class EnvironmentBase { @Source('primary') Facts primary }
            @DSL class Environment extends EnvironmentBase {}
        """

        when:
        instance = create('Environment') { primary { value 'available' } }
        def structure = KlumObjectSupport.of(instance.primary).structure
        def relationship = structure.owningRelationship.orElseThrow()

        then:
        relationship.declaringClass.is(getClass('EnvironmentBase'))
        relationship.name == 'primary'
        relationship.getAnnotation(getClass('Source')).orElseThrow().value() == 'primary'
        structure.getOwningRelationshipAnnotation(getClass('Source')).orElseThrow().value() == 'primary'
        structure.directOwners.size() == ownerCount
        KlumObjectSupport.of(instance).structure.owningRelationship.empty

        where:
        owners                                                                    | ownerCount
        ''                                                                        | 0
        '@Owner EnvironmentBase first; @Owner EnvironmentBase second'             | 1
        '@Owner(transitive = true) EnvironmentBase ancestor'                       | 0
        '@Owner(converter = { EnvironmentBase owner -> "converted" }) String owner' | 0
    }
    def 'null completed receiver names the programming error'() {
        when:
        KlumObjectSupport.of(null)

        then:
        def error = thrown(NullPointerException)
        error.message == 'object'
    }

    def 'descriptors use declaration identity and direct annotation lookup on private fields'() {
        given:
        createNonDslClass '''
            package metadata
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Source { String value() }
            @DSL class Facts {}
            @DSL class Environment {
                @Source('private') private Facts primary
                Facts other
            }
        '''
        def first = create('Environment') { primary {}; other {} }
        def second = create('Environment') { primary {} }
        def descriptor = KlumObjectSupport.of(first.primary).structure.owningRelationship.orElseThrow()
        def sameDeclaration = KlumObjectSupport.of(second.primary).structure.owningRelationship.orElseThrow()
        def otherDeclaration = KlumObjectSupport.of(first.other).structure.owningRelationship.orElseThrow()
        def sourceType = getClass('Source')

        when:
        def annotation = descriptor.getAnnotation(sourceType)

        then:
        descriptor == sameDeclaration
        descriptor.hashCode() == sameDeclaration.hashCode()
        descriptor != otherDeclaration
        descriptor != null
        descriptor != 'primary'
        annotation.orElseThrow().value() == 'private'
        descriptor.getAnnotation(Deprecated).empty
        KlumObjectSupport.of(first.other).structure.getOwningRelationshipAnnotation(sourceType).empty
        KlumObjectSupport.of(first).structure.getOwningRelationshipAnnotation(sourceType).empty

        when:
        descriptor.getAnnotation(null)

        then:
        def descriptorError = thrown(NullPointerException)
        descriptorError.message == 'annotationType'

        when:
        KlumObjectSupport.of(first).structure.getOwningRelationshipAnnotation(null)

        then:
        def structureError = thrown(NullPointerException)
        structureError.message == 'annotationType'

        when: 'same names loaded by a different loader remain different Schema identities'
        def isolated = new GroovyClassLoader(oldLoader)
        isolated.addClasspath(compilerConfiguration.targetDirectory.absolutePath)
        def another = isolated.loadClass('metadata.Environment').Create.With { primary {} }
        def anotherDescriptor = KlumObjectSupport.of(another.primary).structure.owningRelationship.orElseThrow()

        then:
        anotherDescriptor.declaringClass.name == descriptor.declaringClass.name
        anotherDescriptor != descriptor
        ([descriptor, sameDeclaration, anotherDescriptor] as Set).size() == 2

        cleanup:
        isolated?.close()
    }

    def 'an explicit corrupt retained declaration fails with Schema and member context'() {
        given:
        createClass '''
            package metadata
            @DSL class Environment { Facts primary }
            @DSL class Facts {}
        '''
        instance = create('Environment') { primary {} }
        // Test-local corruption simulates an explicit unreadable retained record; it is not an extension seam.
        def stateField = instance.primary.class.getDeclaredField('$state')
        stateField.accessible = true
        def state = stateField.get(instance.primary)
        def declarationField = state.class.getDeclaredField('owningRelationship')
        declarationField.accessible = true
        def declaration = declarationField.get(state)
        def nameField = declaration.class.getDeclaredField('name')
        nameField.accessible = true
        nameField.set(declaration, 'removedRelationship')

        when:
        KlumObjectSupport.of(instance.primary).structure.owningRelationship

        then:
        def error = thrown(KlumSchemaException)
        error.message.contains('metadata.Environment.removedRelationship')

        when:
        KlumObjectSupport.of(instance.primary).structure.getOwningRelationshipAnnotation(Deprecated)

        then:
        thrown(KlumSchemaException)
    }

}
