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
//file:noinspection GrPackage
//file:noinspection GroovyVariableNotAssigned
package com.blackbuild.klum.ast

import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import javax.tools.ToolProvider

@Issue("835")
class StaticEntryPointInheritanceTest extends AbstractDSLSpec {

    def "dynamic literal and variable receivers select their own entry fields in #parentKind #childKind #loading schemas"() {
        given:
        def runtimeLoader = hierarchy(parentKind, childKind, loading)

        when:
        def entries = new GroovyShell(runtimeLoader).evaluate("""
            import pk.Application
            import pk.OrderApplication
            import pk.PriorityOrderApplication
            def receiver = OrderApplication
            [Application.Create, Application.Template,
             OrderApplication.Create, OrderApplication.Template,
             PriorityOrderApplication.Create, PriorityOrderApplication.Template,
             receiver.Create, receiver.Template]
        """)

        then:
        entries[0].is(entry(runtimeLoader, 'Application', 'Create'))
        entries[1].is(entry(runtimeLoader, 'Application', 'Template'))
        entries[2].is(entry(runtimeLoader, 'OrderApplication', 'Create'))
        entries[3].is(entry(runtimeLoader, 'OrderApplication', 'Template'))
        entries[4].is(entry(runtimeLoader, 'PriorityOrderApplication', 'Create'))
        entries[5].is(entry(runtimeLoader, 'PriorityOrderApplication', 'Template'))
        entries[6].is(entries[2])
        entries[7].is(entries[3])

        where:
        [parentKind, childKind, loading] << [['concrete', 'abstract'], ['concrete', 'abstract'], ['source', 'binary']].combinations()
    }

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#templates-in-a-class-hierarchy")
    def "applies child Templates below an abstract application"() {
        given:
        hierarchy('abstract', 'concrete', 'source')

        when:
        def results = new GroovyShell(loader).evaluate("""
            import pk.Application
            import pk.OrderApplication
            def parentTemplate = Application.Create.Template.With { name 'shared' }
            def childTemplate = OrderApplication.Create.Template.With { orderQueue 'orders' }
            def application = Application.Template.With(parentTemplate) {
                OrderApplication.Template.With(childTemplate) {
                    OrderApplication.Create.One()
                }
            }
            def anonymous = OrderApplication.Template.With(orderQueue: 'priority') {
                OrderApplication.Create.One()
            }
            [childTemplate, application, anonymous]
        """)

        then:
        results.every { getClass('pk.OrderApplication').isInstance(it) }
        results[0].orderQueue == 'orders'
        results[1].name == 'shared'
        results[1].orderQueue == 'orders'
        results[2].orderQueue == 'priority'
    }

    def "ordinary user static properties retain inheritance and hiding"() {
        given:
        hierarchy('abstract', 'concrete', 'source')

        when:
        def values = new GroovyShell(loader).evaluate("""
            import pk.Application
            import pk.OrderApplication
            import pk.PriorityOrderApplication
            def receiver = OrderApplication
            [OrderApplication.inheritedValue, OrderApplication.description,
             OrderApplication.shadowedValue, Application.shadowedValue,
             PriorityOrderApplication.shadowedValue, receiver.inheritedValue]
        """)

        then:
        values == ['inherited', 'application', 'child', 'parent', 'child', 'inherited']
    }

    def "static Groovy retains child Template creation and application"() {
        given:
        hierarchy('abstract', 'concrete', 'source')
        def consumer = createSecondaryClass("""
            package pk
            import groovy.transform.CompileStatic
            @CompileStatic class StaticConsumer {
                static OrderApplication create() {
                    def template = OrderApplication.Create.Template.With { orderQueue 'static' }
                    OrderApplication.Template.With(template) { OrderApplication.Create.One() }
                }
            }
        """)

        when:
        def application = consumer.create()

        then:
        getClass('pk.OrderApplication').isInstance(application)
        application.orderQueue == 'static'
    }

    def "Java retains the generated static fields and model-specific Template factory"() {
        given:
        hierarchy('abstract', 'concrete', 'source')
        File output = compilerConfiguration.targetDirectory
        File source = new File(output, 'JavaConsumer.java')
        source.text = """
            package pk;
            import java.util.Map;
            import groovy.lang.Closure;
            public class JavaConsumer {
                public static OrderApplication create() {
                    return OrderApplication.Create.Template.With(Map.of("orderQueue", "java"));
                }
                public static OrderApplication applied() {
                    return OrderApplication.Template.With(create(), new Closure<OrderApplication>(null) {
                        public OrderApplication doCall() { return OrderApplication.Create.One(); }
                    });
                }
                public static OrderApplication_DSL.TemplateScope scope() {
                    return OrderApplication.Template;
                }
            }
        """
        String classpath = output.absolutePath + File.pathSeparator + System.getProperty('java.class.path')

        when:
        int compilation = ToolProvider.systemJavaCompiler.run(null, null, null,
                '-classpath', classpath, '-d', output.absolutePath, source.absolutePath)

        then:
        compilation == 0

        when:
        loader.addClasspath(output.absolutePath)
        def consumer = loader.loadClass('pk.JavaConsumer')
        def template = consumer.create()

        then:
        getClass('pk.OrderApplication').isInstance(template)
        template.orderQueue == 'java'
        consumer.applied().orderQueue == 'java'
        consumer.scope().is(entry(loader, 'OrderApplication', 'Template'))
    }

    private GroovyClassLoader hierarchy(String parentKind, String childKind, String loading) {
        String parentModifier = parentKind == 'abstract' ? 'abstract' : ''
        String childModifier = childKind == 'abstract' ? 'abstract' : ''
        createClass("""
            package pk
            @DSL $parentModifier class Application {
                String name
                static String inheritedValue = 'inherited'
                static String shadowedValue = 'parent'
                static String getDescription() { 'application' }
            }
            @DSL $childModifier class OrderApplication extends Application {
                String orderQueue
                static String shadowedValue = 'child'
            }
            @DSL class PriorityOrderApplication extends OrderApplication { }
        """)
        if (loading == 'source') return loader
        def runtimeLoader = new GroovyClassLoader(getClass().classLoader)
        runtimeLoader.addClasspath(compilerConfiguration.targetDirectory.absolutePath)
        runtimeLoader
    }

    private static Object entry(ClassLoader runtimeLoader, String model, String field) {
        runtimeLoader.loadClass('pk.' + model).getDeclaredField(field).get(null)
    }
}
