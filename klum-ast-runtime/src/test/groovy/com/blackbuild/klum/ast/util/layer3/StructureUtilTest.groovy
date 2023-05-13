package com.blackbuild.klum.ast.util.layer3

import com.blackbuild.klum.ast.util.AbstractRuntimeTest

class StructureUtilTest extends AbstractRuntimeTest {
    def "getPathOfFieldContaining returns the correct field"() {
        given:
        createClass '''
            class Owner {
                String name
                Child child
                Child otherChild
            }
            
            class Child {
                String name
            }
'''
        def child1 = newInstanceOf("Child", [name: "John"])
        def child2 = newInstanceOf("Child", [name: "Jane"])
        def child3 = newInstanceOf("Child", [name: "Jack"])

        when:
        def owner = newInstanceOf("Owner", [name: "John", child: child1, otherChild: child2])

        then:
        StructureUtil.getPathOfFieldContaining(owner, child1).get() == "child"
        StructureUtil.getPathOfFieldContaining(owner, child2).get() == "otherChild"
        !StructureUtil.getPathOfFieldContaining(owner, child3).present
    }

    def "getPathOfFieldContaining for Collections return the right indexed name"() {
        given:
        createClass '''
            class Owner {
                String name
                Collection<Child> children
                Collection<Child> otherChildren
            }
            
            class Child {
                String name
            }
'''
        def child1 = newInstanceOf("Child", [name: "John"])
        def child2 = newInstanceOf("Child", [name: "Jane"])
        def child3 = newInstanceOf("Child", [name: "Jack"])
        def child4 = newInstanceOf("Child", [name: "Jill"])

        when:
        def owner = newInstanceOf("Owner", [name: "John", children: [child1], otherChildren: [child2, child3]]);

        then:
        StructureUtil.getPathOfFieldContaining(owner, child1).get() == "children[0]";
        StructureUtil.getPathOfFieldContaining(owner, child2).get() == "otherChildren[0]";
        StructureUtil.getPathOfFieldContaining(owner, child3).get() == "otherChildren[1]";
        !StructureUtil.getPathOfFieldContaining(owner, child4).present
    }

    def "getPathOfFieldContaining for Maps return the right indexed name"() {
        given:
        createClass '''
            class Owner {
                String name
                Map<String, Child> children
                Map<String, Child> otherChildren
            }
            
            class Child {
                String name
            }
'''
        def child1 = newInstanceOf("Child", [name: "John"])
        def child2 = newInstanceOf("Child", [name: "Jane"])
        def child3 = newInstanceOf("Child", [name: "Jack"])
        def child4 = newInstanceOf("Child", [name: "Jill"])

        when:
        def owner = newInstanceOf("Owner", [name: "John", children: [c1: child1], otherChildren: [c2: child2, 'c-3': child3]])

        then:
        StructureUtil.getPathOfFieldContaining(owner, child1).get() == "children.c1"
        StructureUtil.getPathOfFieldContaining(owner, child2).get() == "otherChildren.c2"
        StructureUtil.getPathOfFieldContaining(owner, child3).get() == "otherChildren.'c-3'"
        !StructureUtil.getPathOfFieldContaining(owner, child4).present
    }

}
