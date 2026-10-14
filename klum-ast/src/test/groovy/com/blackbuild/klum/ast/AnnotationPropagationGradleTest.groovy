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

import com.blackbuild.klum.ast.propagation.PropagationPolicies.ConfiguredChild
import com.blackbuild.klum.ast.propagation.PropagationPolicies.Group
import com.blackbuild.klum.ast.propagation.PropagationPolicies.Query
import com.blackbuild.klum.ast.propagation.PropagationPolicies.RequiredValue
import spock.lang.Issue
import spock.lang.Specification

/** Schemas below are compiled by Gradle's actual GroovyCompile task, not a test-created loader. */
@Issue('890')
class AnnotationPropagationGradleTest extends Specification {
    def 'Gradle transform and source classloaders discover the production provider and normal converters'() {
        when:
        def order = GradlePropagationOrder.Create.With {
            name 'orders'
            assert summary() == 'ORDERS'
        }

        then:
        order.summary() == 'ORDERS'
        order.child.name == 'configured'
        GradlePropagationOrder.getDeclaredField('name').getAnnotationsByType(Validate).size() == 1
        !GradlePropagationOrder.declaredFields*.name.contains('services')
        GradlePropagationOrder.Create.With {
            name 'grouped'
            services { child { name 'supplied' } }
        }.child.name == 'supplied'
    }

    def 'Gradle collected Required still validates the completed Model'() {
        when:
        GradlePropagationOrder.Create.One()

        then:
        def error = thrown(Exception)
        error.message.contains('name')
    }
}

@DSL
class GradlePropagationService { String name }

@DSL
class GradlePropagationOrder {
    @RequiredValue String name
    @ConfiguredChild(name='configured') GradlePropagationService child
    @Group Map<String, GradlePropagationService> services
    @Query String summary() { name.toUpperCase() }
}
