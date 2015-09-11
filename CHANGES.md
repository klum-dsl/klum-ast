0.10.0
======
- reuse changes/improvements #6
  - `reuse()` renamed to `_reuse()`, does NOT set owner field
  - new `_use()` method which *does* set the owner field
  - overwriting the owner field content leads to `IllegalStateException`
  - inner collection closures (bars -> bar) now return the created object to allow easier reuse
  
0.9.5
=====
- Fixed a StackOverflowError in toString() methods with owner fields 
