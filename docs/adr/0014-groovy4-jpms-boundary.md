# Groovy 4/5 JPMS boundary and final package ownership

Date: 2026-07-27

Status: Accepted

Tracking issue: [#391 — Define Java modules and finalize public packages for 4.0](https://github.com/klum-dsl/klum-ast/issues/391)

Implementation plan: [ADR 0014 implementation plan](../implementation/adr-0014-groovy4-jpms-boundary.md)

Related decisions: [ADR 0005 — generated DSL support API](0005-generated-dsl-support-api.md),
[ADR 0008 — phase registration](0008-phase-registration.md),
[ADR 0010 — framework public-interface conventions](0010-framework-public-interface-conventions.md), and
[ADR 0011 — shared multi-Groovy compatibility contract](0011-shared-multi-groovy-compatibility-contract.md).

## Context

KlumAST 4.0 must remove the legacy `com.blackbuild.groovy.configdsl` namespace,
eliminate split packages, and prevent JPMS consumers from depending on accidental
runtime implementation types. The fixed artifact and module identities are:

| Artifact | JPMS module |
| --- | --- |
| `klum-ast-annotations` | `com.blackbuild.klum.ast.annotations` |
| `klum-ast-runtime` | `com.blackbuild.klum.ast.runtime` |
| `klum-ast` | `com.blackbuild.klum.ast.compiler` |
| `klum-ast-jackson` | `com.blackbuild.klum.ast.jackson` |
| `klum-ast-bean-validation` | `com.blackbuild.klum.ast.validation.bean` |

The #391 tracer proved two constraints. Current compiler/runtime artifacts have
split `validation` and `util.layer3` packages, so they cannot form one module
layer. More importantly, a named module that directly uses Groovy must read its
actual module: Groovy 3 is `org.codehaus.groovy`, while Groovy 4 and 5 are
`org.apache.groovy`. A descriptor cannot express those as one alternative
dependency, and a consumer cannot donate its own read edge at runtime. The
reproducible evidence is in [the #391 preflight](../implementation/evidence/issue-391-jpms-preflight.md).

The artifact set remains fixed. Creating Groovy-specific artifacts or keeping
automatic modules would either require a new artifact decision or expose every
implementation package. Neither is appropriate for 4.0.

## Decision

### Compatibility boundary

KlumAST 4.0 ships explicit descriptors for the five modules above. Named-module
support is a Groovy 4/5 contract and descriptors read `org.apache.groovy` where
the owning artifact directly uses Groovy.

Groovy 3 remains a supported 4.x classpath configuration. The production
artifact, baseline `test` lane, generated DSLs, adapters, and ordinary schema
projects continue to support it. A Groovy 3 schema must not declare a
`module-info.java` for KlumAST use; the schema plugin reports this with a
copyable classpath-only remediation. No local JVM flags, descriptor rewrite,
or alternate Groovy artifact is used to make that combination appear modular.

Groovy 4 and 5 retain separate test lanes and both must pass every named-module
fixture. Dropping Groovy 3 entirely is a future major-version decision, not a
side effect of this boundary.

### Package ownership and exports

Every package has one owning artifact. The final import map is generated from
the #468 inventory before the first production move; no forwarding aliases are
retained. Its target boundary is:

| Owning module | Generally exported packages | Non-exported package family |
| --- | --- | --- |
| annotations | `com.blackbuild.klum.ast`, `.copy`, `.layer3` | `com.blackbuild.klum.ast.internal…` |
| runtime | `com.blackbuild.klum.ast.runtime`, `.runtime.validation` | `com.blackbuild.klum.ast.runtime.internal…` |
| compiler | none | `com.blackbuild.klum.ast.compiler.internal…` |
| Jackson | `com.blackbuild.klum.ast.jackson` | `com.blackbuild.klum.ast.jackson.internal…` |
| Bean Validation | `com.blackbuild.klum.ast.validation.bean` | `com.blackbuild.klum.ast.validation.bean.internal…` |

The annotations root contains schema-authoring annotations, while copy policy
and Layer 3 annotations receive focused public subpackages. Runtime's root
package contains only the client, generated-hook, and permitted extension
types from #468; validation has its own public package. `KlumObjectSupport`,
`KlumBuilder`, generated factory descriptors, validation result types, and the
allowed phase/model extension types move into those exported packages.

Compiler transformations, companions, proxies, lifecycle mechanics,
reflection, traversal, generated implementations, Jackson modifiers, and
Builder-only support are internal. The compiler and Jackson adapter receive
only the specific qualified runtime-internal exports that their checked bytecode
requires. They are implementation linkage, not a third-party SPI.

`BuilderVisitingPhaseAction` and its `InternalKlumBuilder` parameter are not a
general named-module extension seam in 4.0. Existing classpath providers retain
their bounded compatibility path. A future public Builder-phase protocol must
be additive, have a concrete consumer, and carry its own named-module fixture.
Klum-Wrap is completely separate and is not a qualified-export consumer.

### Services and schema descriptors

The runtime descriptor declares `uses` for phase actions and instance
validators and `provides` its built-in implementations. The Bean Validation
module provides `InstanceValidator`; the Jackson module provides the Jackson
`Module`.

KlumAST compiler transformations are annotation-triggered local transforms,
not global `META-INF/services` transforms. For Groovy 4/5, the compiler module
requires `org.apache.groovy` and opens `compiler.internal.ast`,
`.ast.converters`, `.ast.mutators`, and `.layer3` only to that module, allowing
Groovy to instantiate the transformations named by the public annotations. It
also opens `compiler.internal.validation` only to
`com.blackbuild.klum.cast.compiler`: the established
`@KlumCastValidator` bindings require Klum Cast to reflectively instantiate
their internal validation checks. That narrow reflective opening is neither an
export nor generic reflection access. The compiler exports none of these
implementation packages and declares no
`provides org.codehaus.groovy.transform.ASTTransformation` clause. Groovy 3
has no descriptor alternative and remains classpath-only.
The named-module fixture proves activation for DSL/mutator, converter, and
Layer 3 annotations rather than relying on resource discovery by accident.

`module-info.java` belongs to a Schema Developer. A Groovy 4/5 schema requires
the annotations and runtime modules, `requires static` compiler support, and
`org.apache.groovy`; it adds adapters only when used. It opens its DSL packages
to `com.blackbuild.klum.ast.runtime`, and—when Jackson introspection is used—to
`com.fasterxml.jackson.databind`. The schema plugin validates and explains
these requirements; it never rewrites a descriptor. Schema exports remain the
Schema Developer's client-API decision.

### Source mirrors and classpath consumption

ADR 0005 remains unchanged. AnnoDocimal `Foo_DSL` source mirrors are IDEA-only
metadata: they are not module sources, compiler inputs, classpaths, archives,
publications, Javadocs, or downstream inputs. Ordinary non-modular classpath
consumption remains supported for Groovy 3, 4, and 5.

## Consequences

- Groovy 4/5 named consumers receive true JPMS encapsulation rather than the
  broad exports of automatic modules.
- Groovy 3 remains a release-supported classpath consumer, with an explicit
  and honest named-module limit.
- All old imports break intentionally in 4.0 and schemas/consumers recompile;
  serialized 3.x graphs and package aliases are not migration inputs.
- The Gradle plugin gains validation and remediation responsibility but never
  descriptor ownership.

## Rejected alternatives

**Stable automatic modules for every Groovy generation.** They preserve the
same artifact set but expose all packages, defeating the positive export list.

**Groovy-specific artifacts or a Groovy-free core split.** This can support
explicit modules across all generations, but is a new artifact/public API
decision outside #391's accepted artifact set.

**Making Groovy 3 module-path support work with flags.** `--add-reads`,
`--add-exports`, patched modules, or generated descriptor variants hide the
unsupported dependency graph and are not portable consumer contracts.

**Publishing compiler transformations as global services or exports.** A
global transform service would run for every source unit rather than only where
the public marker annotation applies. Exporting the implementation packages
would make compiler mechanics a consumer interface. Narrow Groovy-qualified
opens preserve the existing activation semantics without either leak.
