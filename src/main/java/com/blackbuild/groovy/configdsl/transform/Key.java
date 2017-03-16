package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Designates a field as the key of the containing class. The main usage of this is that this field is automatically used
 * when an instance of the class is put into a map.
 * <p>In a hierarchy of model objects, the ancestor model defines whether the hierarchy is keyed or not.</p>
 *
 * <p>it is illegal</p>
 * <ul>
 *     <li>to put this annotation on a field of any other class than the ancestor of a hierarchy</li>
 *     <li>to put this annotation on more than one field in a class.</li>
 * </ul>
 *
 * <p>Marking a field as key has the following consequences:</p>
 *
 * <ul>
 *     <li>a constructor is created with a single argument of the type of the key field, the default constructor is
 *     removed</li>
 *     <li>the {@code create} method and all adder / setter methods creating an instance of this type take an additional
 *     argument of the annotated type</li>
 *     <li>for this field, no dsl setter methods are created</li>
 * </ul>
 *
 * <pre><code>
 * given:
 * &#064;DSL
 * class Foo {
 *   &#064;Key String name
 * }
 *
 * when:
 * instance = Foo.create("Dieter") {}
 *
 * then:
 * instance.name == "Dieter"
 * </code></pre>
 *
 * <h2>Example with map</h2>
 * <pre><code>
 *  given:
 *  &#064;DSL
 *  class Foo {
 *    {@literal Map<String, Bar> bars}
 *  }
 *
 *  &#064;DSL
 *  class Bar {
 *    &#064;Key String name
 *    String url
 *  }
 *
 *  when:
 *  instance = Foo.create {
 *    bars {
 *      bar("Dieter") { url "1" }
 *      bar("Klaus") { url "2" }
 *    }
 *  }
 *
 *  then:
 *  instance.bars.Dieter.url == "1"
 *  instance.bars.Klaus.url == "2"
 *
 * </code></pre>
 *
 * Currently only fields of type String are allowed to be keys.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Key {
}
