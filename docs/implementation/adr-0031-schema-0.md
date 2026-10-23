# SCHEMA-0 — JSON Schema delivery plan after WIRE-1

Date: 2026-10-23. Status: **planning proposal; generator and Gradle integration not implemented or authorized**.
Decision: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md).
Generator owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Language owner: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Reviewed baseline: `c9a8918fba103544209e53cadfc8b51d996e8e63` (merged WIRE-1).

## Authority and reconciled baseline

This is implementation planning within ADR 0031's accepted architecture, not a new descriptor decision. The output is
JSON Schema Draft 2020-12 for the admitted structured-input language. The compiler owns admission and naming; #932 owns
language semantics. Generation reads compiled public Builder/Factory contracts and their private compiler carrier,
without invoking the interpreter. [ADR 0009](../adr/0009-jackson-interoperability.md) independently governs Jackson;
this schema describes neither an arbitrary configured ObjectMapper nor completed-Model serialization.

| Evidence | Current consequence |
| --- | --- |
| [#933](https://github.com/klum-dsl/klum-ast/pull/933), [shared probe](evidence/issue-932-structured-input-probe.md) | Separate API/Schema compilation, binary-only extraction, `$defs`, no-execution traps and pinned-validator checks are reusable test shapes. Its fixture allowlist, two-argument tuple and unkeyed relationship list are historical experiments, not current admission. |
| [#937](https://github.com/klum-dsl/klum-ast/pull/937), [#938](https://github.com/klum-dsl/klum-ast/pull/938) | Compiler-normalized roles, names, payloads, selected public signatures, source identity and rejection facts are persisted in `<Model>_DSL$WirePlan.class`. Annotation-only rediscovery was measured and found incomplete. |
| [#939](https://github.com/klum-dsl/klum-ast/pull/939), [WIRE-RUNTIME plan](adr-0031-wire-runtime-v1.md) | Root/active-Builder interpretation, supplied traversal order and the early-context boundary are settled independently of generation. |
| [#940](https://github.com/klum-dsl/klum-ast/pull/940), [runtime qualification](evidence/issue-932-wire-1-runtime.md) | The restricted runtime is merged, not merely proposed. Numeric/container/keyMapping/fixed-child-key refinements are the current generator baseline. #529 and broader #932 remain open; no release placement follows. |

Read the WIRE-1 evidence as dated checkpoints: [numeric](evidence/issue-932-wire-1-numeric-stop.md),
[container stop](evidence/issue-932-wire-1-container-stop.md),
[collection contracts](evidence/issue-932-wire-1-collection-contracts.md),
[reader](evidence/issue-932-wire-1-reader.md), [construction](evidence/issue-932-wire-1-construction.md),
[keyMapping](evidence/issue-932-wire-1-key-mapping-stop.md), and
[fixed direct-child key](evidence/issue-932-wire-1-direct-key-stop.md). Their original stop alternatives and unpublished
handoffs do not override the accepted resolutions or the live merged PR. Their recorded test results are prior evidence,
not generator qualification performed by SCHEMA-0.

The current `WIRE-PLAN/B` carrier has one private static final String `DATA`, no executable members, and **twelve tokens
per entry**, including optional `fixedChildKey`. The unchanged eleven-token #938 fixture now tests recompile/package
guidance, not successful current consumption. Preserve that boundary; add no legacy decoder, descriptor version,
compatibility shim or encoding change. The single constant retains its 65,535 encoded-byte limit. Use successfully
compiled/packaged artifacts; stray output from a failed compilation is not a supported input artifact.

## Direct projection versus the historical descriptor proposal

Historical evidence only: local branch `codex/issue-529-structured-metadata-feasibility`, commit
`192acd255196f37d11651c11d6547d5c06ef9b00`, files `docs/implementation/issue-529-structured-metadata-feasibility.md`
and `docs/implementation/evidence/issue-529-json-schema-research.md` (recoverable with `git show <commit>:<path>`).
These local reports are not presumed published; the comparison below records the relevant conclusions for reviewers.
Their fidelity cautions survive; their generic metadata architecture and provisional 4.2 recommendation are not authority.

| Requirement | Direct carrier-to-schema projection | Historical extra machinery / disposition |
| --- | --- | --- |
| Names, roles, selected payload/signature, source and inherited identity | Already compiler-owned carrier facts, checked against public contracts | A second declaration descriptor repeats admission and creates drift; reject it. |
| Reusable definitions and binary dependencies | Reachable compiled Builder identities and explicit artifact inputs | No generic registry, universal declaration format or registration step is needed. |
| Prose and unavailable facts | Read retained documentation bytes where safely attributable; report unavailable prose | Do not add AST capture, initializer decompilation or a descriptor transport to make prose mandatory. |
| Validation/default/lifecycle limits | Document structural scope and declaration-linked omissions | No opaque-rule catalog, portable-policy framework, constraint codec or mapper-qualified metadata model is required by this subset. |
| Reproducible consumption | JSON Schema files, diagnostics and explicit roots/classpath | Descriptor serialization/versioning, embedded metadata and paired-artifact negotiation solve no demonstrated first-slice requirement. |

Retain only a private in-memory representation needed to decode/compose carrier facts and emit schema. It is not a public
descriptor, serialized intermediate artifact, SPI or second semantic authority. Do not expose the runtime's private
`StructuredInputPlan` or make generation call it reflectively. A small tooling-local byte reader, checked against the
same carrier corpus, is the initial approach. Consider source-level decoder sharing only if the tracer demonstrates a
concrete maintenance requirement without introducing public API, runtime coupling or new generated linkage.

## Binary-only extraction, roots and identity

Inputs are explicit concrete Model binary names, their compiled output/JARs, and an explicit dependency classpath
including separately compiled API/Schema dependencies. Do not scan for roots, instantiate `Create`, infer a default
implementation, read model descriptors to select providers, or discover subtypes. An abstract API may contribute
inherited facts but cannot be selected as a concrete root. A keyed root's instance key belongs to caller orchestration;
it is not a required property or constant in the generated input schema.

Locate the exact Model, public `<Model>_DSL$Builder`/Factory contracts and sibling carrier in the selected artifact.
Follow only explicit generated Builder parent interfaces and recorded nested identities to their exact artifact origins.
Validate carrier envelope, canonical tokens, roles, payload tree, source owner, referenced types, fixed-key legality and
recorded public descriptors. Compose inherited facts with the current runtime's source-identity and collision rules:
collapse identical diamond facts, retain rejections, reject conflicting facts, and admit no external-name collision.
Do not reconstruct rejected aliases by parsing prose. Missing parent/carrier/type, corrupt entries and mismatched
Model/Builder identities fail with root, artifact, source/contract where known and recompile/package guidance.

Prefer classfile inspection of explicitly supplied JARs/directories to Model Class loading. This gives the optional
tooling its own no-execution path without runtime package access. Prove named-module JAR reading independently: the
runtime module's qualified opening to Schema packages does not authorize an unrelated tooling module's reflective reader.
No new opens/exports, `--add-*` flags, service lookup or core/runtime dependency is justified. Detect duplicate binary
identities in selected artifacts with incompatible bytes; fail rather than choosing by incidental classpath order.

Keep the carrier's exact external spelling and name origin. Current fallback is **identity**, after `@JsonIgnore(true)`
and literal nonempty `@JsonProperty`; do not re-read those annotations to create an alternative spelling. Empty rename
and `@JsonIgnore(false)` follow the compiler result. Camel/kebab strategies, aliases and factory-argument annotations
are not current language options. A source declaration plus declaring Builder and selected descriptor identifies an
entry; an external rename does not create a new declaration identity. These are existing #932 facts, not a new ID API.

Use the fully qualified generated Builder **binary name** as each local `$defs` key, including `$` where present. Retain
distinct definitions for same-simple-name types in different packages. Emit a root `$ref` to its definition and one
definition per reachable concrete Builder; flatten admitted inherited entries into that definition before closing it.
Do not close a base schema and then try to extend it with `allOf`. Track visited identities before following nested
edges, so recursive type graphs reuse `$ref` rather than infinitely expanding. Schema recursion does not permit cyclic
caller Maps or existing-instance references. Form reference fragments by RFC 6901 escaping each definition-key token
(`~` then `/`) and URI-fragment encoding; test `$`, punctuation and non-ASCII identities. Do not invent a network `$id`,
registry address or external reference requiring artifact publication to resolve a local definition.

Retained AnnoDocimal `@AnnoDoc` strings on exact Model/public contract declarations may supply `description` after a
classfile-only attribution check. Use source identity, not guessed getters or singular names, to join prose. Prose from
an inherited declaration follows that declaration; ambiguous/absent documentation is reported as unavailable and does
not change admission or block otherwise valid generation. Never scrape IDE source mirrors, require source JARs, resolve
Javadoc links over the network, instantiate annotation proxies/closures, or infer a default value from documentation.

## Conservative projection and fidelity

Each domain definition is an object with its admitted external names in `properties`, `additionalProperties: false`,
and no inferred `required`. Every property is optional at the raw-input boundary. Do not emit `{}`, `true`, arbitrary
Object recursion, permissive union branches or guessed conversion formats for an unknown admitted payload.

| Carrier contract | Draft 2020-12 proposal and acceptance limit |
| --- | --- |
| String / boolean | `type: string` / `boolean`; no numeric/boolean strings, null or arbitrary caller objects. |
| byte/short/int/long and wrappers | `type: integer` plus exact signed target bounds; mathematical integral JSON values, including `1.0`, are not rejected for their lexical spelling. |
| BigInteger / BigDecimal | `integer` / `number`; use an exact-number validator fixture and document parser precision. Do not round large integers through binary double. |
| float/double and wrappers | `number` is only a candidate: runtime rejects overflow/non-finite conversion and rounds finite values. The tracer must qualify decimal overflow thresholds, underflow and boundary rounding. If exact safe assertions cannot be established, report this admitted target as unsupported projection and fail that selected root; no unconditional `number` placeholder. A narrower first useful subset requires the explicit coverage gate below. |
| Enum | String `enum` of exact declared constant field names from class bytes; never call `values`, `valueOf`, `getEnumConstants` or `toString` during extraction. No ordinal/case/codec aliases. |
| Supported collection PROPERTY | Array with recursive `items`. List, Set, SortedSet, NavigableSet and EnumSet field storage uses compiler-selected generated element population. No `uniqueItems`, sorting or guessed replacement container; repeated inputs remain legal even if Model storage deduplicates. |
| Data-map PROPERTY | Object with recursive `additionalProperties` value schema; Map, SortedMap and NavigableMap field storage does not change literal String data keys. Do not close data maps as domain objects. |
| Ordinary List/Map OPERATION payload | Same recursive array/data-object shape, passed as exactly one payload; nested plain data uses only currently admitted List/Map targets. No positional tuple, named argument spread or invented Model fields for the operation's effects. |
| Concrete OWNED_OBJECT | `$ref` to the nested concrete Builder definition. Keys remain absent from the child's property surface. A compiler fixed direct-child key or ordinary declared key derivation is construction behavior, not an external discriminator or required child field. |
| KEYED_OWNED_MAP | Object with `additionalProperties` referencing the child definition; each outer String key is instance data. No key renaming, subtype dispatch, nonempty-key rule or child key duplication. `@Field.keyMapping` remains compiler-rejected. |

Property names such as `class`, renamed methods and inherited operations come directly from the carrier. Ordinary keys
such as `app.kubernetes.io/name`, `OrderCreated`, `a/b~c` and `camelCase` are never normalized. Preserve empty key acceptance
where the current runtime accepts it. Non-String parsed map keys, null nodes and cyclic parsed containers are outside
the JSON instance subset; parser/source duplicate-key handling is caller-owned. No YAML parser, YAML tag/alias policy,
Jackson codec or serialization-round-trip claim is introduced.

| Semantic fact | Required treatment |
| --- | --- |
| `@Required`, class/field `@Validate`, Optional/Ignore and Bean Validation | Completed-model/runtime checks do not establish input presence, cardinality or numeric bounds beyond the input target type. No automatic `required`, arbitrary Groovy/regex translation or Jakarta constraint mapping. `port: -1` can be structurally valid and fail later validation. |
| Initializers, DefaultValues, Default, OwnerProvidedDefaults, AutoCreate, lifecycle | Never evaluate them or infer effective values. Omitted fields may later be supplied; explicit zero/empty can be changed by runtime defaults. Retained literal metadata may inform prose with its origin, but no first-slice JSON `default` assertion/annotation is promised. |
| LINK / OPTIONAL_LINK, AutoLink / LinkTo | These relationship inputs are not admitted inline v1 owned objects. Preserve carrier rejection/absence; no fake `$ref` instance identity, registry, nullable fallback or Environment input obligation. Caller attaches completed context through normal orchestration; early callback dependencies must already be prepared. |
| Templates | No Template root/mode, recipe serialization, Template expansion or code execution for defaults. A plain domain-object schema does not describe Template/copy controls. |
| Abstract targets / polymorphism | Diagnose unsupported carrier members; no classpath subtype scan, inferred defaultImpl, tag, `oneOf` or alias closure evaluation. Even literal Field.alternatives cannot select a wire syntax until #932 accepts one. |
| Builder-only/protected/internal state | Project only actual carrier admission; do not substitute Model reflection or infer `readOnly`/`writeOnly`, user visibility or ScHelm override authority from field category. |

The emitted document must state in a root `$comment` that it validates only the projected structured-input shapes and
does not guarantee successful operation execution, binding, lifecycle, materialization or completed validation. It is
not a Jackson schema, a complete description of all Groovy DSL calls, or a configuration composer. Include the selected
root/Builder and projection coverage in generation diagnostics. Standard keywords remain meaningful when tools ignore
comments. No custom vocabulary or companion descriptor is needed to express these limits.

[Core §§8.2.3–8.2.4](https://json-schema.org/draft/2020-12/json-schema-core) define references/definitions;
[Validation §§6 and 9.2](https://json-schema.org/draft/2020-12/json-schema-validation) distinguish type/presence assertions
from default annotations. The rules above are KlumAST projection proposals within those semantics.

### Unsupported and unknown are different

Report compiler rejection records by their existing source identity and reason, including ignore and unsupported
members. A rejection need not prevent generating the root's otherwise admitted surface: that member is not part of the
current input language. Ignored/helper names are absent and fail the closed domain object. Rejections are not permission
to invent their external aliases. A collision omits all colliding entries with every source reported, matching #932.

Conversely, an **admitted** entry with an unprojectable type, missing nested artifact or unknown carrier fact is a
generation failure for the selected root. Do not silently omit it and label the result complete. An optional member
whose nested target is unreadable still requires failure: lack of a supplied value at generation time proves nothing.
Any proposed partial-root mode or newly unsupported admitted shape returns to the maintainer with a concrete fixture.
Emit deterministic diagnostics containing root, declaring Builder/source, external name when known, selected descriptor,
payload location, expected/actual metadata and corrective action. Optional prose absence is informational; corrupt
metadata is fatal. No public diagnostic-code enum, generic fidelity protocol or serialized declaration report is frozen.
On failure do not publish a new schema for that root or leave an earlier successful file looking current.

## Executable feasibility proposal — SCHEMA-0/T

This is the smallest proposed **test-only** tracer after plan approval. It must emit and validate actual Draft 2020-12,
not stop at a descriptor dump. Reuse the Spock separate-compilation harness from
[`StructuredInputSchemaProbeTest`](../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/StructuredInputSchemaProbeTest.groovy)
and the compiler's
[`StructuredInputAdmissionPlanTest`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/compiler/internal/ast/StructuredInputAdmissionPlanTest.groovy).
Place new carrier/projection feasibility tests in `klum-ast`, without a Jackson production dependency. Test adapter names
are provisional; use `StructuredInputSchemaCarrierTest` and `StructuredInputSchemaDocumentaryTest` only when implemented.
Each new test carries `@Issue("529")`; add #932 only to actual shared-admission contract tests.

1. **API artifact:** separately compile an abstract API root with an inherited scalar/one-payload operation and a Cluster
   exposing an abstract child API. Include a concrete API dependency child sharing a simple name with a Schema child.
2. **Schema artifact:** compile against the API JAR alone. The concrete root realizes the Cluster with concrete keyed
   children, one direct concrete child, renamed/ignored/inherited members, enum, supported field containers, plain data
   maps and one-payload Map/List operations. Include the fixedKeys Cluster case, with a renamed external field whose
   native fixed child key remains unchanged. Package public contracts and all current twelve-token carriers successfully.
3. **Binary-only extraction consumer:** launch a fresh JVM with explicit root names, packaged API/Schema dependencies and
   the test adapter; no producer sources, source mirrors, AST nodes, compiler transformation or interpreter invocation.
   Read class bytes, produce a schema file, diagnostics and a canonical private fact dump for comparison only.
4. **Independent consumer:** an API-only Java client compiles without Schema-specific types; separately, the validator
   consumes only the emitted JSON Schema and JSON fixtures. An intentional runtime parity phase can then use Schema
   plus `KlumStructuredInput`; it is a separate process from extraction and may execute Schema code by design.

All producer static initializers, enum initialization, constructors, initializers, factories, default/converter/validation
closures, lifecycle callbacks and annotation closures have traps visible across the extraction process boundary.
Accepted extraction and every rejected path leave the trap marker absent. Include an intentional-execution control
proving the instrumentation works. No annotation proxy, Groovy dynamic Class property access or enum reflection shortcut
may bypass this test; #933 already demonstrated that dynamic Class access can initialize user code unexpectedly.

| Tracer acceptance group | Executable assertion |
| --- | --- |
| Binary identity and composition | Separate API -> Schema -> consumer; inherited facts/diamond deduplication, same-simple-name `$defs`, recursion, escaped references, dependency-only byte change; exact equality with current runtime reader facts via test-only access, without exporting that reader. |
| Current ABI and module access | Missing/corrupt carrier, invalid fixed-key role, eleven-token #938 recompile negative, absent parent, mismatched/duplicate class identities; direct named-module JAR reading on Groovy 4/5 without runtime-module friendship or new flags. |
| Real projection | Metaschema check and valid/invalid fixtures with the existing pinned `jsonschema==4.23.0` approach; exact integers/decimals, overflow/fraction/enum/null boundaries, every supported field container, literal maps, nested/keyed refs and single-payload operations. Resolve float/double fidelity before claiming coverage. |
| Name/shape negatives | Unknown/raw renamed/ignored/helper names, nested child key duplication, keyMapping rejection, abstract/polymorphic target, raw/unresolved generics, custom Number/container, unkeyed relationship list and zero/multi-argument operation. Distinguish compiler rejection from an admitted projection failure. |
| Honest semantic limits | Omitted Required satisfied by lifecycle/defaults; structurally valid invalid-completed value; false/zero/empty; caller-supplied late LINK via Holder; OPTIONAL_LINK/Templates excluded as input. The schema does not invent Environment or replay Template actions. |
| Determinism and order separation | Fresh-process byte equality across Groovy 3/4/5, shuffled archive entries and relocated paths; unrelated bytes do not change output, relevant API/Schema/docs bytes do. Reversed ordered runtime Maps can change operation results while emitted schema stays identical. |
| Failure and no execution | All supported/rejected extraction paths keep traps silent; no stale success file after failure, and no use of sources/mirrors when binary facts are missing. |

The test adapter may decode the current carrier and implement only the closed projection branches above. Do not promote
the old fixture allowlist, add a product API/module/plugin, commit generated sample outputs as authority, or modify #932
to make projection easier. Stop with the failing fixture if an admitted fact cannot be read or represented safely.

Illustrative Schema syntax for the future shared documentary case (not a test added in this planning revision):

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL

@DSL class Application {
    String host
    int port
    Map<String, String> labels
    @Builder.Method void endpoint(Map<String, String> settings) {
        host = settings.host
        port = Integer.parseInt(settings.port)
    }
}
// Structured input: [endpoint: [host: 'api', port: '8080'],
//                    labels: ['app.kubernetes.io/name': 'orders']]
```

The schema describes one String-valued map payload for `endpoint`. It does not promise that every string parses as an
integer or infer a hidden port constraint. No new DSL syntax or hypothetical public generator signature is selected.

## Subsequent slices, dependencies and reasoned commits

All slices below require separate admission. SCHEMA-0 is this plan only; no implementation begins automatically.

| Slice / owner | Smallest delivery and commit boundaries | Acceptance and documentation |
| --- | --- | --- |
| SCHEMA-0/T — #529 feasibility | (1) Package separate API/Schema and current carrier corpus with no-execution/negative controls. (2) Add private byte reader plus actual schema/validator proof; do not publish a reader by itself. (3) Record measured coverage, unresolved float/docs/artifact gaps and consumer evidence. | Full tracer matrix above, baseline Groovy 3 then 4/5, licenses/module checks and independent Standards/Specification review. Evidence report updates this plan; no release/user feature claim. |
| SCHEMA-1 — #529 first useful generator | After coverage/consumer gates: (1) Implement optional tooling-local extraction and projection together with binary-consumer acceptance. (2) Complete diagnostics, safe prose, definitions, deterministic file generation and failed-output handling. (3) Add the selected narrow caller boundary and documentation together. No published descriptor-only intermediate API. | All admitted initial scalar/enum/container/concrete/keyed/one-payload forms, or an explicitly accepted smaller coverage contract. No generator dependency enters core/runtime; no call to interpreter internals. Add documentary `@Tag("documentary")`/`@See`, current user page, `_Sidebar.md`, migration navigation and `CHANGES.md` at public delivery, without assigning a release here. |
| SCHEMA-2 — #529 optional Gradle adapter | Only if producer/editor workflows need build automation: (1) Add a thin task around SCHEMA-1 with explicit roots, compiled outputs/dependency inputs and output directory. (2) Add TestKit lifecycle/cache coverage and opt-in wiring. No new carrier or projection semantics. | Clean/repeat/up-to-date, relevant dependency change, root/member deletion, failure/stale removal, relocation/build cache and configuration-cache reuse; no source-mirror input, eager resolution or classloading during configuration. Document setup/output ownership alongside tests. |

The exact optional tooling artifact/package, callable entry point and task/plugin/extension names stay provisional until
SCHEMA-0/T establishes the necessary boundary. Do not put the projector in core merely to avoid this choice, or build a
plugin before there is a caller needing it. The Gradle adapter can be delivered with SCHEMA-1 if the maintainer selects
that as the minimum useful entry point; an automatic separate plugin is not an architectural prerequisite.

Reproducible output uses sorted roots/definitions/properties/diagnostics, canonical JSON encoding, fixed line endings and
no timestamps, machine paths or archive-entry-order dependence. List/enum semantic order is preserved where meaningful.
Gradle inputs include explicit roots, relevant options/tool version, compiled Schema outputs and the resolved dependency
artifacts (including API/carrier/docs bytes). Generate self-contained schema files; consumers own copying/publication.
Up-to-date/cache behavior is a proposed acceptance test, not established by deterministic Spock output. Atomic replacement
and stale-root cleanup must prevent a previous artifact masquerading as successful current generation.

None of that imposes runtime execution order. WIRE-1 traverses supplied Maps recursively in their own iteration order and
preserves List order. Sorted metadata is not a scheduler, commutativity guarantee, YAML source-order promise or layer plan.

## Maintainer decisions before implementation delivery

| Gate | Recommendation for review; decision remains with maintainer |
| --- | --- |
| First useful projection versus descriptor-only delivery | Require actual JSON Schema plus explicit limits as the first useful #529 delivery. SCHEMA-0/T can prove it privately. Descriptor-only delivery is outside accepted ADR 0031 and would require an explicit architectural revision, not a quiet preparatory product. Confirm whether a narrower useful projection is acceptable if float/double or another admitted shape fails qualification; otherwise those are delivery blockers. |
| Normal task output versus companion artifact/variant | Start with ordinary generated JSON Schema files. Confirm whether a demonstrated consumer requires an optional published schema artifact/variant aligned to Schema binaries. This is distribution of JSON Schema, not revival of a generic companion descriptor. No classifier, embedding, registry or publication task is selected here. |
| Mandatory ScHelm acceptance | Decide which concrete ScHelm package/root and consumer-owned command constitute required first-delivery acceptance, who supplies the fixture and whether synthetic Layer 3 proof alone can admit the tracer. Recommend one real concrete application input with literal labels/topic maps and externally supplied Environment; no inferred claim that the whole existing ScHelm Schema is currently admitted. |

ScHelm requirements are evidenced by #529/#932 and the shared probe's recorded consumer inspection, not by product policy
in this plan. In particular abstract NamingStrategy and Map<String, Environment>, compact Duration `30s`, provider/package/
root selection, profiles, fixed/advanced fields, OPTIONAL_LINK fallback and YAML scaffold rendering remain consumer needs
or separate #932 decisions. [Existing ScHelm Fact-binding evidence](evidence/issue-867-schelm-map-closure.md) demonstrates
ordinary consumer/lifecycle code, not permission to execute its annotation closure during generation or create a Klum
policy framework. Do not edit or fabricate a real adopter fixture without a selected contract and authorization.

## Stop conditions and planning delivery

Stop implementation on an unprojectable admitted shape, unavailable required binary fact, unknown carrier encoding,
conflicting artifact identity, initialization trap, named-module access requirement, or runtime/generator name/role drift.
Return the smallest failing fixture and alternatives to the Hive; do not change #932 semantics, generated ABI or public
contracts, add a public generic descriptor/codec/registry, parser, universal declaration format, Jackson-wire promise,
runtime dependency, execution-order policy or ScHelm policy API to unblock generation.

SCHEMA-0 changes planning documents only. No production code, executable tests, Gradle setup, current user documentation,
release placement, curation or issue state changes. Validate changed Markdown links/fences, diff cleanliness, the requirement
map above and current source/PR facts; Groovy lanes are unnecessary for these documentation-only changes. Later slices
carry the executable checks described here and must not cite this plan as passing generator evidence.

The requested delivery is a committed draft planning PR with `Related: #529` and `Related: #932`; both remain open.
Tracker impact: **related issue**; curation impact: **none** for this plan (no changed release gate, owner, placement or
implementation acceptance). The decision queue remains explicit. Report the exact worktree, branch, base/head, review,
validation, authorization channel and live PR/check status to the Hive. Request reconciliation; retain `(PR:open)` and do
not merge, self-archive, start a generator or dispatch subsequent slices.

### SCHEMA-0 validation record

Local documentation validation on 2026-10-23: four changed Markdown files, balanced fenced blocks, 60 existing local
link targets and `git diff --check` pass. Local self-review covered Standards (document scope, vocabulary, imports in
the illustrative Groovy snippet, issue/publication boundaries) and Specification (each requested projection, binary,
fidelity, fixture and decision gate). No outstanding finding was identified; no independent review is claimed.
Groovy lanes, generator execution, metaschema/instance validation and ScHelm acceptance were not run for this plan.
The merged PR states were verified live; historical results above remain attributed to their original evidence.
