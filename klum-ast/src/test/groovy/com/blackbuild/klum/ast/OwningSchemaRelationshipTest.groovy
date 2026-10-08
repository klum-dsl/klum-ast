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
import spock.lang.Issue

@Issue('856')
class OwningSchemaRelationshipTest extends AbstractDSLSpec {

    def 'completed provider exposes its inherited owning declaration without an Owner value'() {
        given:
        createNonDslClass '''
            package metadata
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Source { String value() }
            @DSL class Facts { String value }
            @DSL class EnvironmentBase { @Source('primary') Facts primary }
            @DSL class Environment extends EnvironmentBase {}
        '''

        when:
        instance = create('Environment') { primary { value 'available' } }
        def structure = KlumObjectSupport.of(instance.primary).structure
        def relationship = structure.owningRelationship.orElseThrow()

        then:
        relationship.declaringClass.is(getClass('EnvironmentBase'))
        relationship.name == 'primary'
        relationship.getAnnotation(getClass('Source')).orElseThrow().value() == 'primary'
        structure.getOwningRelationshipAnnotation(getClass('Source')).orElseThrow().value() == 'primary'
        structure.directOwners.empty
        KlumObjectSupport.of(instance).structure.owningRelationship.empty
    }
    def 'null completed receiver names the programming error'() {
        when:
        KlumObjectSupport.of(null)

        then:
        def error = thrown(NullPointerException)
        error.message == 'object'
    }

}
