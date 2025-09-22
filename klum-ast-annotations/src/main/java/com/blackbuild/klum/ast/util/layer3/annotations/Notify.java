package com.blackbuild.klum.ast.util.layer3.annotations;

import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.groovy.configdsl.transform.cast.NeedsDSLClass;
import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.checks.NeedsOneOf;

import java.lang.annotation.*;

/**
 * Can be used to mark fields to emit a notification if its value is manually set or not set.
 * This is most useful in combination with {@link com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate}
 * or {@link com.blackbuild.klum.ast.util.layer3.annotations.LinkTo}, to designate that the AutoCreate/AutoLink mechanism
 * is either an unwanted fallback (ifUnset), or the expected behavior (ifSet).
 * <p>Notify can also be used to change the default behavior for deprecated fields.</p>
 * The default level is WARNING, but this can be overridden by {@link #level()}.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@NeedsDSLClass
@NeedsOneOf(value = {"ifSet", "ifUnset"}, exclusive = true)
@Documented
public @interface Notify {
    /** The message to be given for the generated issue if the field was manually set. */
    String ifSet() default "";
    /** The message to be given for the generated issue if the field was not manually set. */
    String ifUnset() default "";
    /** The level of the generated issue. */
    Validate.Level level() default Validate.Level.WARNING;
}
