# #391 JP-1b real named-schema fixture evidence

This record is fresh positive evidence from the JP-1b fixture. It does not
reuse the historical blocked-evidence commit `8f1f66e5`.

## Proven contract

`JpmsPackageBoundaryTest` builds a consumer-owned `fixture.schema` from a real
`@DSL` Groovy source plus separate Java and `@CompileStatic` Groovy consumers.
The ordinary classpath fixture runs in every Groovy lane. It proves annotation
transformation, generated `Station.Create.One()` factories, protected Builder
`@PostCreate`/`@PostTree` callbacks, Builder Materialization, validation,
runtime `PhaseAction` and Bean Validation `InstanceValidator` service loading,
Jackson export, and static-Groovy factory consumption.

Groovy 3 proves that ordinary classpath contract only. Groovy 4 and 5 additionally
compile with the named `org.apache.groovy` compiler module, package and inspect
the user-owned schema descriptor, and execute the Java and static-Groovy
consumers as named modules.

The named descriptor is intentionally exact:

```java
opens fixture.schema to com.blackbuild.klum.ast.runtime,
    com.fasterxml.jackson.databind,
    org.hibernate.validator;
```

The runtime opening supports generated Builder lifecycle and materialization;
Jackson Databind owns its export reflection; Hibernate Validator owns field
constraint reflection. The Bean Validation adapter declares its non-transitive
ClassMate runtime dependency and `requires com.fasterxml.classmate`, so
module-path consumers resolve the provider without a local launch workaround.

The fixture scans every generated schema class for
`com.blackbuild.klum.ast.runtime.internal` references, checks the public
template-adapter/factory visibility and constructors, and retains the protected
lifecycle-method shape. Every named command rejects `--add-reads`,
`--add-exports`, and `--patch-module` (including equals forms).

## Validation

All focused lanes pass:

```text
./gradlew :klum-ast:test --tests com.blackbuild.klum.ast.JpmsPackageBoundaryTest
./gradlew :klum-ast:groovy4Tests --tests com.blackbuild.klum.ast.JpmsPackageBoundaryTest
./gradlew :klum-ast:groovy5Tests --tests com.blackbuild.klum.ast.JpmsPackageBoundaryTest
BUILD SUCCESSFUL
```

This completes JP-1b's real-schema acceptance evidence for ADR 0014. It does
not authorize broad package moves, additional exports, or general module
migration work.
