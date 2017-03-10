package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Designates a default value for the given field. This automatically creates a getter providing
 * that default value when the value of the annotated field is empty (as defined by Groovy Truth).
 *
 * <p>The default target as decided by the members must be exactly one of:</p>
 * <ul>
 *  <li>{@code field}: return the value of the field with the given name</li>
 *  <li>{@code delegate}: return the value of an identically named field of the given delegate field</li>
 *  <li>{@code closure}: execute the closure (in the context of {@code this}) and return the result</li>
 * </ul>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Default {

    /**
     * @deprecated Use {@link #field()} instead
     */
    @Deprecated
    String value() default "";

    /**
     * Delegates to the field with the given name, if the annotated field is empty.
     *
     * <p>{@code @Default(field = 'other') String aValue}</p>
     * <p>leads to</p>
     * <p>{@code aValue ?: other}</p>
     */
    String field() default "";

    /**
     * Delegates to the given closure, if the annotated field is empty.
     *
     * <p>{@code @Default(code = { name.toLowerCase() }) String aValue}</p>
     * <p>leads to</p>
     * <p>{@code aValue ?: name.toLowerCase()}</p>
     */
    Class code() default None.class;

    /**
     * Delegate to a field with the same name on the targeted field, if the annotated field is empty
     *
     * <p>{@code @Default(delegate = 'other') String aValue}</p>
     * <p>leads to</p>
     * <p>{@code aValue ?: parent.aValue}</p>
     */
    String delegate() default "";

    /**
     * @deprecated Do not use, this is only to provide a default value for the {@link #code()} field.
     */
    @Deprecated
    interface None {}
}
