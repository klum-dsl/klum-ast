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
package com.blackbuild.groovy.configdsl.transform

import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ScriptTest extends AbstractDSLSpec {

    @Rule
    TemporaryFolder tempFolder = new TemporaryFolder()
    File scriptFile

    def "Object key should match the filename"() {
        given:
        createClass '''
package pk

@DSL
class Foo {
    @Key String name
    String value
}
'''
        scriptFile = scriptFile("Dummy.Bummy.template", '''
value "bla"
''')

        when:
        instance = clazz.Create.From(scriptFile)

        then:
        instance.name == "Dummy.Bummy"
        instance.value == "bla"

        when:
        instance = clazz.Create.From(scriptFile.toURI().toURL())

        then:
        instance.name == "Dummy.Bummy"
        instance.value == "bla"
    }

    def "key provider can be explicitly set"() {
        given:
        createClass '''
package pk

@DSL
class Foo {
    @Key String name
    String value
}
'''
        scriptFile = scriptFile("Dummy.Bummy.template", '''
value "bla"
''')

        when:
        instance = clazz.Create.From(scriptFile, { it.name.toLowerCase() - ".template" })

        then:
        instance.name == "dummy.bummy"
        instance.value == "bla"

        when:
        instance = clazz.Create.From(scriptFile.toURI().toURL(), { it.path.tokenize("/").last().toLowerCase() - ".template" })

        then:
        instance.name == "dummy.bummy"
        instance.value == "bla"
    }

    File scriptFile(String filename, @Language("groovy") String code) {
        File file = tempFolder.newFile(filename)
        file.text = code
        return file
    }
}