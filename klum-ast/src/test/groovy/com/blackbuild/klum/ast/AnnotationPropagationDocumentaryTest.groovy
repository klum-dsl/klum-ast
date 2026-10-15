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

import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('890')
@Tag('documentary')
class AnnotationPropagationDocumentaryTest extends AbstractDSLSpec {

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Annotation-Propagation.md#creating-related-services')
    def 'creates related services while retaining domain defaults'() {
        given: 'a separately compiled domain annotation library'
        createSecondaryClass('''
            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import com.blackbuild.klum.ast.layer3.DefaultValues

            @DefaultValues
            @PropagateAnnotation(AutoCreate)
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface ServiceDefaults { String name() default '' }
        ''')
        createSecondaryClass('''
            @DSL class Topic { String name }
            @DSL class Messaging {
                String name
                @ServiceDefaults(name = 'orders') Topic orderTopic
                @ServiceDefaults(name = 'results') Topic resultTopic
            }
            @DSL class Service { String name }
            @DSL class Application {
                @ServiceDefaults(name = 'messaging') Messaging messaging
                @ServiceDefaults(name = 'database') Service database
                @ServiceDefaults(name = 'workload') Service workload
                @ServiceDefaults(name = 'api') Service api
            }
        ''')

        when:
        def application = Application.Create.With {
            messaging { orderTopic { name 'priority-orders' } }
        }

        then:
        application.messaging.name == 'messaging'
        application.messaging.orderTopic.name == 'priority-orders'
        application.messaging.resultTopic.name == 'results'
        application.database.name == 'database'
        application.workload.name == 'workload'
        application.api.name == 'api'
    }

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Annotation-Propagation.md#keeping-customized-creation-explicit')
    def 'keeps optional defaults and customized creation explicit'() {
        given: 'a defaults policy that makes no creation request'
        createSecondaryClass('''
            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target
            import com.blackbuild.klum.ast.layer3.DefaultValues

            @DefaultValues
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface ServiceDefaults { String name() default '' }
        ''')
        createSecondaryClass('''
            import com.blackbuild.klum.ast.layer3.AutoCreate

            @DSL class Service { String name }
            @DSL class Database extends Service { String engine = 'postgres' }
            @DSL class Application {
                @ServiceDefaults(name = 'optional') Service optional
                @AutoCreate(type = Database)
                @ServiceDefaults(name = 'orders') Service database
            }
        ''')

        when:
        def application = Application.Create.One()
        def supplied = Application.Create.With { optional { name 'supplied' } }

        then:
        application.optional == null
        getClass('Database').isInstance(application.database)
        application.database.name == 'orders'
        application.database.engine == 'postgres'
        supplied.optional.name == 'supplied'
    }
}
