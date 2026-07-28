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
package com.blackbuild.klum.ast

import com.blackbuild.klum.ast.runtime.KlumBuilder
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class ModelPhasesDocumentaryTest extends AbstractDSLSpec {

    @Issue("64")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#lifecycle-annotations")
    def "runs a deployment lifecycle on Builders before completing its model"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.AutoCreate
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.runtime.KlumBuilder

            @DSL
            class Deployment {
                static List<String> lifecycle = []

                String environment
                Component component

                @PostCreate
                void beginConfiguration() {
                    lifecycle << "post-create:${this instanceof KlumBuilder}".toString()
                }

                @PostApply
                void finishConfiguration() {
                    lifecycle << "post-apply:${this instanceof KlumBuilder}".toString()
                }

                @AutoCreate
                void chooseEnvironment() {
                    lifecycle << "auto-create:${this instanceof KlumBuilder}".toString()
                    environment ?= 'production'
                }

                @PostTree
                void finishTree() {
                    lifecycle << "post-tree:${this instanceof KlumBuilder}".toString()
                }

                @Validate
                void validateCompletedModel() {
                    lifecycle << "validate:${this instanceof KlumBuilder}".toString()
                    assert component.environment == environment
                }
            }

            @DSL
            class Component {
                @Owner Deployment deployment
                String environment

                @AutoLink
                void inheritEnvironment() {
                    Deployment.lifecycle << "auto-link:${this instanceof KlumBuilder}".toString()
                    environment = deployment.environment
                }

                @Default
                void useInheritedEnvironment() {
                    Deployment.lifecycle << "default:${this instanceof KlumBuilder}".toString()
                    environment ?= deployment.environment
                }
            }
        '''

        when:
        def deployment = clazz.Create.With {
            component {}
        }

        then:
        deployment.environment == 'production'
        deployment.component.deployment.is(deployment)
        deployment.component.environment == 'production'
        clazz.lifecycle == [
                'post-create:true',
                'post-apply:true',
                'auto-create:true',
                'auto-link:true',
                'default:true',
                'post-tree:true',
                'validate:false'
        ]
    }

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#instantiate-40")
    def "materializes a release plan into an independent completed snapshot"() {
        given:
        createClass '''
            package pk

            @DSL
            class ReleasePlan {
                List<String> regions
            }
        '''

        when:
        KlumBuilder builder
        def releasePlan = clazz.Create.With {
            builder = delegate
            regions.addAll(['eu-central', 'us-east'])
        }

        then:
        releasePlan.regions == ['eu-central', 'us-east']

        when: 'the construction-time collection changes after materialization'
        builder.regions.add('builder-only')

        then: 'the completed model retains its independent snapshot'
        releasePlan.regions == ['eu-central', 'us-east']

        when: 'a caller tries to mutate the completed model'
        releasePlan.regions.add('not-allowed')

        then:
        thrown(UnsupportedOperationException)
    }

    @Issue("376")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#applylater-1")
    def "applies a deferred deployment setting before automatic lifecycle work"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.layer3.AutoCreate

            @DSL
            class Deployment {
                static List<String> lifecycle = []

                String environment

                @PostCreate
                void beginConfiguration() {
                    lifecycle << "post-create:$environment".toString()
                }

                @AutoCreate
                void provisionEnvironment() {
                    lifecycle << "auto-create:$environment".toString()
                }
            }
        '''

        when:
        def deployment = clazz.Create.With {
            applyLater {
                environment 'production'
            }
        }

        then:
        deployment.environment == 'production'
        clazz.lifecycle == ['post-create:null', 'auto-create:production']
    }
}
