/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;

import java.util.List;

public class MethodAstHelper {

    private MethodAstHelper() {
        // Utility class
    }

    public static MethodNode findMatchingMethod(ClassNode targetType, String methodName, List<ClassNode> args) {
        if (targetType == null) return null;
        List<MethodNode> targetMethods = targetType
                .redirect()
                .getMethods(methodName);

        return bestMatch(targetMethods, args);
    }

    public static MethodNode bestMatch(List<MethodNode> targetMethods, List<ClassNode> args) {
        if (targetMethods.size() == 1)
            return targetMethods.get(0);

        int currentDistance = Integer.MAX_VALUE;
        MethodNode bestMethod = null;

        for (MethodNode targetMethod : targetMethods) {
            int distance = argumentDistance(targetMethod, args);
            if (distance == 0) return targetMethod;
            if (distance < 0) continue;
            if (distance < currentDistance) {
                currentDistance = distance;
                bestMethod = targetMethod;
            }
        }
        return bestMethod;
    }

    public static Integer argumentDistance(MethodNode methodNode, List<ClassNode> args) {
        int result = 0;
        if (methodNode.getParameters().length != args.size())
            return -1;
        for (int i = 0; i < methodNode.getParameters().length; i++) {
            int paramDistance = classDistance(args.get(i), methodNode.getParameters()[i].getType(), 0);
            if (paramDistance < 0)
                return -1;
            result += paramDistance;
        }
        return result;
    }

    public static int classDistance(ClassNode arg, ClassNode parent, int baseDistance) {
        if (arg == null) return -1;
        if (arg.equals(ClassHelper.VOID_TYPE))
            return baseDistance;
        if (arg.equals(parent))
            return baseDistance;

        if (arg.isArray() && parent.isArray()) {
            return classDistance(arg.getComponentType(), parent.getComponentType(), baseDistance);
        }

        for (ClassNode iface : arg.getInterfaces()) {
            int distance = classDistance(iface, parent, baseDistance + 1);
            if (distance >= 0)
                return distance;
        }

        return classDistance(arg.getSuperClass(), parent, baseDistance + 1);
    }
}
