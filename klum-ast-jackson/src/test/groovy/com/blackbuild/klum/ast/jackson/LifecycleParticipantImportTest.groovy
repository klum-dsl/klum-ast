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
package com.blackbuild.klum.ast.jackson

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Issue

@Issue('867')
class LifecycleParticipantImportTest extends AbstractDSLSpec {
    def 'existing Jackson routes execute participants in their root recipient lifecycle in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = $phase, handler = Configure)
            @interface Configured {}
            class Configure implements LifecycleMutationHandler<Configured> {
                boolean used
                void mutate(LifecycleMutationContext<Configured> c) {
                    assert !used
                    used = true
                    def target = Service.Create.narrowBuilder(c.targetBuilder)
                    target.value(target.value + ':participant')
                }
            }
            @DSL class Service { String value }
            @DSL class Application { @Configured Service service }
        """
        def applicationType = Application
        def serviceType = Service
        def mapper = new ObjectMapper().findAndRegisterModules()
        def importer = KlumJacksonImporter.using(mapper)
        def input = KlumJacksonInput.map([service: [value: 'imported']])

        when:
        def root = importer.readRoot(applicationType, input)
        def ordinary = mapper.readValue('{"service":{"value":"imported"}}', applicationType)
        def template = importer.readTemplate(applicationType, input)
        def recipient = applicationType.Create.With { copyFrom template }
        def fromBuilder = applicationType.Create.With {
            service(importer.readBuilder(serviceType.Create.AsBuilder(), KlumJacksonInput.map([value: 'imported'])))
        }
        def applied = applicationType.Create.With { importer.applyToBuilder(delegate, input) }

        then:
        [root, ordinary, recipient, fromBuilder, applied].every { it.service.value == 'imported:participant' }
        template.service.value == 'imported'
        !recipient.service.is(template.service)

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }
}
