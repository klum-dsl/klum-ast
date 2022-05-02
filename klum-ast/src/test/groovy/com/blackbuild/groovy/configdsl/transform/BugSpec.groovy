/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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

import spock.lang.Issue

/**
 * Tests for various encountered bugs.
 */
class BugSpec extends AbstractDSLSpec {

    def "method with default value is already defined"() {
        when:
        createClass '''
@DSL class ImageRegistrySpec {
}

@DSL
class ImagePushSpecification {
    @Key String imageName
    ImageRegistrySpec srcImageRegistry
}
'''
        then:
        noExceptionThrown()

    }

    def 'closure coercion must be correctly done for List adders'() {
        given:
        createClass '''
        @DSL class ValueProvider {

            String name
            List<DescriptionProvider> descriptionProviders
            
            List<CharSequence> getDescriptions() {
                descriptionProviders.collect {
                    it.getDescription()
                }
            }
        }

        interface DescriptionProvider {
            CharSequence getDescription()
        }
        '''

        when:
        instance = clazz.create {
            name "Klaus"
            descriptionProviders([{ "$name" }, {"2$name"}])
        }

        then:
        noExceptionThrown()

        expect:
        instance.getDescriptions() == ["Klaus", "2Klaus"]
    }

    def 'closure coercion must be correctly done for map adders'() {
        given:
        createClass '''
        @DSL class ValueProvider {

            String name
            Map<String, DescriptionProvider> descriptionProviders
            
            List<CharSequence> getDescriptions() {
                descriptionProviders.collect {
                    it.value.getDescription()
                }
            }
        }

        interface DescriptionProvider {
            CharSequence getDescription()
        }
        '''

        when:
        instance = clazz.create {
            name "Klaus"
            descriptionProviders(a: { "$name" }, b: {"2$name"})
        }

        then:
        noExceptionThrown()

        expect:
        instance.getDescriptions() == ["Klaus", "2Klaus"]
    }

    def "BUG: naming clash if instance methods is the same as the key field (single field)"() {
        given:
        createClass """
@DSL
class Outer {
    Inner job
}

@DSL class Inner  {
    @Key String job
}
"""

        when:
        create("Outer") {
            job("Nightly")
        }

        then:
        noExceptionThrown()
    }

    def "BUG: naming clash if instance methods is the same as the key field (map)"() {
        given:
        createClass '''
@DSL
class Outer {
    Map<String, Inner> jobs
}

@DSL class Inner  {
    @Key String job
}
'''

        when:
        create("Outer") {
            job("Nightly")
        }

        then:
        noExceptionThrown()
    }
    def "BUG: naming clash if instance methods is the same as the key field (list)"() {
        given:
        createClass '''
@DSL
class Outer {
    List<Inner> jobs
}

@DSL class Inner  {
    @Key String job
}
'''

        when:
        create("Outer") {
            job("Nightly")
        }

        then:
        noExceptionThrown()
    }

    def "new bug"() {
        when:
        createClass '''
@DSL
class Outer {
    @Field(key = {Inner.DEFAULT})
    Inner defaultInner
    List<Inner> inners
}

@DSL class Inner  {
    static final String DEFAULT = 'bla'
    @Key String name
}
'''

        then:
        noExceptionThrown()
    }

    @Issue("243")
    def "BUG: Generics in Generics leads to compile error"() {
        given:
        createClass '''
@DSL
class Outer {
    Map<String, List<String>> values
}
'''
        when:
        create("Outer")

        then:
        noExceptionThrown()

    }

}