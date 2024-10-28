/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform.ast

import com.blackbuild.annodocimal.ast.extractor.ASTExtractor
import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
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
        def methodNode = getMethod(rwClassNode, methodName, params)
        return ASTExtractor.extractDocumentation(methodNode)
    }

    String creatorDoc(String methodName, Class... params) {
        def methodNode = getMethod(factoryClassNode, methodName, params)
        return ASTExtractor.extractDocumentation(methodNode)
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

    def "javadoc for class taken from proxied methods"() {
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
        methodDoc("apply") == """Applies the given named params and the closure to this proxy's object.
<p>
Both params are optional. The map will be converted into a series of method calls, with the key being the method name and the value the single method argument.
The closure will be executed against the instance's RW object.
</p>
<p>
Note that explicit calls to apply() are usually not necessary, as apply is part of the creation of an object.
</p>
@param closure Closure to be executed against the instance.
@return the object itself"""

        methodDoc("apply", Closure) == """Applies the given named params and the closure to this proxy's object.
<p>
Both params are optional. The map will be converted into a series of method calls, with the key being the method name and the value the single method argument.
The closure will be executed against the instance's RW object.
</p>
<p>
Note that explicit calls to apply() are usually not necessary, as apply is part of the creation of an object.
</p>
@param closure Closure to be executed against the instance.
@return the object itself"""

        methodDoc("apply", Map) == """Applies the given named params and the closure to this proxy's object.
<p>
Both params are optional. The map will be converted into a series of method calls, with the key being the method name and the value the single method argument.
The closure will be executed against the instance's RW object.
</p>
<p>
Note that explicit calls to apply() are usually not necessary, as apply is part of the creation of an object.
</p>
@param values Map of String to Object which will be translated into Method calls
@param closure Closure to be executed against the instance.
@return the object itself"""

        methodDoc("apply", Map, Closure) == """Applies the given named params and the closure to this proxy's object.
<p>
Both params are optional. The map will be converted into a series of method calls, with the key being the method name and the value the single method argument.
The closure will be executed against the instance's RW object.
</p>
<p>
Note that explicit calls to apply() are usually not necessary, as apply is part of the creation of an object.
</p>
@param values Map of String to Object which will be translated into Method calls
@param closure Closure to be executed against the instance.
@return the object itself"""

        methodDoc("apply", Map) == rwMethodDoc("apply", Map)
        methodDoc("apply", Closure) == rwMethodDoc("apply", Closure)
        methodDoc("apply", Map, Closure) == rwMethodDoc("apply", Map, Closure)

        methodDoc("withTemplate", clazz, Closure) == """Executes the given closure with the given template as the template for the given type.
<p>
This means that all objects of the given type created in the scope of the closure will use the given template,
which also includes objects deeper in the structure.
The old template is restored after the closure has been executed.
</p>
@param template the template
@param closure the closure to execute
@return the result of the closure"""
        methodDoc("withTemplate", Map, Closure) == """Executes the given closure with an anonymous template for the given type.
<p>
This means that all objects of the given type created in the scope of the closure will use the given template,
which also includes objects deeper in the structure.
The template will be created from the given map (using Create.AsTemplate(Map)).
The old template is restored after the closure has been executed.
</p>
@param templateMap the Map to construct the template from
@param closure the closure to execute
@return the result of the closure"""
        methodDoc("withTemplates", List, Closure) == """Executes the given closure with the given templates.
<p>
This means that all objects of the given types created in the scope of the closure will use the given template,
which also includes objects deeper in the structure.
The old templates are restored after the closure has been executed.
</p>
@param templates the templates to apply
@param closure the closure to execute
@return the result of the closure"""

        methodDoc("withTemplates", Map, Closure) == """Executes the given closure with the given templates.
<p>
This means that all objects of the given types created in the scope of the closure will use the given template,
which also includes objects deeper in the structure.
The old templates are restored after the closure has been executed. Usually it
is better to use {@link #withTemplates(List, Closure)}, which maps the templates
to their respective classes.
</p>
@param templates the templates to apply, Mapping classes to their respective templates
@param closure the closure to execute
@return the result of the closure
@deprecated use #withTemplates(List, Closure)"""
        rwMethodDoc("copyFrom", clazz) == """Copies all non null / non empty elements from target to this.
@param template The template to apply"""

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
        creatorDoc("With", Closure) == """Creates a new instance of the model applying the given configuration closure.
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
        rwMethodDoc("bar", Closure) == """Creates a new 'bar' and adds it to the 'bars' collection.
<p>
The newly created element will be configured by the optional parameters values and closure.
</p>
@param closure the closure to configure the new element
@return the newly created element"""
        rwMethodDoc("bar", Map) == """Creates a new 'bar' and adds it to the 'bars' collection.
<p>
The newly created element will be configured by the optional parameters values and closure.
</p>
@param values the optional parameters
@param closure the closure to configure the new element
@return the newly created element""" // closures has a default value, so during ast it is a single method
        rwMethodDoc("bars", getArrayClass("dummy.Bar")) == """Adds one or more existing 'bar' to the 'bars' collection.
@param values the elements to add"""
        rwMethodDoc("bars", Iterable) == """Adds one or more existing 'bar' to the 'bars' collection.
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
        rwMethodDoc("bar", long) == """Creates a new instance of Bar with the given birthday as timestamp.
@param value the timestamp
@return the newly created instance
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
        rwMethodDoc("berry", Closure) == """Creates a new 'berry' and adds it to the 'berries' collection.
<p>
The newly created element will be configured by the optional parameters values and closure.
</p>
@param closure the closure to configure the new element
@return the newly created element"""
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
        rwMethodDoc("berry", Closure) == """Creates a new 'Yummy Berry' and adds it to the 'Yummy Berries' collection.
<p>
The newly created element will be configured by the optional parameters values and closure.
</p>
@param closure the closure to configure the new element
@return the newly created element"""
    }

    Class<?> getArrayClass(String className) {
        return Array.newInstance(getClass(className), 0).getClass()
    }

}