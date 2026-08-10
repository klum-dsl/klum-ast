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
//file:noinspection UnnecessaryQualifiedReference
package com.blackbuild.klum.ast.compiler.internal.ast

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.annodocimal.ast.AstDocumentation
import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.internal.InternalKlumBuilder
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.intellij.lang.annotations.Language
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import java.lang.reflect.Array

@Issue("197")
class AnnoDocTest extends AbstractDSLSpec {

    File srcDir
    ClassNode classNode
    ClassNode builderClassNode
    Class<?> factoryClazz
    ClassNode factoryClassNode

    def setup() {
        srcDir = new File("build/test-sources/${getClass().simpleName}/$safeFilename")
        srcDir.deleteDir()
        srcDir.mkdirs()
    }

    void createClass(String filename, @Language("groovy") String code) {
        File file = new File(srcDir, filename)
        file.parentFile.mkdirs()
        file.text = code
        clazz = loader.parseClass(file)
        builderClass = getBuilderClass(clazz.name)
        factoryClazz = getClass(clazz.name + '$_Factory')
        classNode = ClassHelper.make(clazz)
        builderClassNode = ClassHelper.make(builderClass)
        factoryClassNode = ClassHelper.make(factoryClazz)
    }

    String methodDoc(String methodName, Class... params) {
        def methodNode = getMethod(classNode, methodName, params)
        return AstDocumentation.extractExact(methodNode).get().render()
    }

    String builderImplementationMethodDoc(String methodName, Class... params) {
        return builderClass.getMethod(methodName, params).getAnnotation(AnnoDoc)?.value()
    }

    String builderMethodDoc(String methodName, Class... params) {
        return getClass(clazz.name + '_DSL$Builder').getMethod(methodName, params).getAnnotation(AnnoDoc)?.value()
    }

    String creatorDoc(String methodName, Class... params) {
        def methodNode = getMethod(factoryClassNode, methodName, params)
        return AstDocumentation.extractExact(methodNode).get().render()
    }

    String altCreatorDoc(String methodName, Class... params) {
        def method = factoryClazz.getMethod(methodName, params)
        return method.getAnnotation(AnnoDoc)?.value()
    }

    MethodNode getMethod(ClassNode node, String methodName, Class... paramTypes) {
        Parameter[] params = paramTypes.collect { GeneralUtils.param(ClassHelper.make(it), "_") }.toArray(new Parameter[0])
        methodName == "<init>" ? node.getDeclaredConstructor(params) : node.getDeclaredMethod(methodName, params)
    }

    String fieldDoc(String fieldName) {
        return AstDocumentation.extractExact(classNode.getDeclaredField(fieldName)).get().render()
    }

    String classDoc() {
        return AstDocumentation.extractExact(classNode).get().render()
    }

    String builderClassDoc() {
        return AstDocumentation.extractExact(builderClassNode).get().render()
    }

    def "javadoc reflects the model and Builder API split"() {
        when:
        createClass("dummy/Foo.groovy", '''
package dummy 

import com.blackbuild.klum.ast.DSL

/**
 * This is a class
 */
@DSL class Foo {}
''')

        then:
        classDoc() == '''This is a class'''
        builderClassDoc() == "The generated Builder for dummy.Foo."
        !clazz.declaredMethods*.name.contains("apply")
        builderClass.getMethod("apply", Map).declaringClass == InternalKlumBuilder
        builderImplementationMethodDoc("copyFrom", clazz) == """Copies all non-null/non-empty recipe values from the template to this Builder.

@param template the recipe to apply"""

    }

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Javadoc.md#javadoc-for-models")
    def "generated getters document model and Builder values"() {
        when:
        createClass("dummy/Foo.groovy", '''
package dummy

import com.blackbuild.klum.ast.DSL

@DSL class Foo {
    /** display name. */
    String name

    /** active flag. */
    boolean active

    /** legacy name.
     * @deprecated Use name instead.
     */
    @Deprecated
    String legacyName
}
''')

        then: "property documentation is projected verbatim to completed-model getters"
        methodDoc("getName") == "display name."
        methodDoc("getActive") == "active flag."
        methodDoc("isActive") == "active flag."
        methodDoc("getLegacyName") == "legacy name.\n\n@deprecated Use name instead."

        and: "Builder implementation getters and setters retain the property wording"
        builderImplementationMethodDoc("getName") == "display name."
        builderImplementationMethodDoc("getActive") == "active flag."
        builderImplementationMethodDoc("isActive") == "active flag."
        builderImplementationMethodDoc("getLegacyName") == "legacy name.\n\n@deprecated Use name instead."
        builderImplementationMethodDoc("setName", String) == "display name."
        builderImplementationMethodDoc("setActive", boolean) == "active flag."
        builderImplementationMethodDoc("setLegacyName", String) == "legacy name.\n\n@deprecated Use name instead."

        and: "public Builder contracts expose the same generated-Javadoc evidence"
        builderMethodDoc("getName") == "display name."
        builderMethodDoc("getActive") == "active flag."
        builderMethodDoc("isActive") == "active flag."
        builderMethodDoc("getLegacyName") == "legacy name.\n\n@deprecated Use name instead."
        builderMethodDoc("setName", String) == "display name."
        builderMethodDoc("setActive", boolean) == "active flag."
        builderMethodDoc("setLegacyName", String) == "legacy name.\n\n@deprecated Use name instead."
    }

