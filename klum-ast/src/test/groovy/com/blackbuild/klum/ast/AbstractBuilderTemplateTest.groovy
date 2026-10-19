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

import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.internal.FactoryHelper
import spock.lang.Issue

import java.lang.reflect.Modifier

@Issue("925")
class AbstractBuilderTemplateTest extends AbstractDSLSpec {
    void schema(boolean operation = true) {
        createClass("""
            @DSL abstract class Parent {
                String region
                abstract String getDisplayName()
                ${operation ? '@Builder.Method abstract String render(String prefix, int times)' : ''}
                ${operation ? '@Builder.Method String invokeRender() { render("!", 2) }' : ''}
            }
            @DSL class Child extends Parent {
                @Default(code = { 'fallback' }) String displayName
                @Builder.Method String render(String prefix, int times) { region = prefix * times + region; region }
            }
        """)
    }

    def "abstract parent field recipes support creation scope and copyFrom with abstract operation #operation"() {
        given:
        schema(operation)
        when:
        def mapped = Parent.Create.Template.With(region: 'map')
        def configured = Parent.Create.Template.With { region 'closure' }
        def combined = Parent.Create.Template.With([region: 'map']) { region 'combined' }
        def scoped = Parent.Template.With(mapped) { Child.Create.One() }
        def copied = Child.Create.With { copyFrom(configured) }
        def anonymous = Parent.Template.With([region: 'anonymous']) { Child.Create.One() }
        then:
        scoped.region == 'map'
        scoped.displayName == 'fallback'
        copied.region == 'closure'
        anonymous.region == 'anonymous'
        combined.region == 'combined'
        Modifier.isAbstract(getBuilderClass('Parent').modifiers)
        !Modifier.isAbstract(getClass('Parent$_TemplateBuilder').modifiers)
        !Modifier.isAbstract(getBuilderClass('Child').modifiers)
        where:
        operation << [false, true]
    }

    def "unrealized Template operations throw precisely for direct and inherited concrete calls: #indirect"() {
        given:
        schema()
        when:
        Parent.Create.Template.With {
            if (indirect) invokeRender()
            else render('!', 2)
        }
        then:
        def error = thrown(KlumModelException)
        error.message.contains('Cannot invoke abstract Builder operation')
        error.message.contains('render(java.lang.String, int)')
        error.message.contains('while defining a Template for Parent')
        error.message.contains('define the Template on a concrete subtype which implements that operation')
        where:
        indirect << [false, true]
    }

    def "concrete Template operations retain their concrete behavior"() {
        given:
        schema()
        when:
        def recipe = Child.Create.Template.With { region 'eu'; render('!', 2) }
        def model = Child.Create.With { copyFrom(recipe) }
        then:
        recipe.region == '!!eu'
        model.region == '!!eu'
        hasNoMethod(recipe.class, 'render', String, Integer.TYPE)
        hasNoMethod(model.class, 'render', String, Integer.TYPE)
    }

    def "abstract Model and Template surfaces omit every Builder-only method"() {
        given:
        schema()
        when:
        def recipe = Parent.Create.Template.With()
        then:
        [Parent, Child, recipe.class].every { type ->
            hasNoMethod(type, 'render', String, Integer.TYPE) && hasNoMethod(type, 'invokeRender')
        }
        when:
        FactoryHelper.createBuilder(Parent, null)
        then:
        def error = thrown(KlumModelException)
        error.message.contains('Cannot instantiate abstract class Parent')
    }

    def "abstract Template file and legacy alias preserve field recipes"() {
        given:
        schema()
        File source = tempFolder.newFile('parent-template.groovy')
        source.text = "region 'file'"
        when:
        def fromFile = Parent.Create.Template.From(source)
        def fromAlias = Parent.Template.Create(region: 'alias')
        then:
        Child.Create.With { copyFrom(fromFile) }.region == 'file'
        Child.Create.With { copyFrom(fromAlias) }.region == 'alias'
    }

    def "abstract operations inherited across an abstract intermediate class remain Template-only failures"() {
        given:
        createClass('''
            @DSL abstract class Root {
                String region
                @Builder.Method abstract int count()
            }
            @DSL abstract class Parent extends Root { String label }
            @DSL class Child extends Parent {
                @Builder.Method int count() { 7 }
            }
        ''')
        when:
        def recipe = Parent.Create.Template.With(region: 'eu', label: 'shared')
        def child = Child.Create.With { copyFrom(recipe); assert count() == 7 }
        then:
        child.region == 'eu'
        child.label == 'shared'
        when:
        Parent.Create.Template.With { count() }
        then:
        def error = thrown(KlumModelException)
        error.message.contains('int count()')
    }
    def "nested abstract Template recipes survive serialization and copy into concrete children"() {
        given:
        createClass('''
            @DSL abstract class Parent {
                String region
                @Builder.Method abstract void normalize()
            }
            @DSL class Child extends Parent {
                @Builder.Method void normalize() { region = region.toUpperCase() }
            }
            @DSL class Container { Parent child }
        ''')
        Class parentType = Parent
        Class childType = Child
        def parentRecipe = Parent.Create.Template.With(region: 'eu')
        def nestedRecipe = Container.Create.Template.With { child(parentType) { copyFrom(parentRecipe) } }
        when:
        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(nestedRecipe) }
        def schemaLoader = loader
        def restored = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass descriptor) {
                schemaLoader.loadClass(descriptor.name)
            }
        }.withCloseable { it.readObject() }
        def model = Container.Create.With {
            child(childType) { copyFrom(restored.child); normalize() }
        }
        then:
        restored.child.region == 'eu'
        model.child.region == 'EU'
    }

}
