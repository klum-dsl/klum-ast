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
package qualification.consumer;

import qualification.schema.Application;
import qualification.schema.Application_DSL;
import qualification.schema.Direct;
import qualification.schema.OrdinaryAlias;
import qualification.schema.OrdinaryDirect;
import qualification.javaapi.JavaPolicies;
import qualification.foreign.ForeignTrigger;
import com.blackbuild.klum.ast.PropagateAnnotation;
import com.blackbuild.klum.ast.Required;
import com.blackbuild.klum.ast.Validate;
import com.blackbuild.klum.ast.layer3.AutoCreate;
import qualification.groovyapi.Repeated;
import qualification.groovyapi.Container;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Main {
    public static String query(Application_DSL.Builder<Application> builder) { return builder.summary(); }
    public static void main(String[] args) throws Exception {
        boolean copied = Boolean.parseBoolean(args[0]);
        boolean named = Boolean.parseBoolean(args[1]);
        boolean requested = Boolean.parseBoolean(args[2]);
        for (Class<?> policy : List.of(JavaPolicies.Repeated.class, JavaPolicies.Container.class, Repeated.class, Container.class)) {
            boolean autoCreate = Arrays.stream(policy.getAnnotationsByType(PropagateAnnotation.class))
                    .anyMatch(request -> request.value() == AutoCreate.class);
            if (autoCreate != requested) throw new AssertionError("current library requests: " + policy);
        }
        Direct direct = StaticConsumer.createDirect();
        Application value = StaticConsumer.create();
        if (!value.summary().equals("CONFIGURED") || !Application.Create.isModelOrBuilder(value))
            throw new AssertionError("typed generated contract");
        if (!StaticConsumer.cluster().getServices().get("javaRepeated").getName().equals("cluster"))
            throw new AssertionError("Cluster factory");
        Map<String, Class<? extends Annotation>> domains = Map.of(
                "javaRepeated", JavaPolicies.Repeated.class, "javaContainer", JavaPolicies.Container.class,
                "groovyRepeated", Repeated.class, "groovyContainer", Container.class);
        for (var domain : domains.entrySet()) {
            String fieldName = domain.getKey();
            var field = Application.class.getDeclaredField(fieldName);
            Object child = Application.class.getMethod("get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1)).invoke(value);
            if ((child != null) != copied || (field.getAnnotation(AutoCreate.class) != null) != copied)
                throw new AssertionError("Schema snapshot: " + fieldName);
            if (field.getAnnotation(JavaPolicies.Marker.class) == null) throw new AssertionError("second request");
            String expectedName = fieldName.replace("Repeated", "-repeat").replace("Container", "-container");
            Annotation original = field.getAnnotation(domain.getValue());
            if (original == null || !domain.getValue().getMethod("name").invoke(original).equals(expectedName))
                throw new AssertionError("retained domain members: " + fieldName);
            if (copied) {
                if (!child.getClass().getMethod("getName").invoke(child).equals(expectedName))
                    throw new AssertionError("DefaultValues: " + fieldName);
            }
        }
        var required = Application.class.getDeclaredField("name");
        if (required.getAnnotationsByType(Required.class).length != 1 || required.getAnnotationsByType(Validate.class).length != 1)
            throw new AssertionError("normal Required lowering once");
        for (String method : List.of("foreign_type", "foreign_payload", "foreign_describe")) {
            if (!Application.class.getMethod(method).invoke(value).equals("generated") ||
                    !Direct.class.getMethod(method).invoke(direct).equals("generated") ||
                    !OrdinaryDirect.class.getMethod(method).invoke(new OrdinaryDirect()).equals("direct"))
                throw new AssertionError("foreign generated effect: " + method);
            try {
                OrdinaryAlias.class.getMethod(method);
                throw new AssertionError("non-DSL alias executed");
            } catch (NoSuchMethodException expected) { }
        }
        if (Application.class.getDeclaredField("payload").getAnnotation(ForeignTrigger.class) == null ||
                Application.class.getMethod("describe").getAnnotation(ForeignTrigger.class) == null ||
                Application.class.getAnnotation(ForeignTrigger.class) == null ||
                Application.class.getAnnotation(JavaPolicies.ForeignAlias.class) == null ||
                OrdinaryAlias.class.getAnnotation(ForeignTrigger.class) != null)
            throw new AssertionError("physical foreign copy and inert alias");
        for (Class<?> type : List.of(Application.class, JavaPolicies.class, Repeated.class, ForeignTrigger.class, StaticConsumer.class)) {
            if (type.getModule().isNamed() != named) throw new AssertionError("module identity: " + type);
        }
        for (String compilerType : List.of("com.blackbuild.klum.ast.compiler.internal.ast.AnnotationPropagationContributor",
                "com.blackbuild.klum.cast.compiler.EffectiveDeclarationContributor", "qualification.transform.ForeignTransform")) {
            try {
                Class.forName(compilerType);
                throw new AssertionError("compiler dependency leaked: " + compilerType);
            } catch (ClassNotFoundException expected) { }
        }
        System.out.println("artifacts=true;snapshot=" + copied + ";named=" + named);
    }
}
