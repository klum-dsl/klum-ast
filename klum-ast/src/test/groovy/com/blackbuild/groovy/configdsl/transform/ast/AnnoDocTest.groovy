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
        return ASTExtractor.extractDocumentation(getMethod(classNode, methodName, params))
    }

    String rwMethodDoc(String methodName, Class... params) {
        return ASTExtractor.extractDocumentation(getMethod(rwClassNode, methodName, params))
    }

    String creatorDoc(String methodName, Class... params) {
        return ASTExtractor.extractDocumentation(getMethod(factoryClassNode, methodName, params))
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
@return the object itself
"""
        methodDoc("apply", Closure) == """Applies the given named params and the closure to this proxy's object.
<p>
Both params are optional. The map will be converted into a series of method calls, with the key being the method name and the value the single method argument.
The closure will be executed against the instance's RW object.
</p>
<p>
Note that explicit calls to apply() are usually not necessary, as apply is part of the creation of an object.
</p>
@param closure Closure to be executed against the instance.
@return the object itself
"""
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
@return the object itself
"""
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
@return the object itself
"""
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
@return the result of the closure
"""
        methodDoc("withTemplate", Map, Closure) == """Executes the given closure with an anonymous template for the given type.
<p>
This means that all objects of the given type created in the scope of the closure will use the given template,
which also includes objects deeper in the structure.
The template will be created from the given map (using Create.AsTemplate(Map)).
The old template is restored after the closure has been executed.
</p>
@param templateMap the Map to construct the template from
@param closure the closure to execute
@return the result of the closure
"""
        methodDoc("withTemplates", List, Closure) == """Executes the given closure with the given templates.
<p>
This means that all objects of the given types created in the scope of the closure will use the given template,
which also includes objects deeper in the structure.
The old templates are restored after the closure has been executed.
</p>
@param templates the templates to apply
@param closure the closure to execute
@return the result of the closure
"""

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
@deprecated use #withTemplates(List, Closure)
"""
        rwMethodDoc("copyFrom", clazz) == """Copies all non null / non empty elements from target to this.
@param template The template to apply
"""

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

    protected MyFactory(Class<dummy.Foo> type) {
        super(type)
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

    def "converter factory for dsl field"() {
        when:
        createClass("dummy/Foo.groovy", '''
            package dummy
            @DSL class Foo {
                Bar bar
            }
            
            @DSL class Bar {
                Date birthday
                
                /**
                * Creates a new instance of Bar with the given birthday.
                * @param value the birthday as long
                * @return the instantiated object
                */
                static Bar fromLong(long value) {
                    return Bar.Create.With(birthday: new Date(value))
                }
            }''')

        then:
        rwMethodDoc("bar", long) == """Creates a new instance of Bar with the given birthday.
@param value the birthday as long
@return the instantiated object
"""
    }

}