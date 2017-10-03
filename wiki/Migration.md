# Breaking changes since 0.98

- Models are now read-only. That means changes to fields can only be done:

  - inside an apply or create block
  - inside a lifecycle method
  - Note that it is still possible to some extend to change code from methods inside the class, however this
    is strongly discouraged. It is planned to include a way to mark mutation methods with an annotation. Such methods
    should be automatically moved to RW as well.
  If you access mutators from outside of these methods, you will get compiler errors upon updating to KlumAST 0.98+. In 
  that case, surround the offending code with an apply closure.
    
  Before:
  ```groovy
  model.value('bla')
  model.name = 'Hans'
  ```

  After:
  ```groovy
  model.apply {
    value('bla')
    name = 'Hans'
  }
  ```

# Breaking changes since 0.17

the following features were dropped:
- pre using existing `create` and `apply` methods is no longer supported, this has been replaced by a lifecycle mechanism 
  ([#38](https://github.com/klum-dsl/klum-core/issues/38)), see [[Basics#lifecycle-methods]]
- named alternatives for dsl collections
- shortcut named mappings
- under the hood: the inner class for dsl-collections is now optional (GDSL needs to be adapted)
- member names must now be unique across hierarchies (i.e. it is illegal to annotate two collections with the same
  members value)
- the implicit template feature is deprecated and will eventually be dropped (see [#34](https://github.com/klum-dsl/klum-core/issues/34)), 
  it basically uses global variables, which is of course bad design
  
  The suggested way to use templates would be to explicitly call copyFrom() as first step in a template using configuration
  or using the new named parameters (`Model.create(copyFrom: myTemplate) {..}`)
  
  Alternatively, the new `withTemplate(s)` mechanism can be used (see [Template Mechanism](wiki/Template-Mechanism))