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
package com.blackbuild.groovy.configdsl.transform

class BuilderProjectionWithFilesSpec extends AbstractFileBasedDSLSpec {

    def "generated cross-package callers bind directly to public synthetic twins"() {
        given:
        withFile 'Child.groovy', '''
            package child

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.klum.ast.util.KlumFactory

            @DSL class Child {
                String label

                static class Factory extends KlumFactory.Unkeyed<Child> {
                    protected Factory() { super(Child) }

                    Child fromLabel(String label) {
                        return With(label: label)
                    }
                }
            }
        '''
        withFile 'Root.groovy', '''
            package root

            import child.Child
            import com.blackbuild.groovy.configdsl.transform.DSL

            @DSL class Root {
                List<Child> children
            }
        '''
        prepareCompilationUnitFiles()

        when:
        compile()
        def child = compileUnit.classLoader.loadClass('child.Child')
        def direct = child.Create.fromLabel('direct-root')
        def root = compileUnit.classLoader.loadClass('root.Root')
        def collectionFactoryClass = compileUnit.classLoader.loadClass('root.Root$_children')
        assert collectionFactoryClass.methods*.name.contains('fromLabel')
        def instance = root.Create.With {
            def collectionFactory = collectionFactoryClass.constructors.first().newInstance(delegate)
            collectionFactory.fromLabel('cross-package')
        }
        def twin = compileUnit.classLoader.loadClass('child.Child$Factory').declaredMethods.find {
            it.name == '$klum$asBuilder$fromLabel'
        }

        then:
        child.isInstance(direct)
        direct.label == 'direct-root'
        instance.children*.label == ['cross-package']
        twin != null
        twin.synthetic
        java.lang.reflect.Modifier.isPublic(twin.modifiers)
        !compileUnit.classLoader.loadClass('child.Child_DSL$Builder').declaredMethods*.name.contains(twin.name)
    }
}
