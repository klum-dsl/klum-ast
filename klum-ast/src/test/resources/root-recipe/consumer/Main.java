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
package fixture.consumer;

import fixture.recipe.Application;
import fixture.recipe.Application_DSL;
import fixture.recipe.Environment;
import fixture.recipe.LegacyApplication;
import fixture.recipe.LegacyRecipe;
import fixture.recipe.OrderRecipe;
import groovy.lang.Closure;

import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Environment eu = Environment.Create.With(Map.of("region", "eu"));
        Environment us = Environment.Create.With(Map.of("region", "us"));
        for (Environment selected : List.of(eu, us)) {
            Closure<Void> overlay = new Closure<Void>(null) {
                public Void doCall() {
                    ((Application_DSL.Builder<?>) getDelegate()).environment(selected);
                    return null;
                }
            };
            check(Application.Create.From(OrderRecipe.class, overlay), selected);
            check(Application.Create.FromClasspath(overlay), selected);
            check(Application.Create.FromClasspath(Application.class.getClassLoader(), overlay), selected);
            StaticClient.create(selected).forEach(app -> check(app, selected));
            DynamicClient.create(selected).forEach(app -> check(app, selected));
            if (overlay.getDelegate() != null || overlay.getResolveStrategy() != Closure.OWNER_FIRST)
                throw new AssertionError("Caller closure was mutated");
        }
        // These bytecode calls retain the pre-overlay Factory descriptors.
        if (!LegacyApplication.Create.From(LegacyRecipe.class).getService().equals("legacy") ||
                !LegacyApplication.Create.FromClasspath().getService().equals("legacy") ||
                !LegacyApplication.Create.FromClasspath(LegacyApplication.class.getClassLoader()).getService().equals("legacy"))
            throw new AssertionError("No-overlay Factory calls changed");
        System.out.println("recipe-consumers=true");
    }

    private static void check(Application app, Environment selected) {
        if (app.getEnvironment() != selected || !app.getName().equals("OrderRecipe") ||
                !app.getService().equals("orders") || app.getApplications() != 1)
            throw new AssertionError("Recipe overlay did not complete exactly once with the selected LINK");
    }
}
