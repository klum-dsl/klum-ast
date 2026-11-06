# SET-SEQ — exact String converter-backed Set elements

Refreshed `origin/master` base: `8f789abbd4cf49a07414b89931f8fbfcbe5b614b` (2026-11-06).
Branch: `codex/952-set-seq`. Authority: the accepted SET-SEQ assignment, #952, ADR 0032 and the R1
characterization at local commit `b2923977354bd9f1d1ce0e194e3a42f5a3fc08e0`.
Tracker relationship: **Related: #952**. Curation impact: **feature pull-request update**: the #1015 inventory's
Set-converter row records the bounded local implementation; CharSequence container routes remain deferred.
No issue closure, label/milestone, release qualification or adopter qualification change is claimed.

## Implementation and compatibility

`StructuredInputConverter` recognizes exact declared List/Set converter sequences. One compiler-selected generated
public exact String adder retains the existing Converter/Selection facts and explicit association count; no new metadata
shape, protocol version, public authoring API or converter discovery is added. `ConverterBuilder` uses the existing
direct String collection hook for Set, as for List. `StructuredInputAdmissionPlan` and `InputContractResolver` normalize
to the existing List-of-String input payload. Schema projection consumes that same validated payload without user code.
The interpreter and ordinary population engine are unchanged.

Every raw String converts in supplied order, followed by ordinary Set insertion. Defaults, equality/comparators,
first-equal retention, final iteration order, partial failures, Template retained values and lifecycle remain native.
Empty input makes zero calls, preserving defaults without replacement. JSON Schema emits String arrays without
uniqueness/cardinality/comparator/final-order/replacement assertions. Matched compiler/runtime/plugin and Schema
recompilation are required; generated callable signatures and module descriptors are unchanged.

Exact declared SortedSet/NavigableSet/EnumSet, custom collection grammar, owned DSL converter values, CharSequence
container routes, named-map/CollectionFactory work, generic coercion and constraint policy are outside this slice.
Existing native/owned grammars remain authoritative for their independently supported shapes.

## Acceptance map

- `StructuredSetConverterTest`: caller-parsed YAML documentary example, ordinary Groovy parity; equality/default
  collisions, distinct raw Strings converting equal, every-conversion order/identity, backing comparator order;
  converter/insertion failure with original cause and indexed source, earlier effects and skipped later work;
  empty/default/replacement-setter and input-Set traps; Template definition versus recipient replay;
  strict String-only item grammar, competing/excluded/colliding routes and unsupported shapes;
  same-compilation/adapter/constructor/bounded-generic origins, binary inheritance and descendant shadow rejection;
  source-free positive/no-initialization controls, missing/corrupt descriptor/association/count/type metadata.
- `StructuredListConverterTest` moves the formerly rejected Set case to a still-excluded SortedSet declaration.
- `CompiledConverterJsonSchemaTest`: sources/classes removed after G3/G4/G5 JAR assembly, inherited/local/closure/
  adapter/generic Set routes, no-execution traps and no uniqueness/cardinality assertions.
- `validate_converter_schema.py`: independent Draft 2020-12 validation of actual emitted Set arrays, accepting repeated
  strings and rejecting non-String elements/containers, while retaining scalar C1 and List/Map controls.
- `JpmsPackageBoundaryTest`: direct Set collection-hook bytecode linkage plus packaged Java/static Groovy invocation;
  the existing fixture exercises classpath G3/G4/G5 and named modules G4/G5.

