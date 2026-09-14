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
package com.blackbuild.klum.ast.runtime.internal;

import com.blackbuild.klum.ast.testsupport.TemplateScope;
import org.junit.jupiter.api.Test;
import spock.lang.Issue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Issue("658")
class TemplateScopeRuntimeTest {

    @Test
    void addsValuesThroughBothFormsAndRemovesAnEmptyPriorScope() {
        ScopeTemplateValue first = new ScopeTemplateValue();
        ScopeTemplateValue second = new ScopeTemplateValue();

        try (TemplateScope scope = new TemplateScope().with(first).with(List.of(second))) {
            assertSame(second, currentTemplate());
        }

        assertNull(currentTemplate());
    }

    @Test
    void restoresOuterValuesAfterNestedShadowing() {
        ScopeTemplateValue outerValue = new ScopeTemplateValue();
        ScopeTemplateValue innerValue = new ScopeTemplateValue();

        try (TemplateScope outer = new TemplateScope().with(outerValue)) {
            try (TemplateScope inner = new TemplateScope().with(innerValue)) {
                assertSame(innerValue, currentTemplate());
            }
            assertSame(outerValue, currentTemplate());
        }

        assertNull(currentTemplate());
    }

    @Test
    void laterValuesWinWithinOneScopeWithoutLeapfroggingAnInnerFrame() {
        ScopeTemplateValue initial = new ScopeTemplateValue();
        ScopeTemplateValue replacement = new ScopeTemplateValue();
        ScopeTemplateValue specialized = new ScopeTemplateValue();

        try (TemplateScope firstField = new TemplateScope().with(initial)) {
            TemplateScope secondField = new TemplateScope().with(specialized);
            try {
                firstField.with(replacement);
                assertSame(specialized, currentTemplate());
            } finally {
                secondField.close();
            }
            assertSame(replacement, currentTemplate());
        }

        assertNull(currentTemplate());
    }

    @Test
    void retainsTheLastDuplicateValueFromVarargsAndCollectionSnapshots() {
        ScopeTemplateValue first = new ScopeTemplateValue();
        ScopeTemplateValue second = new ScopeTemplateValue();
        ScopeTemplateValue third = new ScopeTemplateValue();

        try (TemplateScope scope = new TemplateScope().with(first, second).with(List.of(first, third))) {
            assertSame(third, currentTemplate());
        }
    }

    @Test
    void snapshotsCallerOwnedVarargsAndCollectionInputs() {
        ScopeTemplateValue varargsValue = new ScopeTemplateValue();
        OtherScopeTemplateValue collectionValue = new OtherScopeTemplateValue();
        Object[] varargs = {varargsValue};
        List<OtherScopeTemplateValue> collection = new ArrayList<>(List.of(collectionValue));

        try (TemplateScope scope = new TemplateScope().with(varargs).with(collection)) {
            varargs[0] = new ScopeTemplateValue();
            collection.set(0, new OtherScopeTemplateValue());

            assertSame(varargsValue, currentTemplate());
            assertSame(collectionValue, TemplateManager.getInstance().getTemplate(OtherScopeTemplateValue.class));
        }
    }

    @Test
    void restoresStateWhenTheScopedBodyFails() {
        ScopeTemplateValue value = new ScopeTemplateValue();

        assertThrows(IllegalArgumentException.class, () -> {
            try (TemplateScope ignored = new TemplateScope().with(value)) {
                assertSame(value, currentTemplate());
                throw new IllegalArgumentException("expected");
            }
        });

        assertNull(currentTemplate());
    }

    @Test
    void rejectsWrongThreadAndOutOfOrderClosureWithoutChangingTheActiveMapping() throws InterruptedException {
        ScopeTemplateValue outerValue = new ScopeTemplateValue();
        ScopeTemplateValue innerValue = new ScopeTemplateValue();

        try (TemplateScope outer = new TemplateScope().with(outerValue);
             TemplateScope inner = new TemplateScope().with(innerValue)) {
            assertThrows(IllegalStateException.class, outer::close);
            assertSame(innerValue, currentTemplate());

            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try {
                    inner.close();
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            });
            thread.start();
            thread.join();

            assertEquals(IllegalStateException.class, failure.get().getClass());
            assertSame(innerValue, currentTemplate());
        }

        assertNull(currentTemplate());
    }

    @Test
    void makesSuccessfulCloseIdempotentAndRejectsFurtherValues() {
        TemplateScope scope = new TemplateScope().with(new ScopeTemplateValue());

        scope.close();
        scope.close();

        assertThrows(IllegalStateException.class, () -> scope.with(new ScopeTemplateValue()));
        assertNull(currentTemplate());
    }

    private ScopeTemplateValue currentTemplate() {
        return TemplateManager.getInstance().getTemplate(ScopeTemplateValue.class);
    }
}

final class ScopeTemplateValue {

    ScopeTemplateValue() {
    }
}

final class OtherScopeTemplateValue {

    OtherScopeTemplateValue() {
    }
}
