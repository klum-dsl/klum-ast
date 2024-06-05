//file:noinspection GrPackage
package com.blackbuild.groovy.configdsl.transform.ast

import com.blackbuild.annodocimal.ast.extractor.ClassDocExtractor
import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import org.intellij.lang.annotations.Language
import spock.lang.Issue

@Issue("197")
class AnnoDocTest extends AbstractDSLSpec {

    File srcDir

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
    }

    String methodDoc(String methodName, Class... params) {
        return ClassDocExtractor.extractDocumentation(clazz.getDeclaredMethod(methodName, params))
    }

    String rwMethodDoc(String methodName, Class... params) {
        return ClassDocExtractor.extractDocumentation(rwClazz.getDeclaredMethod(methodName, params))
    }

    String fieldDoc(String fieldName) {
        return ClassDocExtractor.extractDocumentation(clazz.getDeclaredField(fieldName))
    }

    String classDoc() {
        return ClassDocExtractor.extractDocumentation(clazz)
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


}