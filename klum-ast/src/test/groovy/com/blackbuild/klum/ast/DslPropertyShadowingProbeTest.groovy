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

/** Current-behavior probes for #371, pending the static-field compatibility decision. */
@Issue("371")
class DslPropertyShadowingProbeTest extends AbstractDSLSpec {

    def "same-named instance properties split configuration and inherited lifecycle state"() {
        given:
        createClass('''
            package shadowing
            @DSL class Service {
                String name = 'ancestor'
                String fromParent
                @PostTree void recordParent() { fromParent = name }
            }
            @DSL class WebService extends Service { String name = 'subclass' }
        ''')
        Map builderValues = [:]

        when:
        instance = WebService.Create.With {
            name 'configured'
            def childField = delegate.class.getDeclaredField('name')
            def parentField = delegate.class.superclass.getDeclaredField('name')
            childField.accessible = true
            parentField.accessible = true
            builderValues.child = childField.get(delegate)
            builderValues.parent = parentField.get(delegate)
        }

        then: 'characterizes the defect; these are not proposed supported semantics'
        builderValues == [child: 'configured', parent: 'ancestor']
        instance.name == 'configured'
        instance.fromParent == 'ancestor'
    }

    def "owner discovery sees both same-named owner declarations"() {
        given:
        createClass('''
            package shadowing
            @DSL class Container { Child child }
            @DSL class Parent { @Owner Object container }
            @DSL class Child extends Parent { @Owner Object container }
        ''')
        def childBuilder = getBuilderClass('shadowing.Child')

        when:
        instance = Container.Create.With { child {} }
        def ownerDeclarations = [childBuilder, childBuilder.superclass]
                .collectMany { it.declaredFields.toList() }
                .findAll { it.isAnnotationPresent(Owner) }

        then:
        ownerDeclarations.collect { it.declaringClass.name + '.' + it.name } == [
                'shadowing.Child$Builder.container', 'shadowing.Parent$Builder.container']
        instance.child.container.is(instance)
    }

    def "same-named defaults currently select the subclass default"() {
        given:
        createClass('''
            package shadowing
            @DSL class Service {
                @Default(code = { 'ancestor-default' }) String name
            }
            @DSL class WebService extends Service {
                @Default(code = { 'subclass-default' }) String name
            }
        ''')

        when:
        instance = WebService.Create.One()

        then:
        instance.name == 'subclass-default'
    }

    def "same-named static counters retain independent class state across compilation units"() {
        given:
        createClass('''
            package shadowing
            @DSL class Service {
                static int initializerCalls
                String parentValue = initializeParent()
                private static String initializeParent() {
                    initializerCalls++
                    'parent'
                }
            }
        ''')
        def parentClass = clazz
        def parentBuilderClass = builderClass
        createSecondaryClass('''
            package shadowing
            @DSL class WebService extends Service {
                static int initializerCalls
                String childValue = initializeChild()
                private static String initializeChild() {
                    initializerCalls++
                    'child'
                }
            }
        ''')

        when:
        instance = WebService.Create.One()

        then:
        instance.parentValue == 'parent'
        instance.childValue == 'child'
        parentClass.initializerCalls == 1
        WebService.initializerCalls == 1
        !parentBuilderClass.declaredFields.any { it.name == 'initializerCalls' }
        !getBuilderClass('shadowing.WebService').declaredFields.any { it.name == 'initializerCalls' }
    }

    def "inherited instance storage supports ordinary getter overrides"() {
        given:
        createClass('''
            package shadowing
            @DSL class Service { String name }
            @DSL class WebService extends Service {
                @Override String getName() { 'web:' + super.getName() }
            }
        ''')

        when:
        instance = WebService.Create.With { name 'frontend' }

        then:
        instance.name == 'web:frontend'
    }
}
