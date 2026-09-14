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
package com.blackbuild.klum.ast.testsupport;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.Test;
import spock.lang.Issue;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Issue("658")
class TemplateScopeJavaConsumerTest {

    @Test
    void appliesBothJavaWithFormsToNormalRootAndOwnedBuilderCreation() {
        Fixture fixture = new Fixture();
        TemplateScope scope = new TemplateScope();
        scope.with(fixture.template("Delivery", "region", "eu-central"));
        scope.with(List.of(fixture.template("DeliveryOptions", "enabled", true)));

        try (scope) {
            assertEquals("eu-central", fixture.deliveryRegion());
            assertEquals(true, fixture.deliveryOptionsEnabled());
        }

        assertNull(fixture.deliveryRegion());
        assertEquals(false, fixture.deliveryOptionsEnabled());
    }

    @Test
    void restoresNormalDslCreationAfterExceptionalResourceExit() {
        Fixture fixture = new Fixture();
        TemplateScope scope = new TemplateScope();
        scope.with(fixture.template("Delivery", "region", "eu-central"));

        assertThrows(IllegalStateException.class, () -> {
            try (scope) {
                assertEquals("eu-central", fixture.deliveryRegion());
                throw new IllegalStateException("expected");
            }
        });

        assertNull(fixture.deliveryRegion());
    }

    private static final class Fixture {

        private final GroovyShell shell;

        private Fixture() {
            GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());
            loader.parseClass("""
                import com.blackbuild.klum.ast.DSL

                @DSL
                class Delivery {
                    String region
                    DeliveryOptions options
                }

                @DSL
                class DeliveryOptions {
                    boolean enabled
                }
            """);
            shell = new GroovyShell(loader);
        }

        private Object template(String type, String property, Object value) {
            shell.setVariable("templateValue", value);
            return shell.evaluate(type + ".Create.Template.With(" + property + ": templateValue)");
        }

        private String deliveryRegion() {
            return (String) shell.evaluate("Delivery.Create.With { options {} }.region");
        }

        private boolean deliveryOptionsEnabled() {
            return (boolean) shell.evaluate("Delivery.Create.With { options {} }.options.enabled");
        }
    }
}
