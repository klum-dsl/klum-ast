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
package com.blackbuild.groovy.configdsl.transform.ast.process

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import com.blackbuild.klum.ast.process.BreadcrumbCollector
import com.blackbuild.klum.ast.util.KlumModelException

import static com.blackbuild.klum.ast.util.KlumInstanceProxy.getProxyFor

@SuppressWarnings("GrPackage")
class BreadcrumbCollectorRuntimeTest extends AbstractDSLSpec {

    def cleanup() {
        BreadcrumbCollector.INSTANCE.remove()
    }

    def "basic breadcrumb test"() {
        given:
        createClass '''
package pk

@DSL
class Foo {
}
 '''
        when:
        String breadCrumbPath = null
        instance = Foo.Create.With {
            breadCrumbPath = BreadcrumbCollector.instance.fullPath
        }

        then:
        breadCrumbPath == '$/p.Foo.With'
        breadCrumbFor(instance) == '$/p.Foo.With'
    }

    def "basic keyed breadcrumb test"() {
        given:
        createClass '''
package pk

import com.blackbuild.groovy.configdsl.transform.Key

@DSL
class Foo {
    @Key String name
}
 '''
        when:
        String breadCrumbPath = null
        instance = clazz.Create.With("Kurt") {
            breadCrumbPath = BreadcrumbCollector.instance.fullPath
        }

        then:
        breadCrumbPath == '$/p.Foo.With(Kurt)'
        breadCrumbFor(instance) == '$/p.Foo.With(Kurt)'
    }

    def "nested breadcrumb test"() {
        given:
        createClass '''
package pk

import com.blackbuild.groovy.configdsl.transform.Key

@DSL
class Foo {
    Bar bar
    KeyBar keyBar
}

@DSL
class Bar {
}

@DSL
class KeyBar {
    @Key String name
}

 '''
        when:
        String breadCrumbPath = null
        String breadCrumbPath2 = null
        instance = clazz.Create.With {
            bar {
                breadCrumbPath = BreadcrumbCollector.instance.fullPath
            }
            keyBar("Kurt") {
                breadCrumbPath2 = BreadcrumbCollector.instance.fullPath
            }
        }

        then:
        breadCrumbPath == '$/p.Foo.With/bar'
        breadCrumbPath2 == '$/p.Foo.With/keyBar(Kurt)'
        breadCrumbFor(instance) == '$/p.Foo.With'
        breadCrumbFor(instance.bar) == '$/p.Foo.With/bar'
        breadCrumbFor(instance.keyBar) == '$/p.Foo.With/keyBar(Kurt)'

        when:
        instance = clazz.Create.With {
            bar {}
            keyBar("Kurt") {}
            bar {}
            keyBar("Kurt") {}
        }

        then:
        breadCrumbFor(instance.bar) == '$/p.Foo.With/bar.2'
        breadCrumbFor(instance.keyBar) == '$/p.Foo.With/keyBar(Kurt).2'

        when:
        instance = clazz.Create.With {
            keyBar("Kurt") {}
            keyBar("Murt") {}
        }

        then:
        def e = thrown(KlumModelException)
        e.breadCrumbPath == '$/p.Foo.With/keyBar(Murt)'
    }

    private String breadCrumbFor(instance) {
        getProxyFor(instance).getBreadcrumbPath()
    }

    def "list members breadcrumb test"() {
        given:
        createClass '''
@DSL
class Foo {
    List<Bar> bars
}

@DSL
class Bar {
}
'''

        when:
        instance = Foo.Create.With {
            bar {}
            bar {}
        }

        then:
        breadCrumbFor(instance) == '$/Foo.With'
        breadCrumbFor(instance.bars[0]) == '$/Foo.With/bar'
        breadCrumbFor(instance.bars[1]) == '$/Foo.With/bar[2]'
    }

    def "complex test with collections and maps"() {
        given:
        createClass '''
package pk

import com.blackbuild.groovy.configdsl.transform.Key

@DSL
class Model {
    Middle singleMiddle
    List<Middle> middles
    Map<String, Middle> mapMiddles
}

@DSL
class Middle {
    @Key String name
    Set<Inner> inners
}

class Inner {
}
 '''
        when:
        instance = clazz.Create.With {
            singleMiddle("Kurt") {}
            mapMiddle("Hans") {}
            mapMiddle("Peter") {}
            middle("Hans") {}
            middle("Franz") {}
        }

        then:
        breadCrumbFor(instance.singleMiddle) == '$/p.Model.With/singleMiddle(Kurt)'
        breadCrumbFor(instance.middles[0]) == '$/p.Model.With/middle(Hans)'
        breadCrumbFor(instance.middles[1]) == '$/p.Model.With/middle(Franz)'
        breadCrumbFor(instance.mapMiddles["Hans"]) == '$/p.Model.With/mapMiddle(Hans)'
        breadCrumbFor(instance.mapMiddles["Peter"]) == '$/p.Model.With/mapMiddle(Peter)'
    }

