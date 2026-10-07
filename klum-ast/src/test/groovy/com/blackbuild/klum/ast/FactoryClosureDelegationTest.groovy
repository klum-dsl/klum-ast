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

import java.lang.reflect.Modifier

import groovy.lang.DelegatesTo
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue("842")
class FactoryClosureDelegationTest extends AbstractDSLSpec {

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/release/4.0.x/docs/user/Factory-Classes.md#forwarding-builder-closures")
    def "forwards a Builder closure directly through a statically checked custom Factory"() {
        given:
        createClass """
            import com.blackbuild.klum.ast.runtime.KlumFactory
            import groovy.transform.TypeChecked
            import groovy.transform.CompileStatic

            @DSL
            class OrderApplication {
                String environment
                String name

                @$checking
                static class Factory extends KlumFactory.Unkeyed<OrderApplication> {
                    protected Factory() { super(OrderApplication) }

                    OrderApplication ForEnvironment(String environment,
                            @DelegatesToBuilder(OrderApplication) Closure<?> applicationInput) {
                        With(environment: environment, applicationInput)
                    }
                }
            }
        """

        when:
        def client = createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class Client {
                static OrderApplication create() {
                    OrderApplication.Create.ForEnvironment('production') {
                        name 'orders'
                        assert resolveStrategy == Closure.DELEGATE_ONLY
                    }
                }
            }
        '''
        instance = client.create()

        then:
        instance.environment == 'production'
        instance.name == 'orders'

        where:
        checking << ['TypeChecked', 'CompileStatic']
    }

    def "retains legacy super and cast forwarding forms"() {
        given:
        createClass """
            import com.blackbuild.klum.ast.runtime.KlumFactory
            import groovy.transform.TypeChecked
            import groovy.transform.CompileStatic

            @DSL
            class OrderApplication {
                String environment
                String name

                @$checking
                static class Factory extends KlumFactory.Unkeyed<OrderApplication> {
                    protected Factory() { super(OrderApplication) }

                    OrderApplication ForEnvironment(String environment,
                            @DelegatesToBuilder(OrderApplication) Closure<?> applicationInput) {
                        $forwarding
                    }
                }
            }
        """

        when:
        def client = createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class Client {
                static OrderApplication create() {
                    OrderApplication.Create.ForEnvironment('production') {
                        name 'orders'
                        assert resolveStrategy == Closure.DELEGATE_ONLY
                    }
                }
            }
        '''
        instance = client.create()

        then:
        instance.environment == 'production'
        instance.name == 'orders'

        where:
        checking        | forwarding
        'TypeChecked'   | 'With(environment: environment, (Closure<?>) applicationInput)'
        'CompileStatic' | 'With(environment: environment, (Closure<?>) applicationInput)'
        'TypeChecked'   | 'super.With(environment: environment, applicationInput)'
        'CompileStatic' | 'super.With(environment: environment, applicationInput)'
        'TypeChecked'   | 'super.With(environment: environment, (Closure<?>) applicationInput)'
        'CompileStatic' | 'super.With(environment: environment, (Closure<?>) applicationInput)'
    }

