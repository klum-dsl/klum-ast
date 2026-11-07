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
package fixture.legacyconsumer;

import fixture.recipe.KeyedLegacyApplication;
import fixture.recipe.LegacyApplication;

import java.util.List;
import java.util.Map;

public class LegacyMain {
    public static void main(String[] args) {
        Map<String, String> body = Map.of("service", "legacy");
        Map<String, String> document = Map.of("name", "legacy", "service", "legacy");
        List<LegacyApplication> unkeyed = List.of(
                LegacyApplication.Create.FromStruct(body),
                LegacyApplication.Create.FromStruct(body, "old.yml"));
        List<KeyedLegacyApplication> keyed = List.of(
                KeyedLegacyApplication.Create.FromStruct(document),
                KeyedLegacyApplication.Create.FromStruct(document, "old.yml"),
                KeyedLegacyApplication.Create.FromStruct("legacy", body),
                KeyedLegacyApplication.Create.FromStruct("legacy", body, "old.yml"));
        unkeyed.forEach(app -> check(app.getService()));
        keyed.forEach(LegacyMain::checkKeyed);
        LegacyStaticClient.unkeyed().forEach(app -> check(app.getService()));
        LegacyStaticClient.keyed().forEach(LegacyMain::checkKeyed);
        System.out.println("pre-addition-binary-calls=true");
    }

    private static void checkKeyed(KeyedLegacyApplication app) {
        check(app.getService());
        if (!app.getName().equals("legacy")) {
            throw new AssertionError("Pre-addition keyed identity changed");
        }
    }

    private static void check(String service) {
        if (!service.equals("legacy")) {
            throw new AssertionError("Pre-addition FromStruct call changed");
        }
    }
}
