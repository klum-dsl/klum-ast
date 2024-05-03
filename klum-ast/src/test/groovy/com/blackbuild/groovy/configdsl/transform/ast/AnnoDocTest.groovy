//file:noinspection GrPackage
package com.blackbuild.groovy.configdsl.transform.ast

import com.blackbuild.annodocimal.ast.formatting.AnnoDocUtil
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
    }


    def "Basic javadoc for class"() {
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
        AnnoDocUtil.getDocumentation(clazz) == '''This is a class'''
        AnnoDocUtil.getDocumentation(clazz.getDeclaredMethod("apply")) == """Applies the given named params and the closure to this proxy's object.
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
        AnnoDocUtil.getDocumentation(clazz.getDeclaredMethod("apply", Closure)) == """Applies the given named params and the closure to this proxy's object.
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
        AnnoDocUtil.getDocumentation(clazz.getDeclaredMethod("apply", Map)) == """Applies the given named params and the closure to this proxy's object.
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
        AnnoDocUtil.getDocumentation(clazz.getDeclaredMethod("apply", Map, Closure)) == """Applies the given named params and the closure to this proxy's object.
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
    }

}