The documentary method `converts every supplied tag and retains ordinary Set equality semantics` carries `@Issue('952')`,
`@Tag('documentary')` and `@See` for [Set elements](../user/Structured-Input.md#converter-backed-set-elements).
Structured Input, Converters, JSON Schema, Migration, CHANGES and the accepted ADR/plan/inventory are synchronized.
No page was added/renamed, so sidebar navigation needs no change.

## Final local qualification

Production code revision: `095be6f376629f44cfed4dc819a4f314048b6b54`. Final executable fixture revision:
`478e129c72a4b69f593fd4157b25ca355db8ac46`. Subsequent evidence-only changes alter no executable inputs.
Java 17 focused Groovy 3 Set/List/Map regressions first passed 157 cases; adding the retained scalar-origin suite
passed **184 cases, zero failures/errors/skips**. All **53 Set cases pass in each of Groovy 3/4/5**, without skips.

The complete compiler suites pass 3,247 / 3,249 / 3,249 cases on Groovy 3/4/5. Root validation was completed across
successive runs, reusing unchanged successful suites rather than rerunning them for test-fixture-only corrections:

| Scope | Cases | Remaining failures / errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 compiler/runtime/Bean Validation/Jackson | 3,595 | 0 / 0 | 17 |
| Groovy 4 same modules | 3,595 | 0 / 0 | 16 |
| Groovy 5 same modules | 3,595 | 0 / 0 | 16 |
| Plugin baseline (full suite plus corrected class) | 669 | 0 / 0 | 0 |
| Total | 11,454 | 0 / 0 | 49 |

The first root attempt found a stale scalar-origin negative control still excluding exact Sets; it now uses the
still-excluded SortedSet declaration. The next full root run passed all compiler lanes and 661/669 plugin cases.
Its eight converter/schema cases failed because the new fixture's `setLocal` element adder collided with the existing
`local` JavaBean setter. A standalone compile exposed the exact MethodBuilderException; Groovy's warning formatter had
masked it with a null-context NPE. Fixture fields now use unambiguous names, and the shared inherited-property inventory
includes `inheritedSet`. No production repair or compatibility expansion was needed. The corrected entire packaged
converter/schema class passes **8/8** cases across G3/G4/G5, including source-free generation, exact output, commands and
binary controls. Other plugin cases have unchanged inputs and retain their passing full-suite evidence.
The root continuation passes the remaining checks/modules with the already completed compiler suites and plugin tests
explicitly excluded. This is aggregate local evidence, not a claim of a single uninterrupted green root invocation.
Binary Java/static Groovy consumers, G4/G5 named modules, direct generated hook bytecode, all module lanes, license,
static/plugin validation and lane isolation are included. No new callable signature or module descriptor is introduced.

Exactly two independent read-only local children reviewed the implementation: Standards and Specification. Standards
found a copied issue annotation and redundant documentary markers; both were corrected and independently reverified.
Specification found zero actionable findings. Both children also reviewed the final packaged-fixture corrections and
report **zero unresolved findings**. Neither edited files, ran heavy tests or mutated remote state. Their scope was this
local branch against the recorded base; no other subworkers were used.

The independent actual-output oracle passes **28 JDK + 270 ECMA/Draft C1 + 270 structural String + 198 List +
207 Map + 213 Set grammar controls**. Duplicate Set inputs are accepted; malformed containers/items and the
CharSequence-only route reject. All three converter schemas are byte-identical, SHA-256
`fc8f31445a1dd2a227d7a0f402e98c240753f742ed38146731c3ee052876f93a`.

Documentation rendering and the real rendered-site crawl pass on clean immutable revision `478e129c72a4b69f593fd4157b25ca355db8ac46`.
The first renderer invocation correctly refused uncommitted fixture changes; after committing them, rendering passed
in 28 seconds with a fresh 2 GiB daemon. Changed Markdown fences, 284 relative links, new-test issue/documentary
traceability, suppression checks and `git diff --check` pass. No page/sidebar changes are needed.

Commands (Java 17 via `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home`):

```shell
./gradlew :klum-ast:test --tests '*StructuredSetConverterTest' --tests '*StructuredListConverterTest' --tests '*StructuredMapConverterTest' --tests '*StructuredConverterOriginTest' --no-parallel --max-workers=2 --console=plain
./gradlew check --no-parallel --max-workers=2 --console=plain
./gradlew :klum-ast-gradle-plugin:test --tests '*CompiledConverterJsonSchemaTest' --no-parallel --max-workers=2 --console=plain
./gradlew check -x :klum-ast:test -x :klum-ast:groovy4Tests -x :klum-ast:groovy5Tests -x :klum-ast-gradle-plugin:test --no-parallel --max-workers=2 --console=plain
./gradlew renderLocalDocumentation --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m' --no-parallel --max-workers=2 --console=plain
PYTHONPATH=/private/tmp/l1a-jsonschema python3 klum-ast-gradle-plugin/src/test/resources/validate_converter_schema.py klum-ast-gradle-plugin/build/schema-validation /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin/java
git diff --check
```

History review retains one implementation step and additive traceability, regression and packaged-fixture corrections.
Reviewed commits were preserved; no rewrite was needed. The final evidence-only commit affects no tested or rendered
user-facing input. Sonar-focused local hygiene passes; no local upload occurred. Hosted final-head CI/SonarCloud remains
final qualification. GitHub CLI repository delivery capability: **authorized**; no credentials/scopes are recorded.

## Delivery boundary

Local implementation is not remote delivery. Draft-publication prerequisites are met: applicable local checks, both
review axes, synchronized documentation and commit-history review. The change is localized and follows the existing
List converter pattern without an unresolved maintainer decision. No merge, issue closure or self-archive. Hive owns final reconciliation.
