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
package com.blackbuild.klum.ast.compiler.internal.ast

/** Exact source corpus shared by WIRE regression tests and the binary schema comparison. */
class StructuredInputContractFixtures {
    static final String ADMISSION = '''
            package admission

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            import com.fasterxml.jackson.annotation.JsonIgnore
            import com.fasterxml.jackson.annotation.JsonProperty

            enum Region { EU, US }

            @DSL class Base {
                String inheritedLabel
                @JsonProperty('renamed') Region region
                @JsonIgnore(true) String secret
                @JsonIgnore(true) @JsonProperty('still-hidden') String ignoredRename
                @JsonIgnore String implicitSecret
                @JsonIgnore(false) @JsonProperty('') String fallback

                @Builder.Method
                void inheritedOperation(String level) { }
            }

            @DSL class Child {
                @Key String name
                String value
            }

            @DSL class Parent extends Base {
                List<String> labels
                Map<String, String> annotations
                Map<String, List<String>> nestedAnnotations
                Child child
                Map<String, Child> children

                @Builder.Method
                void promote(String level) { }
            }
        '''

    static final String REJECTIONS = '''
            package rejection

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL class Child {
                @Key String name
            }

            @DSL abstract class AbstractChild {
            }

            @DSL class Parent {
                List<Child> unkeyedChildren
                AbstractChild abstractChild
                Map<Integer, Child> numberedChildren
                char marker

                @JsonProperty('duplicate') String first
                @JsonProperty('duplicate') String second

                @Builder.Method
                void reset() { }

                @Builder.Method
                void replace(String first, String second) { }

                @Builder.Method
                void raw(Map values) { }

                @JsonProperty('operation') @Builder.Method
                void firstOperation(String value) { }

                @JsonProperty('operation') @Builder.Method
                void secondOperation(String value) { }
            }
        '''

    static final String NUMERIC_REJECTIONS = '''
            package numericboundary

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL

            final class OpaqueNumber extends Number {
                private OpaqueNumber() { throw new AssertionError('No input construction route') }
                int intValue() { throw new UnsupportedOperationException() }
                long longValue() { throw new UnsupportedOperationException() }
                float floatValue() { throw new UnsupportedOperationException() }
                double doubleValue() { throw new UnsupportedOperationException() }
            }

            @DSL class Configuration {
                OpaqueNumber budget
                List<OpaqueNumber> budgets
                Map<String, OpaqueNumber> limits
                @Builder.Method void allocate(OpaqueNumber value) { budget = value }
            }
        '''

    static final String POPULATION = '''
            package shadowedpopulation
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Field
            @DSL class Configuration {
                @Field(members='addValue') NavigableSet<String> values
                @Builder.Method void values(Iterable<String> values) { throw new AssertionError('Authored override selected') }
            }
        '''

    static String collection(String declaration) {
        """
            package collectioncontracts
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Field
            @DSL class Configuration {
                @Field(members='addValue') $declaration values
            }
            enum Mode { A, Z }
        """
    }
    static String operationArgument(String target) {
        """
            package restrictedarguments
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                NavigableSet<String> fieldValues
                @Builder.Method void consume($target value) { }
            }
            enum Mode { A, Z }
        """
    }
}
