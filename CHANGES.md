## 0.14.11
- apply did override values with template values. Fixed

## 0.14.10
- Shortcut syntax for boolean setters. Now the boolean setter can be called without argument, defaulting to
  true. (e.g. you yould write "skipSonar()" instead of "skipSonar(true)")

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