    def "legacy super forwarding bypasses custom With overrides and supports the closure-only overload"() {
        given:
        createClass """
            import com.blackbuild.klum.ast.runtime.KlumFactory
            import groovy.transform.TypeChecked
            import groovy.transform.CompileStatic

            @DSL
            class OrderApplication {
                String environment
                String name

                @$checking
                static class Factory extends KlumFactory.Unkeyed<OrderApplication> {
                    protected Factory() { super(OrderApplication) }

                    @Override
                    OrderApplication With(Map<String, ?> values,
                            @DelegatesToBuilder(OrderApplication) Closure<?> body) {
                        super.With(environment: 'override', body)
                    }

                    OrderApplication ForEnvironment(String environment,
                            @DelegatesToBuilder(OrderApplication) Closure<?> body) {
                        super.With(environment: environment, body)
                    }

                    OrderApplication WithoutValues(@DelegatesToBuilder(OrderApplication) Closure<?> body) {
                        super.With(body)
                    }
                }
            }
        """

        when:
        def client = createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class Client {
                static List<OrderApplication> create() {
                    [OrderApplication.Create.ForEnvironment('production') {
                        name 'orders'
                        assert resolveStrategy == Closure.DELEGATE_ONLY
                    }, OrderApplication.Create.With(environment: 'production') { name 'orders' },
                    OrderApplication.Create.WithoutValues { name 'orders' }]
                }
            }
        '''
        def applications = client.create()

        then:
        applications*.environment == ['production', 'override', null]
        applications*.name == ['orders', 'orders', 'orders']
        getClass('OrderApplication_DSL$Factory').declaredMethods.every { !it.name.startsWith('$klum$superWith$') }
        def bridges = getClass('OrderApplication$Factory').declaredMethods.findAll { it.name.startsWith('$klum$superWith$') }
        bridges.size() == 2
        bridges.every {
            it.synthetic && Modifier.isProtected(it.modifiers)
        }

        where:
        checking << ['TypeChecked', 'CompileStatic']
    }

    def "preserves superclass dispatch from an inherited source Factory method"() {
        given:
        createClass """
            import com.blackbuild.klum.ast.runtime.KlumFactory
            import groovy.transform.TypeChecked
            import groovy.transform.CompileStatic

            @DSL
            class OrderApplication {
                String environment
                String name

                @$checking
                static class ParentFactory extends KlumFactory.Unkeyed<OrderApplication> {
                    protected ParentFactory() { super(OrderApplication) }

                    @Override
                    OrderApplication With(Map<String, ?> values,
                            @DelegatesToBuilder(OrderApplication) Closure<?> body) {
                        super.With(environment: 'parent override', body)
                    }

                    OrderApplication ForEnvironment(String environment,
                            @DelegatesToBuilder(OrderApplication) Closure<?> body) {
                        super.With(environment: environment, body)
                    }
                }

                @$checking
                static class Factory extends ParentFactory {
                    @Override
                    OrderApplication With(Map<String, ?> values,
                            @DelegatesToBuilder(OrderApplication) Closure<?> body) {
                        super.With(environment: 'child override', body)
                    }
                }
            }
        """

        when:
        def client = createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class Client {
                static List<OrderApplication> create() {
                    [OrderApplication.Create.ForEnvironment('production') {
                        name 'orders'
                        assert resolveStrategy == Closure.DELEGATE_ONLY
                    }, OrderApplication.Create.With(environment: 'production') { name 'orders' }]
                }
            }
        '''
        def applications = client.create()

        then:
        applications*.environment == ['production', 'parent override']
        applications*.name == ['orders', 'orders']

        where:
        checking << ['TypeChecked', 'CompileStatic']
    }

    def "preserves legacy super forwarding inherited from a generic source Factory"() {
        given:
        createClass """
            import com.blackbuild.klum.ast.runtime.KlumFactory
            import groovy.transform.TypeChecked
            import groovy.transform.CompileStatic

            @DSL(factory = OrderFactory)
            class OrderApplication {
                String environment
                String name
            }

            @$checking
            class ParentFactory<T> extends KlumFactory.Unkeyed<T> {
                protected ParentFactory(Class<T> type) { super(type) }

                T ForEnvironment(String environment,
                        @DelegatesToBuilder(OrderApplication) Closure<?> body) {
                    super.With(environment: environment, body)
                }
            }

            class OrderFactory extends ParentFactory<OrderApplication> {
                protected OrderFactory() { super(OrderApplication) }
            }
        """

        when:
        instance = clazz.Create.ForEnvironment('production') {
            name 'orders'
            assert resolveStrategy == Closure.DELEGATE_ONLY
        }
        def method = getClass('OrderApplication_DSL$Factory').getMethod('ForEnvironment', String, Closure)

        then:
        instance.environment == 'production'
        instance.name == 'orders'
        method.returnType == clazz
        method.parameters.last().getAnnotation(DelegatesTo).value() == getClass('OrderApplication_DSL$Builder')
        method.parameters.last().getAnnotation(DelegatesTo).strategy() == Closure.DELEGATE_ONLY

        where:
        checking << ['TypeChecked', 'CompileStatic']
    }

    def "custom and standard public factory closure annotations agree"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumFactory

            @DSL
            class OrderApplication {
                String environment

                static class Factory extends KlumFactory.Unkeyed<OrderApplication> {
                    protected Factory() { super(OrderApplication) }

