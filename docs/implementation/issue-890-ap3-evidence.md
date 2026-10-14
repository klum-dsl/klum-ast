# Issue 890 / ADR 0029 AP-3 evidence

Date: 2026-10-14. Scope: generated, inherited and runtime direct-annotation parity.
Base: `93e5a452` (merged AP-2). Branch: `codex/issue-890-ap3`.

## Implementation boundary

This slice adds qualification tests and documentation only. No production code, generated public API,
serialization, runtime lifecycle, contributor contribution/diagnostic contract, validation engine or
dependency/publication default changes. Required/Optional retain their ordinary local registrations.
KlumCast annotations/SPI/compiler remain aligned at public `0.5.0-rc.2`, resolved normally from Central;
no Maven Local, dynamic version, init-script overlay or alternate dependency route is used.

## Acceptance matrix

| Contract | Executable evidence |
| --- | --- |
| Required default message/ERROR, direct custom message/WARNING, no domain-member forwarding | `AnnotationPropagationContractTest`: retained configured domain values, one Validate per field, customized direct control succeeds with a missing value |
| Optional Ignore and non-DSL compatibility | Same test: class-level Validate, boxed false accepted, absent required field fails equally, ordinary direct Required/Optional still lower once and domain-only ordinary fields remain inert |
| Required primitive boolean limitation | Same test: both shortcuts compile and lower once, while direct Validate rejects primitive boolean; see the decision note below |
| Query shape, purity, categories and Builder state | Direct/contributed paired failures for private/static/void/abstract, Builder.Method/lifecycle conflict, mutation, construction-only state and Model-bearing results; successful queries observe successive Builder values and completed Model state |
| Cluster field/method conversion and restrictions | Paired field/method projection, removed owner field, retained getter domain and exactly one Cluster, generated factories; static/type/raw-map/key-generic/wildcard/parameter/body failure controls have matching diagnostics/counts |
| Abstract/source/precompiled inheritance | Same-unit and fresh-loader binary controls preserve Required, AutoCreate, Query and field/method Cluster contracts; no source AST from the compiled parent remains in the child loader |
| Overrides/shadowing | Source/binary overrides preserve Query behavior and public signatures; field shadowing retains two matching ordinary hierarchy errors |
| Nested/mixed/interface eligibility | Static nested and nonstatic inner DSL declarations qualify independently; ordinary enclosing/inner/sibling declarations and DSL-interface members remain inert; existing same-unit non-DSL-subclass diagnostic remains |
| Public API and hidden implementations | Recursively compare generic method contracts of the entire emitted namespace, including Builder/Factory/nested cluster factories; Create field uses public Factory; hidden implementations retain interface metadata/linkage |
| Typed consumers and source mirrors | Java 17 and CompileStatic Groovy consume public Builder/Factory/cluster signatures and execute Query/cluster creation; namespace mirrors compile with javac and match direct contracts after expected domain-retention/name/format normalization |
| Projections do not expand again | Model method, implementation twin and public Builder query each have one Query; compiled base Required has one Validate; Cluster getter has one Cluster; Query mirror retains exactly one domain annotation |
| AutoCreate ordinary construction | `AnnotationPropagationLifecycleTest`: paired controls for initialized child assignment, owner identity, active Templates and supplied-child precedence |
| Direct-before-Cluster fallback | Paired direct creation/mutation, existing child, declining external Creator and bare Cluster fallback; no field mutation replay, all children receive ordinary callbacks |
| Callback/phase ordering | Paired method-before-Closure callbacks; parent sees creation and mutations; AutoLink-created children do not replay earlier AutoCreate callbacks or child creation |
| Existing failure contracts | Same-phase external Creator conflicts and bare keyed/abstract AutoCreate runtime failures match direct controls |

Existing AP-1/AP-2 admission, foreign-transform, DefaultValues, diagnostic/provenance and artifact/module
tests remain regression inputs. This slice does not add AP-4 artifact/JPMS/editor qualification or AP-5
ScHelm/release delivery. Existing named-module tests are exercised by root checks; no new module claim
substitutes for AP-4.

The documentary test is
`AnnotationPropagationContractTest#'abstract bases retain public Query and Cluster contracts through #binary inheritance'`,
linked to [inherited and generated contracts](../user/Annotation-Propagation.md#inherited-and-generated-contracts).
The earlier AP-1 AutoCreate/DefaultValues documentary example remains current.

## Direct Required limitation: maintainer follow-up

The public Required Javadoc says primitive boolean is illegal. Current master nonetheless accepts both
direct Required and propagated Required on primitive boolean, while direct Validate is rejected.
Required lowers to Validate in its ordinary semantic local transform, after effective-declaration
validation has completed. AP-3 records this baseline limitation with an executable paired control; it
does not move converters, revalidate generated annotations, manipulate VALIDATED or add another engine.
Boxed Boolean remains the documented usable shape. Correcting direct Required's restriction requires
a separate maintainer decision about the existing shortcut contract and validation seam. No separate
issue is created or release placement selected by this worker. No propagation-specific incompatibility
has been observed in the paired baseline matrix.

The non-DSL-subclass diagnostic control is explicitly same-unit, matching the existing DSL transform's
source-unit hierarchy scan. It does not claim a new binary-parent hierarchy validator. Nonstatic inner
DSL declarations are compilation/public-contract controls, without adding a new inner-instance factory
contract.

## Validation and delivery

Java 17 focused Groovy 3 qualification of both new test classes passes: 40 cases, zero failures/skips.
Full root checks, compatibility matrix and independent review are in progress; final evidence will
replace this status before handoff.

Tracker impact: `Related: #890`; AP-3 is a partial implementation-plan slice and selects no issue-closing
syntax. Issue state, labels, milestone, curation/release indexes and reusable agent policy are unchanged.
Hive retains issue/documentary cross-link reconciliation and release/consumer admission.