    @Issue("461")
    def "explicit accessor documentation wins over property documentation"() {
        when:
        createClass("dummy/Foo.groovy", '''
package dummy

import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Mutator

@DSL class Foo {
    /** The banner displayed to readers. */
    private String banner

    /** Reads the banner from the configured model. */
    String getBanner() { banner }

    /** Replaces the banner in the configured model. */
    @Mutator void setBanner(String value) { banner = value }
}
''')

        then:
        methodDoc("getBanner") == "Reads the banner from the configured model."
        builderImplementationMethodDoc("getBanner") == "Reads the banner from the configured model."
        builderImplementationMethodDoc("setBanner", String) == "Replaces the banner in the configured model."
        builderMethodDoc("getBanner") == "Reads the banner from the configured model."
        builderMethodDoc("setBanner", String) == "Replaces the banner in the configured model."
    }

    def "javadoc for auto overridden creator"() {
        when:
        createClass("dummy/Foo.groovy", '''
package dummy 

import com.blackbuild.klum.ast.DSL

/**
 * This is a class
 */
@DSL class Foo {}
''')

        then:
        altCreatorDoc("With", Closure) == """Creates a new instance of the model applying the given configuration closure.

@param configuration The configuration closure to apply to the model.
@return The instantiated object."""
    }

    def "javadoc for manually overridden creator and own creator methods"() {
        when:
        createClass("dummy/Foo.groovy", '''
package dummy 

import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.DelegatesToRW
import com.blackbuild.klum.ast.runtime.KlumFactory

/**
 * This is a class
 */
@DSL(factory = MyFactory) 
class Foo {}

/**
 * Factory for Foo
 */
class MyFactory extends KlumFactory.Unkeyed<Foo> {

    protected MyFactory() {
        super(Foo)
    }
    
    /**
     * New text.
     * @param configuration The configuration closure to apply to the model.
     * @return The instantiated object.
     */
     Foo WithIt(Closure configuration) {
        With(configuration)
     }
}''')

        then:
        creatorDoc("WithIt", Closure) == """New text.

@param configuration The configuration closure to apply to the model.
@return The instantiated object."""
    }

    def "annodoc for collection methods"() {
        when:
        createClass("dummy/Foo.groovy", '''
            package dummy
            @DSL class Foo {
                List<Bar> bars
            }
            
            @DSL class Bar {
                String value
            }''')

        then:
        builderImplementationMethodDoc("bar", Closure) == """Creates a new 'bar' Builder and adds it to the Builder's 'bars' collection.

<p>The newly created Builder is configured by the optional values and closure.</p>

@param closure the closure to configure the new element
@return the newly created Builder"""
        builderImplementationMethodDoc("bar", Map) == """Creates a new 'bar' Builder and adds it to the Builder's 'bars' collection.

<p>The newly created Builder is configured by the optional values and closure.</p>

@param values the optional parameters
@param closure the closure to configure the new element
@return the newly created Builder""" // closures has a default value, so during ast it is a single method
        builderImplementationMethodDoc("bars", getArrayClass("dummy.Bar\$Builder")) == """Adds one or more 'bar' Builders to the Builder's 'bars' collection.

@param values the elements to add"""
        builderImplementationMethodDoc("bars", Iterable) == """Adds one or more 'bar' Builders to the Builder's 'bars' collection.

@param values the elements to add"""

    }

    def "converter factory for dsl field"() {
        when:
        createClass("dummy/Foo.groovy", '''
            @DSL class Foo {
                Bar bar
            }
            
            @DSL class Bar {
                Date birthday
                
                /**
                * Creates a new instance of Bar with the given birthday as timestamp.
                * @param value the timestamp
                * @return the newly created instance 
                */
                static Bar fromLong(long value) {
                    return Bar.Create.With(birthday: new Date(value))
                }
            }
            ''')

        then:
        builderImplementationMethodDoc("bar", long) == """Creates an unsealed Builder in the active construction session and attaches it to this relationship.

<p>The returned Builder remains attached to the current construction session; it cannot be independently materialized or validated.</p>

@param value the timestamp
@return the attached, unsealed Builder
@see Bar#fromLong(long)"""
    }

    def "documentation with custom member name"() {
        when:
        createClass("dummy/Foo.groovy", '''import com.blackbuild.klum.ast.Field
            @DSL class Foo {
                @Field(members = "berry")
                List<Berry> berries
            }
            
            @DSL class Berry {
                String color
            }
            ''')

        then:
        builderImplementationMethodDoc("berry", Closure) == """Creates a new 'berry' Builder and adds it to the Builder's 'berries' collection.

<p>The newly created Builder is configured by the optional values and closure.</p>

@param closure the closure to configure the new element
@return the newly created Builder"""
    }

    def "documentation with template tags"() {
        when:
        createClass("dummy/Foo.groovy", '''import com.blackbuild.klum.ast.Field
            @DSL class Foo {
                /**
                * The berries in the bag. 
                * @template singleElementName Yummy Berry
                * @template fieldName Yummy Berries
                */
                @Field(members = "berry")
                List<Berry> berries
            }
            
            @DSL class Berry {
                String color
            }
            ''')

        then:
        builderImplementationMethodDoc("berry", Closure) == """Creates a new 'Yummy Berry' Builder and adds it to the Builder's 'Yummy Berries' collection.

<p>The newly created Builder is configured by the optional values and closure.</p>

@param closure the closure to configure the new element
@return the newly created Builder"""
    }

    Class<?> getArrayClass(String className) {
        return Array.newInstance(getClass(className), 0).getClass()
    }

}
