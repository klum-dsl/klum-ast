---
name: feature-advisor
description: Advise on applicable KlumAST features in an existing Schema or configured model. Use when an adopter wants a ranked, evidence-based KlumAST improvement review, an explanation of supported 4.x features that fit their model, or selected, validated model improvements.
---

# Advise on KlumAST features

This is a KlumAST feature and maintainer-education review, not a generic style review or a feature catalogue. Use version-matched KlumAST documentation as the authority.

1. Inspect the Schema, configured-model inputs, Gradle/KlumAST/Groovy versions, executable tests, and the explicit project shape. Identify whether the concern is Schema design, configuration, validation, lifecycle, interoperability, or IDE support.
2. Consider only features supported by the adopted KlumAST version. For each candidate, trace it to authoritative guidance and reject speculative, future, or version-incompatible ideas.
3. Report findings in descending benefit. For every finding, state: the supported feature; why this exact Schema or configured model fits it; expected benefit; trade-offs; confidence; migration risk; effort; and a link to the matching guidance. Clearly mark non-recommendations and speculative ideas as such.
4. Keep a review-only request read-only. Do not edit files, run formatting mutations, or silently apply recommendations.
5. When the adopter selects findings to apply, make only those adjustments. Update affected executable tests or user documentation, run the relevant Gradle validation, and report the result and any remaining trade-off.

Useful authoritative starting points include [Basics](https://github.com/klum-dsl/klum-ast/wiki/Basics), [Validation](https://github.com/klum-dsl/klum-ast/wiki/Validation), [Templates](https://github.com/klum-dsl/klum-ast/wiki/Templates), [Jackson Integration](https://github.com/klum-dsl/klum-ast/wiki/Jackson-Integration), [Layer 3](https://github.com/klum-dsl/klum-ast/wiki/Layer3), and [Builder-first migration](https://github.com/klum-dsl/klum-ast/wiki/Builder-First-Migration).
