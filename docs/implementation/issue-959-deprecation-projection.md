# Issue #959 — default advisory deprecation projection

Base: current master `9504604fb8be18a8dcb33a1b453139bb89a7c13b` (merged P1 #966 and P2 #967).
Branch: `codex/959-deprecation-projection`. Tracker impact: **Related: #959**; keep the broader issue open.
No milestone, labels, release gate, curation or consumer acceptance commitment changes.

## Approved boundary

The maintainer assigned default Draft 2020-12 `deprecated: true` for an effective admitted member whose original
Schema field, construction key or authored operation carries `java.lang.Deprecated`. It is advisory and independent
of the opt-in integral profile; no Gradle switch, source-name matching or shared-child mutation is added. Existing
requiredness/null admission, validation, lifecycle, defaults and converters are unchanged. `since`, `forRemoval`,
replacement/migration behavior and the remaining #959 constraint/default mappings are deferred.

## Implementation

`StructuredInputAdmissionPlan` records explicit true/false `sourceDeprecated` on existing generated `Field` and `Key`
facts. A minimal internal `Operation` fact is attached to the exact public projection using its authored implementation,
never the projection's possibly deprecated framework alias. The construction-key fallback retains its fact inside the
existing declaring-Builder `Schema.keyMember` when no public getter survives. No public descriptor or SPI is introduced.

The exact public-contract byte reader requires explicit source-deprecation Booleans under the shared metadata protocol.
Hierarchy/admission/rename/exclusion/collision resolution produces the same effective entries. Deprecation is attached to
each property/operation payload, beside a relationship `$ref`, or on the outer keyed Map. A construction key is marked only
in member-key definitions; keyless definitions, sibling references and documentation remain unchanged.

**Current compatibility rule:** both consumers reject older or incomplete metadata, including absent source provenance.
Recompile participating root/inherited/owned Schema artifacts with a matching supported protocol. Neither generated-alias
guessing nor Model-byte fallback is permitted. This supersedes the original pre-fact compatibility behavior qualified below.

## Executable coverage and documentation

The existing P2 API/base/derived source-free producer fixture supplies separate API/Schema JARs for Groovy 3/4/5,
with source trees deleted before generation and no consumer source or Bean provider. Throwing Model initializers,
operations and key-provider/default controls remain in the fixtures; generation does not run them. The original
structural initialization control is retained in the full generator regression.

`KlumJsonSchemaGenerationTest` now covers direct, inherited and renamed deprecated fields/operations; scalar, enum,
List/Map and operation payload placement; ignored, transient, unsupported and colliding sources; deprecated owned
relationships and outer keyed Maps; clean ordinary/shared-child uses; direct/inherited keys, renamed getter-less key
facts and keyless bodies; type-level deprecation and TemplateScope compatibility aliases. Public-byte mutations retain
actual Deprecated annotations while removing optional provenance, and inject Deprecated on an ordinary selected
population overload to guard against OR-ing generated overload annotations. Shared public setter/accessor signatures
retain separate field and operation deprecation, qualified in both directions.

Documentary feature:
`deprecated Schema members remain admitted with advisory metadata by default with Groovy #generation`,
linked to [Json-Schema.md — deprecation](../user/Json-Schema.md#deprecation). The page, migration guide and CHANGES.md
record the default behavior and bounded old-artifact omission. ADR 0031 records the maintainer's independent default
activation and compatibility policy; earlier P2 and profile decisions remain dated evidence.

The separate `validate_deprecation_schema.py` checks production-emitted artifacts using `jsonschema==4.23.0`:
metaschema validity, accepted old/new/both/absent inputs, structural null/unknown/exclusion/key rejection, equality with
an annotation-stripped copy, and generation-identical output. It is an external qualification, not a Gradle test claim.

## Validation and delivery

Focused verification passes: the full packaged generator regression has 49 cases; compiled-contract/admission tests have
73 cases in each Groovy 3/4/5 lane. The final 10-case deprecation cohort includes the shared-signature, inherited-key,
legacy and alias controls. Affected source/test licenses pass. External `jsonschema==4.23.0` passes six emitted
metaschemas and 96 input checks with advisory-removal parity; emitted bytes are identical across producer generations.
The retained structural/integral corpora also pass 195/426 inputs and 18/3 metaschemas. Changed Markdown fences,
relative file targets and diff whitespace checks pass.

Independent read-only Standards and Specification reviewers checked `9504604fb...2f4ee62a`, reserving two slots with
the worker as sole build owner. Both report zero actionable findings. The local history is reviewed as two focused
steps: implementation with tests, then documentation; only assigned files are present. Delivery-channel audit:
Git SSH feature-branch publication **authorized** (non-mutating dry run); GitHub CLI repository mutation **authorized**
(read-only repository capability check). No credentials, scopes or raw authentication errors are recorded.

The first full-root attempt passed all core Groovy lanes but failed source-mirror integration resolution: the worker
committed while the build was starting, leaving the plugin's embedded pre-commit version different from the version
published by nested fixture builds. Exact marker/repository inspection identified that mismatch. No source workaround,
regression suppression or unrelated mirror fix was introduced. The final root check is run with Git state held fixed.

Publication requires that stable-state root check, feature-branch push and a local branch-against-master SonarCloud
analysis with quality-gate waiting. The safe final root/Sonar result and analyzed revision are recorded on
[issue #959](https://github.com/klum-dsl/klum-ast/issues/959) and the draft PR after these gates. Token values are never
printed or inspected. Subsequent PR CI/Sonar remains the authoritative PR-specific result. No worker merge, issue
closure or archive reconciliation is authorized by this slice.


### Initial worker handoff — ready for PR, Sonar access required

Stable-state JDK 17 root `check --no-parallel --max-workers=2` **passed** at `71a85c1ac`, including the previously
mismatched source-mirror integrations, plugin validation, documentation renderer, licenses and test-lane isolation.
JUnit totals: **3,136 / 2,634 / 2,634** cases for Groovy 3/4/5; zero failures/errors, with 17/16/16 existing skips.
The final external deprecation corpus passes six metaschemas and 96 input cases with annotation-removal parity;
retained structural/integral corpora pass 195/426 inputs. Production/test commit remains `83eb03616`.
Both independent reviewers rechecked the documentation/evidence at `71a85c1ac` with zero actionable findings.

The reviewed feature branch is published. The local Sonar command selects the actual feature branch, target/reference
`master`, and `sonar.qualitygate.wait=true`. Execution approval was established from the explicit human Hive request,
public repository visibility and existing configured SonarCloud destination. SonarCloud scanner bootstrap nevertheless
returns **HTTP 403** at `api.sonarcloud.io`. A value-free configuration check confirms the standard token system
property is present; forwarding a plain Gradle property offers no alternative because none is configured. No credential
values were printed or inspected. No analysis or quality-gate result was produced and no actionable findings can be
claimed absent.

**Delivery boundary:** `(ready:PR)`. No draft PR has been created because the required local Sonar gate is unmet.
Restore SonarCloud analysis access, rerun the branch analysis and inspect its gate/findings, then create the draft PR
with **Related: #959**. PR CI/Sonar remains independently authoritative. Issue #959 stays open; no merge, issue closure
or Hive archive reconciliation was performed. This final follow-up changes evidence only; the qualified implementation
and tests are unchanged.


### Resumed delivery — local SonarCloud preflight passed

After the maintainer restored access, the normal local branch analysis completed successfully at `cc37a8dfe` using the
configured secure credential. Its coverage dependencies refreshed the Groovy 3/4/5 test evidence without implementation
changes. SonarCloud's branch quality gate is **OK**, with zero new unresolved issues and zero new security hotspots to
review. New-code coverage is **97.0%**, and new-code duplicated-line density is **0.0%**. No credential values were
printed or inspected. The earlier HTTP 403 boundary above is historical and no longer blocks publication.

The final evidence-only follow-up preserves production/test commit `83eb03616`. Its scan reuses the just-qualified test
reports; no implementation or test source changed. The final analyzed revision and draft-PR link are recorded on
[issue #959](https://github.com/klum-dsl/klum-ast/issues/959) and in the PR. The draft relationship remains **Related: #959**;
the issue stays open, and PR CI/Sonar is the separate authoritative PR-specific gate. Review/merge and Hive archive
reconciliation remain outside this worker's publication scope.
