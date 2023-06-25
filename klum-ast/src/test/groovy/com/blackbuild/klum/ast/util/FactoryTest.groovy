/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import spock.lang.Issue

@Issue("76")
class FactoryTest extends AbstractDSLSpec {

    def "Create field is created and initialized"() {
        when:
        createClass '''import com.blackbuild.groovy.configdsl.transform.DSL
@DSL abstract class AnAbstractClass {}

@DSL class NonAbstractSubclassOfAnAbstractClass extends AnAbstractClass {}

@DSL class AClass {}

@DSL class AKeyedClass {
    @Key String id
}

@DSL class ASubclassOfAKeyedClass extends AKeyedClass {}

@DSL(defaultImpl = DefaultImpl) 
abstract class AbstractWithDefaultImpl {}

@DSL class DefaultImpl extends AbstractWithDefaultImpl {}
'''
        then:
        !getClass("AnAbstractClass").fields.any {it.name == "Create" }

        when:
        def factoryField = getClass(className).getField("Create")

        then:
        factoryType.isAssignableFrom(factoryField.type)

        when:
        KlumFactory factory = getClass(className).Create

        then:
        factory.type.name == factoryTargetClassName

        where:
        className                              || factoryType           | factoryTargetClassName
        "NonAbstractSubclassOfAnAbstractClass" || KlumFactory.KlumUnkeyedFactory | "NonAbstractSubclassOfAnAbstractClass"
        "AClass"                               || KlumFactory.KlumUnkeyedFactory | "AClass"
        "AKeyedClass"                          || KlumFactory.KlumKeyedFactory | "AKeyedClass"
        "ASubclassOfAKeyedClass"               || KlumFactory.KlumKeyedFactory | "ASubclassOfAKeyedClass"
        "AbstractWithDefaultImpl"              || KlumFactory.KlumUnkeyedFactory | "DefaultImpl"
        "DefaultImpl"                          || KlumFactory.KlumUnkeyedFactory | "DefaultImpl"
    }

    def "basic test"() {
        given:
        createClass '''
import com.blackbuild.groovy.configdsl.transform.DSL

@DSL class AClass {}
'''
        when:
        clazz.Create.One()

        then:
        noExceptionThrown()
    }



}