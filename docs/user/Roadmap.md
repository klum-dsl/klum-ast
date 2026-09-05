# 4.0 release

KlumAST 4.0.0 is released. Its headline change is Builder-first construction: factories configure mutable Builders,
materialize a completed structurally immutable DSL Object graph, and then validate it. Read
[Builder First Migration](Builder-First-Migration.md) before moving an existing Schema, client, or extension to 4.0.

The immutable release record is tag `v4.0.0` (2026-08-20). The [Changelog](Changelog.md) is the authoritative
inventory of delivered user-visible behavior; current and future work is tracked in GitHub rather than on this
historical release page.

The 4.0 documentation set includes version-matched Gradle onboarding, domain-first and target-contract journeys,
completed-object support, and asymmetric Jackson integration.

## Historical roadmap

The 2.x and 3.0 roadmap notes were planning material for releases that are now historical. Their migration guidance is
preserved in [Migration](Migration.md); this page intentionally does not present those earlier plans as current commitments.



