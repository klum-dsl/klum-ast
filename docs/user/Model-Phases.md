# Model phases

When you write a Model, the Schema can fill in missing children, resolve links, supply defaults, and check the result.
A root factory first configures a mutable Builder tree from your input. It runs the phases below before returning
the completed Model. The same Schema rules apply to Groovy configuration and admitted [structured input](Structured-Input.md).

## Complete lifecycle example

The Deployment Schema sets a fallback environment in `@AutoCreate`. The component reads the environment from its Owner
in `@AutoLink`, after the Owner phase has established the backlink. The Model Writer only has to configure the component.

The [Deployment documentary test](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/ModelPhasesDocumentaryTest.groovy)
includes the full Schema and asserts callback order.

(See: `ModelPhasesDocumentaryTest#'runs a deployment lifecycle on Builders before completing its model'`.)

```groovy
def deployment = Deployment.Create.With {
    component {}
}

assert deployment.component.deployment.is(deployment)
assert deployment.component.environment == 'production'
```

## Creation

One root factory owns one Construction session on the current thread. Use the root's generated Builder methods to add
owned children so they share that session. A separate root factory produces a completed Model, which cannot be adopted
as owned composition. See [Ownership and `@Owner`](Basics.md#ownership-and-owner).

For each Builder, KlumAST applies [Templates](Templates.md), runs `@PostCreate`, applies explicit configuration, and
runs `@PostApply`. These two callbacks are part of creating and configuring each object. The root then runs the
graph-wide phases in the order shown below.

## Lifecycle annotations

Schema Developers can use lifecycle annotations on methods or, where supported, on Closure fields. Lifecycle callbacks
such as `@AutoCreate` and `@PostTree` take no parameters and must be non-private.
Before materialization, mutating methods run on Builders and lifecycle Closures use the Builder as their delegate.
`@Validate` sees the completed Model.

Lifecycle callbacks run whenever the object is visited for their phase. The callback decides whether it needs to do
anything. For example, an annotated `@Default` method can run even when an explicit value is already present.
Declarative default fields follow the eligibility rules for [Default Values](Default-Values.md).

## Phase Details

Read the following phases in order. The numbers are the phase IDs used for explicitly scheduled work and plugin actions.

### ApplyLater (1)

Runs deferred Builder actions scheduled without a phase during ordinary configuration. This lets Model Writers defer
configuration until the explicit inputs are available and before automatic lifecycle work starts.
See [`applyLater` methods](#applylater-methods) for other scheduling options.

### Early Validation (5)

Checks configured Builder values before automatic creation and defaults. This is when deprecated-field checks and
`@Notify` examine the fields as they stand after configuration.

### AutoCreate (10)

Creates missing children declared with `@AutoCreate` and runs the corresponding Schema callbacks. Choose this phase
when other lifecycle work needs the child to exist. For annotation composition, see
[Annotation Propagation](Annotation-Propagation.md#default-only-composition).

### Owner (15)

Establishes `@Owner` and `@Role` values before AutoLink. Declaring or configuring a relationship alone does not
immediately assign the Owner field. See [Ownership and `@Owner`](Basics.md#ownership-and-owner).

### AutoLink (20)

Resolves links to existing values, for example through `@LinkTo`, and runs `@AutoLink` callbacks.
See [Builder-backed providers](Behind-the-Curtain.md#builder-backed-linkto-providers) for provider reads and
[owning Schema declarations](Completed-Object-Support.md#owning-schema-declarations) for metadata available after Owner.

### Default (25)

Supplies Schema defaults. Owner-provided defaults run first, before containing-field and type defaults, direct field
defaults, and `@Default` callbacks. See [Default Values](Default-Values.md) for field eligibility and precedence.

### PostTree (30)

Runs Schema callbacks on the configured Builder tree after linking and defaulting. Use this phase for final construction
work that needs values across the tree.

### Instantiate (40)

Materializes the owned graph as structurally immutable DSL Objects, preserving relationship identity and cycles.
Collection state is snapshotted. From here on, phases receive completed Models.
See [Builder-first materialization](Behind-the-Curtain.md#builder-first-materialization) for mechanics.

### Validation (50)

Collects findings from `@Validate` on completed Models. Builder findings carry forward through materialization.
Keep validation read-only. See [Validation](Validation.md) for defining rules and inspecting findings.

### Verify (80)

Throws if a collected finding reaches the configured fail level or higher. The default fail level is `ERROR`.
A factory returns a completed Model only after verification and completion succeed.

### Completion (100)

Runs final actions on the verified Model. See [Plugin phase actions](Behind-the-Curtain.md#plugin-phase-actions)
for extending the lifecycle.

## Ordering, late children, and existing links

- **A late child joins the current phase.** Containing Builder work runs before descent to its owned children. A child
  added during the containing Builder's AutoLink visit can receive its own AutoLink callback. Earlier phases do not replay.
  AutoCreate has already passed, and the child's Owner field can still be null. Initialize any state the late child needs.
- **Creation comes before mutation.** Where a Schema uses external lifecycle participants, one direct field creator can
  claim a field in a phase. It runs only if the field is unset. Mutators run afterwards if a target exists. Repeated
  mutators on one annotation follow declaration order or the explicit container's array order. Relative order is
  unspecified across distinct annotations, across fields, and when a singular marker is mixed with an explicit container.
  Type mutators run before their own Builder's field work, after the parent's field work.
- **A completed LINK stays sealed.** Supplying a completed Model as a `LINK` target keeps it outside owned traversal.
  When a field mutator encounters the sealed target, its policy defaults to `FAIL` or can explicitly select `SKIP`.
  Both policies act before handler construction for that target. Type mutators do not visit sealed aggregation targets.

See [Lifecycle Participants](Lifecycle-Participants.md) for supported declarations, exact ordering, and handler examples.

## `applyLater` Methods

Pass a Closure to `applyLater` to defer a Builder change. Inside a running phase, a call without a phase runs after
the current phase. During ordinary configuration it runs in [ApplyLater (1)](#applylater-1). An explicit phase must
be below Instantiate (40). Use a [Model phase action](Behind-the-Curtain.md#plugin-phase-actions) for plugin work on the
completed Model.

(See: `ModelPhasesDocumentaryTest#'applies a deferred deployment setting before automatic lifecycle work'`.)

```groovy
def deployment = Deployment.Create.With {
    component {}
    applyLater {
        environment 'staging'
    }
}

assert deployment.component.environment == 'staging'
```

A Template stores deferred work for each fresh recipient Builder. See [`applyLater` and Templates](Templates.md#applylater-and-templates)
for replay and Closure capture rules.

## Error handling

Phase failures can carry the relevant phase and construction path in a `KlumException`. See
[Exception Handling](Exception-Handling.md) for interpreting failures and [Validation](Validation.md) for collected findings.

## Topics moved to the reference

These links point to the current home of each topic.

### [PhaseActions](Behind-the-Curtain.md#plugin-phase-actions)
### [External field participants (LP-1)](Lifecycle-Participants.md#declaring-participants)
### [Reusing a typed consumer contract](Lifecycle-Participants.md#using-public-builder-contracts)
### [Creating the annotated relationship](Lifecycle-Participants.md#creation-and-mutation)
### [Context and qualification boundary](Lifecycle-Participants.md#handler-context-and-access)
### [Participant composition (LP-2)](Lifecycle-Participants.md#creation-and-mutation)
### [Field participants in four phases (LP-3)](Lifecycle-Participants.md#phase-ordering)
### [Sealed participant targets (LP-4)](Lifecycle-Participants.md#sealed-targets)
### [Type mutation (LP-5)](Lifecycle-Participants.md#type-mutation)
### [Annotation Closure members](Lifecycle-Participants.md#annotation-closure-members)
### [Participant validation](Validation.md#participant-validation)
### [Participant diagnostics](Lifecycle-Participants.md#diagnostics)
### [Methods that modify datastructures](#applylater-methods)
