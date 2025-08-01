/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.klum.common;

import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.StringGroovyMethods;

import java.util.ArrayList;
import java.util.List;

import static groovy.transform.Undefined.isUndefined;

@SuppressWarnings("java:S1168") // null is intentional, mimics the behavior of wrapped groovy methods
public class Groovy3To4MigrationHelper {

    private Groovy3To4MigrationHelper() {
        // Utility class
    }

    // copy of method from AbstractASTTransformation, but this method has been renamed between 3 and 4, so we copy it out
    public static List<String> getMemberStringList(AnnotationNode anno, String name) {
        Expression expr = anno.getMember(name);
        if (expr == null) {
            return null;
        }
        if (expr instanceof ListExpression) {
            final ListExpression listExpression = (ListExpression) expr;
            if (isUndefinedMarkerList(listExpression)) {
                return null;
            }

            return getValueStringList(listExpression);
        }
        return tokenize(getMemberStringValue(anno, name));
    }

    private static boolean isUndefinedMarkerList(ListExpression listExpression) {
        if (listExpression.getExpressions().size() != 1) return false;
        Expression itemExpr = listExpression.getExpression(0);
        if (itemExpr == null) return false;
        if (itemExpr instanceof ConstantExpression) {
            Object value = ((ConstantExpression) itemExpr).getValue();
            if (value instanceof String && isUndefined((String)value)) return true;
        } else if (itemExpr instanceof ClassExpression && isUndefined(itemExpr.getType())) {
            return true;
        }
        return false;
    }

    private static List<String> getValueStringList(ListExpression listExpression) {
        List<String> list = new ArrayList<>();
        for (Expression itemExpr : listExpression.getExpressions()) {
            if (itemExpr instanceof ConstantExpression) {
                Object value = ((ConstantExpression) itemExpr).getValue();
                if (value != null) list.add(value.toString());
            }
        }
        return list;
    }

    public static List<String> tokenize(String rawExcludes) {
        return rawExcludes == null ? new ArrayList<>() : StringGroovyMethods.tokenize(rawExcludes, ", ");
    }

    public static String getMemberStringValue(AnnotationNode node, String name, String defaultValue) {
        final Expression member = node.getMember(name);
        if (member instanceof ConstantExpression) {
            Object result = ((ConstantExpression) member).getValue();
            if (result instanceof String && isUndefined((String) result)) result = null;
            if (result != null) return result.toString();
        }
        return defaultValue;
    }

    public static String getMemberStringValue(AnnotationNode node, String name) {
        return getMemberStringValue(node, name, null);
    }

    public static List<ClassNode> getMemberClassList(AnnotationNode anno, String name, SourceUnit sourceUnit) {
        List<ClassNode> list = new ArrayList<>();
        Expression expr = anno.getMember(name);
        if (expr == null) {
            return null;
        }
        if (expr instanceof ListExpression) {
            final ListExpression listExpression = (ListExpression) expr;
            if (isUndefinedMarkerList(listExpression)) {
                return null;
            }
            list = getTypeList(anno, name, listExpression, sourceUnit);
        } else if (expr instanceof ClassExpression) {
            ClassNode cn = expr.getType();
            if (isUndefined(cn)) return null;
            if (cn != null) list.add(cn);
        } else if (expr instanceof VariableExpression) {
            CommonAstHelper.addCompileError(sourceUnit, "Expecting to find a class value for '" + name + "' but found variable: " + expr.getText() + ". Missing import or unknown class?", anno);
        } else if (expr instanceof ConstantExpression) {
            CommonAstHelper.addCompileError(sourceUnit, "Expecting to find a class value for '" + name + "' but found constant: " + expr.getText() + "!", anno);
        }
        return list;
    }

    private static List<ClassNode> getTypeList(AnnotationNode anno, String name, ListExpression listExpression, SourceUnit sourceUnit) {
        List<ClassNode> list = new ArrayList<>();
        for (Expression itemExpr : listExpression.getExpressions()) {
            if (itemExpr instanceof ClassExpression) {
                ClassNode cn = itemExpr.getType();
                if (cn != null) list.add(cn);
            } else if (itemExpr instanceof VariableExpression) {
                CommonAstHelper.addCompileError(sourceUnit, "Expecting a list of class values for '" + name + "' but found variable: " + itemExpr.getText() + ". Missing import or unknown class?", anno);
            } else if (itemExpr instanceof ConstantExpression) {
                CommonAstHelper.addCompileError(sourceUnit, "Expecting a list of class values for '" + name + "' but found constant: " + itemExpr.getText() + "!", anno);
            }
        }
        return list;
    }



}
