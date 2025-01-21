package com.blackbuild.groovy.configdsl.transform.ast.process

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import com.blackbuild.klum.ast.process.BreadcrumbCollector
import com.blackbuild.klum.ast.util.KlumModelException

import static com.blackbuild.klum.ast.util.KlumInstanceProxy.getProxyFor

@SuppressWarnings("GrPackage")
class BreadcrumbCollectorRuntimeTest extends AbstractDSLSpec {

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
        instance = clazz.Create.With {
            breadCrumbPath = BreadcrumbCollector.instance.fullPath
        }

        then:
        breadCrumbPath == "/With:p.Foo"
        breadCrumbFor(instance) == "/With:p.Foo"
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
        breadCrumbPath == "/With:p.Foo(Kurt)"
        breadCrumbFor(instance) == "/With:p.Foo(Kurt)"
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
        breadCrumbPath == "/With:p.Foo/bar"
        breadCrumbPath2 == "/With:p.Foo/keyBar(Kurt)"
        breadCrumbFor(instance) == "/With:p.Foo"
        breadCrumbFor(instance.bar) == "/With:p.Foo/bar"
        breadCrumbFor(instance.keyBar) == "/With:p.Foo/keyBar(Kurt)"

        when:
        instance = clazz.Create.With {
            bar {}
            keyBar("Kurt") {}
            bar {}
            keyBar("Kurt") {}
        }

        then:
        breadCrumbFor(instance.bar) == "/With:p.Foo/bar.2"
        breadCrumbFor(instance.keyBar) == "/With:p.Foo/keyBar(Kurt).2"

        when:
        instance = clazz.Create.With {
            keyBar("Kurt") {}
            keyBar("Murt") {}
        }

        then:
        def e = thrown(KlumModelException)
        e.breadCrumbPath == "/With:p.Foo/keyBar(Murt)"
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
        breadCrumbFor(instance) == "/With:Foo"
        breadCrumbFor(instance.bars[0]) == "/With:Foo/bar"
        breadCrumbFor(instance.bars[1]) == "/With:Foo/bar(2)"
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
        breadCrumbFor(instance.singleMiddle) == "/With:p.Model/singleMiddle(Kurt)"
        breadCrumbFor(instance.middles[0]) == "/With:p.Model/middle(Hans)"
        breadCrumbFor(instance.middles[1]) == "/With:p.Model/middle(Franz)"
        breadCrumbFor(instance.mapMiddles["Hans"]) == "/With:p.Model/mapMiddle(Hans)"
        breadCrumbFor(instance.mapMiddles["Peter"]) == "/With:p.Model/mapMiddle(Peter)"
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
    Set<Inner> inners
}

class Inner {
}
 '''

        when:
        instance = clazz.Create.With {
            mapMiddles {
                mapMiddle("Hans") {}
                With("Peter") {}
            }
            middles {
                middle("Hans") {}
                One("Franz")
            }
        }

        then:
        breadCrumbFor(instance.middles[0]) == "/With:p.Model/middles/middle(Hans)"
        breadCrumbFor(instance.middles[1]) == "/With:p.Model/middles/With:p.Middle(Franz)"
        breadCrumbFor(instance.mapMiddles["Hans"]) == "/With:p.Model/mapMiddles/mapMiddle(Hans)"
        breadCrumbFor(instance.mapMiddles["Peter"]) == "/With:p.Model/mapMiddles/With:p.Middle(Peter)"
    }

}