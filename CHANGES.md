0.10.0
======
- reuse changes/improvements [#6](https://github.com/blackbuild/config-dsl/pull/6)
  - `reuse()` renamed to `_reuse()`, does NOT set owner field
  - new `_use()` method which *does* set the owner field
  - overwriting the owner field content leads to `IllegalStateException`
  - inner collection closures (bars -> bar) now return the created object to allow easier reuse
  
0.9.5
=====
- Fixed a StackOverflowError in `toString()` methods with owner fields 

0.9.4
=====
- gdsl improvements
  - NPE Guards
  - Documentation for generated methods
  - Fixed most cases for single object closures

0.9.3
=====
- Code refactorings (extracted method creating code to separate class `MethodBuilder`
- Introduced owner fields
- Allow inheritance in DSLObjects