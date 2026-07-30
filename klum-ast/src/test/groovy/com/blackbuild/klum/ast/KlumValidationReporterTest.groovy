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

import com.blackbuild.klum.ast.runtime.KlumSchemaSupport
import com.blackbuild.klum.ast.runtime.KlumValidationException
import com.blackbuild.klum.ast.runtime.KlumValidationReporter
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag
import uk.org.webcompere.systemstubs.properties.SystemProperties

import java.lang.reflect.Modifier
import javax.tools.ToolProvider

@Issue("626")
@Tag("documentary")
@See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#custom-validation-reporting")
class KlumValidationReporterTest extends AbstractDSLSpec {

    @AutoCleanup("teardown") SystemProperties systemProperties = new SystemProperties()

    @Override
    def setup() {
        systemProperties.setup()
    }

    def "reports a current validation-method issue through the static Groovy property"() {
        given:
        createClass '''
            package pk

            import static com.blackbuild.klum.ast.runtime.KlumSchemaSupport.klumValidation

            @DSL
            class Release {
                String name

                @Validate
                void nameMustBePresent() {
                    if (!name)
                        klumValidation.error('name must be present')
                }
            }
        '''

        when:
        clazz.Create.One()

        then:
        def exception = thrown(KlumValidationException)
        exception.message.endsWith('- ERROR #nameMustBePresent(): name must be present')
    }

    def "retains early lifecycle diagnostics and applies reporter suppression and fail level"() {
        given:
        createClass '''
            package pk

            import static com.blackbuild.klum.ast.runtime.KlumSchemaSupport.klumValidation

            @DSL
            class Release {
                @PostCreate
                void reportEarlyDiagnostics() {
                    klumValidation.suppressOn('releaseNote', Validate.Level.WARNING)
                    klumValidation.suppressAll(Validate.Level.INFO)
                    klumValidation.issueAt('releaseNote', 'suppressed warning', Validate.Level.WARNING)
                    klumValidation.issue('suppressed information', Validate.Level.INFO)
                    klumValidation.issue('visible warning', Validate.Level.WARNING)
                }
            }
        '''

        when:
        def release = clazz.Create.One()
        def stored = KlumObjectSupport.of(release).validation.result

        then:
        stored.issues*.message == ['visible warning']
        KlumSchemaSupport.klumValidation.failLevel == Validate.Level.ERROR

        when:
        systemProperties.set('klum.validation.failOnLevel', 'WARNING')

        then:
        KlumSchemaSupport.klumValidation.failLevel == Validate.Level.WARNING
    }

    def "reports an explicit child target with that target's construction path"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.runtime.KlumSchemaSupport

            @DSL
            class Root {
                Child child

                @PostTree
                void diagnoseChild() {
                    KlumSchemaSupport.klumValidationForObject(child).error('child needs a release name')
                }
            }

            @DSL
            class Child {
            }
        '''

        when:
        clazz.Create.With {
            child {}
        }

        then:
        def exception = thrown(KlumValidationException)
        def childResult = exception.validationResults.find { it.issues*.message.contains('child needs a release name') }
        childResult.breadcrumbPath.contains('Root.With/child')
        childResult.message.endsWith('- ERROR #<none>: child needs a release name')
    }

    def "reports through a Java helper reached from a lifecycle callback"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.ValidationReporterJavaHelper

            @DSL
            class Release {
                @PostTree
                void reportFromJavaHelper() {
                    ValidationReporterJavaHelper.reportMissingReleaseName(this)
                }
            }
        '''

        when:
        clazz.Create.One()

        then:
        def exception = thrown(KlumValidationException)
        exception.message.endsWith('- ERROR #releaseName: release name is required')
    }

    def "exposes only the reporter facade and rejects preliminary Validator types"() {
        expect:
        KlumSchemaSupport.declaredMethods.findAll { Modifier.isPublic(it.modifiers) }*.name as Set ==
                ['getKlumValidation', 'klumValidationForObject'] as Set
        KlumValidationReporter.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
        KlumValidationReporter.declaredMethods.findAll { Modifier.isPublic(it.modifiers) }*.name as Set ==
                ['error', 'errorAt', 'issue', 'issueAt', 'suppressOn', 'suppressAll', 'getFailLevel'] as Set

        and:
        compileJavaConsumerFails('''
            import com.blackbuild.klum.ast.runtime.internal.validation.Validator;

            public final class LegacyValidationConsumer {
                Validator validator;
            }
        ''', 'cannot find symbol')
        compileJavaConsumerFails('''
            import com.blackbuild.klum.ast.runtime.internal.validation.ValidatorBase;

            public final class LegacyValidationConsumer {
                ValidatorBase validator;
            }
        ''', 'cannot find symbol')
        compileJavaConsumer('''
            import com.blackbuild.klum.ast.Validate;
            import com.blackbuild.klum.ast.runtime.KlumSchemaSupport;
            import com.blackbuild.klum.ast.runtime.KlumValidationReporter;

            public final class ValidationConsumer {
                static void report(Object target) {
                    KlumValidationReporter reporter = KlumSchemaSupport.klumValidationForObject(target);
                    reporter.error("invalid target");
                    reporter.errorAt("name", "missing name");
                    reporter.issue("advisory", Validate.Level.WARNING);
                    reporter.issueAt("name", "advisory", Validate.Level.WARNING);
                    reporter.suppressOn("name");
                    reporter.suppressAll(Validate.Level.INFO);
                    reporter.getFailLevel();
                }
            }
        ''')
    }

    private void compileJavaConsumer(String source) {
        assertJavaCompilation(source, null, 'ValidationConsumer.java')
    }

    private void compileJavaConsumerFails(String source, String expectedDiagnostic) {
        assertJavaCompilation(source, expectedDiagnostic, 'LegacyValidationConsumer.java')
    }

    private void assertJavaCompilation(String source, String expectedDiagnostic, String filename) {
        File sourceFile = new File(tempFolder.root, filename)
        sourceFile.text = source.stripIndent()
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)
        def errors = new ByteArrayOutputStream()
        int result = ToolProvider.systemJavaCompiler.run(
                null,
                null,
                errors,
                '-classpath', classpath,
                '-d', compilerConfiguration.targetDirectory.absolutePath,
                sourceFile.absolutePath
        )
        if (expectedDiagnostic == null) {
            assert result == 0: errors.toString()
        } else {
            assert result != 0
            assert errors.toString().contains(expectedDiagnostic)
        }
    }
}
