/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.*;


/**
 DSL is used to designate a DSL/Model object, which is enriched using the AST transformation.

 The DSL annotation leads to the creation of a couple of useful methods.

 ## Factory and `apply` methods

 A factory method named `create` is generated, using either a single closure as parameter, or, in case of a keyed
 object, using a String and a closure parameter.

 ```groovy
 {@literal @DSL}
 class Config {
 }

 {@literal @DSL}
 class ConfigWithKey {
 {@literal @Key }String name
 }
 ```

 creates the following methods:

 ```groovy
 static Config create(Closure c = {})

 static ConfigWithKey create(String name, Closure c = {})
 ```

 Additionally, an `apply` method is created, which takes single closure and applies it to an existing object.

 ```groovy
 def void apply(Closure c)
 ```

 Both `apply` and `create` also support named parameters, allowing to set values in a concise way. Every map element of
 the method call is converted in a setter call (actually, any method named like the key with a single argument will be called):


 ```groovy
 Config.create {
 name "Dieter"
 age 15
 }
 ```

 Could also be written as:
 ```groovy
 Config.create(name: 'Dieter', age: 15)
 ```

 Of course, named parameters and regular calls inside the closure can be combined ad lib.

 There are also a couple of [[Convenience Factories]] to load a model into client code.

 ## Lifecycle Methods

 Lifecycle methods can are methods annotated with `{@literal @PostCreate}` and `{@literal @PostApply}`. These methods will be called automatically
 after the creation of the object (**after afhe templatee has been applied**) and after the call to the apply method, respectively.

 Lifecycle methods must not be `private`.

 ## copyFrom() method

 Each DSLObject gets a `copyFrom()` method with its own class as parameter. This method copies fields from the given
 object over to this objects, excluding key and owner fields. For non collection fields, only a reference is copied,
 for Lists and Maps, shallow copies are created.

 Currently, it is in discussion whether this should be deep clone instead, see: ([#36](https://github.com/klum-dsl/klum-core/issues/36))

 ## equals() and toString() methods

 If not yet present, `equals()` and `toString()` methods are generated using the respective ASTTransformations. You
 can customize them by using the original ASTTransformations.

 ## hashCode()
 A barebone hashcode is created, with a constant 0 for non-keyed objects, and the hashcode of
 the key for keyed objects. While this is correct and works with changing objects after
 adding them to a HashSet / HashMap, the performance for Sets of non-Keyed objects is severely
 reduced.

 ## Field setter
 ### Field setter for simple fields

 For each simple value field create an accessor named like the field, containing the field type as parameter

 ```groovy
 {@literal @DSL}
 class Config {
   String name
 }
 ```

 creates the following method:

 ```groovy
 def name(String value)
 ```

 Used by:
 ```groovy
 Config.create {
   name "Hallo"
 }
 ```

 ### Setter for simple collections

 for each simple collection, two/three methods are generated:

 -   two methods with the collection name and a Iterable/Vararg argument for Collections or a Map argument for maps. These methods
  *add* the given parameters to the collection

 -   an adder method named like the element name of the collection an containing a the element type

 ```groovy
 {@literal @DSL}
 class Config {
   List<String> roles
   Map<String, Integer> levels
 }
 ```

 creates the following methods:

 ```groovy
 def roles(String... values)
 def roles(Iterable<String> values)
 def role(String value)
 def levels(Map levels)
 def level(String key, Integer value)
 ```

 Usage:
 ```groovy
 Config.create {
   roles "a", "b"
   role "another"
   levels a:5, b:10
   level "high", 8
 }
 ```

 If the collection has no initial value, it is automatically initialized.

 ### Setters and closures for DSL-Object Fields

 for each dsl-object field, a closure method is generated, if the field is a keyed object, this method has an additional
 String parameter. Also, a regular setter method is created for reusing an existing object.

 ```groovy
 {@literal @DSL}
 class Config {
   UnKeyed unkeyed
   Keyed keyed
 }

 {@literal @DSL}
 class UnKeyed {
   String name
 }

 {@literal @DSL}
 class Keyed {
 {@literal @Key }String name
 String value
 }
 ```

 creates the following methods (in Config):

 ```groovy
 def unkeyed(UnKeyed reuse) // reuse an exiting object
 Unkeyed unkeyed({@literal @DelegatesTo}(Unkeyed) Closure closure)
 def keyed(UnKeyed reuse) // reuse an exiting object
 Keyed keyed(String key, {@literal @DelegatesTo}(Unkeyed) Closure closure)
 ```

 Usage:
 ```groovy
 Config.create {
   unkeyed {
     name "other"
   }
   keyed("klaus") {
     value "a Value"
   }
 }

 def objectForReuse = UnKeyed.create { name = "reuse" }

 Config.create {
   unkeyed objectForReuse
 }
 ```

 The closure methods return the created objects, so you can also do the following:

 ```groovy
 def objectForReuse
 Config.create {
   objectForReuse = unkeyed {
     name "other"
   }
 }

 Config.create {
   unkeyed objectForReuse
 }
 ```

 ### Collections of DSL Objects

 Collections of DSL-Objects are created using a nested closure. The name of the (optional) outer closure is the field name, the
 name of the inner closures the element name (which defaults to field name minus a trailing 's'). The syntax for adding
 keyed members to a list and to a map is identical (obviously, only keyed objects can be added to a map).

 The inner creator can also take an existing object instead of a closure, which adds that object to the collection.
 In that case, **the owner field of the added object is only set, when it does not yet have an owner**.

 This syntax is especially useful for delegating the creation of objects into a separate method.

 As with simple objects, the inner closures return the existing object for reuse

 ```groovy
 {@literal @DSL}
 class Config {
   List<UnKeyed> elements
   List<Keyed> keyedElements
   Map<String, Keyed> mapElements
 }

 {@literal @DSL}
 class UnKeyed {
   String name
 }

 {@literal @DSL}
 class Keyed {
   {@literal @Owner} owner
   {@literal @Key} String name
   String value
 }

 def objectForReuse = UnKeyed.create { name "reuse" }
 def anotherObjectForReuse

 def createAnObject(String name, String value) {
   Keyed.create(name) { value(value) }
 }

 Config.create {
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
 Config.create {
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
 ```

 ## On collections

 Although most examples in this wiki use `List`, basically any class implementing / sub interface of `Collection` can be
 used instead. There are a couple of points to take note, however:

 - The default Java Collection Framework interfaces (Collection, List, Set, SortedSet, Stack, Queue) work out of the box
 - When using a custom collection **class** or **interface**, in order for initial values to be provided, `List` must be
 coerced to your custom type, i.e. the code `[] as <YourType>` must be resolvable. This can be done by
 - enhance the `List.asType()` method to handle your custom type
 - in case of a custom class, provide a constructor taking an `Iterable` (or `Collection` or `List`) argument

 However, it is strongly advised to only take the basic interfaces. If additional functionality is needed, it might make more
 sense to apply it using a decorator (for example using KlumWrap) after the object is constructed.

 For maps, **only `Map` and `SortedMap` is supported**.

  __Be careful when using a simple `Set`.__ Since Klum creates barebone hashcode implementations
 (constant zero for non-keyed objects, hashCode of key for keyed objects), a (non Sorted)`Set` of
 non-Keyed model objects might result in a severe degradation of performance of that Set.

 # The {@literal @Key} annotation

 The key annotation is used to designate a special key field, making the annotated class a keyed class. This has the
 following consequences:

 - no setter method is generated for the key field
 - a constructor using a single field of the key type is created (currently, only String is allowed)
 - factory and apply methods get an additional key parameter
 - only keyed classes are allowed as values in a Map

 # The {@literal @Owner} annotation

 DSL-Objects can have an optional owner field, decorated with the `{@literal @Owner}` annotation.

 When the inner object (containing the owner) is added to another dsl-object, either directly or into a collection,
 the owner-field is automatically set to the outer instance.

 This has two dangers:

 - no validity checks are performed during transformation time, leading to runtime ClassCastExceptions if the owner
 type is incorrect
 - If an object that already has an existing owner is reused, the owner is not overridden, but silently ignored. I.e. the first
 object that an object is assigned to, is the actual owner.

 ```groovy
 {@literal @DSL}
   class Foo {
   Bar bar
 }

 {@literal @DSL}
 class Bar {
   {@literal @Owner} Foo owner
 }

 def c = Config.create {
   bar {}
 }

 assert c.bar.owner === c
 ```

 Note that since 0.17.0, the `projects` closure is only optional syntactic sugar, `project` entry could also be out
 directly under config.

 Since complete objects are created, using the configuration is simple:

 ```groovy
 if (config.debugMode) println "Debug mode is active!"

 config.projects.each { name, project ->
   println "Running $name with '${project.mvn.goals.join(' ')}'"
 }

 def projectsWithoutClean = config.projects.findAll { !it.value.mvn.goals.contains("clean")}.values()

 if (projectsWithoutClean) {
   println "WARNING: The following projects do not clean before build:"
   projectsWithoutClean.each {
     println it
   }
 }
 ```

 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Inherited // This is currently not used, see https://issues.apache.org/jira/browse/GROOVY-6765
@GroovyASTTransformationClass(classes={DSLASTTransformation.class})
@Documented
public @interface DSL {
}
