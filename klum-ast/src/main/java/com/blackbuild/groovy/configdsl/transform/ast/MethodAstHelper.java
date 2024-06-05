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
