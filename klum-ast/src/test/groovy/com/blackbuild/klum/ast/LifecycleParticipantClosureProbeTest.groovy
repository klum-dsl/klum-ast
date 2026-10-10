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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@Issue('867')
class LifecycleParticipantClosureProbeTest extends AbstractDSLSpec {

    def 'generic annotation Closure bound does not check an inline result even under CompileStatic'() {
        when:
        createSecondaryClass '''
            import groovy.transform.CompileStatic
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Selection { Class<? extends Closure<String>> value() }
            @CompileStatic @DSL class Application {
                @Selection({ 42 }) String selected
            }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)

        when:
        def annotation = Application.getDeclaredField('selected').getAnnotation(Selection)
        def expression = annotation.value().getConstructor(Object, Object).newInstance(null, null)
        def result = expression.call()

        then:
        result == 42

        when:
        String.cast(result)

        then:
        thrown(ClassCastException)
    }

    def 'ordinary annotation map configures a relationship without a Klum Closure API'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import org.codehaus.groovy.runtime.InvokerHelper
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = AutoLink, handler = BindFacts)
            @interface FactBinding { Class<? extends Closure<Map<String, String>>> value() }
            class BindFacts implements LifecycleMutationHandler<FactBinding> {
                void mutate(LifecycleMutationContext<FactBinding> c) {
                    Closure<Map<String, String>> expression = InvokerHelper.invokeConstructorOf(
                        c.annotation.value(), [null, null] as Object[]) as Closure<Map<String, String>>
                    Map<String, String> bindings = expression.call()
                    def app = Application.Create.narrowBuilder(c.containingBuilder)
                    bindings.each { source, target ->
                        InvokerHelper.invokeMethod(c.targetBuilder, target, app.catalog[source])
                    }
                }
            }
            @DSL class Domain { String facts }
            @DSL class Application {
                Map<String, String> catalog
                @FactBinding({ [messaging: 'facts'] }) Domain domain
            }
        '''

        when:
        def first = Application.Create.With { catalog(messaging: 'first'); domain {} }
        def second = Application.Create.With { catalog(messaging: 'second'); domain {} }

        then:
        first.domain.facts == 'first'
        second.domain.facts == 'second'
    }

    def 'explicit Schema argument is retargeted to a Builder in static annotation expressions'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import groovy.transform.CompileStatic
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = AutoLink, handler = Select)
            @interface Selection { Class<? extends Closure<String>> value() }
            class Select implements LifecycleMutationHandler<Selection> {
                void mutate(LifecycleMutationContext<Selection> c) {
                    def expression = c.annotation.value().getConstructor(Object, Object).newInstance(null, null)
                    expression.delegate = c.containingBuilder
                    expression.resolveStrategy = Closure.DELEGATE_ONLY
                    Domain.Create.narrowBuilder(c.targetBuilder).facts(String.cast(expression.call(c.containingBuilder)))
                }
            }
            @DSL class Domain { String facts }
            @CompileStatic @DSL class Application {
                String source
                @Selection({ Application app -> app.source.toUpperCase() }) Domain domain
            }
        '''

        when:
        def result = Application.Create.With { source 'provider'; domain {} }

        then:
        result.domain.facts == 'PROVIDER'
    }

    def 'runtime selected delegate supports explicit dynamic access through ordinary Groovy'() {
        given:
        createSecondaryClass '''
            import java.lang.annotation.*
            import org.codehaus.groovy.runtime.InvokerHelper
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Selection { Class<? extends Closure<String>> value() }
            @DSL class Application {
                @Selection({ InvokerHelper.getProperty(delegate, 'externalValue') }) String selected
            }
        '''

        when:
        def annotation = Application.getDeclaredField('selected').getAnnotation(Selection)
        def expression = annotation.value().getConstructor(Object, Object).newInstance(null, null)
        expression.delegate = [externalValue: 'delegate-value']
        expression.resolveStrategy = Closure.DELEGATE_ONLY
        def result = expression.call(expression.delegate)

        then:
        result == 'delegate-value'
    }

    def 'runtime selected delegate is not inferred in annotation Closures (#mode)'() {
        when:
        createSecondaryClass """
            import groovy.transform.CompileStatic
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Selection { Class<? extends Closure<String>> value() }
            $mode @DSL class Application {
                @Selection({ externalValue }) String selected
            }
        """

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('The variable [externalValue] is undeclared')

        where:
        mode << ['', '@CompileStatic']
    }

    def 'Validate derives its implicit argument from the field under CompileStatic'() {
        given:
        createSecondaryClass '''
            import groovy.transform.CompileStatic
            @CompileStatic @DSL class Application {
                @Validate({ it.length() > 2 }) String name
            }
        '''

        when:
        def result = Application.Create.With { name 'valid' }

        then:
        result.name == 'valid'
    }

    def 'Validate rejects unknown operations on its inferred field argument'() {
        when:
        createSecondaryClass '''
            import groovy.transform.CompileStatic
            @CompileStatic @DSL class Application {
                @Validate({ it.missingOperation() > 2 }) String name
            }
        '''

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('String#missingOperation')
    }
}
