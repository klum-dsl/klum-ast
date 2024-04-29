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
        AnnoDocUtil.getDocumentation(clazz.getDeclaredMethod("apply")) == """Configures this instance with the given values and closure.
<p>
The optional value map is converted to single parameter calls.
</p>
<p>
Note that explicit calls to apply() are usually not necessary, as apply is usually part of the creation of an object.
</p>
@param closure the closure to configure this instance
@return the configured instance
"""
        AnnoDocUtil.getDocumentation(clazz.getDeclaredMethod("apply", Closure)) == """Configures this instance with the given values and closure.
<p>
The optional value map is converted to single parameter calls.
</p>
<p>
Note that explicit calls to apply() are usually not necessary, as apply is usually part of the creation of an object.
</p>
@param closure the closure to configure this instance
@return the configured instance
"""
        AnnoDocUtil.getDocumentation(clazz.getDeclaredMethod("apply", Map)) == """Configures this instance with the given values and closure.
<p>
The optional value map is converted to single parameter calls.
</p>
<p>
Note that explicit calls to apply() are usually not necessary, as apply is usually part of the creation of an object.
</p>
@param values map of values to apply
@param closure the closure to configure this instance
@return the configured instance
"""
        AnnoDocUtil.getDocumentation(clazz.getDeclaredMethod("apply", Map, Closure)) == """Configures this instance with the given values and closure.
<p>
The optional value map is converted to single parameter calls.
</p>
<p>
Note that explicit calls to apply() are usually not necessary, as apply is usually part of the creation of an object.
</p>
@param values map of values to apply
@param closure the closure to configure this instance
@return the configured instance
"""
    }

}