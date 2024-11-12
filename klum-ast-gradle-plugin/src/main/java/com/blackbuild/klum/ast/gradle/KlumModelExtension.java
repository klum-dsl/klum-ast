package com.blackbuild.klum.ast.gradle;

import org.gradle.api.Action;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Nested;

public abstract class KlumModelExtension {

    @Nested
    public abstract SchemaDependencies getSchemas();

    public abstract MapProperty<String, String> getTopLevelScripts();

    public void topLevelScript(String model, String script) {
        getTopLevelScripts().put(model, script);
    }

    public void schemas(Action<? super SchemaDependencies> action) {
        action.execute(getSchemas());
    }

}
