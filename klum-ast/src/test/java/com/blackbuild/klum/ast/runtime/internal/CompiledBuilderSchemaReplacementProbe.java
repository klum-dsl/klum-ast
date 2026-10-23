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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Test bridge to the non-public production seam; the comparison never enters the seam. */
public final class CompiledBuilderSchemaReplacementProbe {
    private CompiledBuilderSchemaReplacementProbe() { }
    public static Map<String, Object> read(Class<?> builder, Class<?> factory) {
        CompiledBuilderSchema schema = CompiledBuilderSchema.read(builder, factory);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", schema.model().getName());
        result.put("builder", schema.contract().getName());
        result.put("entries", schema.entries().values().stream().map(CompiledBuilderSchemaReplacementProbe::entry).toList());
        result.put("rejections", schema.rejections().stream().map(value -> Map.of("source", value.source(), "reason", value.reason())).toList());
        result.put("declarations", schema.declarations());
        result.put("defaults", schema.defaultStatus());
        return result;
    }
    public static List<Map<String, Object>> scope(Class<?> contract) throws IOException, ClassNotFoundException {
        return CompiledBuilderSchema.scope(contract).stream().map(declaration -> Map.<String, Object>of(
                "owner", declaration.owner(), "name", declaration.name(), "parameters", declaration.parameters(),
                "returns", declaration.returns(), "documentation", declaration.documentation() == null ? "" : declaration.documentation())).toList();
    }
    private static Map<String, Object> entry(CompiledBuilderSchema.Entry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("externalName", entry.externalName());
        result.put("role", entry.role().name());
        result.put("source", entry.source());
        result.put("owner", entry.contract().getName());
        result.put("operation", entry.operation());
        result.put("descriptors", entry.methods().stream().map(CompiledBuilderSchemaReplacementProbe::descriptor).sorted().toList());
        result.put("nameOrigin", entry.nameOrigin());
        result.put("mapKeyType", entry.keyType());
        result.put("excludedChildKeyMember", entry.excludedKey());
        result.put("fixedChildKey", entry.fixedChildKey());
        result.put("payload", payload(entry.payload()));
        return result;
    }
    private static Map<String, Object> payload(CompiledBuilderSchema.Payload payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", payload.kind().name());
        result.put("type", payload.type().getName());
        result.put("builder", payload.builder() == null ? null : payload.builder().getName());
        result.put("keyType", payload.keyType());
        result.put("item", payload.item() == null ? null : payload(payload.item()));
        return result;
    }
    private static String descriptor(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", "))
                + "):" + method.getReturnType().getTypeName();
    }
}
