# #856 D5: copied composition alias decision boundary

Date: 2026-10-08

Status: Implementation blocked at the explicit D5 semantic-conflict gate; no production change.

Authority: [D5 maintainer decision](https://github.com/klum-dsl/klum-ast/issues/856#issuecomment-6066757844),
[ADR 0027](../adr/0027-owning-relationship-metadata.md), and the D5 assignment. The subsequent Hive instruction
limits completion to preserving this characterization, Groovy 4/5 confirmation and final evidence; no repair or publication.

Base: current master `276c749c328d28f1d7d9e6432b6d7655133890b6`, after merged RM-1/#861, RM-2/#862 and RM-3/#863.
Branch: `codex/issue-856-d5-recipient-declarations`.
Worktree: `/Users/stephan/.codex/worktrees/db1a/klum-ast`.

## Concrete counterexample

The generated public `copyFrom` operation currently accepts this graph without rejection:

```groovy
@DSL class Node { String value }
@DSL class Recipient {
    List<Node> first
    @Field(keyMapping = { it.value }) Map<String, Node> second
}

def sharedRecipe = [value: 'shared']
def result = Recipient.Create.With {
    copyFrom([first: [sharedRecipe], second: [shared: sharedRecipe]])
}
assert result.first[0].value == 'shared'
assert result.first[0].is(result.second.shared)
assert KlumObjectSupport.of(result.first[0]).structure.owningRelationship.empty
assert KlumObjectSupport.of(result.second.shared).structure.owningRelationship.empty
```

Executable observation: `CopyCompositionCharacterizationTest#'one Map recipe currently materializes as the same object in two composition fields without a declaration'`.
The class is explicitly current-master characterization for a decision gate, not the desired ownership contract.
It carries `@Issue('856')` and introduces no ignored or pending test.

| Position | Source identity | Result identity | Relationship semantics | Observed declaration |
| --- | --- | --- | --- | --- |
| Donor `first[0]` / `second.shared` | One shared Map recipe | Not a completed Node | Value-only copy input | Not applicable |
| Recipient `first[0]` | Shared recipe | Fresh materialized Node R | Ordinary composition List | Empty |
| Recipient `second.shared` | Same shared recipe | Exact same Node R | Ordinary composition Map | Empty |

Both recipient fields are ordinary composition, with no LINK/OPTIONAL_LINK semantics, Owner backreference or explicit
claim supplying an authoritative owning declaration. One source recipe does **not** produce independent children here:
the two resulting positions share exact identity. Collection REPLACE and Map FULL_REPLACE are the default modes exercised;
both recipients start empty. The list index and Map key do not supply declaration identity.

## Source audit and conflict

`CopyHandler.copyToFrom` creates one IdentityHashMap per copy. `rehydrateDslRecipe` returns the already rehydrated Builder
for the second occurrence. `addCollectionValues` and `addMapValues` insert that Builder directly into their containers,
bypassing `InternalKlumBuilder.normalizeRelationshipValue` / `claimComposition`. Ordinary composition traversal accepts
these entries without requiring a recorded claim; materialization yields the same Node at both positions. No path or
traversal ordering establishes an authoritative owner.

This is the explicit stopping condition: existing accepted copy behavior permits one resulting object in distinct
composition fields without an unambiguous authoritative owner. Adding rejection at adoption would change currently
accepted alias behavior. Choosing either field, cloning the result, or converting an edge to LINK contradicts the approved
D5 restrictions. No repair is attempted and no maintainer choice is inferred.

The required broader characterization matrix remains incomplete: completed Model/Template and same-session Builder donors,
other overwrite modes, repeated within-field copies, fresh/already-claimed Builder inputs, LINK/OPTIONAL_LINK and mixed
owned/aggregated copies have not been newly executed here. Earlier RM-0/RM-2 evidence is background, not fresh D5 validation.
The stop gate was reached by the first minimal current-master probe. Consumer-container annotation selection, implementation
TDD, regressions, full check, two-axis implementation reviews and final-head CI/Sonar remain contingent on a new decision.

## Validation and handoff

Focused characterization: **1 case per G3/G4/G5 lane, zero failures/errors/skips**. The license and lane-isolation tasks also pass.
Lane isolation requires test-task dependencies; its invocation ran the full G3 AST suite because that task has no filter
in the second command: **1562 tests, 15 existing skips, zero failures/errors**. This incidental run is not evidence of
full repository `check`, which was not requested at the stop boundary. Final evidence links, fences and whitespace pass;
the only post-run test edit removes trailing whitespace from a Schema string delimiter.

Commands use Java 17.0.3, Groovy 3.0.25 / 4.0.32 / 5.0.6 and the repository's Gradle 8.14.4 wrapper:

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew \
  --no-daemon --max-workers=1 :klum-ast:test \
  --tests '*CopyCompositionCharacterizationTest' --console=plain
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew \
  --no-daemon --max-workers=1 \
  :klum-ast:groovy4Tests --tests '*CopyCompositionCharacterizationTest' \
  :klum-ast:groovy5Tests --tests '*CopyCompositionCharacterizationTest' \
  :klum-ast:licenseTest :klum-ast:verifyTestLaneIsolation --console=plain
```

No production, generated/public contract, serialization, lifecycle, annotation, module descriptor, user documentation,
changelog, issue state or release-curation change is made. No push or PR exists for this slice. Issue #856 stays open.
Hive received the concrete conflict with explicit human handoff authorization and owns the next maintainer decision and
later delivery/archive reconciliation. The task remains `(blocked)`; its execution result is a preserved counterexample,
not completed D5 implementation or an archive-safe delivery.
