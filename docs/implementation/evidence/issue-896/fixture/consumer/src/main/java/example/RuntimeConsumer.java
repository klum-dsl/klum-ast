package example;

import com.blackbuild.klum.ast.runtime.KlumBuilder;

public class RuntimeConsumer {
    public static void main(String[] args) {
        EnvironmentApi result = new ConfiguredModel().get();
        if (result.getApplications().size() != 2
                || !"billing:1".equals(result.getApplications().get("billing").getImage())
                || !"inventory".equals(result.getApplications().get("inventory").getName())) {
            throw new AssertionError("Layer 3 cluster/model mismatch");
        }
        System.out.println("CONSUMER_OK " + result.getApplications().keySet());
        System.out.println("RUNTIME_ORIGIN " + KlumBuilder.class.getProtectionDomain().getCodeSource().getLocation());
        try {
            Class.forName("com.blackbuild.klum.ast.compiler.internal.ast.DSLASTTransformation");
            throw new AssertionError("Compiler leaked into runtime");
        } catch (ClassNotFoundException expected) {
            System.out.println("NO_AST_COMPILER");
        }
    }
}
