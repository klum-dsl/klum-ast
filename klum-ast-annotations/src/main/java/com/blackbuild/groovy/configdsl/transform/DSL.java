/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform;

import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.KlumCastValidator;
import groovy.transform.Undefined;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 <p>DSL is used to designate a DSL/Model object, which is enriched using the AST transformation. The annotation
 can also be placed on interfaces, however, interfaces are not transformed at all, fields with an DSL interface type
 are still handled correctly.</p>
 <p>The DSL annotation leads to the creation of a couple of useful DSL methods. Note that most of these methods
 are not visible by default, as not to clutter the interface of the model. Instead they are created in a special
 inner class that is only accessible with</p>

 <h2>Factory and {@code apply} methods</h2>

 <p>Each instantiable DSL class gets a static field {@code Create} of either a subclass of KlumFactory.Keyed or
 KlumFactory.Unkeyed, which provides methods to create instances of the class; abstract classed get an
 implementation of KlumFactory instead.</p>

 <pre><code>
 {@literal @}DSL
 class Config {}

 {@literal @}DSL
 class ConfigWithKey {
   {@literal @Key} String name
 }
 </code></pre>

 <p>allows to create instances with the following calls:</p>

 <pre><code>
 Config.Create.One()
 Config.Create.With(a: 1, b: 2)
 Config.Create.With(a: 1, b: 2) { c 3 }
 Config.Create.With { c 3 }

 ConfigWithKey.Create.One('Dieter')
 ConfigWithKey.Create.With('Dieter', a: 1, b: 2)
 ConfigWithKey.Create.With('Dieter', a: 1, b: 2) { c 3 }
 ConfigWithKey.Create.With('Dieter') { c 3 }
 </code></pre>

 The optional closure to the {@code With} method is used to set values on the created object. The 'One' method is a shortcut for
 'With' without any given values, which makes a nicer syntax ({@code Config.Create.With()} seems a bit strange).

 <p><b>Note that pre 2.0 versions of KlumAST did create the methods directly as static methods of the model class. These methods
 are now deprecated in will be removed in a future version.</b></p>

 <p>If the class contains an static inner class named Factory of the appropriate type or the member factoryBase points
 to such a class, this class is used as a base
 for the generated factory instead. This allows adding additional methods to the factory.</p>


 <p>Additionally, an {@code apply} method is created, which takes single closure and applies it to an existing object.</p>

 <pre><code>
 def void apply(Closure c)
 </code></pre>

 <p>Both {@code apply} and {@code create} also support named parameters, allowing to set values in a concise way. Every map element of
 the method call is converted in a setter call (actually, any method named like the key with a single argument will be called):</p>

 <pre><code>
 Config.Create.With {
   name "Dieter"
   age 15
 }
 </code></pre>

 <p>Could also be written as:</p>
 <pre><code>
 Config.Create.With(name: 'Dieter', age: 15)
 </code></pre>

 <p>Of course, named parameters and regular calls inside the closure can be combined ad lib.</p>

 <p>There are also a couple of [[Convenience Factories]] to load a model into client code.</p>

 <h2>Lifecycle Methods</h2>

 <p>Lifecycle methods can are methods annotated with {@code {@literal @PostCreate}} and {@code {@literal @PostApply}}. These methods will be called
 automatically after the creation of the object (**after the [[template|Templates]] - if set - has been applied**) and
 after the call to the apply method, respectively.</p>

 <p>Lifecycle methods must not be {@code private} and will automatically be made protected, which means you can usually safely
 use default groovy visibility (i.e. simply use {@code def myMethod()}).</p>

 <h2>copyFrom() method</h2>

 <p>Each DSLObject gets a {@code copyFrom()} method with its own class as parameter. This method copies fields from the given
 object over to this objects, excluding key and owner fields. For non collection fields, only a reference is copied,
 for Lists and Maps, shallow copies are created.</p>

 <p>Currently, it is in discussion whether this should be deep clone instead, see: (<a href="https://github.com/klum-dsl/klum-core/issues/36">#36</a>)</p>

 <h2>equals() and toString() methods</h2>

 <p>If not yet present, {@code equals()} and {@code toString()} methods are generated using the respective ASTTransformations. You
 can customize them by using the original ASTTransformations.</p>

 <h2>hashCode()</h2>
 <p>A barebone hashcode is created, with a constant 0 for non-keyed objects, and the hashcode of
 the key for keyed objects. While this is correct and works with changing objects after
 adding them to a HashSet / HashMap, the performance for Sets of non-Keyed objects is severely
 reduced.</p>

 <h2>Field setter</h2>
 <h3>Field setter for simple fields</h3>

 <p>For each simple value field create an accessor named like the field, containing the field type as parameter.</p>

 <pre><code>
 {@literal @}DSL
 class Config {
   String name
 }
 </code></pre>

 <p>creates the following method:</p>

 <pre><code>
 def name(String value)
 </code></pre>

 <p>Used by:</p>
 <pre><code>
 Config.Create.With {
   name "Hallo"
 }
 </code></pre>

 <h3>Setter for simple collections</h3>

 <p>for each simple collection, two/three methods are generated:</p>

 <ul>
   <li>two methods with the collection name and a Iterable/Vararg argument for Collections or a Map argument for maps. These methods
 *add* the given parameters to the collection</li>
   <li>an adder method named like the element name of the collection an containing a the element type</li>
 </ul>

 <pre><code>
 {@literal @}DSL
 class Config {
   {@code List<String>} roles
   {@code Map<String, Integer>} levels
 }
 </code></pre>

 <p>creates the following methods:</p>

 <pre><code>
 def roles(String... values)
 def roles({@code Iterable<String>} values)
 def role(String value)
 def levels(Map levels)
 def level(String key, Integer value)
 </code></pre>

 <p>Usage:</p>
 <pre><code>
 Config.Create.With {
   roles "a", "b"
   role "another"
   levels a:5, b:10
   level "high", 8
 }
 </code></pre>

 <p>If the collection has no initial value, it is automatically initialized.</p>

 <h3>Setters and closures for DSL-Object Fields</h3>

 <p>for each dsl-object field, a closure method is generated, if the field is a keyed object, this method has an additional
 String parameter. Also, a regular setter method is created for reusing an existing object.</p>

 <pre><code>
 {@literal @}DSL
 class Config {
   UnKeyed unkeyed
   Keyed keyed
 }

 {@literal @}DSL
 class UnKeyed {
   String name
 }

 {@literal @}DSL
 class Keyed {
 {@literal @}Key String name
 String value
 }
 </code></pre>

 <p>creates the following methods (in Config):</p>

 <pre><code>
 def unkeyed(UnKeyed reuse) // reuse an exiting object
 Unkeyed unkeyed({@literal @}DelegatesTo(Unkeyed) Closure closure)
 def keyed(UnKeyed reuse) // reuse an exiting object
 Keyed keyed(String key, {@literal @}DelegatesTo(Unkeyed) Closure closure)
 </code></pre>

 <p>Usage:</p>
 <pre><code>
 Config.Create.With {
   unkeyed {
     name "other"
   }
   keyed("klaus") {
     value "a Value"
   }
 }

 def objectForReuse = UnKeyed.Create.With { name = "reuse" }

 Config.Create.With {
   unkeyed objectForReuse
 }
 </code></pre>

 <p>The closure methods return the created objects, so you can also do the following:</p>

 <pre><code>
 def objectForReuse
 Config.Create.With {
   objectForReuse = unkeyed {
     name "other"
   }
 }

 Config.Create.With {
   unkeyed objectForReuse
 }
 </code></pre>

 <h3>Collections of DSL Objects</h3>

 <p>Collections of DSL-Objects are created using a nested closure. The name of the (optional) outer closure is the field name, the
 name of the inner closures the element name (which defaults to field name minus a trailing 's'). The syntax for adding
 keyed members to a list and to a map is identical (obviously, only keyed objects can be added to a map).</p>

 <p>The inner creator can also take an existing object instead of a closure, which adds that object to the collection.
 In that case, <b>the owner field of the added object is only set, when it does not yet have an owner</b>.</p>

 <p>This syntax is especially useful for delegating the creation of objects into a separate method.</p>

 <p>As with simple objects, the inner closures return the existing object for reuse</p>

 <pre><code>
 {@literal @}DSL
 class Config {
   {@code List<UnKeyed>} elements
   {@code List<Keyed>} keyedElements
   {@code Map<String, Keyed>} mapElements
 }

 {@literal @}DSL
 class UnKeyed {
   String name
 }

 {@literal @}DSL
 class Keyed {
   {@literal @}Owner owner
   {@literal @}Key String name
   String value
 }

 def objectForReuse = UnKeyed.Create.With { name "reuse" }
 def anotherObjectForReuse

 def createAnObject(String name, String value) {
   Keyed.Create.With(name) { value(value) }
 }

 Config.Create.With {
   elements {
     element {
        name "an element"
     }
     element {
       name "another element"
     }
       element objectForReuse
     }
     keyedElements {
       anotherObjectForReuse = keyedElement ("klaus") {
       value "a Value"
     }
   }
   mapElements {
     mapElement ("dieter") {
       value "another"
     }
     mapElement anotherObjectForReuse // owner is NOT changed
     mapElement createAnObject("Hans", "Franz") // owner is set to Config instance
   }
 }

 // flat syntax without nested closures:
 Config.Create.With {
   element {
     name "an element"
   }
   element {
     name "another element"
   }
   element objectForReuse
   anotherObjectForReuse = keyedElement ("klaus") {
     value "a Value"
   }
   mapElement ("dieter") {
     value "another"
   }
   mapElement anotherObjectForReuse // owner is NOT changed
   mapElement createAnObject("Hans", "Franz") // owner is set to Config instance
 }
 </code></pre>

 <h2>On collections</h2>

  <p>Although most examples in this wiki use {@code List}, basically any class implementing / sub interface of {@code Collection} can be
 used instead. There are a couple of points to take note, however:</p>

 <ul>
     <li>The default Java Collection Framework interfaces (Collection, List, Set, SortedSet, Stack, Queue) work out of the box</li>
     <li>When using a custom collection **class** or **interface**, in order for initial values to be provided, {@code List} must be
     coerced to your custom type, i.e. the code {@code [] as <YourType>} must be resolvable. This can be done by
     <ul>
         <li>enhance the {@code List.asType()} method to handle your custom type</li>
         <li>in case of a custom class, provide a constructor taking an {@code Iterable} (or {@code Collection} or {@code List}) argument</li>
     </ul>
 </ul>

 <p>However, it is strongly advised to only take the basic interfaces. If additional functionality is needed, it might make more
 sense to apply it using a decorator (for example using KlumWrap) after the object is constructed.</p>

 For maps, <b>only {@code Map} and {@code SortedMap} is supported</b>.

 <p><b>Be careful when using a simple {@code Set}.</b> Since Klum creates barebone hashcode implementations
 (constant zero for non-keyed objects, hashCode of key for keyed objects), a (non Sorted){@code Set} of
 non-Keyed model objects might result in a severe degradation of performance of that Set.</p>

 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited // This is currently not used, see https://issues.apache.org/jira/browse/GROOVY-6765
@GroovyASTTransformationClass({
        "com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation",
        "com.blackbuild.groovy.configdsl.transform.ast.mutators.ModelVerifierTransformation",
        "com.blackbuild.groovy.configdsl.transform.ast.DelegatesToRWTransformation",
})
@KlumCastValidated
@KlumCastValidator("com.blackbuild.klum.ast.validation.CheckDslDefaultImpl")
@Documented
public @interface DSL {
    /**
     * The short name of the class to be used in collections. If not set, defaults to the name of
     * the class, with the first character converted to lowercase.
     */
    String shortName() default "";

    /**
     * When present, the given suffix is stripped from child class names to determine the short name.
     */
    String stripSuffix() default "";

    /**
     * When present, the given type is used as default type for a field of this type.
     * This makes most sense on interfaces or abstract classes.
     */
    Class<?> defaultImpl() default Undefined.class;

    /**
     * When set, the given class, which must be a subclass of either KlumFactory (for abstract classes) or
     * KlumFactory.Keyed/Unkeyed will be used as a base for the generated factory class. Note that if the annotated class
     * contains a static inner class named "Factory", this class will be used by default.
     */
    Class<?> factoryBase() default Undefined.class;
}
