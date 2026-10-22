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
package com.blackbuild.klum.ast.runtime.internal

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.Field
import groovy.transform.CompileStatic
import spock.lang.Issue

import java.util.function.Consumer

@Issue('932')
class StructuredInputDirectKeyBoundaryTest extends AbstractDSLSpec {
    def 'fixed Cluster key exists in the public creator but not the current fixed-target Consumer path'() {
        given:
        createClass '''
            package fixedkeyboundary
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.layer3.Cluster
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            @DSL abstract class Home {
                @Cluster(value = ManagedZone, fixedKeys = true)
                abstract Map<String, Kitchen> getZones()
            }
            @Retention(RetentionPolicy.RUNTIME) @interface ManagedZone {}
            @DSL class Kitchen { @Key String name; String purpose }
            @DSL class FloorPlan extends Home { @ManagedZone Kitchen kitchen }
        '''
        def artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def floorPlan = Class.forName('fixedkeyboundary.FloorPlan', false, artifactLoader)
        def kitchenType = Class.forName('fixedkeyboundary.Kitchen', false, artifactLoader)

        when:
        def plan = StructuredInputPlan.read(floorPlan)
        def entry = plan.entries().get('kitchen')
        def ordinary = floorPlan.Create.With { kitchen(purpose: 'ordinary') }
        def callback = FactoryHelper.createWithConfiguration(floorPlan, null, { root ->
            StructuredInputDirectKeyBoundaryTest.configureKitchen(root, kitchenType)
        } as Consumer)

        then:
        entry.role().name() == 'OWNED_OBJECT'
        entry.source() == 'fixedkeyboundary.FloorPlan#kitchen'
        entry.keyType() == null
        entry.excludedKey() == null
        entry.methods().any { it.parameterCount == 0 }
        !floorPlan.getDeclaredField('kitchen').isAnnotationPresent(Field)
        ordinary.kitchen.name == 'kitchen'
        ordinary.kitchen.purpose == 'ordinary'
        callback.kitchen.name == null
        callback.kitchen.purpose == 'callback'

        cleanup:
        artifactLoader?.close()
    }

    @CompileStatic
    private static <T> void configureKitchen(InternalKlumBuilder<?> root, Class<T> type) {
        root.createSingleChildWithConfiguration('kitchen', type, false, null, { InternalKlumBuilder<T> child ->
            child.applyOnly([purpose: 'callback'], null)
        } as Consumer<InternalKlumBuilder<T>>)
    }
}
