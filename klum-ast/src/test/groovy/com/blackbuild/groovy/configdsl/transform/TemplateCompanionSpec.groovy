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

import com.blackbuild.klum.ast.util.DslHelper
import com.blackbuild.klum.ast.util.KlumModelProxy
import com.blackbuild.klum.ast.util.KlumObjectCompanion
import com.blackbuild.klum.ast.util.KlumTemplateProxy
import com.blackbuild.klum.ast.util.TemplateManager

import java.lang.reflect.Modifier

class TemplateCompanionSpec extends AbstractDSLSpec {

    def "generated models use the sealed common companion path contract"() {
        given:
        createClass '''
            package pk

            @DSL
            class CompanionModel {
                String value
            }
        '''

        when:
        def model = clazz.Create.With { value "model" }
        def template = clazz.Template.Create { value "template" }
        def companionField = clazz.getDeclaredField('$proxy')
        companionField.accessible = true
        KlumObjectCompanion modelCompanion = companionField.get(model)
        KlumObjectCompanion templateCompanion = companionField.get(template)

        then:
        companionField.type == KlumObjectCompanion
        Modifier.isPrivate(companionField.modifiers)
        Modifier.isFinal(companionField.modifiers)
        companionField.synthetic

        and:
        KlumObjectCompanion.sealed
        KlumObjectCompanion.permittedSubclasses as Set == [KlumModelProxy, KlumTemplateProxy] as Set
        KlumObjectCompanion.declaredMethods*.name as Set == [
                'getObject',
                'getBreadcrumbPath',
                'getModelPath'
        ] as Set
        Modifier.isFinal(KlumModelProxy.modifiers)
        Modifier.isFinal(KlumTemplateProxy.modifiers)

        and:
        modelCompanion.class == KlumModelProxy
        modelCompanion.object.is(model)
        modelCompanion.breadcrumbPath == DslHelper.getBreadcrumbPath(model)
        modelCompanion.modelPath == DslHelper.getModelPath(model)

        and:
        templateCompanion.class == KlumTemplateProxy
        templateCompanion.object.is(template)
        templateCompanion.breadcrumbPath == DslHelper.getBreadcrumbPath(template)
        templateCompanion.modelPath == DslHelper.getModelPath(template)
    }

    def "Template identity means only a persistent Template companion"() {
        given:
        createClass '''
            package pk

            @DSL
            class IdentityModel {
                String value
            }
        '''

        when:
        def templateBuilder
        def template = clazz.Template.Create {
            templateBuilder = delegate
            value "template"
        }
        def model = clazz.Template.With(template) {
            clazz.Create.With { value "model" }
        }

        then:
        TemplateManager.isTemplate(template)
        !TemplateManager.isTemplate(model)
        !TemplateManager.isTemplate(templateBuilder)
        !TemplateManager.isTemplate(null)
        !TemplateManager.isTemplate([value: "data"])
    }
}
