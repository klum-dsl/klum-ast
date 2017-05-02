## 0.99
- Alternatives are back

## 0.98.1
- Fix accidential setting of properties to final
- additional convenience factory for delegating scripts


## 0.98.0
- Breaking changes:
  - Models are now read only. This means all mutation methods are constrained to the content of the `apply()` / `create()`
    closures. Unfortunately, there is no deprecation path for this change, which means that in order to use
    this version, you might have to change code to fix compilation errors.
    
    see [Static Models](https://github.com/klum-dsl/klum-ast/wiki/Static-Models) and 
    [#56](https://github.com/klum-dsl/klum-ast/issues/56) 
  - Additional methods that change state must/should now be marked with @Mutator, which effectively moves them 
    to the RW class. This is currently partially validated. An example usage for this are pseudo setters (i.e. 
    methods named as setters, but relying on other methods)
- Lifecycle methods are moved to the rw-class and made protected, which effectively makes them invisible when
  instantiating and using the model. This means more reduction of interface clutter.
- Model classes are now made `TypeChecked` by default. This can be disabled for single methods or the whole
  model using `@TypeChecked(TypeCheckingMode.SKIP)`.

- Javadocs
- renamed `makeTemplate` to `createAsTemplate` (see [#61](https://github.com/klum-dsl/klum-ast/issues/61))
- include source code pointer to base field for setter methods (see [#69](https://github.com/klum-dsl/klum-ast/issues/69))

## 0.97.5
- Fix: DelegatesTo annotations for polymorphic setters where not correct (see [#67](https://github.com/klum-dsl/klum-ast/issues/67))

## 0.97.2
- remove gdsl file from jar file for now. It's neither up to date, nor does it work with current IntelliJ IDEA versions

## 0.97.1
- Critical Bugfix: all methods were marked as deprecated
- Fix: PostCreate was not called on inner objects (see [#64](https://github.com/klum-dsl/klum-ast/issues/64))
- reduced to barebone hashcodes for models (constant 0 for non-Keyed, hash of the keye for keyed)
- lots of JavaDoc

## 0.97
- methods derived from a deprecated field are now themselves deprecated. Note that this has only an impact
  when using the compiled classes, the gdsl does currently not support creating deprecated methods 
  (see [#54](https://github.com/klum-dsl/klum-ast/issues/54))
- Allow more collection types instead of only `List` (i.e. `Set`, `SortedSet`, `Stack`, etc.). Custom collections
  are allowed, provided they have a constructor taking an Iterable, a custom coercion method or initial values.
- Allow SortedMap in place of Maps. Note that other Map implementations are currently NOT supported

## 0.96.1
- `createFromScript` was deprecated, without creating a matching `createFrom` method, corrected

## 0.96
No code changes, renamed project coordinates and URLs to new name

## 0.95
- `withTemplates` now supports Lists as argument
- `withTemplates` supports anonymous templates (actually, it always did, now there is a testcase and documentation for that)
- new `makeTemplate` method, which only creates template, without assigning it
- [Lifecycle methods](https://github.com/klum-dsl/klum-ast#Lifecycle-Methods) can be created using the `@PostCreate` and `@PostApply` annotations (see [#38](https://github.com/klum-dsl/klum-ast/issues/38))
- DSL classes are now `Serializable` (see [#35](https://github.com/klum-dsl/klum-ast/issues/35)) 
- `@Default` now also supports `delegate` members (see [#46](https://github.com/klum-dsl/klum-ast/issues/46))

## 0.20
- A new, explicit [Template Mechanism](https://github.com/klum-dsl/klum-ast#Template-Mechanism) (see [#34](https://github.com/klum-dsl/klum-ast/issues/34)) 

## 0.19
- Implemented [Default values](https://github.com/klum-dsl/klum-ast#Default-Values) (see [#29](https://github.com/klum-dsl/klum-ast/issues/29))
- Implemented better [Convenience Factories](https://github.com/klum-dsl/klum-ast#Convenience-Factories), (see [#33](https://github.com/klum-dsl/klum-ast/issues/33))

- __Deprecation__:
    - `createFromSnippet` has been renamed to simply `createFrom`, `createFromSnippet` will eventually be dropped.

## 0.18.1
- Named parameters should be available for inner objects as well

## 0.18.0
- __Breaking changes__:
  - removed the creation of `_create` and `_apply` if the original methods are already present. This will be replaced with a lifecycle mechanism (see [#38](https://github.com/klum-dsl/klum-ast/issues/38))
- new Features:
  - `create` and `apply` now support named parameters (`MyObject.create(value: 'x') {...`)  
- Closures for create method are now optional (This is especially useful in combination with named parameters) ([#20](https://github.com/klum-dsl/klum-ast/issues/20))
- Resolved a potential Stackoverflow in GDSL (which happened in a two step recursion, i.e. A contains B and B contains A)

## 0.17.0
- Breaking changes:
  - alternatives syntax has been dropped
  - named map elements have been dropped
  - the outer closure for collections is now optional
  - field member names (either implicit or explicit) must now be unique across the whole hierarchy
- transient fields are not enriched

## 0.16.1
- Convenience factory for URLs

## 0.16.0
- New Feature: [Convenience Factories](https://github.com/klum-dsl/klum-ast#Convenience-Factories)

## 0.15.2
- Fixed: validation of nested elements does not work ([#25](https://github.com/klum-dsl/klum-ast/issues/25))

## 0.15.1
- Fixed: Validation changes broke template behaviour. Previously, each call of `createTemplate` created a 
 new instance as the template. Now the new template was accidently created based on the previous
 template.

## 0.15.0
- New Feature: Validation (https://github.com/klum-dsl/klum-ast#validation)

## 0.14.13
- Fixed Compilation fails on final fields ([#21](https://github.com/klum-dsl/klum-ast/issues/21))
- Fixed Helper Methods for $TEMPLATE fields are created ([#22](https://github.com/klum-dsl/klum-ast/issues/22))

## 0.14.12
- allow fields to be marked as ignored using an annotation ([#18](https://github.com/klum-dsl/klum-ast/issues/18))

## 0.14.11
- apply did override values with template values. Fixed

## 0.14.10
- Shortcut syntax for boolean setters. Now the boolean setter can be called without argument, defaulting to
  true. (e.g. you could write "skipSonar()" instead of "skipSonar(true)")

## 0.14.9
- GDSL improvements
- Changed Retention of DSL annotations to class to allow extending DSLs from a jar (i.e. have a dependency
  on a jar containing parent DSL classes).

## 0.14.8
- created methods should **not** be synthetic to allow proper code completion when using precompiled DSLs.

## 0.14.7
- Bugfix: equals/hashcode did not take parent classes into account

## 0.14.6
- gdsl improvements

## 0.14.4
- no functional changes
- Fixed nested gdsl cases
- Reduced groovy dependency to 2.3

## 0.14.3
Changed Source/Target compatibility to 1.6

## 0.14.2
- Fixed a ClassCast Exception when reusing an Object in a different structure
- Owner fields are now set before applying the closure itself, allow to access the owner inside the closure
- Simple list field elements can now also be added using a list instead of varargs.

## 0.14.1
Fixed small typing bug with synthetic template class

## 0.14.0
- Allow templates for abstract classes

## 0.13.0
**Minor breaking change**
- Completely dropped `_use()` and `_reuse()` (they never looked nice anyway).
- Both are now replaced with an element adder method taking a single existing object.
- Owner is only overridden if not yet set (previously, `use() did set the owner, while `_reuse()` did not, attempting to override existing owner threw exception.


## 0.12.4
Fixed a StackOverflowError with which occurred when using Owner fields with inheritance 

## 0.12.3
NPE Guard in GDSL

## 0.12.2
No functional changes, only gdsl improvements
- alternatives
- typed closures

## 0.12.1
- Fixed a corner case of template inheritance
- Now, if Child extends Parent and a template is created for Parent only, 
  this template will also be applied to all created instances of Child

## 0.12.0
- **Breaking change**
- Major renaming of annotations:
  - DSLConfig -> DSL
- Owner and Keys are now decorated by own annotations
- Removed Ability to rename fields using the field annotation
- The ability to rename the inner values is retained.

## 0.11.0
- Introduced template objects to configure default values.

## 0.10.0
- reuse changes/improvements [#6](https://github.com/klum-dsl/klum-ast/pull/6)
  - `reuse()` renamed to `_reuse()`, does NOT set owner field
  - new `_use()` method which *does* set the owner field
  - overwriting the owner field content leads to `IllegalStateException`
  - inner collection closures (bars -> bar) now return the created object to allow easier reuse
  
## 0.9.5
- Fixed a StackOverflowError in `toString()` methods with owner fields 

## 0.9.4
- gdsl improvements
  - NPE Guards
  - Documentation for generated methods
  - Fixed most cases for single object closures

## 0.9.3
- Code refactorings (extracted method creating code to separate class `MethodBuilder`)
- Introduced owner fields
- Allow inheritance in DSLObjects
