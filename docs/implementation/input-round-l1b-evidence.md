# #952 L1-B — exact String scalar Map-value routes

Base: `b2e53159d1e1608d9dc086fcec96f64e346374e6`, verified against live `origin/master` on 2026-11-03.
Dedicated isolated worktree: `/Users/stephan/.codex/worktrees/911c/klum-ast`.
Branch: `codex/952-map-value-string-conversion`. Tracker impact: **Related: #952**; no issue closure, labels,
milestones, curation or release-gate changes. Hive owns final reconciliation; the worker does not self-archive.

## Contract and implementation

L1-A / PR #1018 is the exact base. L1-B reuses its compiler-owned source/generated-method association, PROPERTY
selection and getter-side zero/one completeness count for exact `Map<String, T>` of non-native, non-DSL scalar values.
The selected ordinary public method must take exact `(String key, String value)` and return the declared value type.
Only exact String converter sources qualify; competing String/CharSequence attempts, missing projections and descendant
shadowing remain excluded. Custom key mapping and non-String declared keys cannot gain converter admission.

The runtime validates the reflected Map destination, key type, exact selected public signature, association, role and
count. Its immutable payload is Map-of-String shared with schema projection. The existing interpreter iterates supplied
entries, checks each literal String key and raw String value, and invokes the selected ordinary entry adder. There is
no interpreter population change, container setter/factory invocation, general Map reinterpretation or second converter
engine. A protected generated-code bridge preserves direct named-module linkage to ordinary Map conversion/population.

JSON Schema emits an object with String `additionalProperties` without initializing Schema/domain classes or running
converters. Converter parameter assertions remain scalar-C1-only; Map values carry structural grammar. Existing
version-1 metadata is reused; matched compiler/runtime/plugin artifacts and Schema hierarchy recompilation are required.

Defaults, existing-key replacement, empty input, caller Map iteration order and Builder lifecycle remain ordinary.
Reached malformed keys/values and converter failures retain earlier work; later entries/operations never run. Diagnostics
retain source, escaped per-key pointer and original cause. No List admission extension, owned DSL Map values,
CollectionFactory, NamedParams, selectors, custom key mapping, public authoring API, parser, Jackson, copy or composition
change belongs to this slice.

## Acceptance map

- `StructuredMapConverterTest`: documentary Duration values/types, ordinary Groovy parity, literal keys/order and renamed
  members, initializer/default retention, existing-key replacement, empty input and whole-container-setter trap;
  first-failure partial state, source/escaped-key diagnostics, original cause and skipped later work;
  null/numeric/boolean/GString/Object/mutable-CharSequence/Map/List value rejection and non-String input keys;
  same-compilation/external adapter/constructor origins, inherited binary dispatch and descendant shadowing;
  ambiguous/excluded/colliding/missing routes, non-String declared keys, key-mapping and unsupported destinations;
  source-free positive inspection and corrupted/incomplete association/count/signature/type failures with initialization traps.
- `StructuredListConverterTest` and `StructuredConverterOriginTest`: retain L1-A/scalar regressions and move the formerly
  excluded Map control to an unsupported key shape.
- `CompiledConverterJsonSchemaTest`: source-free packaged G3/G4/G5 producers covering closure, local, adapter,
  inherited constructor and bounded generic value origins; exact String value grammar, no C1 leakage, excluded
  CharSequence/non-String-key/key-mapping declarations, deterministic/cacheable output and no-execution traps.
- `validate_converter_schema.py`: independent Draft 2020-12 accepted/rejected instances against actual emitted schemas,
  including closed-root controls and retained scalar/C1/List corpus.
- `JpmsPackageBoundaryTest`: Java/static Groovy artifact consumers and bytecode proof of direct Map-converter hook
  linkage, classpath G3/G4/G5 and named-module G4/G5 qualification.

Documentary link: `StructuredMapConverterTest#'converts duration Map values independently under literal String keys'`
with `@Issue('952')`, `@Tag('documentary')` and `@See`. Matching user contract:
[converter-backed Map values](../user/Structured-Input.md#converter-backed-map-values).
Converters, JSON Schema, Migration, CHANGES and ADR/plan documentation are synchronized.

## Validation and reviews

Qualification is in progress. The initial documentary test failed before implementation. The focused baseline run passes
56 Map and 47 retained List cases without skips; a broader run identified a superseded scalar-origin Map exclusion,
which now tests non-String declared keys. Final consumer/full-lane, documentation and independent-schema results will be
recorded here before publication. Exactly two read-only local review axes (Standards and Specification) are required;
no local SonarCloud upload is authorized or performed.

## Delivery

No remote branch or PR has been published at this checkpoint. Authorized delivery is a draft **Related: #952** PR;
merge remains outside the assignment. Hosted CI/SonarCloud qualification is separate from local evidence.
