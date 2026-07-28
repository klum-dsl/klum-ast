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
package com.blackbuild.klum.ast.runtime.generated

import com.blackbuild.klum.ast.runtime.internal.AbstractRuntimeTest
import spock.lang.Issue

import java.lang.annotation.Annotation

@Issue('391')
class GeneratedClustersTest extends AbstractRuntimeTest {

    def "generated Cluster bridge preserves each emitted query shape"() {
        given:
        createClass '''
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy

            @Retention(RetentionPolicy.RUNTIME)
            @interface Important {}

            class Person {
                @Important String firstName
                String lastName
                @Important String optional
                @Important List<String> nicknames
                List<String> aliases
            }
        '''
        instance = newInstanceOf('Person', [
                firstName: 'John',
                lastName : 'Doe',
                nicknames: ['John', 'Johnny'],
                aliases   : ['JD']
        ])
        Class<? extends Annotation> important = getClass('Important') as Class<? extends Annotation>

        expect:
        GeneratedClusters.$klum$getPropertiesOfType(instance, String) == [
                firstName: 'John', lastName: 'Doe', optional: null
        ]
        GeneratedClusters.$klum$getPropertiesOfType(instance, String, important) == [
                firstName: 'John', optional: null
        ]
        GeneratedClusters.$klum$getNonEmptyPropertiesOfType(instance, String) == [
                firstName: 'John', lastName: 'Doe'
        ]
        GeneratedClusters.$klum$getNonEmptyPropertiesOfType(instance, String, important) == [
                firstName: 'John'
        ]
        GeneratedClusters.$klum$getCollectionsOfType(instance, String) == [
                nicknames: ['John', 'Johnny'], aliases: ['JD']
        ]
        GeneratedClusters.$klum$getCollectionsOfType(instance, String, important) == [
                nicknames: ['John', 'Johnny']
        ]
    }
}
