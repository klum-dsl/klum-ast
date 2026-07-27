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

import com.blackbuild.groovy.configdsl.transform.ast.FieldAstValidator
import com.blackbuild.groovy.configdsl.transform.ast.mutators.WriteAccessMethodCheck
import com.blackbuild.klum.ast.util.copy.Overwrite
import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues
import com.blackbuild.klum.ast.util.layer3.DefaultValuesCheck
import com.blackbuild.klum.ast.validation.CheckDslAnnotation
import com.blackbuild.klum.ast.validation.CheckForPrimitiveBoolean
import com.blackbuild.klum.ast.validation.OverwriteMapCheck
import com.blackbuild.klum.ast.validation.OverwriteSingleCheck
import com.blackbuild.klum.ast.validation.ValidateAnnotationCheck
import com.blackbuild.klum.cast.checks.impl.KlumCastCheck
import com.blackbuild.klum.cast.KlumCastValidator
import com.blackbuild.klum.cast.spi.Check
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@Issue("460")
class KlumCastCheckSpiMigrationTest extends AbstractDSLSpec {

    def "all name-bound KlumAST checks implement the durable SPI directly"() {
        expect:
        Check.isAssignableFrom(checkType)
        !KlumCastCheck.isAssignableFrom(checkType)
        checkType.getDeclaredConstructor()
        newInstance(checkType).diagnosticDefinitions*.code == [checkType.name]

        where:
        checkType << [
                FieldAstValidator,
                WriteAccessMethodCheck,
                DefaultValuesCheck,
                CheckDslAnnotation,
                CheckForPrimitiveBoolean,
                OverwriteMapCheck,
                OverwriteSingleCheck,
                ValidateAnnotationCheck,
        ]
    }

    def "annotation artifact retains the eight supported name bindings"() {
        expect:
        annotationTypes
                .collect { it.getAnnotationsByType(KlumCastValidator).toList() }
                .flatten()
                .collect { it.value() }
                .toSet() == checkTypes*.name.toSet()

        where:
        annotationTypes = [Field, DSL, WriteAccess, Validate, Overwrite.Single, Overwrite.Map, DefaultValues]
        checkTypes = [
                FieldAstValidator,
                WriteAccessMethodCheck,
                DefaultValuesCheck,
                CheckDslAnnotation,
                CheckForPrimitiveBoolean,
                OverwriteMapCheck,
                OverwriteSingleCheck,
                ValidateAnnotationCheck,
        ]
    }

    def "name-bound validation check emits a positioned structured diagnostic"() {
        when:
        createClass '''
            @DSL
            class BrokenModel {
                @Validate boolean enabled
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains(CheckForPrimitiveBoolean.name)
        error.message.contains("Validation is not valid on 'boolean' fields")
    }

    private static Check newInstance(Class<? extends Check> checkType) {
        checkType.getDeclaredConstructor().newInstance()
    }
}
