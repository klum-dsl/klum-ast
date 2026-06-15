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
package com.blackbuild.klum.ast.util

import com.blackbuild.groovy.configdsl.transform.Validate

class LifecycleHelperTest extends AbstractRuntimeTest {

    def "lifecycle classes are public non static inner classes"() {
        given:
        createClass '''
    package pk
    import com.blackbuild.groovy.configdsl.transform.Validate
    class MyClass {
        @Validate class NonStatic {}
        @Validate static class StaticClass {}
        @Validate interface Interface {}
        @Validate private class PrivateClass {} 
        @Validate abstract class AbstractClass {}
    }
'''

        when:
        def lifecycleClasses = LifecycleHelper.getLifecycleClasses(getClass("pk.MyClass"), Validate)

        then:
        lifecycleClasses.collect { it.getSimpleName() } == ['NonStatic']
    }

    def "lifecycle classes from parent classes are included"() {
        given:
        createClass '''
    package pk
    import com.blackbuild.groovy.configdsl.transform.Validate
    class Parent {
        @Validate class ParentInner {}
    }
    
    class Child extends Parent {
        @Validate class ChildInner {}
    }
'''

        when:
        def lifecycleClasses = LifecycleHelper.getLifecycleClasses(getClass("pk.Child"), Validate)

        then:
        lifecycleClasses.collect { it.getSimpleName() } == ['ChildInner', 'ParentInner']
    }

    def "if a child's lifecycle class extends it's parent's, only the child's is used"() {
        given:
        createClass '''
    package pk
    import com.blackbuild.groovy.configdsl.transform.Validate
    class Parent {
        @Validate class ParentInner {}
    }
    
    class Child extends Parent {
        @Validate class ChildInner extends Parent.ParentInner {}
    }
'''

        when:
        def lifecycleClasses = LifecycleHelper.getLifecycleClasses(getClass("pk.Child"), Validate)

        then:
        lifecycleClasses.collect { it.getSimpleName() } == ['ChildInner']
    }

}
