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
package com.blackbuild.klum.ast.compiler.internal.ast;

import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.classgen.asm.BytecodeHelper;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getFieldType;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getElementTypeForCollection;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getElementTypeForMap;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getKeyTypeForMap;
import static com.blackbuild.klum.ast.compiler.internal.ast.DSLASTTransformation.DSL_FIELD_ANNOTATION;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getAnnotation;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static groovyjarjarasm.asm.Opcodes.ACC_BRIDGE;
import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC;
import static groovyjarjarasm.asm.Opcodes.ACC_VARARGS;

/** Keeps compiler discovery and the exact generated overload together before parameter rebuilding loses provenance. */
final class StructuredInputConverter {
    private static final String ROUTES = StructuredInputConverter.class.getName();

    private StructuredInputConverter() { }

    private record Route(MethodNode source, MethodNode generated) { }

    static void recordRoute(FieldNode field, MethodNode source, MethodNode generated) {
        Parameter[] parameters = source.getParameters();
        if (parameters.length != 1 || !isStringInput(parameters[0].getOriginType())) return;
        List<Route> routes = field.getNodeMetaData(ROUTES);
        if (routes == null) {
            routes = new ArrayList<>();
            field.setNodeMetaData(ROUTES, routes);
        }
        // Keep unsuccessful generation too: optional signature collisions must not select the first route.
        routes.add(new Route(source, generated));
    }

    static boolean isStringInput(ClassNode type) {
        return !type.isGenericsPlaceHolder()
                && (ClassHelper.STRING_TYPE.equals(type) || type.getName().equals(CharSequence.class.getName()));
    }

    static boolean attempted(FieldNode field) { return field.getNodeMetaData(ROUTES) != null; }

    static MethodNode selected(FieldNode field) {
        List<Route> routes = field.getNodeMetaData(ROUTES);
        ClassNode destination = destination(field);
        if (routes == null || routes.size() != 1 || getFieldType(field) != FieldType.DEFAULT
                || !StructuredInputAdmissionPlan.supportsScalarConverterInput(destination)) return null;
        Route route = routes.get(0);
        // Container routes admit exact String values only.
        if ((isListField(field) || isMapField(field)) && !ClassHelper.STRING_TYPE.equals(route.source().getParameters()[0].getOriginType())) return null;
        if (isMapField(field) && !literalStringKeys(field)) return null;
        if (route.generated() == null) return null;
        MethodNode api = GeneratedDslSupport.publicProjectionOf(route.generated());
        if (api == null || !api.isPublic() || api.isStatic() || api.isSynthetic()
                || (api.getModifiers() & (ACC_BRIDGE | ACC_SYNTHETIC)) != 0
                || !matchesParameters(field, api, route.source())
                || !api.getReturnType().redirect().equals(destination.redirect())) return null;
        return api;
    }

    static boolean isListField(FieldNode field) {
        return field.getType().redirect().getName().equals(List.class.getName());
    }

    static boolean isMapField(FieldNode field) {
        return field.getType().redirect().getName().equals(Map.class.getName());
    }

    private static boolean literalStringKeys(FieldNode field) {
        if (!ClassHelper.STRING_TYPE.equals(getKeyTypeForMap(field.getType()))) return false;
        AnnotationNode options = getAnnotation(field, DSL_FIELD_ANNOTATION);
        return options == null || options.getMember("keyMapping") == null;
    }

    private static boolean matchesParameters(FieldNode field, MethodNode api, MethodNode source) {
        Parameter[] parameters = api.getParameters();
        int valueIndex = isMapField(field) ? 1 : 0;
        return parameters.length == valueIndex + 1
                && (valueIndex == 0 || ClassHelper.STRING_TYPE.equals(parameters[0].getOriginType()))
                && parameters[valueIndex].getOriginType().equals(source.getParameters()[0].getOriginType());
    }

    private static ClassNode destination(FieldNode field) {
        if (isListField(field)) return getElementTypeForCollection(field.getType());
        if (isMapField(field)) return getElementTypeForMap(field.getType());
        return field.getType();
    }

    private static boolean hasParameterAuthority(ClassNode domain, MethodNode source) {
        ClassNode owner = domain.redirect();
        if (!ClassHelper.STRING_TYPE.equals(source.getParameters()[0].getOriginType()) || !owner.isResolved() || !Modifier.isPublic(owner.getModifiers()) || owner.isInterface() || owner.isEnum()
                || owner.getGenericsTypes() != null || domain.isGenericsPlaceHolder()
                || !source.getDeclaringClass().redirect().equals(owner)
                || !source.isPublic() || source.isSynthetic() || (source.getModifiers() & (ACC_BRIDGE | ACC_SYNTHETIC | ACC_VARARGS)) != 0
                || source.getGenericsTypes() != null || source.hasDefaultValue()) return false;
        if (source instanceof ConstructorNode)
            return !Modifier.isAbstract(owner.getModifiers()) && owner.getDeclaredConstructors().size() == 1;
        return source.isStatic() && source.getReturnType().redirect().equals(owner)
                && owner.getDeclaredMethods(source.getName()).size() == 1;
    }

    static void enrich(FieldNode field, MethodNode api) {
        List<Route> routes = field.getNodeMetaData(ROUTES);
        MethodNode source = routes.get(0).source();
        AnnotationNode fact = new AnnotationNode(ClassHelper.make(GeneratedInput.Converter.class));
        fact.setMember("owner", constX(source.getDeclaringClass().getName()));
        fact.setMember("name", constX(source instanceof ConstructorNode ? "<init>" : source.getName()));
        fact.setMember("descriptor", constX(BytecodeHelper.getMethodDescriptor(source)));
        fact.setMember("parameter", constX(0));
        fact.setMember("parameterFacts", constX(!isListField(field) && !isMapField(field) && hasParameterAuthority(field.getType(), source)));
        api.addAnnotation(fact);
    }
}
