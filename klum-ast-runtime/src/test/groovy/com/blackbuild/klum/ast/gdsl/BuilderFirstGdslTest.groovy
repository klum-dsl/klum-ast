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

@Issue('737')
class BuilderFirstGdslTest extends Specification {

    def "static model completion uses an uppercase raw field when GDSL properties normalize JavaBean names"() {
        when: 'the packaged contributor source is inspected'
        String createProperties = gdsl('CreateProperties.gdsl')

        then: 'it preserves the literal property spelling and public support types'
        createProperties.contains('import com.intellij.psi.JavaPsiFacade')
        createProperties.contains("[Create: 'Factory', Template: 'TemplateScope']")
        createProperties.contains('JavaPsiFacade.getElementFactory(project).createFieldFromText(')
        createProperties.contains('"public static ${contract.qualifiedName} ${propertyName}"')
        createProperties.contains('add(field)')

        and: 'the documented JavaBean helper cannot silently restore lowercase completion'
        !createProperties.contains('property name:')

        and: 'the contributor remains narrow and fails closed before the internal hook is reached'
        createProperties.contains("model?.modifierList?.findAnnotation(dslAnnotation)")
        createProperties.contains('findClass("${qualifiedName}_DSL.${contractName}")')
    }

    def "static model completion fails closed before the internal field hook without a DSL annotation or support mirror"() {
        when: 'a normal source class is inspected'
        GdslDelegate ordinary = new GdslDelegate(new GdslClass('fixture.Ordinary'), [:])
        execute('CreateProperties.gdsl', ordinary)

        and: 'a DSL source class has no matching support namespace after an incomplete refresh'
        GdslDelegate withoutMirror = new GdslDelegate(dslClass('fixture.Missing'), [:])
        execute('CreateProperties.gdsl', withoutMirror)

        then: 'the raw IntelliJ hook is never reached'
        ordinary.findClassCalls.empty
        withoutMirror.findClassCalls == ['fixture.Missing_DSL.Factory', 'fixture.Missing_DSL.TemplateScope']
    }

    def "static model completion contributes literal uppercase static fields through the GDSL raw-member hook"() {
        given: 'a DSL source class and its refreshed public support source mirror'
        GdslClass model = dslClass('fixture.Foo')
        GdslDelegate delegate = new GdslDelegate(model, [
                'fixture.Foo_DSL.Factory' : new GdslClass('fixture.Foo_DSL.Factory'),
                'fixture.Foo_DSL.TemplateScope': new GdslClass('fixture.Foo_DSL.TemplateScope')
        ])
        List<Object> fieldFactoryProjects = []

        when: "the contributor invokes an instrumented equivalent of IntelliJ's raw PSI factory"
        execute('CreateProperties.gdsl', delegate, fieldFactoryProjects)

        then: 'it contributes uppercase static fields of the public support types through add(field)'
        delegate.fields*.declaration == [
                'public static fixture.Foo_DSL.Factory Create',
                'public static fixture.Foo_DSL.TemplateScope Template'
        ]
        delegate.fields*.context == [model, model]
        fieldFactoryProjects == [delegate.project, delegate.project]
    }

    def "the distinct polymorphic closure contributor delegates only to the public Builder contract"() {
        expect: 'both packaged resources register successfully as GDSL contributors'
        contributor('CreateProperties.gdsl')
        contributor('PolymorphicMethods.gdsl')

        and: 'static property completion has no closure-delegate inference, so the closure resource retains a distinct use case'
        gdsl('CreateProperties.gdsl').contains("[Create: 'Factory', Template: 'TemplateScope']")
        !gdsl('CreateProperties.gdsl').contains('delegatesTo')

        and: 'the retained contributor remains closure-scoped and names no legacy RW implementation type'
        gdsl('PolymorphicMethods.gdsl').contains('context(scope:closureScope())')
        gdsl('PolymorphicMethods.gdsl').contains('findContainingClosureMethod(place)')
        gdsl('PolymorphicMethods.gdsl').contains('findFirstClassArgumentOf(method)')
        gdsl('PolymorphicMethods.gdsl').contains('getActualClassValueOf(classArgument)')
        gdsl('PolymorphicMethods.gdsl').contains("findClass(type.resolve().qualName + '_DSL.Builder')")
        gdsl('PolymorphicMethods.gdsl').contains("_DSL.Builder")
        gdsl('PolymorphicMethods.gdsl').contains('delegatesTo(builderClass)')
        !gdsl('PolymorphicMethods.gdsl').contains('$_RW')
        !gdsl('PolymorphicMethods.gdsl').contains('KlumRwObject')
    }

    private Closure contributor(String resourceName, List<Object> fieldFactoryProjects = null) {
        List<Closure> contributors = []
        String source = gdsl(resourceName)
        if (fieldFactoryProjects != null) {
            source = source.replace('import com.intellij.psi.JavaPsiFacade\n', '')
                    .replace('JavaPsiFacade.getElementFactory(project).createFieldFromText(',
                            'fieldFactory(project).createFieldFromText(')
        }
        Binding binding = new Binding(
                context: { Map arguments = [:] -> arguments },
                closureScope: { [:] },
                contributor: { Object context, Closure body -> contributors << body },
                fieldFactory: { Object project ->
                    fieldFactoryProjects << project
                    new GdslElementFactory(project)
                })
        new GroovyShell(getClass().classLoader, binding).evaluate(source)
        assert contributors.size() == 1
        contributors[0]
    }

    private GdslClass dslClass(String qualifiedName) {
        new GdslClass(qualifiedName, true)
    }

    private void execute(String resourceName, GdslDelegate delegate, List<Object> fieldFactoryProjects = null) {
        Closure gdslContributor = contributor(resourceName, fieldFactoryProjects)
        gdslContributor.delegate = delegate
        gdslContributor.resolveStrategy = Closure.DELEGATE_FIRST
        gdslContributor.call()
    }

    private String gdsl(String resourceName) {
        getClass().getResource("/${getClass().package.name.replace('.', '/')}/$resourceName").text
    }

    private static class GdslDelegate {
        final GdslClass classType
        final Map<String, GdslClass> classes
        final List<String> findClassCalls = []
        final Object project = new Object()
        final List<GdslField> fields = []

        GdslDelegate(GdslClass classType, Map<String, GdslClass> classes) {
            this.classType = classType
            this.classes = classes
        }

        GdslClass findClass(String qualifiedName) {
            findClassCalls << qualifiedName
            classes[qualifiedName]
        }

        void add(GdslField field) {
            fields << field
        }
    }

    private static class GdslElementFactory {
        final Object project

        GdslElementFactory(Object project) {
            this.project = project
        }

        GdslField createFieldFromText(String declaration, Object context) {
            new GdslField(declaration, context)
        }
    }

    private static class GdslField {
        final String declaration
        final Object context

        GdslField(String declaration, Object context) {
            this.declaration = declaration
            this.context = context
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