                    OrderApplication ForEnvironment(String environment,
                            @DelegatesToBuilder(OrderApplication) Closure<?> applicationInput) {
                        With(environment: environment, applicationInput)
                    }
                }
            }
        '''

        when:
        def factory = getClass('OrderApplication$Factory')
        def publicFactory = getClass('OrderApplication_DSL$Factory')
        def publicBuilder = getClass('OrderApplication_DSL$Builder')
        def methods = [factory.getMethod('ForEnvironment', String, Closure),
                       publicFactory.getMethod('ForEnvironment', String, Closure),
                       factory.getMethod('With', Map, Closure),
                       factory.getMethod('With', Closure),
                       publicFactory.getMethod('With', Map, Closure),
                       publicFactory.getMethod('With', Closure)]

        then:
        methods.every { method ->
            def delegation = method.parameters.last().getAnnotation(DelegatesTo)
            delegation != null && delegation.value() == publicBuilder &&
                    delegation.strategy() == Closure.DELEGATE_ONLY
        }
    }

    def "forwards a Builder closure in an external keyed Factory"() {
        given:
        createClass """
            import com.blackbuild.klum.ast.runtime.KlumFactory
            import groovy.transform.TypeChecked
            import groovy.transform.CompileStatic

            @DSL(factory = OrderFactory)
            class OrderApplication {
                @Key String id
                String environment
                String name
            }

            @$checking
            class OrderFactory extends KlumFactory.Keyed<OrderApplication> {
                protected OrderFactory() { super(OrderApplication) }

                OrderApplication ForEnvironment(String key, String environment,
                        @DelegatesToBuilder(OrderApplication) Closure<?> applicationInput) {
                    $forwarding
                }
            }
        """

        when:
        instance = clazz.Create.ForEnvironment('orders', 'production') { name 'checkout' }

        then:
        instance.id == 'orders'
        instance.environment == (forwarding.contains('environment:') ? 'production' : null)
        instance.name == 'checkout'

        where:
        checking        | forwarding
        'TypeChecked'   | 'With(environment: environment, key, applicationInput)'
        'CompileStatic' | 'With(environment: environment, key, applicationInput)'
        'TypeChecked'   | 'super.With(environment: environment, key, applicationInput)'
        'CompileStatic' | 'super.With(environment: environment, key, applicationInput)'
        'TypeChecked'   | 'super.With(key, applicationInput)'
        'CompileStatic' | 'super.With(key, applicationInput)'
    }

    def "preserves a custom closure delegate and return subtype on the public Factory"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumFactory

            @DSL
            class OrderApplication {
                static class Factory extends KlumFactory.Unkeyed<OrderApplication> {
                    protected Factory() { super(OrderApplication) }

                    SpecializedApplication Specialized(
                            @DelegatesToBuilder(SpecializedApplication) Closure<?> body) {
                        SpecializedApplication.Create.With(body)
                    }
                }
            }

            @DSL
            class SpecializedApplication extends OrderApplication {
                String specialization
            }
        '''

        when:
        def client = createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class Client {
                static SpecializedApplication create() {
                    OrderApplication.Create.Specialized { specialization 'checkout' }
                }
            }
        '''
        instance = client.create()
        def method = getClass('OrderApplication_DSL$Factory').getMethod('Specialized', Closure)

        then:
        instance.specialization == 'checkout'
        method.returnType == getClass('SpecializedApplication')
        method.parameters[0].getAnnotation(DelegatesTo).value() == getClass('SpecializedApplication_DSL$Builder')
    }

    def "rejects unknown Builder operations in a separately compiled custom factory consumer"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.runtime.KlumFactory

            @DSL
            class OrderApplication {
                String name

                static class Factory extends KlumFactory.Unkeyed<OrderApplication> {
                    protected Factory() { super(OrderApplication) }

                    OrderApplication Configured(@DelegatesToBuilder Closure<?> body) {
                        With(body)
                    }
                }
            }
        '''

        when:
        createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class Client {
                static OrderApplication create() {
                    OrderApplication.Create.Configured { missingOperation 'checkout' }
                }
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Cannot find matching method')
        error.message.contains('missingOperation')
    }
}
