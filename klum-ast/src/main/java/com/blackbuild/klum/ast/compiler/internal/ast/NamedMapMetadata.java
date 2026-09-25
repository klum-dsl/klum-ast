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

import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ListExpression;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;

/** Emits native Groovy named-parameter metadata for fixed-target Builder-call maps. */
final class NamedMapMetadata {

    private static final String TARGET_MODEL_METADATA_KEY = NamedMapMetadata.class.getName() + ".targetModel";
    private static final String TRACKED_PARAMETERS_METADATA_KEY = NamedMapMetadata.class.getName() + ".trackedParameters";
    private static final String CATALOG_METADATA_KEY = NamedMapMetadata.class.getName() + ".catalog";

    private static final ClassNode NAMED_PARAM = ClassHelper.make(NamedParam.class);
    private static final ClassNode NAMED_PARAMS = ClassHelper.make(NamedParams.class);

    private NamedMapMetadata() {
    }

    static void target(Parameter parameter, ClassNode targetModel) {
        ClassNode model = targetModel.redirect();
        parameter.setNodeMetaData(TARGET_MODEL_METADATA_KEY, model);

        List<Parameter> tracked = model.getNodeMetaData(TRACKED_PARAMETERS_METADATA_KEY);
        if (tracked == null) {
            tracked = new ArrayList<>();
            model.setNodeMetaData(TRACKED_PARAMETERS_METADATA_KEY, tracked);
        }
        if (!tracked.contains(parameter))
            tracked.add(parameter);

        List<Entry> catalog = model.getNodeMetaData(CATALOG_METADATA_KEY);
        if (catalog == null && model.isResolved()) {
            ClassNode builder = DslAstHelper.getBuilderClassOf(model);
            catalog = catalog(GeneratedDslSupport.publicType(builder).redirect());
            model.putNodeMetaData(CATALOG_METADATA_KEY, catalog);
        }
        if (catalog != null) {
            annotate(parameter, catalog);
        }
    }

    static void copyTarget(Parameter source, Parameter target) {
        ClassNode targetModel = source.getNodeMetaData(TARGET_MODEL_METADATA_KEY);
        if (targetModel != null)
            target(target, targetModel);
    }

    static void copyTargets(MethodNode source, MethodNode target) {
        if (target == null) return;
        for (Parameter sourceParameter : source.getParameters()) {
            for (Parameter targetParameter : target.getParameters()) {
                if (sourceParameter.getName().equals(targetParameter.getName()))
                    copyTarget(sourceParameter, targetParameter);
            }
        }
    }

    static void complete(ClassNode model) {
        ClassNode target = model.redirect();
        List<Entry> catalog = catalog(GeneratedDslSupport.of(target).getBuilderInterface().redirect());
        target.putNodeMetaData(CATALOG_METADATA_KEY, catalog);

        List<Parameter> tracked = target.getNodeMetaData(TRACKED_PARAMETERS_METADATA_KEY);
        if (tracked != null)
            tracked.forEach(parameter -> annotate(parameter, catalog));
    }

    private static List<Entry> catalog(ClassNode publicBuilder) {
        Map<String, List<ClassNode>> candidates = new LinkedHashMap<>();
        collectCandidates(publicBuilder, candidates, new LinkedHashSet<>());

        return candidates.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), commonType(entry.getValue())))
                .toList();
    }

    private static void collectCandidates(ClassNode publicBuilder, Map<String, List<ClassNode>> candidates,
                                          Set<String> visited) {
        ClassNode current = publicBuilder.redirect();
        if (!visited.add(current.getName())) return;

        current.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(current))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .filter(method -> method.getParameters().length == 1)
                .forEach(method -> candidates.computeIfAbsent(method.getName(), ignored -> new ArrayList<>())
                        .add(metadataType(method.getParameters()[0].getOriginType())));
        for (ClassNode parent : current.getInterfaces())
            collectCandidates(parent, candidates, visited);
    }

    private static ClassNode metadataType(ClassNode type) {
        ClassNode projected = GeneratedDslSupport.publicType(type);
        if (projected == null || projected.isGenericsPlaceHolder())
            return ClassHelper.OBJECT_TYPE;
        if (ClassHelper.isPrimitiveType(projected))
            return ClassHelper.getWrapper(projected);
        if (projected.isArray()) {
            ClassNode component = metadataType(projected.getComponentType());
            return component.equals(ClassHelper.OBJECT_TYPE) && !projected.getComponentType().equals(ClassHelper.OBJECT_TYPE)
                    ? ClassHelper.OBJECT_TYPE
                    : component.makeArray();
        }
        if (!Modifier.isPublic(projected.redirect().getModifiers()))
            return ClassHelper.OBJECT_TYPE;
        return projected;
    }

    private static ClassNode commonType(List<ClassNode> candidates) {
        ClassNode first = candidates.get(0);
        boolean sameType = candidates.stream()
                .allMatch(candidate -> candidate.redirect().getName().equals(first.redirect().getName()));
        return sameType ? first : ClassHelper.OBJECT_TYPE;
    }

    private static void annotate(Parameter parameter, List<Entry> catalog) {
        parameter.getAnnotations().removeIf(annotation -> annotation.getClassNode().equals(NAMED_PARAMS));

        ListExpression values = new ListExpression();
        for (Entry entry : catalog) {
            AnnotationNode namedParam = new AnnotationNode(NAMED_PARAM);
            namedParam.setMember("value", constX(entry.name));
            namedParam.setMember("type", classX(entry.type));
            values.addExpression(new AnnotationConstantExpression(namedParam));
        }

        AnnotationNode namedParams = new AnnotationNode(NAMED_PARAMS);
        namedParams.setMember("value", values);
        parameter.addAnnotation(namedParams);
    }

    private record Entry(String name, ClassNode type) {
    }
}
