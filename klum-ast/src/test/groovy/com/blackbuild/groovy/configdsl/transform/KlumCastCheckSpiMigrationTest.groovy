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

import com.blackbuild.klum.ast.compiler.internal.ast.FieldAstValidator
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessMethodCheck
import com.blackbuild.klum.ast.copy.Overwrite
import com.blackbuild.klum.ast.layer3.DefaultValues
import com.blackbuild.klum.ast.compiler.internal.layer3.DefaultValuesCheck
import com.blackbuild.klum.ast.compiler.internal.validation.CheckDslAnnotation
import com.blackbuild.klum.ast.compiler.internal.validation.CheckForPrimitiveBoolean
import com.blackbuild.klum.ast.compiler.internal.validation.OverwriteMapCheck
import com.blackbuild.klum.ast.compiler.internal.validation.OverwriteSingleCheck
import com.blackbuild.klum.ast.compiler.internal.validation.OverwriteStrategiesCheck
import com.blackbuild.klum.ast.compiler.internal.validation.ValidateAnnotationCheck
import com.blackbuild.klum.cast.KlumCastValidator
import com.blackbuild.klum.cast.spi.Check
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.Unroll

@Issue("460")
class KlumCastCheckSpiMigrationTest extends AbstractDSLSpec {

    private static final CHECK_TYPES = [
            FieldAstValidator,
            WriteAccessMethodCheck,
            DefaultValuesCheck,
            CheckDslAnnotation,
            CheckForPrimitiveBoolean,
            OverwriteMapCheck,
            OverwriteSingleCheck,
            OverwriteStrategiesCheck,
            ValidateAnnotationCheck,
    ]

    def "all name-bound KlumAST checks implement the durable SPI directly"() {
        expect:
        Check.isAssignableFrom(checkType)
        checkType.superclass == Object
        checkType.getDeclaredConstructor()
        newInstance(checkType).diagnosticDefinitions*.code == [checkType.name]

        where:
        checkType << CHECK_TYPES
    }

    def "annotation artifact retains the nine supported name bindings"() {
        expect:
        annotationTypes
                .collect { it.getAnnotationsByType(KlumCastValidator).toList() }
                .flatten()
                .collect { it.value() }
                .toSet() == CHECK_TYPES*.name.toSet()

        where:
        annotationTypes = [Field, DSL, WriteAccess, Validate, Overwrite, Overwrite.Single, Overwrite.Map, DefaultValues]
    }

    @Unroll
    def "name-bound #checkType.simpleName check emits a positioned structured diagnostic"() {
        expect:
        def error = compilationError(source)
        error.message.contains(checkType.name)
        error.message.contains(message)
        error.message.contains("@ line")

        where:
        checkType                 | message                                         | source
        CheckDslAnnotation        | "defaultImpl must be a subtype"                | '''
            @DSL(defaultImpl = String)
            interface BrokenModel { }
        '''
        FieldAstValidator          | "Default Implementation must be an DSL-Object" | '''
            @DSL
            class BrokenModel {
                @Field(defaultImpl = String) CharSequence value
            }
        '''
        WriteAccessMethodCheck     | "Lifecycle methods must not be private"        | '''
            @DSL
            class BrokenModel {
                @PostCreate private void initialize() { }
            }
        '''
        CheckForPrimitiveBoolean   | "Validation is not valid on 'boolean' fields"  | '''
            @DSL
            class BrokenModel {
                @Validate boolean enabled
            }
        '''
        ValidateAnnotationCheck    | "@Validate can only be used on non-static fields" | '''
            @DSL
            class BrokenModel {
                @Validate static String enabled
            }
        '''
        OverwriteSingleCheck       | "MERGE is only allowed for DSL objects"        | '''
            import com.blackbuild.klum.ast.copy.Overwrite
            import com.blackbuild.klum.ast.copy.OverwriteStrategy

            @DSL
            class BrokenModel {
                @Overwrite.Single(OverwriteStrategy.Single.MERGE) String title
            }
        '''
        OverwriteMapCheck          | "MERGE_VALUES is only allowed for DSL objects" | '''
            import com.blackbuild.klum.ast.copy.Overwrite
            import com.blackbuild.klum.ast.copy.OverwriteStrategy

            @DSL
            class BrokenModel {
                @Overwrite.Map(OverwriteStrategy.Map.MERGE_VALUES) Map<String, String> values
            }
        '''
    }

    def "name-bound DefaultValues check emits a positioned structured diagnostic"() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.layer3.DefaultValues
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE])
            @DefaultValues
            @interface BrokenDefaults {
                String value()
            }
        '''

        when:
        createClass '''
            @BrokenDefaults
            @DSL
            class BrokenModel { }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains(DefaultValuesCheck.name)
        error.message.contains("does have a 'value' member")
        error.message.contains("@ line")
    }

    @Unroll
    def "Field defaultImpl #description is rejected with a structured diagnostic"() {
        expect:
        def error = compilationError(source)
        error.message.contains(FieldAstValidator.name)
        error.message.contains(message)
        error.message.contains("@ line")

        where:
        description       | message                                                | source
        "targets final type" | "is final and cannot be overridden"                | '''
            @DSL
            class BrokenModel {
                @Field(defaultImpl = String) String value
            }
        '''
        "is not assignable" | "is not a valid subtype"                           | '''
            @DSL
            class DefaultImplementation { }

            @DSL
            class BrokenModel {
                @Field(defaultImpl = DefaultImplementation) Runnable value
            }
        '''
        "is used on a LINK method" | "Default Implementation is not allowed on LINK fields" | '''
            @DSL
            class DefaultImplementation { }

            @DSL
            class BrokenModel {
                @Field(value = FieldType.LINK, defaultImpl = DefaultImplementation)
                void linked(DefaultImplementation value) { }
            }
        '''
        "changes keyedness" | "is keyed, but field"                              | '''
            @DSL
            abstract class Base { }

            @DSL
            class KeyedImplementation extends Base {
                @Key String id
            }

            @DSL
            class BrokenModel {
                @Field(defaultImpl = KeyedImplementation) Base value
            }
        '''
        "is not instantiable" | "is not instantiable"                              | '''
            @DSL
            abstract class AbstractImplementation { }

            @DSL
            class BrokenModel {
                @Field(defaultImpl = AbstractImplementation) AbstractImplementation value
            }
        '''
    }

    def "zero-argument virtual field keeps its parameter-contract diagnostic"() {
        expect:
        def error = compilationError '''
            @DSL
            class DefaultImplementation { }

            @DSL
            class BrokenModel {
                @Field(defaultImpl = DefaultImplementation)
                void value() { }
            }
        '''
        error.message.contains("must have 1 parameters")
        !error.message.contains("Technical failure")
    }

    private static Check newInstance(Class<? extends Check> checkType) {
        checkType.getDeclaredConstructor().newInstance()
    }

    private MultipleCompilationErrorsException compilationError(String source) {
        try {
            createClass(source)
        } catch (MultipleCompilationErrorsException error) {
            return error
        }
        throw new AssertionError("Expected compilation to fail")
    }
}
