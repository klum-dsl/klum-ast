# 4.0 release scope

KlumAST 4.0 is an unreleased breaking release. Its headline change is Builder-first construction: factories configure
mutable Builders, materialize a completed structurally immutable DSL Object graph, and then validate it. Read
[[Builder First Migration]] before moving existing Schema, client, or extension code to 4.0.

The current 4.0 documentation set also includes version-matched Gradle onboarding, domain-first and target-contract
journeys, completed-object support, and the asymmetric Jackson integration. The release notes in [[Changelog]] are the
authoritative inventory of delivered user-visible behavior.

## Content pending maintainer acceptance

Before the first public RC, the documentation content requires explicit maintainer acceptance under
[#544](https://github.com/klum-dsl/klum-ast/issues/544). Versioned rendering, Pages staging, aliases, and publication
mechanics are owned separately by [#456](https://github.com/klum-dsl/klum-ast/issues/456).

Layer 3 terminology, variants, and representative examples remain under [#454](https://github.com/klum-dsl/klum-ast/issues/454).
The current guidance states only the settled API–Schema–Model pattern and must not be read as a commitment to a broader
Layer 3 contract.

## Historical roadmap

The 2.x and 3.0 roadmap notes were planning material for releases that are now historical. Their migration guidance is
preserved in [[Migration]]; this page intentionally does not present those earlier plans as current commitments.