    def "complex test with collections and maps and collection factories"() {
        given:
        createClass '''
package pk

import com.blackbuild.groovy.configdsl.transform.Key

@DSL
class Model {
    Middle singleMiddle
    List<Middle> middles
    Map<String, Middle> mapMiddles
}

@DSL
class Middle {
    @Key String name
}

class Inner {
}
 '''

        when:
        Class<?> Middle = getClass("pk.Middle")
        instance = clazz.Create.With {

            singleMiddle("adder") {}
            mapMiddles {
                mapMiddle("adder") {}
                mapMiddle(Middle, "adderWithClass") {}
                With("factory") {}
            }
            middle("adder") {}
            middle(Middle, "adderWithClass") {}

            middles {
                middle("addera") {}
                middle(Middle, "adderWithClassa") {}
                One("factory")
            }
        }

        then:
        verifyAll {
            breadCrumbFor(instance.singleMiddle) == '$/p.Model.With/singleMiddle(adder)'
            breadCrumbFor(instance.mapMiddles["adder"]) == '$/p.Model.With/mapMiddles/mapMiddle(adder)'
            breadCrumbFor(instance.mapMiddles["adderWithClass"]) == '$/p.Model.With/mapMiddles/mapMiddle:p.Middle(adderWithClass)'
            breadCrumbFor(instance.mapMiddles["factory"]) == '$/p.Model.With/mapMiddles/With(factory)'
            breadCrumbFor(instance.middles[0]) == '$/p.Model.With/middle(adder)'
            breadCrumbFor(instance.middles[1]) == '$/p.Model.With/middle:p.Middle(adderWithClass)'
            breadCrumbFor(instance.middles[2]) == '$/p.Model.With/middles/middle(addera)'
            breadCrumbFor(instance.middles[3]) == '$/p.Model.With/middles/middle:p.Middle(adderWithClassa)'
            breadCrumbFor(instance.middles[4]) == '$/p.Model.With/middles/One(factory)'
        }
    }

    def "from script adds to the breadcrumb"() {
        given:
        createClass '''
package pk

@DSL
class Foo {
    @Key String name
    String value
}
'''
        def scriptFile = scriptFile("bla/my.model", '''
value "bla"
''')
        when:
        instance = Foo.Create.From(scriptFile)

        then:
        breadCrumbFor(instance) == "\$/p.Foo.From:file($scriptFile.path)"
    }

    def "from url adds to the breadcrumb"() {
        given:
        createClass '''
package pk

@DSL
class Foo {
    @Key String name
    String value
}
'''
        def scriptUrl = scriptFile("bla/my.model", '''
value "bla"
''').toURI().toURL()
        when:
        instance = Foo.Create.From(scriptUrl)

        then:
        breadCrumbFor(instance) == "\$/p.Foo.From:url($scriptUrl)"
    }

    def "breadcrumbs in templates"() {
        given:
        createClass '''
package pk

import com.blackbuild.groovy.configdsl.transform.Key

@DSL
class Model {
    Middle singleMiddle
    List<Middle> middles
    Map<String, Middle> mapMiddles
}

@DSL
class Middle {
    @Key String name
}

class Inner {
}
 '''

        when:
        Class<?> Middle = getClass("pk.Middle")
        def template = clazz.Create.Template {

            singleMiddle("adder") {}
            mapMiddles {
                mapMiddle("adder") {}
                mapMiddle(Middle, "adderWithClass") {}
                With("factory") {}
            }
            middle("adder") {}
            middle(Middle, "adderWithClass") {}

            middles {
                middle("addera") {}
                middle(Middle, "adderWithClassa") {}
                One("factory")
            }
        }

        then:
        verifyAll {
            breadCrumbFor(template.singleMiddle) == '$/p.Model.Template/singleMiddle(adder)'
            breadCrumbFor(template.mapMiddles["adder"]) == '$/p.Model.Template/mapMiddles/mapMiddle(adder)'
            breadCrumbFor(template.mapMiddles["adderWithClass"]) == '$/p.Model.Template/mapMiddles/mapMiddle:p.Middle(adderWithClass)'
            breadCrumbFor(template.mapMiddles["factory"]) == '$/p.Model.Template/mapMiddles/With(factory)'
            breadCrumbFor(template.middles[0]) == '$/p.Model.Template/middle(adder)'
            breadCrumbFor(template.middles[1]) == '$/p.Model.Template/middle:p.Middle(adderWithClass)'
            breadCrumbFor(template.middles[2]) == '$/p.Model.Template/middles/middle(addera)'
            breadCrumbFor(template.middles[3]) == '$/p.Model.Template/middles/middle:p.Middle(adderWithClassa)'
            breadCrumbFor(template.middles[4]) == '$/p.Model.Template/middles/One(factory)'
        }

        when:
        instance = Model.withTemplate(template) {
            Model.Create.One()
        }

        then:
        verifyAll {
            breadCrumbFor(instance.singleMiddle) == '$/p.Model.With/singleMiddle(adder)'
            breadCrumbFor(instance.mapMiddles["adder"]) == '$/p.Model.With/mapMiddles/mapMiddle(adder)'
            breadCrumbFor(instance.mapMiddles["adderWithClass"]) == '$/p.Model.With/mapMiddles/mapMiddle:p.Middle(adderWithClass)'
            breadCrumbFor(instance.mapMiddles["factory"]) == '$/p.Model.With/mapMiddles/With(factory)'
            breadCrumbFor(instance.middles[0]) == '$/p.Model.With/middle(adder)'
            breadCrumbFor(instance.middles[1]) == '$/p.Model.With/middle:p.Middle(adderWithClass)'
            breadCrumbFor(instance.middles[2]) == '$/p.Model.With/middles/middle(addera)'
            breadCrumbFor(instance.middles[3]) == '$/p.Model.With/middles/middle:p.Middle(adderWithClassa)'
            breadCrumbFor(instance.middles[4]) == '$/p.Model.With/middles/One(factory)'
        }
    }

}