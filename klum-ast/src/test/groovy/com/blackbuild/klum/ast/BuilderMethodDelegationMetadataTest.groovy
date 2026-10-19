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

import com.blackbuild.annodocimal.generator.ProjectionPolicy
import com.blackbuild.annodocimal.generator.SourceProjector
import spock.lang.Issue

import static com.blackbuild.klum.ast.TestHelper.delegatesToPointsTo

@Issue('923')
class BuilderMethodDelegationMetadataTest extends AbstractDSLSpec {

    def "projects #annotation closure metadata onto the public Builder and source mirror"() {
        given:
        createClass """
            package delegation

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DelegatesToBuilder
            import com.blackbuild.klum.ast.DelegatesToRW

            @DSL class Catalog {
                Map<String, Product> products

                @Builder.Method
                void multiProduct(String id, @$annotation(MultiProduct) Closure body) {
                    product(MultiProduct.Create, id, body)
                }
            }

            @DSL class Product {
                @Key String id
            }

            @DSL class MultiProduct extends Product {
                int quantity
            }
        """

        when:
        def catalog = clazz.Create.With {
            multiProduct('bundle') { quantity 3 }
        }
        def method = getClass('delegation.Catalog_DSL$Builder').getMethod('multiProduct', String, Closure)

        then: 'forwarding through the keyed Map relationship still constructs the selected subtype'
        catalog.products.bundle.class == getClass('delegation.MultiProduct')
        catalog.products.bundle.quantity == 3

        and: 'public bytecode retains the exact public delegate, strategy and generic metadata'
        delegatesToPointsTo(method.parameterAnnotations[1], 'delegation.MultiProduct_DSL.Builder')

        when:
        File mirrorRoot = new File(tempFolder.root, 'delegation-mirrors')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(
                new File(compilerConfiguration.targetDirectory, 'delegation/Catalog_DSL.class').toPath(),
                mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'delegation/Catalog_DSL.java').text
        String helper = (mirror =~ /(?s)void multiProduct\(.*?;/)[0]

        then: 'the IDE mirror retains the standard annotation without exposing the hidden Builder'
        helper.contains('@DelegatesTo(')
        helper.contains('value = MultiProduct_DSL.Builder.class')
        helper.contains('strategy = 3')
        !helper.contains('MultiProduct.Builder')
        !helper.contains('MultiProduct$Builder')

        where:
        annotation << ['DelegatesToBuilder', 'DelegatesToRW']
    }

    def "uses the owning public Builder for #annotation without a target"() {
        given:
        createClass """
            package implicitdelegate

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DelegatesToBuilder
            import com.blackbuild.klum.ast.DelegatesToRW

            @DSL class Catalog {
                @Builder.Method
                void configure(@$annotation Closure body) { }
            }
        """

        when:
        def method = getClass('implicitdelegate.Catalog_DSL$Builder').getMethod('configure', Closure)

        then:
        delegatesToPointsTo(method.parameterAnnotations[0], 'implicitdelegate.Catalog_DSL.Builder')

        where:
        annotation << ['DelegatesToBuilder', 'DelegatesToRW']
    }

    def "preserves an explicit standard delegation annotation and its generic target metadata"() {
        given:
        createClass '''
            package explicitdelegate

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DelegatesToBuilder

            @DSL class Catalog {
                @Builder.Method
                void configure(@DelegatesTo.Target('values') List<String> values,
                               @DelegatesToBuilder
                               @DelegatesTo(target = 'values', genericTypeIndex = 0,
                                            strategy = Closure.DELEGATE_FIRST) Closure body) { }
            }
        '''

        when:
        def method = getClass('explicitdelegate.Catalog_DSL$Builder').getMethod('configure', List, Closure)
        def delegation = method.parameters[1].getAnnotation(DelegatesTo)

        then:
        delegation.value() == DelegatesTo.Target
        delegation.strategy() == Closure.DELEGATE_FIRST
        delegation.genericTypeIndex() == 0
        delegation.target() == 'values'
        method.parameters[0].getAnnotation(DelegatesTo.Target).value() == 'values'
    }
}
