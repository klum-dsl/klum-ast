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

import com.blackbuild.klum.ast.AbstractDSLSpec
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('342')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Tips-and-Tricks.md#name-a-builder-helper-for-a-seeded-relationship')
class BuilderRelationshipHelpersDocumentaryTest extends AbstractDSLSpec {

    def "names a seeded relationship helper without replacing a domain converter"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DelegatesToBuilder
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class House {
                String name
                Room baseline
                Bedroom bedroom

                @Builder.Method
                void bedroomFrom(@Builder.Input Room seed,
                                 @DelegatesToBuilder(Bedroom) Closure body) {
                    bedroom(copyFrom: seed, body)
                }
            }

            @DSL class Room {
                String name
                boolean heated
                @Owner House house
                String ownerName
                int postTreeCalls

                @PostTree
                void recordOwner() {
                    ownerName = house.name
                    postTreeCalls++
                }
            }

            @DSL class Bedroom extends Room {
                int beds

                static Bedroom fromRoom(@Builder.Input Room source) {
                    Bedroom.Create.With(name: "converted:$source.name", heated: false)
                }
            }
        '''

        when:
        def house = clazz.Create.With {
            name 'Home'
            def seed = baseline {
                name 'Heated room'
                heated true
            }
            bedroomFrom(seed) {
                name 'Main bedroom'
                beds 2
            }
        }

        def convertedHouse = clazz.Create.With {
            name 'Converted home'
            def seed = baseline {
                name 'Heated room'
                heated true
            }
            bedroom seed
        }

        then: 'the helper copies ancestor state into a fresh owned subtype, then configures it'
        house.bedroom.class == getClass('Bedroom')
        house.bedroom.heated
        house.bedroom.name == 'Main bedroom'
        house.bedroom.beds == 2
        !house.bedroom.is(house.baseline)
        house.bedroom.house.is(house)
        house.bedroom.ownerName == 'Home'
        house.bedroom.postTreeCalls == 1
        house.baseline.name == 'Heated room'
        house.baseline.heated
        house.baseline.house.is(house)
        house.baseline.postTreeCalls == 1

        and: 'the existing domain converter keeps its distinct meaning'
        convertedHouse.bedroom.name == 'converted:Heated room'
        !convertedHouse.bedroom.heated
        convertedHouse.bedroom.house.is(convertedHouse)
        convertedHouse.bedroom.postTreeCalls == 1

        and: 'the explicit method exists only on the public Builder with a projected input'
        hasNoMethod(clazz, 'bedroomFrom')
        def method = getClass('House_DSL$Builder').getMethod(
                'bedroomFrom', getClass('Room_DSL$Builder'), Closure)
        method.returnType == Void.TYPE
    }
}
