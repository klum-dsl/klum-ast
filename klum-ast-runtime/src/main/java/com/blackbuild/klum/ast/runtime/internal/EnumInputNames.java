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

import com.blackbuild.klum.ast.Struct;
import groovyjarjarasm.asm.AnnotationVisitor;
import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.FieldVisitor;
import groovyjarjarasm.asm.Opcodes;
import groovyjarjarasm.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.blackbuild.klum.ast.runtime.internal.BuilderContractReader.verifyResourceOrigin;

/** Resolves native enum tokens from declaration facts, before any enum instance is needed. */
public final class EnumInputNames {
    private static final String NAME_ANNOTATION = Type.getDescriptor(Struct.Name.class);
    private static final ClassValue<Map<String, String>> TOKENS = new ClassValue<>() {
        @Override
        protected Map<String, String> computeValue(Class<?> type) {
            return readDeclarations(type);
        }
    };

    private EnumInputNames() { }

    /** Build an external-token to declared-constant map from source or binary declaration facts. */
    public static Map<String, String> resolve(String enumType, Map<String, String> declarations) {
        Map<String, String> tokens = new LinkedHashMap<>();
        declarations.forEach((constant, token) -> {
            if (token == null || token.isEmpty()) {
                throw new IllegalArgumentException("@Struct.Name requires a nonempty exact String on " + enumType + "#" + constant);
            }
            String previous = tokens.putIfAbsent(token, constant);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate structured enum token '" + token + "' in " + enumType
                        + " for constants " + previous + " and " + constant + ". Give them distinct @Struct.Name values");
            }
        });
        return Map.copyOf(tokens);
    }

    static Map<String, String> tokens(Class<?> type) {
        return TOKENS.get(type);
    }

    private static Map<String, String> readDeclarations(Class<?> type) {
        Map<String, String> declarations = new LinkedHashMap<>();
        try {
            ClassReader reader = enumDeclaration(type);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if ((access & Opcodes.ACC_ENUM) == 0) return null;
                    declarations.put(name, name);
                    return constantAnnotations(declarations, name);
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot read packaged enum declarations for " + type.getName(), failure);
        }
        return resolve(type.getName(), declarations);
    }

    private static ClassReader enumDeclaration(Class<?> type) throws IOException {
        String internalName = type.getName().replace('.', '/');
        String resource = internalName + ".class";
        verifyResourceOrigin(type, resource);
        try (InputStream bytes = type.getModule().getResourceAsStream(resource)) {
            if (bytes == null) throw new IOException("Missing packaged enum class " + resource);
            ClassReader reader = new ClassReader(bytes);
            if (!internalName.equals(reader.getClassName()) || (reader.getAccess() & Opcodes.ACC_ENUM) == 0) {
                throw new IllegalArgumentException("Enum declaration resource disagrees with loaded type " + type.getName());
            }
            return reader;
        }
    }

    private static FieldVisitor constantAnnotations(Map<String, String> declarations, String constant) {
        return new FieldVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!visible || !NAME_ANNOTATION.equals(descriptor)) return null;
                declarations.put(constant, null);
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String member, Object value) {
                        if ("value".equals(member) && value instanceof String token) declarations.put(constant, token);
                    }
                };
            }
        };
    }
}
