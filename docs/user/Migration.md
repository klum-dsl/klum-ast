# Migration

## To 4.0

4.0 replaces the generated mutable RW object with a true Builder and materializes a completed, structurally immutable DSL Object graph before validation. Completed models no longer expose generated `apply`, owned composition cannot adopt already completed objects, lifecycle extensions are split at the new `INSTANTIATE` phase, and collection declarations now have explicit snapshot-safe limits. Templates now have persistent graph-wide recipe identity separate from ordinary models; marked Templates cannot be relationship values or ordinary Jackson export values, and deferred Builder actions cannot be scheduled at phase 40 or later. Jackson is an asymmetric external-format integration rather than Klum persistence and adds no wire metadata.

See the dedicated [Builder First Migration](Builder-First-Migration.md) guide for the complete migration checklist and compatibility breaks, plus
[Templates](Templates.md), [Copy Strategies](Copy-Strategies.md), and [Model Phases](Model-Phases.md) for materialization boundaries and [Jackson Integration](Jackson-Integration.md) for
foreign-data import and ordinary POJO export.

Validation callers must import and catch
`com.blackbuild.klum.ast.runtime.validation.KlumValidationException`. The former
`com.blackbuild.klum.ast.runtime.KlumValidationException` type has been removed; this is an intentional 4.0 source and
binary compatibility break.

### Template creation and scoped application

Create a reusable Template below `Create`, then apply it below `Template`:

(See: `TemplatesDocumentaryTest#'applies one scoped template to multiple service configurations'`.)

```groovy
def baseline = ServiceConfiguration.Create.Template.With(region: 'eu-central')

ServiceConfiguration.Template.With(baseline) {
    ServiceConfiguration.Create.With { }
}
```

