## 0.96
No code changes, renamed project coordinates and URLs to new name

## 0.95
- `withTemplates` now supports Lists as argument
- `withTemplates` supports anonymous templates (actually, it always did, now there is a testcase and documentation for that)
- new `makeTemplate` method, which only creates template, without assigning it
- [Lifecycle methods](https://github.com/blackbuild/config-dsl#Lifecycle-Methods) can be created using the `@PostCreate` and `@PostApply` annotations (see [#38](https://github.com/blackbuild/config-dsl/issues/38))
- DSL classes are now `Serializable` (see [#35](https://github.com/blackbuild/config-dsl/issues/35)) 
- `@Default` now also supports `delegate` members (see [#46](https://github.com/blackbuild/config-dsl/issues/46))

## 0.20
- A new, explicit [Template Mechanism](https://github.com/blackbuild/config-dsl#Template-Mechanism) (see [#34](https://github.com/blackbuild/config-dsl/issues/34)) 

## 0.19
- Implemented [Default values](https://github.com/blackbuild/config-dsl#Default-Values) (see [#29](https://github.com/blackbuild/config-dsl/issues/29))
- Implemented better [Convenience Factories](https://github.com/blackbuild/config-dsl#Convenience-Factories), (see [#33](https://github.com/blackbuild/config-dsl/issues/33))

- __Deprecation__:
    - `createFromSnippet` has been renamed to simply `createFrom`, `createFromSnippet` will eventually be dropped.

## 0.18.1
- Named parameters should be available for inner objects as well

## 0.18.0
- __Breaking changes__:
  - removed the creation of `_create` and `_apply` if the original methods are already present. This will be replaced with a lifecycle mechanism (see [#38](https://github.com/blackbuild/config-dsl/issues/38))
- new Features:
  - `create` and `apply` now support named parameters (`MyObject.create(value: 'x') {...`)  
- Closures for create method are now optional (This is especially useful in combination with named parameters) ([#20](https://github.com/blackbuild/config-dsl/issues/20))
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
- New Feature: [Convenience Factories](https://github.com/blackbuild/config-dsl#Convenience-Factories)

## 0.15.2
- Fixed: validation of nested elements does not work ([#25](https://github.com/blackbuild/config-dsl/issues/25))

## 0.15.1
- Fixed: Validation changes broke template behaviour. Previously, each call of `createTemplate` created a 
 new instance as the template. Now the new template was accidently created based on the previous
 template.

## 0.15.0
- New Feature: Validation (https://github.com/blackbuild/config-dsl#validation)

## 0.14.13
- Fixed Compilation fails on final fields ([#21](https://github.com/blackbuild/config-dsl/issues/21))
- Fixed Helper Methods for $TEMPLATE fields are created ([#22](https://github.com/blackbuild/config-dsl/issues/22))

## 0.14.12
- allow fields to be marked as ignored using an annotation ([#18](https://github.com/blackbuild/config-dsl/issues/18))

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
- reuse changes/improvements [#6](https://github.com/blackbuild/config-dsl/pull/6)
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
