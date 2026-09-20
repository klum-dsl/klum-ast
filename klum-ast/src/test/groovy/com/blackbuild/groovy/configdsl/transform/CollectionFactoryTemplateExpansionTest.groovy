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

import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import spock.lang.Issue

@Issue("135")
class CollectionFactoryTemplateExpansionTest extends AbstractDSLSpec {

    def setup() {
        createClass '''
            package pk

            @DSL
            class Team {
                List<Member> members
                @Field(FieldType.OPTIONAL_LINK)
                List<Member> optionalMembers
                @Field(members = 'mappedMember', keyMapping = { it.role })
                Map<String, Member> membersByName
                @Field(FieldType.LINK)
                List<Member> linkedMembers
            }

            @DSL
            class Member {
                String name
                String role
                String identifier
                String configuredBy
                String configurationObservedByPostApply
                boolean postCreateCalled
                boolean postApplyCalled

                @PostCreate
                void recordPostCreate() {
                    postCreateCalled = true
                }

                @PostApply
                void recordPostApply() {
                    postApplyCalled = true
                    configurationObservedByPostApply = configuredBy
                }
            }
        '''
    }

    def "expands each Template immediately as an independent owned child in call order"() {
        given:
        def Member = getClass('pk.Member')
        def admin = Member.Create.Template.With(name: 'admin', role: 'administrator') {
            applyLater {
                identifier name.toUpperCase()
            }
        }
        def reader = Member.Create.Template.With(name: 'reader', role: 'reader')
        def ambient = Member.Create.Template.With(role: 'ambient')
        int configurationCalls = 0

        when:
        def team
        Member.Template.With(ambient) {
            team = clazz.Create.With {
                members {
                    withTemplates([admin, reader]) {
                        configuredBy "batch-${++configurationCalls}"
                    }
                    member {
                        name 'plain'
                    }
                    withTemplates([admin]) { }
                }
                optionalMembers {
                    withTemplates([reader]) { }
                }
            }
        }

        then:
        configurationCalls == 2
        team.members*.name == ['admin', 'reader', 'plain', 'admin']
        team.members*.role == ['administrator', 'reader', 'ambient', 'administrator']
        team.members*.identifier == ['ADMIN', null, null, 'ADMIN']
        team.members*.configuredBy == ['batch-1', 'batch-2', null, null]
        team.members*.configurationObservedByPostApply == ['batch-1', 'batch-2', null, null]
        team.members.findAll { it.name == 'admin' }.with {
            size() == 2
            !get(0).is(get(1))
            every { it.postCreateCalled && it.postApplyCalled }
        }
        !team.members.any { TemplateManager.isTemplate(it) }
        team.optionalMembers*.name == ['reader']
        !TemplateManager.isTemplate(team.optionalMembers.first())
        TemplateManager.isTemplate(admin)
        admin.identifier == null
        !admin.postCreateCalled
        !admin.postApplyCalled
    }

    def "uses existing map key derivation and duplicate replacement behavior"() {
        given:
        def Member = getClass('pk.Member')
        def firstAdmin = Member.Create.Template.With(name: 'first admin', role: 'admin')
        def reader = Member.Create.Template.With(name: 'reader', role: 'reader')
        def replacementAdmin = Member.Create.Template.With(name: 'replacement admin', role: 'admin')

        when:
        def team = clazz.Create.With {
            membersByName {
                withTemplates([firstAdmin]) { }
                withTemplates([reader, replacementAdmin]) { }
            }
        }

        then:
        team.membersByName.keySet().toList() == ['admin', 'reader']
        team.membersByName.admin.name == 'replacement admin'
        team.membersByName.reader.role == 'reader'
    }

    def "rejects a non-Template batch before attaching any child"() {
        given:
        def Member = getClass('pk.Member')
        def template = Member.Create.Template.With(name: 'template')
        def completedModel = Member.Create.With(name: 'ordinary')
        int configurationCalls = 0
        KlumModelException failure

        when:
        def team = clazz.Create.With {
            members {
                try {
                    withTemplates([template, completedModel]) {
                        configurationCalls++
                    }
                } catch (KlumModelException caught) {
                    failure = caught
                }
                member {
                    name 'retained'
                }
            }
        }

        then:
        failure.message.contains('accepts only marked Templates')
        configurationCalls == 0
        team.members*.name == ['retained']
    }

    def "does not expose Template expansion on LINK collection factories"() {
        given:
        Class<?> linkFactory = getClass('pk.Team_DSL$Builder$CollectionFactory_linkedMembers')

        expect:
        !linkFactory.methods.any { it.name == 'withTemplates' }
    }
}
