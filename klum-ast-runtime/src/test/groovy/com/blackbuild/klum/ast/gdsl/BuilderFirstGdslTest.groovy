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
package com.blackbuild.klum.ast.gdsl

import groovy.lang.Binding
import groovy.lang.GroovyShell
import spock.lang.Issue
import spock.lang.Specification

@Issue('703')
class BuilderFirstGdslTest extends Specification {

    def "Create completion contributes the mirrored public Factory getter for DSL source PSI"() {
        given: 'a DSL source class and its refreshed Foo_DSL.Factory source mirror'
        GdslClass factory = new GdslClass('fixture.Foo_DSL.Factory')
        GdslClass model = dslClass('fixture.Foo')
        GdslDelegate delegate = new GdslDelegate(model, ['fixture.Foo_DSL.Factory': factory])

        when:
        execute('CreateProperties.gdsl', delegate)

        then: 'Foo.Create starts the truthful public Factory chain as a static read-only property'
        delegate.methods == [[name: 'getCreate', type: 'fixture.Foo_DSL.Factory', isStatic: true]]
        !delegate.methods*.name.contains('setCreate')
    }

    def "Create completion fails closed without a DSL annotation or public Factory mirror"() {
        when: 'a normal source class is inspected'
        GdslDelegate ordinary = new GdslDelegate(new GdslClass('fixture.Ordinary'), [:])
        execute('CreateProperties.gdsl', ordinary)

        and: 'a DSL source class has no matching support namespace after an incomplete refresh'
        GdslDelegate withoutMirror = new GdslDelegate(dslClass('fixture.Missing'), [:])
        execute('CreateProperties.gdsl', withoutMirror)

        then:
        ordinary.methods.empty
        withoutMirror.methods.empty
    }

    def "the distinct polymorphic closure contributor delegates only to the public Builder contract"() {
        expect: 'both packaged resources register successfully as GDSL contributors'
        contributor('CreateProperties.gdsl')
        contributor('PolymorphicMethods.gdsl')

        and: 'Create completion has no closure-delegate inference, so the closure resource retains a distinct use case'
        gdsl('CreateProperties.gdsl').contains('getCreate')
        !gdsl('CreateProperties.gdsl').contains('delegatesTo')

        and: 'the retained contributor remains closure-scoped and names no legacy RW implementation type'
        gdsl('PolymorphicMethods.gdsl').contains('context(scope:closureScope())')
        gdsl('PolymorphicMethods.gdsl').contains("_DSL.Builder")
        gdsl('PolymorphicMethods.gdsl').contains('delegatesTo(builderClass)')
        !gdsl('PolymorphicMethods.gdsl').contains('$_RW')
        !gdsl('PolymorphicMethods.gdsl').contains('KlumRwObject')
    }

    private GdslClass dslClass(String qualifiedName) {
        new GdslClass(qualifiedName, true)
    }

    private void execute(String resourceName, GdslDelegate delegate) {
        Closure gdslContributor = contributor(resourceName)
        gdslContributor.delegate = delegate
        gdslContributor.resolveStrategy = Closure.DELEGATE_FIRST
        gdslContributor.call()
    }

    private Closure contributor(String resourceName) {
        List<Closure> contributors = []
        Binding binding = new Binding(
                context: { Map arguments = [:] -> arguments },
                closureScope: { [:] },
                contributor: { Object context, Closure body -> contributors << body })
        new GroovyShell(getClass().classLoader, binding).evaluate(gdsl(resourceName))
        assert contributors.size() == 1
        contributors[0]
    }

    private String gdsl(String resourceName) {
        getClass().getResource("/${getClass().package.name.replace('.', '/')}/$resourceName").text
    }

    private static class GdslDelegate {
        final GdslClass classType
        final Map<String, GdslClass> classes
        final List<Map<String, Object>> methods = []

        GdslDelegate(GdslClass classType, Map<String, GdslClass> classes) {
            this.classType = classType
            this.classes = classes
        }

        GdslClass findClass(String qualifiedName) {
            classes[qualifiedName]
        }

        void method(Map<String, Object> declaration) {
            methods << declaration
        }
    }

    private static class GdslClass {
        final String qualifiedName
        final GdslModifierList modifierList

        GdslClass(String qualifiedName, boolean dsl = false) {
            this.qualifiedName = qualifiedName
            modifierList = new GdslModifierList(dsl)
        }
    }

    private static class GdslModifierList {
        final boolean dsl

        GdslModifierList(boolean dsl) {
            this.dsl = dsl
        }

        Object findAnnotation(String qualifiedName) {
            dsl && qualifiedName == 'com.blackbuild.klum.ast.DSL' ? new Object() : null
        }
    }
}
