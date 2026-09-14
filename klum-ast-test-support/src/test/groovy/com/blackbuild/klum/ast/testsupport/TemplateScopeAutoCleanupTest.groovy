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
package com.blackbuild.klum.ast.testsupport

import spock.lang.AutoCleanup
import spock.lang.FailsWith
import spock.lang.Issue
import spock.lang.Specification

@Issue("658")
class TemplateScopeAutoCleanupTest extends Specification {

    @AutoCleanup
    TemplateScopeRestorationObserver restorationObserver = new TemplateScopeRestorationObserver()

    @AutoCleanup
    TemplateScope templates = new TemplateScope()

    def cleanup() {
        assert delivery().region == 'leaked'
    }

    @FailsWith(IllegalStateException)
    def "restores normal creation after AutoCleanup closes a scope following a failing feature"() {
        given:
        templates.with(Delivery.Create.Template.With(region: 'leaked'))

        expect:
        delivery().region == 'leaked'

        when:
        throw new IllegalStateException('expected')

        then:
        true
    }

    private static Delivery delivery() {
        Delivery.Create.With {
            options {}
        }
    }

    private static final class TemplateScopeRestorationObserver implements AutoCloseable {

        @Override
        void close() {
            assert Delivery.Create.With { options {} }.region == null
        }
    }
}
