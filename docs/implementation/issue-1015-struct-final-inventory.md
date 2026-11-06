# Final 4.2 Struct support inventory — #1015

Date: 2026-11-06. Related: [#1015](https://github.com/klum-dsl/klum-ast/issues/1015).
Authority: merged source/tests, [ADR 0032](../adr/0032-builder-oriented-structured-input.md), the accepted
[4.2 plan](input-round-plan-4-2.md), and prior maintainer dispositions. This documentation record authorizes no feature work.

## Inspected repository delivery

Refreshed `origin/master` base: `7cd6043c7089fcc592e130eba6d0eaf5a6fbfcb5`.
Worktree: `/Users/stephan/.codex/worktrees/6ac6/klum-ast`; branch: `codex/1015-doc-final`.
The following PRs were verified merged through GitHub; their merge commits are ancestors of that base:

| Delivery | Merge commit | Bounded outcome |
| --- | --- | --- |
| [#1017](https://github.com/klum-dsl/klum-ast/pull/1017) | `45729e625385fb59bca878cce3771391d6d727d3` | F2-B: one explicit literal alternative for the abstract keyed owned Map family. |
| [#1029](https://github.com/klum-dsl/klum-ast/pull/1029) | `669418b0d30a235d951165a05e4bd741094a99d9` | Native scalar/enum derived-key Map sequences (#1028). |
| [#1032](https://github.com/klum-dsl/klum-ast/pull/1032) | `50914613589c45690ae6e4778ef85c6393785448` | Finite explicit literal alias groups (#1030), with field-based admission independent of public convenience creators. |
| [#1035](https://github.com/klum-dsl/klum-ast/pull/1035) | `6b62920f91afbaf4ba91ff927eb9d2fa0a04ba6b` | Exact String converter-backed Set sequences. |
| [#1036](https://github.com/klum-dsl/klum-ast/pull/1036) | `7cd6043c7089fcc592e130eba6d0eaf5a6fbfcb5` | Exact CharSequence converter List/Map routes and recorded-signature dispatch. |

F2/F2-A were feasibility work. **F2-B is delivered**, and multiple explicit aliases extend the same sole admitted
CollectionFactory family. General CollectionFactory grammar is not delivered. Earlier evidence files retain their
slice-time local/hosted boundaries; this record supersedes their pending-merge wording only. No release, deployment,
renewed Schelm qualification or broad Groovy parity is claimed.

The owning compiler `StructuredInputAdmissionPlan` records field/role, converter and literal-alternative admission.
Runtime `InputContractResolver` verifies those facts; `StructuredInputSupport` executes the admitted operations.
`JsonSchemaProjection` describes the same input structure without executing Schema code. Documentation follows those
sources and executable specifications rather than hypothetical public method availability.

## Supported examples and executable protection

Use one central example and link from specialist pages when repetition adds no value. YAML parsing, duplicate-key
rejection and preservation of required Map order belong to the caller. Struct has no loader or implicit schema validator.
[Managed Jackson](../user/Jackson-Integration.md) remains an independent mapper-owned import path; generated Struct
schemas describe neither that path nor completed-Model export.

All test names below resolve under
[`klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/`](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/).
These are existing documentary methods with `@Tag('documentary')`, driving `@Issue`, and `@See` user-page links.

| Admitted family | Groovy/Struct/YAML example | Documentary method(s) |
| --- | --- | --- |
| Generated Factory roots and active Builder input | [Factory roots](../user/Structured-Input.md#factory-roots), [Builder application](../user/Structured-Input.md#apply-to-an-active-builder) | `StructuredYamlDocumentaryTest#'caller parsed YAML creates keyed roots and configures an active Builder body'`; `StructuredFactoryTest#'active Builders apply structured bodies without completing construction'`. |
| Nested child and outer-keyed concrete Map | [Construction keys](../user/Structured-Input.md#construction-keys) | `StructuredYamlDocumentaryTest#'caller parsed YAML supplies direct child identity and literal Map entry identity'`. |
| One typed Map operation payload | [Endpoint](../user/Structured-Input.md#read-a-concrete-root) | `StructuredYamlDocumentaryTest#'caller parsed YAML supplies one nested Map payload to an authored operation'`. |
| Ordered operations, first failure, zero-argument `{}` command | [Order](../user/Structured-Input.md#order-and-failures), [commands](../user/Structured-Input.md#zero-argument-commands) | `StructuredInputRuntimeTest#'supplied operation order configures the Builder until the first invalid member'`; `StructuredCommandTest#'an empty mapping invokes the domain reset command once'`. |
| Keyless Struct Template recipe | [Templates](../user/Templates.md#structured-input-templates) | `StructuredYamlDocumentaryTest#'caller parsed keyless YAML defines a reusable Template recipe'`; `StructuredInputTemplateDocumentaryTest#'parsed keyless bodies define ordinary reusable Template recipes'`. |
| Non-DSL scalar String/CharSequence converters | [Converters](../user/Converters.md#structured-string-input) | `StructuredConverterOriginTest#'a field converter accepts the same duration String through ordinary and structured construction'`; `StructuredCharSequenceTest#'a projected Duration parser accepts a String through its exact CharSequence signature'`. |
| Exact String List/Map converters | [Lists](../user/Structured-Input.md#converter-backed-list-elements), [Maps](../user/Structured-Input.md#converter-backed-map-values) | `StructuredListConverterTest#'converts duration List elements independently in supplied order'`; `StructuredMapConverterTest#'converts duration Map values independently under literal String keys'`. |
| Exact String Set converters | [Sets](../user/Structured-Input.md#converter-backed-set-elements) | `StructuredSetConverterTest#'converts every supplied tag and retains ordinary Set equality semantics'` (parses inline YAML). |
| Exact CharSequence List/Map converters | [CharSequence containers](../user/Structured-Input.md#charsequence-container-converters) | `StructuredContainerCharSequenceTest#'natural duration strings populate Lists and literal keyed Maps'` (parses inline YAML and compares ordinary Groovy). |
| Concrete unkeyed/keyed owned List/Set/SortedSet/NavigableSet bodies | [Owned sequences](../user/Structured-Input.md#owned-child-sequences), [keyed items](../user/Structured-Input.md#keyed-child-sequences) | `StructuredYamlDocumentaryTest#'caller parsed YAML preserves owned sequence order and duplicate child bodies'`; `StructuredKeyedSequenceTest#'creates keyed routes from structured sequence items'`. |
| Native scalar/enum derived-key Map sequence | [Derived-key Maps](../user/Structured-Input.md#derived-key-map-values) | `StructuredDerivedMapCharacterizationTest#'derives Map keys from structured values through the ordinary Builder adder'`. |
| Sole literal CollectionFactory family, single/multiple aliases | [Literal alternatives](../user/Structured-Input.md#literal-collection-alternative), linked from [Alternatives Syntax](../user/Alternatives-Syntax.md#structured-input) | `StructuredLiteralAlternativeTest#'configures a concrete element through its literal collection alias'`; `StructuredMultipleLiteralAlternativeTest#'multiple explicit literal groups preserve supplied group and keyed entry order'`. |
| Concrete unkeyed virtual Builder scope | [Virtual scopes](../user/Structured-Input.md#concrete-virtual-builder-scopes) | `StructuredVirtualScopeDocumentaryTest#'a repository mapping configures its Builder before the virtual field consumes it'`. |

[JSON Schema](../user/Json-Schema.md) already documents the corresponding body/key positions, structural String
items/values, empty command objects, virtual scopes, derived-key arrays and closed literal alias groups. It promises
no converter success, collection uniqueness, completed order or user-code execution during generation.
[Migration](../user/Migration.md) already links the delivered API and grammar extensions; this pass creates no migration
or new user page, so no sidebar or migration navigation change is needed.
No uncovered language guarantee was found requiring a new test. This pass edits prose only; it adds no executable fixture.

## Non-admitted capability dispositions

“Defer” retains prior scope decisions; effort is a qualitative assessment of the missing contract, not an estimate or
implementation authorization. “Reject” below concerns the named representation under the accepted language constraints,
not every possible future domain-specific feature. No additional 4.2 implementation is proposed by this inventory.

| Ordinary Groovy form → unavailable Struct/YAML form | Natural representation? / semantic work | Disposition, workaround and owner |
| --- | --- | --- |
| `child 'supplied'` via a projected owned-child producer → `child: supplied` | Yes, concise; needs exact compiler-owned producer selection/completeness, lifecycle, replacement/reuse and schema proof. F1 proves only its bounded fixture. | **Defer post-4.2**, explicit maintainer decision in [#1033](https://github.com/klum-dsl/klum-ast/issues/1033). Use the admitted nested child body or eligible authored operation. KlumAST maintainer owns re-entry on adopter need. |
| Other CollectionFactory/subtype creators, automatic `shortName`/`stripSuffix`, List/Set alternatives, concrete-base plus aliases → invented subtype wrappers | A mapping may look natural, but identity, eligible families, naming and attachment need separate decisions. Cost exceeds an example-only change. | **Defer**, #952/ADR 0032. Use Groovy, concrete admitted fields, or the one explicit abstract keyed Map family. Maintainer owns each separately admitted family. |
| Converter/owned-value `@Field.keyMapping` adders → derived-key value sequence | Yes; native scalar/enum sequence is delivered, but converter and ownership/key contracts need independent proof (K2/K3/K4). | **Defer**, retained #1028 boundary under #952. Use ordinary Groovy or eligible one-payload domain operation; explicit outer-keyed owned Maps work where the Schema has no keyMapping. KlumAST maintainer. |
| CharSequence-only Set adder or specialized converter container → String sequence | Yes; exact signature hooks and collection semantics need qualification per declaration, not blanket recursion. | **Defer**, SET-SEQ/CHARSEQ-COL exclusions. Use an existing eligible exact String Set route, eligible wrapper, or Groovy. KlumAST maintainer under #952. |
| Generic/abstract/specialized/aggregation owned sequence creators → child-body array | Often natural; target selection, identity, ownership and destination semantics are unproven outside ordinary concrete nongeneric owned families. | **Defer**, ADR 0032/#952. Use ordinary Groovy or an admitted concrete relationship. KlumAST maintainer. |
| `operation(a, b)`, expanded defaults/varargs or multi-argument converter → argument array/map spreading | Several plausible forms, no selected one; needs arity, overload, diagnostics and schema design. | **Defer**, #952. Expose one eligible typed Map/List/String operation or use Groovy. Schema author owns workaround; maintainer owns grammar admission. |
| `@NamedParams`/`@NamedParam` Map operation → closed named-parameter object with required/type facts | Yes; L2 needs authoritative declared keys, actual required flags and inherited/binary safety. Ordinary typed Map input does not imply these guarantees. | **Defer**, accepted L2/#952. Use an independently admitted typed Map payload, eligible wrapper, or Groovy. KlumAST maintainer. |
| Repeated/interleaved `route(...)` or alias calls → duplicate mapping members or generic call-event array | Duplicate members cannot survive a Map. A new event language would need independent ordering/repetition semantics. | **Reject duplicate-member representation**; **defer new event grammar**, #952. Use admitted population sequences, a domain List operation, or Groovy. Caller parser rejects duplicates; maintainer owns language changes. |
| Clear/replace/reset methods or nullable setter → `field: []`, `field: {}`, `field: null` as implicit mutation directives | Empty population is already zero calls; null is rejected. Reinterpreting these changes existing semantics. | **Reject these implicit directives** under ADR 0032. Use an eligible authored command (`clearRoutes: {}`) whose body owns clearing, or Groovy. Schema author owns operation; new general policy belongs to #952. |
| `child(Subtype, ...)`, caller Factory token → class-name selector or arbitrary `$ref` | Could be written as data, but violates closed compiler-owned admission; no runtime subtype scan/registry is accepted. | **Reject generic token/selector representation** under #952 constraints. Use explicit admitted aliases, concrete fields or Groovy. Schema author; maintainer for a new bounded family. |
| `target existingModel`, LINK/reference assignment → object token or reference identifier | No generic identity/lookup policy; arbitrary caller objects are outside the data language. | **Defer domain reference grammar; reject arbitrary object payloads** under ADR 0032. Caller establishes context before required callbacks; ordinary Schema linking phases or Groovy provide links. Schema/consumer owner. |
| Arbitrary closure/SAM delegation, keyed/custom virtual scopes, query/result chaining, Cluster grouping → mapping/query directives | Some mapping scopes look natural, but only the concrete unkeyed virtual family has fixed entry/sink proof. Other scopes/results need execution and lifecycle design. | **Defer**, #952/ADR 0032. Address admitted fields directly, use an eligible operation or Groovy. Fixed Cluster key authority remains supported. Schema author; maintainer owns admission. |
| Template selection, `copyFrom`, `applyLater`, layered inputs → YAML recipe selectors, donor/precedence/scheduling directives | Possible shapes, but require explicit composition policy and identity/lifecycle ownership. A keyless Struct Template only defines a recipe. | **Defer**, separate composition track in ADR 0032 (4.3-oriented, not a release commitment). Caller selects Template scopes/donors and applies bodies; Groovy remains available. Composition maintainer/consumer. |
| Calling helpers, queries or lifecycle callbacks by name → input members | No: public availability is not admission and phase control is framework-owned. | **Reject** as generic Struct operations under ADR 0032. Use eligible Schema-authored Builder operations; normal lifecycle still runs. Schema author/framework. |

There is no claim that every Groovy convenience needs a YAML counterpart. Defaults, phases, validation, inheritance,
linking and collection equality/comparators still operate through ordinary construction without extra input keywords.
Parser/file/URL convenience, parameter validation (#971), broader constraint projection and export round trips are
separate concerns; none is silently implemented or required by this documentation pass.

## Closure and delivery boundary

After this documentation PR merges, #1015 is a genuine closure candidate: supported example families and executable
protection are linked, F2-B is correctly delivered, and every inventoried gap has a retained disposition and viable
workaround/owner. The exact remaining acceptance action is the **maintainer/Hive final implement/defer/reject decision
round confirming this inventory**, as requested by #1015; publication/merge and that confirmation are not performed by
this record. No missing production implementation is proposed as a #1015 blocker. If the maintainer selects a new feature,
it needs its own bounded assignment rather than extending this prose-only PR.

Tracker impact: `Related: #1015`; no issue closure, labels, milestones, curation or release-gate mutation.
Hive owns final acceptance, delivery and archive reconciliation. Validation and both local review results are recorded in
the PR/handoff against the final head; historical feature test results are not claimed as rerun here.
