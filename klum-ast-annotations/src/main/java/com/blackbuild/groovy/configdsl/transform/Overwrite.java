package com.blackbuild.groovy.configdsl.transform;

import com.blackbuild.groovy.configdsl.transform.cast.NeedsDSLClass;
import com.blackbuild.klum.ast.util.copy.CopyStrategy;
import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.checks.NeedsOneOf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Handles how values are copied from one object to another.
 */
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@NeedsDSLClass
@NeedsOneOf(value = {"value", "customType", "custom"}, exclusive = true)
public @interface Overwrite {

    Strategy value() default Strategy.DEFAULT;

    String customType() default "";

    Class<? extends CopyStrategy> custom() default CopyStrategy.class;

    enum Strategy {
        DEFAULT("com.blackbuild.klum.ast.util.copy.DefaultCopyStrategy"),
        HELM("com.blackbuild.klum.ast.util.copy.HelmCopyStrategy");

        public final String typeName;

        Strategy(String typeName) {
            this.typeName = typeName;
        }
    }

}
