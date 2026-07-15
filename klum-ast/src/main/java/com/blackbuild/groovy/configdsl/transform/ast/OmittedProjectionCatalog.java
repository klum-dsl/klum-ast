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
package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.klum.ast.util.OmittedProjectionSupport;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.MethodCallExpression;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;

/** Compile-time catalog for dynamic diagnostics of intentionally omitted projection methods. */
final class OmittedProjectionCatalog {

    private static final String ENTRIES_METADATA_KEY = OmittedProjectionCatalog.class.getName() + ".entries";
    private static final String METHOD_METADATA_KEY = OmittedProjectionCatalog.class.getName() + ".method";

    private OmittedProjectionCatalog() {
    }

    static void omit(ClassNode target, MethodNode source, String reason) {
        omit(target, source.getName(), source, reason);
    }

    static void omit(ClassNode target, String exposedName, MethodNode source, String reason) {
        List<Entry> entries = target.getNodeMetaData(ENTRIES_METADATA_KEY);
        if (entries == null) {
            entries = new ArrayList<>();
            target.setNodeMetaData(ENTRIES_METADATA_KEY, entries);
        }
        Entry entry = new Entry(exposedName, source, reason);
        if (!entries.contains(entry)) entries.add(entry);
    }

    static void complete(ClassNode target) {
        List<Entry> entries = target.getNodeMetaData(ENTRIES_METADATA_KEY);
        if (entries == null || entries.isEmpty()) return;

        String catalog = entries.stream().map(Entry::encode).collect(Collectors.joining(";"));
        MethodNode existing = target.getNodeMetaData(METHOD_METADATA_KEY);
        if (existing != null) {
            existing.setCode(returnS(createHandlerCall(catalog)));
            return;
        }

        MethodNode methodMissing = MethodBuilder.createPublicMethod("methodMissing")
                .returning(ClassHelper.OBJECT_TYPE)
                .param(ClassHelper.STRING_TYPE, "name")
                .param(ClassHelper.OBJECT_TYPE, "arguments")
                .doReturn(createHandlerCall(catalog))
                .addTo(target);
        methodMissing.setModifiers(methodMissing.getModifiers() | Opcodes.ACC_SYNTHETIC);
        methodMissing.setSynthetic(true);
        target.setNodeMetaData(METHOD_METADATA_KEY, methodMissing);
    }

    private static MethodCallExpression createHandlerCall(String catalog) {
        return callX(
                classX(ClassHelper.make(OmittedProjectionSupport.class)),
                "handle",
                args(varX("this"), varX("name"), varX("arguments"), constX(catalog)));
    }

    private record Entry(String name, List<String> parameterTypes, int minimumArguments, boolean varargs,
                         String signature, String reason) {

        private Entry(String exposedName, MethodNode method, String reason) {
            this(
                    exposedName,
                    Arrays.stream(method.getParameters())
                            .map(Parameter::getOriginType)
                            .map(ClassNode::getName)
                            .toList(),
                    (int) Arrays.stream(method.getParameters())
                            .limit((method.getModifiers() & Opcodes.ACC_VARARGS) != 0
                                    ? Math.max(0, method.getParameters().length - 1)
                                    : method.getParameters().length)
                            .filter(parameter -> !parameter.hasInitialExpression())
                            .count(),
                    (method.getModifiers() & Opcodes.ACC_VARARGS) != 0,
                    exposedName + "(" + Arrays.stream(method.getParameters())
                            .map(Parameter::getOriginType)
                            .map(ClassNode::getName)
                            .collect(Collectors.joining(", ")) + ")",
                    reason
            );
        }

        private String encode() {
            return encoded(name) + "." + minimumArguments + "." + varargs + "."
                    + encoded(String.join(",", parameterTypes)) + "." + encoded(signature) + "." + encoded(reason);
        }

        private static String encoded(String value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
