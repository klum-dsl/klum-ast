package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Designates a default value for the given field. This automatically creates a getter providing
 * that default value when the value of the annotated field is empty (as defined by Groovy Truth).
 *
 * <p>The default target as decided by the members must be exactly one of:</p>
 * <ul>
 *  <li>{@code field}: return the value of the field with the given name</li>
 *  <li>{@code delegate}: return the value of an identically named field of the given delegate field.
 *  This is especially useful in with the {@link Owner} annotation to create composite like tree structures.</li>
 *  <li>{@code closure}: execute the closure (in the context of {@code this}) and return the result</li>
 * </ul>
 *
 * <pre><code>
 * given:
 * &#064;DSL
 * class Container {
 *   String name
 *   Element element
 * }
 *
 * &#064;DSL
 * class Element {
 *   &#064;Owner Container owner
 *
 *   &#064;Default(delegate = 'owner')
 *   String name
 * }
 *
 * when: "No name is set for the inner element"
 * instance = Container.create {
 * name "outer"
 *   element {}
 * }
 *
 * then: "the name of the outer instance is used"
 * instance.element.name == "outer"
 *
 * when:
 * instance = Container.create {
 *   name "outer"
 *   element {
 *     name "inner"
 *   }
 * }
 *
 * then:
 * instance.element.name == "inner"
 * </code></pre>
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
