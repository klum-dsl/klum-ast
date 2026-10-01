# Tips and Tricks

These patterns help Schema Developers give Model Writers concise, domain-meaningful construction methods using existing
KlumAST capabilities. The Builder annotations in this example require KlumAST 4.1; see
[Advanced Techniques](Advanced-Techniques.md) for their contracts.

## Name a Builder Helper for a Seeded Relationship

Sometimes a Model Writer wants to start a child from an existing room configuration, then specialize it. A domain
converter may already accept that same input type with a different meaning. An automatic overload such as
`bedroom(seed) { ... }` would compete with that converter or a Schema-defined Builder method.

Choose an explicit name such as `bedroomFrom` and wrap the existing named-map `copyFrom` creator. The Schema Developer
chooses the name; it is an ordinary method, not a framework naming convention. The normal `bedroom(seed)` converter
keeps its meaning.

(See: `BuilderRelationshipHelpersDocumentaryTest#'names a seeded relationship helper without replacing a domain converter'`.)

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.DelegatesToBuilder
import com.blackbuild.klum.ast.Owner
import groovy.transform.CompileStatic

// Schema
@CompileStatic
@DSL class House {
    String name
    Room baseline
    Bedroom bedroom

    @Builder.Method
    void bedroomFrom(@Builder.Input Room seed,
                     @DelegatesToBuilder(Bedroom) Closure body) {
        bedroom(copyFrom: seed, body)
    }
}

@DSL class Room {
    String name
    boolean heated
    @Owner House house
}

@DSL class Bedroom extends Room {
    int beds

    // This converter intentionally has different semantics from copying a seed.
    static Bedroom fromRoom(@Builder.Input Room source) {
        Bedroom.Create.With(name: "converted:$source.name", heated: false)
    }
}

// Model
def house = House.Create.With {
    name 'Home'
    def seed = baseline {
        name 'Heated room'
        heated true
    }
    bedroomFrom(seed) {
        name 'Main bedroom'
        beds 2
    }
}

def convertedHouse = House.Create.With {
    name 'Converted home'
    def seed = baseline {
        name 'Heated room'
        heated true
    }
    bedroom seed
}

// Assertions
assert house.bedroom.heated
assert house.bedroom.name == 'Main bedroom'
assert house.bedroom.beds == 2
assert !house.bedroom.is(house.baseline)
assert house.bedroom.house.is(house)
assert house.baseline.name == 'Heated room'
assert convertedHouse.bedroom.name == 'converted:Heated room'
assert !convertedHouse.bedroom.heated
```

`@Builder.Method` moves `bedroomFrom` onto the generated House Builder and keeps it off the completed House Model.
`@Builder.Input` changes the selected `Room` parameter to its public Builder type in that construction method. The
same input annotation lets the recognized `fromRoom` converter read the live Room Builder during relationship
construction; a direct completed-Model call still accepts a completed Room. Unannotated Model parameters retain
completed-Model semantics. See [explicit inputs and results](Advanced-Techniques.md#flowing-builders-through-explicit-inputs-and-results).

`@DelegatesToBuilder(Bedroom)` supplies IDE and static-checker hints for the forwarded closure. The helper returns
`void` and attaches the child through the generated relationship creator, so it needs no `@Builder.Result`.

The helper creates the relationship's declared `Bedroom` type, applies the donor through the existing `copyFrom` protocol
before running `body`, and attaches the fresh child as owned composition. The donor remains a separate object. Normal
Owner assignment, lifecycle, and graph-wide Materialization still apply; the documentary test also checks the Owner seen during `@PostTree` and one
lifecycle pass per child. This is copying, not a `LINK` relationship or adoption of the source Builder.

The shown helper accepts a live ancestor-typed Builder in the same active Construction session. A sealed Builder or a
Builder from another session is not a valid live copy source. A completed Model or marked Template needs a separately
named helper with an ordinary, unannotated Model parameter, or the existing `bedroom(copyFrom: donor) { ... }` form.
The source category determines the contribution:

- An ordinary completed Model contributes values only.
- A marked Template contributes values plus Template recipe replay.
- An eligible live same-session Builder contributes current values plus the pending-action snapshot allowed by the
  existing copy-source protocol.

Copying follows the configured overwrite strategies; it is not an unconditional clone. See
[Copy Strategies](Copy-Strategies.md#copy-source-protocol) and [Templates](Templates.md#copyfrom).

The relationship fixes the target type: this helper does not infer a runtime subtype from the donor. It adds no automatic
seeded overloads and does not implement [issue #342](https://github.com/klum-dsl/klum-ast/issues/342). Explicit helpers are
useful precisely because the Schema author controls the name, input contract, target relationship, and copy policy while
[Converters](Converters.md) retain their domain meaning. This is the supported practical route in 4.1. The automatic
`relationship(seed) { ... }` design is preserved in
[ADR 0024](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0024-seeded-relationship-creation.md#current-disposition)
for future reconsideration; SEED-0 through SEED-3 are deferred beyond 4.1 and are neither a release commitment nor a
release gate.
