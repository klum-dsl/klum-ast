/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util.layer3

import com.blackbuild.klum.ast.util.AbstractRuntimeTest
import spock.lang.Issue

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

    def "bug: getNonIgnoredProperties fails on String fields"() {
        given:
        createClass '''
            class Owner {
                String name
            }
        '''

        def instance = newInstanceOf("Owner", [name: "John"])

        when:
        def result = StructureUtil.deepFind(instance, Integer)

        then:
        noExceptionThrown()
        result.isEmpty()
    }

    @Issue("396")
    def "structureUtil should ignore owner fields and link only fields"() {
        given:
        createClass '''import com.blackbuild.groovy.configdsl.transform.Owner
import com.blackbuild.klum.ast.KlumModelObject

            class Container implements KlumModelObject {
                String name
                Content child
                Content otherChild
            }
            
            class Content implements KlumModelObject {
                String name
                @Owner Container container 
            }
        '''

        def visitor = new DslObjectOnlyModelVisitor() {
            List<Object> visited = []

            @Override
            void visit(String path, Object element, Object container, String nameOfFieldInContainer) {
                visited.add(element)
            }
        }

        def container = newInstanceOf("Container", [name: "Container1", child: newInstanceOf("Content", [name: "Child1"]), otherChild: newInstanceOf("Content", [name: "Child2"])])
        container.child.container = container
        container.otherChild.container = container

        when:
        StructureUtil.visit(container, visitor)

        then:
        visitor.visited.size() == 3
        visitor.visited[0].is(container)
        visitor.visited[1].is(container.child)
        visitor.visited[2].is(container.otherChild)

        when:
        visitor.visited.clear()
        StructureUtil.visit(container.child, visitor)

        then:
        visitor.visited.size() == 1
        visitor.visited[0].is(container.child)
    }
    
}
