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
import spock.lang.Issue

@Issue("706")
class FactoryMethodProjectionTest extends AbstractDSLSpec {

    private static final STATIC_FACTORY_METHOD_MESSAGE = "Public methods declared on a DSL Factory are exposed through Create and must be instance methods. Remove static, or move a model-level static converter out of Factory."

    def "rejects a public static method declared on a custom Factory"() {
        when:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumFactory

            @DSL
            class Product {
                static class Factory extends KlumFactory.Unkeyed<Product> {
                    protected Factory() { super(Product) }

                    public static Product named(String name) {
                        Product.Create.With(name: name)
                    }
                }

                String name
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains(STATIC_FACTORY_METHOD_MESSAGE)
        error.message.contains("@ line 9")
    }

    def "projects a public instance method declared on a custom Factory through Create"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumFactory

            @DSL
            class Product {
                static class Factory extends KlumFactory.Unkeyed<Product> {
                    protected Factory() { super(Product) }

                    Product named(String name) {
                        Product.Create.With(name: name)
                    }
                }

                String name
            }
        '''

        expect:
        clazz.Create.named('catalog').name == 'catalog'
    }

    def "allows non-public static Factory helpers"() {
        when:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumFactory
            import groovy.transform.PackageScope

            @DSL
            class Product {
                static class Factory extends KlumFactory.Unkeyed<Product> {
                    protected Factory() { super(Product) }

                    private static String privateHelper() { 'private' }
                    protected static String protectedHelper() { 'protected' }
                    @PackageScope static String packageHelper() { 'package' }
                }
            }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "allows model-level static converters"() {
        when:
        createClass '''
            @DSL
            class Product {
                String name

                static Product fromName(String name) {
                    Product.Create.With(name: name)
                }
            }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
    }
}
