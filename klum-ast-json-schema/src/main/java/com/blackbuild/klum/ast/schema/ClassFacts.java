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
package com.blackbuild.klum.ast.schema;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Only declaration bytes are visited; method bodies and annotation classes are never evaluated. */
final class ClassFacts {
    private static final String ANNO_DOC = "Lcom/blackbuild/annodocimal/annotations/AnnoDoc;";
    final String name;
    final String parent;
    final int access;
    final List<String> interfaces;
    final Map<String, Method> methods = new TreeMap<>();
    final Map<String, String> documentation = new TreeMap<>();
    final Map<String, String> keys = new TreeMap<>();
    final List<String> enumConstants = new ArrayList<>();
    String carrier;

    ClassFacts(BinaryArtifacts.Binary binary, boolean wireCarrier) {
        ClassReader reader = new ClassReader(binary.bytes());
        name = reader.getClassName().replace('/', '.');
        parent = reader.getSuperName() == null ? null : reader.getSuperName().replace('/', '.');
        access = reader.getAccess();
        interfaces = Arrays.stream(reader.getInterfaces()).map(value -> value.replace('/', '.')).toList();
        if (wireCarrier && (access & Opcodes.ACC_PUBLIC) != 0) throw invalid("Public carrier");
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return prose(descriptor, value -> documentation.put(name, value));
            }
            @Override
            public FieldVisitor visitField(int fieldAccess, String fieldName, String descriptor, String signature, Object value) {
                if (wireCarrier) {
                    if (!fieldName.equals("DATA") || !descriptor.equals("Ljava/lang/String;") || !(value instanceof String)
                            || fieldAccess != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL) || carrier != null)
                        throw invalid("Invalid carrier DATA constant");
                    carrier = (String) value;
                }
                if ((fieldAccess & Opcodes.ACC_ENUM) != 0) enumConstants.add(fieldName);
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (annotation.equals("Lcom/blackbuild/klum/ast/Key;")) keys.put(fieldName, Type.getType(descriptor).getClassName());
                        return prose(annotation, text -> documentation.put(name + "#" + fieldName, text));
                    }
                };
            }
            @Override
            public MethodVisitor visitMethod(int methodAccess, String methodName, String descriptor, String signature, String[] exceptions) {
                if (wireCarrier) throw invalid("Unexpected executable carrier member");
                Method method = new Method(methodName, methodAccess,
                        Arrays.stream(Type.getArgumentTypes(descriptor)).map(Type::getClassName).toList(),
                        Type.getReturnType(descriptor).getClassName());
                methods.put(method.signature(), method);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        return prose(annotation, text -> documentation.put(name + "#" + method.signature(), text));
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (wireCarrier && carrier == null) throw invalid("Missing carrier DATA constant");
    }

    void requirePublicInterface() {
        if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE)) != (Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE))
            throw invalid("Not a public generated interface");
    }

    void requireConcrete() {
        if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0) throw invalid("Root/relationship is abstract");
    }

    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message + ": " + name); }

    private static AnnotationVisitor prose(String descriptor, Consumer<String> consumer) {
        if (!descriptor.equals(ANNO_DOC)) return null;
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                if (name.equals("value") && value instanceof String text && !text.isBlank()) consumer.accept(text);
            }
        };
    }

    record Method(String name, int access, List<String> parameters, String result) {
        String signature() { return name + "(" + String.join(", ", parameters) + "):" + result; }
        boolean publicInstance() { return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC; }
    }
}
