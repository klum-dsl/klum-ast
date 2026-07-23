# Why `*aC` is not enough

`*aC` means “anything as code”: Infrastructure as Code, Configuration as Code, Policy as Code, and similar practices.
The point of this deliberately provocative title is not that those practices fail. They make important promises; the
question is whether a configuration stored in Git has earned the same confidence as ordinary software.

## What `*aC` already does well

When inputs, versions, and the target environment are controlled, the same configuration should produce the same result.
Version control also gives the configuration a reviewable history and a clear change lifecycle. These are real strengths.

Tooling can be strong too: editors can offer completion, schemas can reject malformed documents, and generated or
handwritten documentation can explain available fields. In many document-oriented systems, however, those helpers are
separate, domain-specific artifacts. The consumer, its document schema, and its documentation must still evolve together.
They can make a structure understandable without proving that it represents a useful domain model.

## The testing gap

Many `*aC` workflows make syntax checks and target-environment integration tests straightforward. They do not prevent a
team from writing unit tests, but the model often has no natural unit-test seam: important failures are first discovered
only when a deployment, controller, or remote API receives the configuration.

That leaves a costly feedback loop. A change can be syntactically valid and accepted by a document schema while still
violating a domain rule, selecting incompatible values, or producing the wrong target projection. Integration tests
remain necessary for the real target, but they are usually slower and depend on more external state than a local model
test.

## What KlumAST adds

A KlumAST Schema is executable model knowledge, not just a description of document shape:

- Schema-specific rules can be declared with [[Validation|validation annotations and methods]]. A generated root factory
  materializes the model and runs validation, so every ordinary construction is an opportunity to reject an invalid
  model before it reaches a target.
- A Model Writer can construct representative configurations with `Create.With` and assert the completed model or its
  validation result in an ordinary unit test. These tests run quickly without contacting the target when their assertions
  are model-local.
- The same test suite can run in a pull-request build before a change reaches the main branch. The repository's Gradle
  onboarding starts with exactly this small `Create.With` test; the GitOps examples add target-contract conformance tests
  where a target projection matters.

This is not a claim that KlumAST replaces acceptance or integration testing. It moves the domain checks that belong to a
model into a fast, local layer of the testing pyramid and leaves target behavior to the appropriate outer tests.

## Keep domain knowledge with the Schema

The Schema Developer can keep constraints, defaults, relationship rules, and source documentation near the model they
describe. Generated Builder Javadocs reuse that source documentation; generated IDE mirrors expose the same construction
surface for completion. This separates concerns cleanly: the schema owns domain rules, Model Writers own concrete
configuration, and clients consume completed models through their public contract. See [[Terms]] and [[Javadoc]].

For a target-contract case such as Helm values, the target remains authoritative. A project can use a validated KlumAST
authoring model and test an explicit target projection against representative target values. See
[[Target Contract Modeling]].

## Current standalone-script boundary

The supported onboarding path is Gradle. A project-less standalone-script route is not an established documented user
journey. Do not treat this page as an `@Grab` support promise; the release documentation still needs a maintained example
and an identified owner before any such route can be recommended.
