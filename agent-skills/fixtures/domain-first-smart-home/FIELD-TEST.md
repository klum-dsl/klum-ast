# Later field test

Use this fixture only as a starting artifact for a later real-project evaluation; it is not a generic smart-home template.

## Starting artifact

Begin with [`field-test/SMART-HOME-BRIEF.md`](field-test/SMART-HOME-BRIEF.md), copy the Gradle structure that fits the real project, and replace its names, invariants, and API-only client with actual domain evidence. Preserve the separate Domain API module only if real Client Developers need that boundary.

## Prompt

> Using the supplied smart-home brief, decide whether the completed model is the canonical domain abstraction and whether consumers need a Domain API separate from the Schema. Explain the decision using the Domain API Developer, Schema Developer, Model Writer, and Client Developer roles. Separate fixed floorplan facts from changing device configuration. Create one registered Model script, one validation assertion, and one API-only client test. Record one friction point or unanswered question without proposing a new portable skill or changing Layer 3 policy.

Capture the decision, build result, and friction point in the adopting project's notes. Turn a concrete KlumAST product gap into a focused follow-up issue; do not broaden this onboarding baseline during the field test.
