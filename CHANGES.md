# 2.0.0 (unreleased)
## New Features
- New Field Type: BUILDER: Getters are protected or private in model, but dsl methods are public
- Compatibility with Groovy 3 and 4. KlumAST is currently still built with Groovy 2.4 (for compatibility with Jenkins). Tests are run with Groovy 3 and 4 as well.
- Replace basic jackson transformation with a dedicated (beta) JacksonModule (see [Jackson Integration](https://github.com/klum-dsl/klum-ast/wiki/Migration))).
- First steps for Layer3 models. (see [Layer3](https://github.com/klum-dsl/klum-ast/wiki/Layer3))    
- Split model creation into distinct phases (see [#156](https://github.com/klum-dsl/klum-ast/issues/156), [#155](https://github.com/klum-dsl/klum-ast/issues/155),[#187](https://github.com/klum-dsl/klum-ast/issues/187) and [Model Phases](https://github.com/klum-dsl/klum-ast/wiki/Model-Phases))
- New Phases:
  - PostTree: is run after the model is completely realized ([#280](https://github.com/klum-dsl/klum-ast/issues/280)
  - AutoCreate: automatic creation of null fields ([#275](https://github.com/klum-dsl/klum-ast/issues/275)
  - Owner: Sets owners and calls owner methods ([#284](https://github.com/klum-dsl/klum-ast/issues/284))
  - AutoLink: Links fields to other fields in the model ([#275](https://github.com/klum-dsl/klum-ast/issues/275)
  - Defaults: sets default values ([#196](https://github.com/klum-dsl/klum-ast/issues/196)
  - Validate: Validation of the model
- In addition to lifecycle methods, fields of type `Closure` can now be used to define model provided (instead of schema provided) lifecycle methods. These closures will be executed in their respective Lifecycle phases.
- default implementation: by providing the attribute `defaultImpl` on either `@DSL` or `@Field`, one can allow the creation of non-polymorphic field methods even for interfaces and abstract types. (see [Default Implementations](https://github.com/klum-dsl/klum-ast/wiki/Basics#default-implementations))
- Creator methods have been moved to a separate creator class (see [#76](https://github.com/klum-dsl/klum-ast/issues/76)), creator methods on the model class have been deprecated (see [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration)). 
- Custom creator classes can be provided (see [Factory Classes](https://github.com/klum-dsl/klum-ast/wiki/Factory-Classes))
- Methods of creator classes (including custom creators) are included in collection factories (see [#300](https://github.com/klum-dsl/klum-ast/issues/300) and [Factory Classes](https://github.com/klum-dsl/klum-ast/wiki/Factory-Classes#Creator-methods-and-collection-factories))
- Creator class also supports creating templates from scripts (files or URLS) (see [Templates](https://github.com/klum-dsl/klum-ast/wiki/Templates) and [#322](https://github.com/klum-dsl/klum-ast/issues/322))
- Switch annotation validation to [KlumCast](https://github.com/klum-dsl/klum-cast) Framework (see [#312](https://github.com/klum-dsl/klum-ast/issues/312)))
- Generate Documentation for almost all generated methods via [AnnoDocimal](https://github.com/blackbuild/anno-docimal) (see [#197](https://github.com/klum-dsl/klum-ast/issues/197)))
- [Gradle Plugin](https://github.com/klum-dsl/klum-ast/wiki/Gradle-Plugins) for easier project setup
- Various owner improvement:
  - Owner targets now can be transitive, i.e. be filled with the value of an ancestor of the specified type (instead of the direct owner) (see [Transitive Owners](https://github.com/klum-dsl/klum-ast/wiki/Basics#transitive-owners) and [#49](https://github.com/klum-dsl/klum-ast/issues/49))
  - Ower fields can be filled with the actual root of the model. This works even if no explicit owner field is present (see [Root Owner](https://github.com/klum-dsl/klum-ast/wiki/Basics#root-owners))
  - Owner objects can be converted before handing them to owner fields or methods (see [Owner Converters](https://github.com/klum-dsl/klum-ast/wiki/Basics#owner-converters) and [#189](https://github.com/klum-dsl/klum-ast/issues/189))
  - New `@Role` annotation to infer the name of the owner field containing an object (see [Role fields](https://github.com/klum-dsl/klum-ast/wiki/Layer3#role-fields) and [#86](https://github.com/klum-dsl/klum-ast/issues/86))
- Overwrite strategies for `copyFrom` and templates (see [Copy Strategies](https://github.com/klum-dsl/klum-ast/wiki/Copy-Strategies), [#309](https://github.com/klum-dsl/klum-ast/issues/309), [#348](https://github.com/klum-dsl/klum-ast/issues/348))
- Multiple calls to a single object closure now configure the same object instead of completely overriding the previous field, the same for map entries using the same key. (see [#325](https://github.com/klum-dsl/klum-ast/issues/325)). While this is a more natural behaviour, it might break existing code in some corner cases, see [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration)).
- Breadcrumbs: each Method or closures encountered while creating model is used to setup a breadcrumb path. This path is used in exceptions to identify the location of the problem in the scripts, which is especially handy when the model is split over various scripts. (see [Exception Handling](https://github.com/klum-dsl/klum-ast/wiki/Exception-Handling) and [#264](https://github.com/klum-dsl/klum-ast/issues/264))
- Rework exception handling as a whole, this includes a new hierarchy of exceptions (see [Exception Handling](https://github.com/klum-dsl/klum-ast/wiki/Exception-Handling)) which contain relevant information about the phase in which the exception occured as well as the object which caused the exception. This is especially useful for validation exceptions. (see [#149](https://github.com/klum-dsl/klum-ast/issues/149) and [#288](https://github.com/klum-dsl/klum-ast/issues/288))
- Validations are now all executed, even if exceptions are encountered. All violations are aggregated into a single `KlumValidationException` which is thrown at the end of the phase (see [#146](https://github.com/klum-dsl/klum-ast/issues/146))
- New `FromMap` factory to allow a "poor man's deserialization" (see [Convenience Factories](https://github.com/klum-dsl/klum-ast/wiki/Convenience-Factories#Map) and [#359](https://github.com/klum-dsl/klum-ast/issues/359)) 
- DefaultValues annotations provide an annotation based way to set default values in Layer3 scenarios (see [Default Values](https://github.com/klum-dsl/klum-ast/wiki/Default-Values#DefaultValues-annotation) and [#361](https://github.com/klum-dsl/klum-ast/issues/361))
- `@Cluster`-Fields create now a factory closure for that field, containing only the cluster members (see [#365](https://github.com/klum-dsl/klum-ast/issues/365))

## Improvements
- Creator classes also support methods creating multiple instances at once (see [#319](https://github.com/klum-dsl/klum-ast/issues/319))
- CopyFrom now creates deep clones (see [#36](https://github.com/klum-dsl/klum-ast/issues/36))
- `boolean` fields are never validated (makes no sense), `Boolean` fields are evaluated against not null, not against Groovy Truth (i.e. the field must have an explicit value assigned) (see [#223](https://github.com/klum-dsl/klum-ast/issues/223))
- Provide `@Required` as an alternative to an empty `@Validate` annotation (see [#221](https://github.com/klum-dsl/klum-ast/issues/221))
- `EnumSet` fields are now supported. Note that for enum sets a copy of the underlying set is returned as opposed to a readonly instance. (see [#249](https://github.com/klum-dsl/klum-ast/issues/249))
- Converter methods are now honored for Alternatives methods as well. (see [#270](https://github.com/klum-dsl/klum-ast/issues/270))
- `@Validate` now can be placed on classes. This effectively replaces `@Validate(option=Validation.Option.VALIDATE_UNMARKED)`, which is internally converted to the new format (see [#276](https://github.com/klum-dsl/klum-ast/issues/276)). The `@Validation` annotation is deprecated.
- Sanity check: Key Fields must not have `@Owner` or `@Field` annotations.
- Selector members for `@LinkTo` annotations allows to determine the link source from the provider based on the value of another field (see [#302](https://github.com/klum-dsl/klum-ast/issues/302))
- @LinkTo now correctly handles empty collections/maps as target
- Allow a custom key-provider function for `createFrom(URL)` and `createFrom(File)` 
- `@Cluster` can also be placed on fields, which will be converted into a getter method (see [#366](https://github.com/klum-dsl/klum-ast/issues/366))
- `@Cluster` can be combined with `@AutoCreate` to auto create all members of the annotated cluster (see [#363](https://github.com/klum-dsl/klum-ast/issues/363))

## Deprecations (see [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration)):
  - The `@Validation` annotation is deprecated. Use `@Validate` on class level instead.
  - creator methods on the model class have been deprecated.
- Breaking changes (see [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration))
- it is a compile error to place the `@Validate` annotation on a boolean field.
- KlumAST is split into different modules, klum-ast-compile is compile-time only,
  klum-ast-runtime is needed for runtime as well. This completes
  the changes started in 1.2.0
- In order for the serialization in jackson to work, the new klum-ast-jackson module needs to be included in the project (see [Jackson Integration](https://github.com/klum-dsl/klum-ast/wiki/Jackson-Integration))
- The naming of virtual fields is changed, now the virtual field
  is identical to the method name (previously, the first element of the camel
  cased method name was removed).
- methods named `doValidate` are no longer considered Validate methods by default.
- Static Type Checking for Configuration Scripts does not (yet) work under Groovy 3
- The `@Validation` annotation is deprecated, any use except for `@Validate(option=Validation.Option.VALIDATE_UNMARKED)` will have no effect.
- Previously, only public methods were checked for illegal write access. This has been changed to include all visibilities. Protected methods that are conceptually write access methods must now also be annotated with @Mutator, otherwise a compile error is thrown.
- The generated `validate()` method is now deprecated, use `KlumInstanceProxy.validate()` instead. This means that creating own `validate()` methods is legal again.
- Owner fields are now set in a later phase, meaning that they are not yet set when apply closures are resolved. This logic must be moved to a later phase (postTree), for example using lifecycle closures.
- Default values are no longer a modification of the getter but rather explicitly set during the 'default' phase. This might result in subtle differences in the behavior, especially when using a non-template as template / target for
 `copyFrom`. Make sure to create template instances with `Create.Template` if you want to use them as templates.
- `withTemplates(Map, Closure)` now only accepts anonymous templates, i.e. the signature changed from `withTemplates(Map<Class, Object>, Closure)` to `withTemplates(Map<Class, Map<String, Object>, Closure)`. Calls using concrete templates now must use `withTemplates(List<Object>, Closure)` instead.

## Fixes
- since rc.54
  - `Required.value()` is correctly translated to `Validate.message()` (see [#373](https://github.com/klum-dsl/klum-ast/issues/373))
  - CopyHandler should ignore field type IGNORED (see [#374](https://github.com/klum-dsl/klum-ast/issues/374))
- since rc.51
  - root objects of the wrong type should silently be ignored. This allows partial models to be created, which is especially
    important for testing.
- since rc.40
  - unqualified links in KlumFactory's javadoc lead to javadoc failures
  - gradle model plugin used the wrong class in the model descriptor (model class instead of script class)
  - ClusterModel filtering against annotations did not always work due to the actual Field object not being retrieved
- since rc.39
  - correctly determine the script name if the filename contains multiple "." (see [#328](https://github.com/klum-dsl/klum-ast/issues/328))
- since rc.33
  - Make RW classes public, not protected. Otherwise, static type checking can fail if owner and child are in different packages 
- since rc.32
  - new AnnoDocimal version ([Fix for inner enum final modifier](https://github.com/blackbuild/anno-docimal/issues/31))
- since rc.31
  - Don't copy Overrides annotation to RW delegation methods (see [#340](https://github.com/klum-dsl/klum-ast/issues/340))
- since rc.13
  - Fix polymorphic virtual setters (see [#250](https://github.com/klum-dsl/klum-ast/issues/250))
  - Converter methods should honor default values (see [#268](https://github.com/klum-dsl/klum-ast/issues/268))
- since rc.12
  - More fixes to nested generics (see [#248](https://github.com/klum-dsl/klum-ast/issues/248))
- since rc.11
  - Converter methods not working for maps of DSL objects (see [#242](https://github.com/klum-dsl/klum-ast/issues/242))
  - Created class is invalid if field type is generic and contains generic factories (like `List.of`) (see [#243](https://github.com/klum-dsl/klum-ast/issues/243))
  - Default delegate failed if delegate was a getter instead of a real field (see [#244](https://github.com/klum-dsl/klum-ast/issues/244))
  - `apply` did not accept a Map only call (see [#241](https://github.com/klum-dsl/klum-ast/issues/241))
- since rc.9
  - Handling of key fields in hierarchies (see [#238](https://github.com/klum-dsl/klum-ast/issues/238))
- since rc.5
  - Visibility for creator methods was wrong (see [#232](https://github.com/klum-dsl/klum-ast/issues/232))
    
## Dependency changes/Internal
- since rc.14
  - Update Jackson to 2.13.3 (see [#260](https://github.com/klum-dsl/klum-ast/issues/260))

