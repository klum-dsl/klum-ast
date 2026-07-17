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
package com.blackbuild.groovy.configdsl.transform.ast

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.annodocimal.ast.extractor.ASTExtractor
import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import com.blackbuild.klum.ast.util.KlumBuilder
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.intellij.lang.annotations.Language
import spock.lang.Issue

import java.lang.reflect.Array

@Issue("197")
class AnnoDocTest extends AbstractDSLSpec {

    File srcDir
    ClassNode classNode
    ClassNode rwClassNode
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
        rwClazz = getRwClass(clazz.name)
        factoryClazz = getClass(clazz.name + '$_Factory')
        classNode = ClassHelper.make(clazz)
        rwClassNode = ClassHelper.make(rwClazz)
        factoryClassNode = ClassHelper.make(factoryClazz)
    }

    String methodDoc(String methodName, Class... params) {
        def methodNode = getMethod(classNode, methodName, params)
        return ASTExtractor.extractDocumentation(methodNode)
    }

    String rwMethodDoc(String methodName, Class... params) {
        return rwClazz.getMethod(methodName, params).getAnnotation(AnnoDoc)?.value()
    }

    String builderMethodDoc(String methodName, Class... params) {
        return getClass(clazz.name + '_DSL$Builder').getMethod(methodName, params).getAnnotation(AnnoDoc)?.value()
    }

    String creatorDoc(String methodName, Class... params) {
        def methodNode = getMethod(factoryClassNode, methodName, params)
        return ASTExtractor.extractDocumentation(methodNode)
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
        return ASTExtractor.extractDocumentation(classNode.getDeclaredField(fieldName),)
    }

    String classDoc() {
        return ASTExtractor.extractDocumentation(classNode)
    }

    String rwClassDoc() {
        return ASTExtractor.extractDocumentation(rwClassNode)
    }

    def "javadoc reflects the model and Builder API split"() {
        when:
        createClass("dummy/Foo.groovy", '''
package dummy 

import com.blackbuild.groovy.configdsl.transform.DSL

/**
 * This is a class
 */
@DSL class Foo {}
''')

        then:
        classDoc() == '''This is a class'''
        rwClassDoc() == "The generated Builder for dummy.Foo."
        !clazz.declaredMethods*.name.contains("apply")
        rwClazz.getMethod("apply", Map).declaringClass == KlumBuilder
        rwMethodDoc("copyFrom", clazz) == """Copies all non-null/non-empty recipe values from the template to this Builder.
@param template the recipe to apply"""

    }

    def "generated getters document model and Builder values"() {
        when:
        createClass("dummy/Foo.groovy", '''
package dummy

import com.blackbuild.groovy.configdsl.transform.DSL

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

        then: "completed-model getters are documented"
        methodDoc("getName") == "Returns the display name."
        methodDoc("getActive") == "Returns the active flag."
        methodDoc("isActive") == "Returns the active flag."
        methodDoc("getLegacyName") == "Returns the legacy name.\n@deprecated Use name instead."

        and: "Builder implementation getters are documented"
        rwMethodDoc("getName") == "Returns the display name."
        rwMethodDoc("getActive") == "Returns the active flag."
        rwMethodDoc("isActive") == "Returns the active flag."
        rwMethodDoc("getLegacyName") == "Returns the legacy name.\n@deprecated Use name instead."

        and: "public Builder contract getters carry the same documentation"
        builderMethodDoc("getName") == "Returns the display name."
        builderMethodDoc("getActive") == "Returns the active flag."
        builderMethodDoc("isActive") == "Returns the active flag."
        builderMethodDoc("getLegacyName") == "Returns the legacy name.\n@deprecated Use name instead."
    }

    def "javadoc for auto overridden creator"() {
        when:
        createClass("dummy/Foo.groovy", '''
package dummy 

import com.blackbuild.groovy.configdsl.transform.DSL

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

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.DelegatesToRW
import com.blackbuild.klum.ast.util.KlumFactory

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
        rwMethodDoc("bar", Closure) == """Creates a new 'bar' Builder and adds it to the Builder's 'bars' collection.
<p>
The newly created Builder is configured by the optional values and closure.
</p>
@param closure the closure to configure the new element
@return the newly created Builder"""
        rwMethodDoc("bar", Map) == """Creates a new 'bar' Builder and adds it to the Builder's 'bars' collection.
<p>
The newly created Builder is configured by the optional values and closure.
</p>
@param values the optional parameters
@param closure the closure to configure the new element
@return the newly created Builder""" // closures has a default value, so during ast it is a single method
        rwMethodDoc("bars", getArrayClass("dummy.Bar\$_RW")) == """Adds one or more 'bar' Builders to the Builder's 'bars' collection.
@param values the elements to add"""
        rwMethodDoc("bars", Iterable) == """Adds one or more 'bar' Builders to the Builder's 'bars' collection.
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
        rwMethodDoc("bar", long) == """Creates an unsealed Builder in the active construction session and attaches it to this relationship.
<p>
The returned Builder remains attached to the current construction session; it cannot be independently materialized or validated.
</p>
@param value the timestamp
@return the attached, unsealed Builder
@see Bar#fromLong(long)"""
    }

    def "documentation with custom member name"() {
        when:
        createClass("dummy/Foo.groovy", '''import com.blackbuild.groovy.configdsl.transform.Field
            @DSL class Foo {
                @Field(members = "berry")
                List<Berry> berries
            }
            
            @DSL class Berry {
                String color
            }
            ''')

        then:
        rwMethodDoc("berry", Closure) == """Creates a new 'berry' Builder and adds it to the Builder's 'berries' collection.
<p>
The newly created Builder is configured by the optional values and closure.
</p>
@param closure the closure to configure the new element
@return the newly created Builder"""
    }

    def "documentation with template tags"() {
        when:
        createClass("dummy/Foo.groovy", '''import com.blackbuild.groovy.configdsl.transform.Field
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
        rwMethodDoc("berry", Closure) == """Creates a new 'Yummy Berry' Builder and adds it to the Builder's 'Yummy Berries' collection.
<p>
The newly created Builder is configured by the optional values and closure.
</p>
@param closure the closure to configure the new element
@return the newly created Builder"""
    }

    Class<?> getArrayClass(String className) {
        return Array.newInstance(getClass(className), 0).getClass()
    }

}