`Foo.Create.Template(...)` and `Foo.Create.TemplateFrom(...)` have been removed so `Foo.Create.Template` can be the
unambiguous generated Factory property. Migrate them to `Foo.Create.Template.With(...)` and
`Foo.Create.Template.From(...)`. The older documented `Foo.Template.Create(...)` and `CreateFrom(...)` spellings remain
deprecated 4.x aliases; move new code to the canonical `Create.Template` chain. The best-effort
[Builder First migration helper](Builder-First-Migration.md#optional-mechanical-starting-point) recognizes only direct
type-qualified occurrences. Run it in a clean, version-controlled schema-module worktree, inspect its diff, then compile
and finish the checklist.

Code that explicitly named the generated scoped-application type must now use `Foo_DSL.TemplateScope` rather than
`Foo_DSL.Template`. This intentional 4.0 RC source and binary compatibility break has no alias: recompile schemas and
typed Java or static Groovy clients after changing the name. `Foo_DSL.Factory.Template` remains the distinct type for
the literal `Foo.Create.Template` root-creation field.

For a foreign YAML/JSON migration, configure one caller-owned Jackson mapper, import one input into one Builder lifecycle,
and treat the completed-model export as a separately owned external projection. Do not feed it back as Klum persistence or
use repeated imports as a Jackson-specific merge/layering mechanism; [#304](https://github.com/klum-dsl/klum-ast/issues/304)
owns source-neutral composition.

### Named modules and Groovy

Groovy 3 remains an ordinary-classpath configuration. Do not add a KlumAST
`module-info.java` to a Groovy 3 schema or compensate with JVM module flags.

Groovy 4 and 5 schemas may be named modules and must require
`org.apache.groovy`, the annotations/runtime modules, and `requires static
com.blackbuild.klum.ast.compiler`; add adapter modules only when used. A schema
that uses Jackson and Jakarta Bean Validation has this narrow descriptor shape:

```java
module example.schema {
    requires com.blackbuild.klum.ast.annotations;
    requires com.blackbuild.klum.ast.runtime;
    requires static com.blackbuild.klum.ast.compiler;
    requires com.blackbuild.klum.ast.jackson;
    requires com.blackbuild.klum.ast.validation.bean;
    requires org.apache.groovy;

    exports example.schema;
    opens example.schema to com.blackbuild.klum.ast.runtime,
        com.fasterxml.jackson.databind,
        org.hibernate.validator;
}
```

Keep the runtime opening for generated Builder lifecycle/materialization. Add
the Jackson and Hibernate Validator targets only when those adapters inspect
the schema; do not substitute `--add-reads`, `--add-exports`, or
`--patch-module` flags.

The Schema plugin validates this user-owned descriptor through
`validateKlumSchemaModule`, which is part of `check` and Maven publication. It
reports copyable missing `requires` and qualified `opens` directives but never
rewrites `module-info.java`. The generated `Foo_DSL` mirrors used for IntelliJ
completion are not module sources, compiler inputs, or publication inputs.

Recompile schemas and clients for 4.0. Java-serialized 3.x graphs are not 4.0
migration inputs, and Java serialization is not a cross-version persistence
format; own external compatibility data and migrations in the Schema.

### Custom validation reporting

`Validator` and `ValidatorBase` are removed in 4.0; there is no source or binary compatibility adapter. In lifecycle
methods, lifecycle closures, and validation classes, replace their shortcuts with
`KlumSchemaSupport.getKlumValidation()` (Groovy's `klumValidation` property) and its `error`, `errorAt`, `issue`,
`issueAt`, `suppressOn`, or `suppressAll` operations. Use
`KlumSchemaSupport.klumValidationForObject(target)` only when a lifecycle callback must report an issue on an explicit
object. It retains that object's construction path and does not alter lifecycle ordering. The separate #406 compiler
restriction remains a later placement check; it is not a migration fallback.

## KlumCast 0.4 final dependencies

KlumAST 4.0 uses the immutable [KlumCast `0.4.0` artifact set](https://github.com/klum-dsl/klum-cast/releases/tag/v0.4.0):
`klum-cast-annotations`, `klum-cast-spi`, and
`klum-cast-compile`. Its stable automatic module names are `com.blackbuild.klum.cast.annotations`,
`com.blackbuild.klum.cast.spi`, and `com.blackbuild.klum.cast.compiler`; do not substitute filename-derived names or use
local module-path flags to compensate for an invalid dependency graph.

Recompile schemas and custom checks when moving to KlumAST 4.0. KlumAST's built-in name-bound checks use KlumCast's
durable stateless `Check` SPI and report structured, source-positioned diagnostics. Custom checks must implement that SPI;
the deprecated compatibility adapter is only a temporary migration aid for external consumers ([#460](https://github.com/klum-dsl/klum-ast/issues/460)).

## To 2.2

`toString()` methods are not automatically generated anymore, to restore the old behavior, add the `@ToString` annotation to the classes.
`manualValidation` has been dropped, as it does not work with stackable issues. This feature can be simulated by either downgrading the issues on the object at hand or skipping the Verify phase and handling errors manually

## To 2.1

It is strongly advised to first update to 2.0 and the to 2.1. 

2.1 drops all deprecated methods of 2.0. Since they are documented, replacing them with their new counterparts should be straightforward.

## To 2.0

The sections below describe historical migration steps and may show APIs, such as completed-model `apply`, that were
subsequently removed in 4.0. Apply the historical migration first, then follow [Builder First Migration](Builder-First-Migration.md).

## Validation now throws KlumValidationException

which wraps `KlumValidationResult`s for the validated objects, each containing the relevant `KlumValidationIssue`s. Previously, an AssertionError was thrown, so calling code might need to be adjusted.

## Multiple Inner Create Calls on the Same Field (or Key in a Map Field) Now Stack Instead of Replacing

Previously, multiple calls to the same inner create method would replace the previous value. 

```groovy
Foo.Create.With {
    bar {
        value = 1
        anotherValue = 2
    }
    bar {
        anotherValue = 3
    }
}
```

Previously, the second call of "bar" would result in a new object, i.e. the object created by the first call would be
replaced, resulting in:

```groovy
foo.bar.anotherValue == 3
foo.bar.value == null
```

Now those calls stack, so the result would be:

```groovy
foo.bar.anotherValue == 3
foo.bar.value == 1
```

This is especially useful for using deep templates to set the first object:

```groovy
def template = Foo.Create.Template {
    bar {
        value = 1
        anotherValue = 2
    }
}

Foo.withTemplate(template) {
  Foo.Create.With {
      bar {
          anotherValue = 3
      }
  }
}
```

If the existing object does not match the new object (either because a different key is provided or specific type is given that is
different from the existing type), an Exception is thrown. In that case, the behaviour can be explicitly overridden by using
either apply or a setter (to explicitly merge or replace):

```groovy
Foo.withTemplate(template) {
  Foo.Create.With {
      bar.apply { // explicitly force merge
          anotherValue = 3
      }
  }
}
```

or

```groovy
Foo.withTemplate(template) {
  Foo.Create.With {
      bar = Bar.Create.With { // explicitly force overwrite
          anotherValue = 3
      }
  }
}
```

## Default Values are actually set, not only returned

This makes objects used in copyFrom behave differently. Previously, the copyFrom methode explicitly ignored default values,
now they would be copied as well if already set. This can lead to different results if the copy source a) is not a template
object and b) was created outside the current phase run.

If the object was created outside the phase run, it will most likely be a template, so using the `Create.Template()` 
creator method (or the deprecated `createAsTemplate()` method) will lead to the same result as before.

## Owners are now set in the owner phase

Previously, they have been set before apply was called, so `apply` had already access to the owner, which could be
used in separate scripts. Any logic accessing the owner must be placed in a later phase (for example AutoLink or PostTree).

This also holds true for methods using default values populated by the owner.

`PostApply` methods accessing the owner must also be move to a later phase (or split).

## Layer 3 `@LinkTo` relationships

In 4.0, `@LinkTo` is an `OPTIONAL_LINK` relationship by default. A local Builder created in the current construction
session remains owned composition; an already owned Builder or a completed DSL Object is an aggregation target. This
preserves the usual Layer 3 pattern in which a local value overrides an Auto-Link fallback.

Use `@Field(FieldType.LINK) @LinkTo` when the field must be aggregation-only. Ordinary relationships remain
composition-only and reject completed or already claimed Builders. Custom `@AutoLink` code that previously overwrote a
configured value must use `builder.link(fieldName, target)` for an explicit non-destructive fallback instead.

## Deprecation: Validation annotation -> Validate

`@Validation.mode()` is replaced by phases and thus ignored. `Validation.Option.IGNORE_UNMARKED` is default anyway, so
the only useful variation of the annotation is `@Validation(option=VALIDATE_UNMARKED)`, which is replaced by `@Validate`
on class level.

## Deprecation: Factory methods -> Factory class

All static factory methods on DSL classes are deprecated in favor of a single `Create` class field which encapsulates all
relevant factory methods.

The following factory calls should be renamed:

| Old                            | New                             |
|--------------------------------|---------------------------------|
| `Foo.create()`                 | `Foo.Create.One()`              |
| `Foo.create(...)`              | `Foo.Create.With(...)`          |
| `Foo.createFrom(...)`          | `Foo.Create.From()`             |
| `Foo.createAsTemplate(...)`    | `Foo.Create.Template(...)`      |
| `Foo.createFromClasspath(...)` | `Foo.Create.FromClasspath(...)` |


NOTE that in addition to `.One()` for empty factory calls, `.With()` is also working, but since it makes for a strange
sounding call is deprecated and only present to allow a simple search and replace.

## Dependency changes
For 2.0, the single klum-ast dependency is replaced by two KlumAST is split into three distinct jars:

### `klum-ast-annotations`

Does not usually need to be addressed directly except in very special cases, since it is a dependency of both of
the other jars.

### `klum-ast`

Contains the actual AST transformations, i.e. the core of KlumAST. These need to be present during compile-time only
and need not be present on runtime (usually it should be safe if they are).

### `klum-ast-runtime`

Contains classes needed during runtime.

### `compileOnly` vs. Runtime Scope

Since klum-ast now relies on a runtime component, a schema now should have two separate dependencies, `klum-ast` as 
`compileOnly` (`provided` for Maven) and and `klum-ast-runtime` as `api`  (`runtime` for Maven), i.e.:

```groovy
dependencies {
  compileOnly 'com.blackbuild.klum.ast:klum-ast:<klum-version>'
  implementation 'com.blackbuild.klum.ast:klum-ast-runtime:<klum-version>'
}
```

or

```xml
<dependencies>
  <dependency>
    <groupId>com.blackbuild.klum.ast</groupId>
    <artifactId>klum-ast</artifactId>
    <version>...</version>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>com.blackbuild.klum.ast</groupId>
    <artifactId>klum-ast-runtime</artifactId>
    <version>...</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

## To 1.2

## DelegateOnly Strategy for closures

Closures are all `DelegateOnly` instead of the previous `DelegateFirst`. This means that you cannot access
methods of an outer object directly (which would not be very intuitive). If you need this functionality,
you need to access the outer object directly, using the `owner` property of `Closure`, an `@Owner` field
of the outer instance or a local variable pointing to the targeted instance.

Instead of:

```groovy
Foo.create {
    bar {
        methodInFoo()
    }
}
```

Write instead:

```groovy
Foo.create {
    bar {
        owner.methodInFoo() // owner is property of Closure
    }
}
```

Note that naming an `@Owner` field actually `owner` leads to the field being overshadowed
by the owner field of the closure. While this is usually not a problem, it might cause failures
when used inside a Collection-Factory:

```groovy
@DSL
class Bar {
  @Owner
  Foo owner
}

Foo.create {
    bars {
        bar {
            // owner points to the owner of the closure, i.e. the collection factory, so this will fail:
            owner.doSomething()
        }
    }
}
```

Calling the owner field (or any other field) actually `owner` leads to a compiler warning (which might
eventually be replace with a compiler error). Consider using a more domain specific field name like
`graph` or `parent` is advisable.


## Breaking changes since 0.98

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

## Breaking changes since 0.17

the following features were dropped:
- pre using existing `create` and `apply` methods is no longer supported, this has been replaced by a lifecycle mechanism 
  ([#38](https://github.com/klum-dsl/klum-core/issues/38)), see [Basics#lifecycle-methods](Basics.md#lifecycle-methods)
- named alternatives for dsl collections
- shortcut named mappings
- under the hood: the inner class for dsl-collections is now optional (GDSL needs to be adapted)
- member names must now be unique across hierarchies (i.e. it is illegal to annotate two collections with the same
  members value)
- the implicit template feature is deprecated and will eventually be dropped (see [#34](https://github.com/klum-dsl/klum-core/issues/34)), 
  it basically uses global variables, which is of course bad design
  
  The suggested way to use templates would be to explicitly call copyFrom() as first step in a template using configuration
  or using the new named parameters (`Model.create(copyFrom: myTemplate) {..}`)
  
  Alternatively, the new `withTemplate(s)` mechanism can be used (see [Template Mechanism](Templates))
