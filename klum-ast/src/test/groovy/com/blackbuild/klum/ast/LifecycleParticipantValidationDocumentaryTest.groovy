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

import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag
import uk.org.webcompere.systemstubs.properties.SystemProperties

@Issue('867')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#participant-validation')
class LifecycleParticipantValidationDocumentaryTest extends AbstractDSLSpec {
    @AutoCleanup('teardown') SystemProperties systemProperties = new SystemProperties()

    def setup() { systemProperties.setup() }

    def 'reports missing Facts on the explicit Domain and transfers findings to the completed Model'() {
        given:
        schema()

        when:
        def application = Application.Create.With { domain {} }
        def result = KlumObjectSupport.of(application.domain).validation.result

        then:
        result.issues*.member == ['facts', '<none>']
        result.issues*.level == [Validate.Level.WARNING, Validate.Level.INFO]
        result.issues*.message == ['facts are required', 'domain inspected']
        result.issues.every { it.breadcrumbPath.contains('Application.With/domain') }
        !KlumObjectSupport.of(application).validation.result.issues

        and: 'suppression applies before reporting, including suppression state transferred to the Model'
        def suppressed = Application.Create.With { domain { suppressFacts true } }
        KlumObjectSupport.of(suppressed.domain).validation.result.issues*.message == ['domain inspected']

        and: 'configured Facts do not produce a missing-Facts warning'
        def configured = Application.Create.With { domain { facts {} } }
        KlumObjectSupport.of(configured.domain).validation.result.issues*.message == ['domain inspected']
    }

    def 'Verify applies the existing #threshold threshold to participant #severity findings'() {
        given:
        schema()
        systemProperties.set('klum.validation.failOnLevel', threshold)

        when:
        Application.Create.With { domain { level severity } }

        then:
        KlumValidationException failure = thrown()
        def issue = failure.validationResults*.issues.flatten().find { it.message == 'facts are required' }
        issue.member == 'facts'
        issue.level.name() == severity
        issue.breadcrumbPath.contains('Application.With/domain')
        !failure.message.contains('Participant CheckFacts handler')

        where:
        threshold | severity
        'WARNING' | 'WARNING'
        'ERROR'   | 'ERROR'
    }

    private void schema() {
        createSecondaryClass '''
            package participant.validation
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Validate
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.runtime.KlumSchemaSupport
            import com.blackbuild.klum.ast.runtime.LifecycleMutationContext
            import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler
            import com.blackbuild.klum.ast.runtime.LifecycleMutator
            import groovy.transform.CompileStatic
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = AutoLink, handler = CheckFactsHandler)
            @interface CheckFacts {}

            @CompileStatic
            class CheckFactsHandler implements LifecycleMutationHandler<CheckFacts> {
                void mutate(LifecycleMutationContext<CheckFacts> context) {
                    def domain = Domain.Create.narrowBuilder(context.targetBuilder)
                    def report = KlumSchemaSupport.klumValidationForObject(domain)
                    report.issue('domain inspected', Validate.Level.INFO)
                    report.suppressAll(Validate.Level.INFO)
                    report.suppressOn('obsolete', Validate.Level.WARNING)
                    report.issue('suppressed information', Validate.Level.INFO)
                    report.issueAt('obsolete', 'suppressed warning', Validate.Level.WARNING)
                    if (domain.suppressFacts) report.suppressOn('facts', Validate.Level.WARNING)
                    if (!domain.facts) report.issueAt('facts', 'facts are required', Validate.Level.valueOf(domain.level))
                }
            }
            @DSL class Facts {}
            @DSL class Domain {
                Facts facts
                boolean suppressFacts
                String level = 'WARNING'
            }
            @DSL class Application { @CheckFacts Domain domain }
        '''
    }
}
