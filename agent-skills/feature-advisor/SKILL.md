---
name: feature-advisor
description: Advise on applicable KlumAST features in an existing Schema or configured model. Use when an adopter wants a ranked, evidence-based KlumAST improvement review, an explanation of supported 4.x features that fit their model, or selected, validated model improvements.
---

# Advise on KlumAST features

This is a KlumAST feature and maintainer-education review, not a generic style review or a feature catalogue. Use version-matched KlumAST documentation as the authority.

1. Inspect the Schema, configured-model inputs, Gradle/KlumAST/Groovy versions, executable tests, and the explicit project shape. Identify whether the concern is Schema design, configuration, validation, lifecycle, interoperability, or IDE support.
2. Inspect the recorded KlumAST release and portable-skill source tag or commit. Check the canonical KlumAST release and `agent-skills/` distribution for newer versions when network access is available. Do not modify the build or installed skills during this check.
3. Report an update assessment before feature findings. Classify each KlumAST and skills update as **needed** (the current version is unsupported, incompatible, or affected by a relevant defect), **recommended** (a compatible update materially helps this project), **unnecessary** (the current version remains supported and no relevant benefit is established), or **unknown** (the installed source or current canonical version cannot be verified). State the current and available version/source, compatibility or migration implications, the evidence link, and why the classification applies.
4. Consider only features supported by the adopted KlumAST version. For each candidate, trace it to authoritative guidance and reject speculative, future, or version-incompatible ideas.
5. Report feature findings in descending benefit. For every finding, state: the supported feature; why this exact Schema or configured model fits it; expected benefit; trade-offs; confidence; migration risk; effort; and a link to the matching guidance. Clearly mark non-recommendations and speculative ideas as such.
6. Keep a review-only request read-only. Do not edit files, run formatting mutations, or silently apply recommendations.
7. When the adopter selects findings to apply, make only those adjustments. Update affected executable tests or user documentation, run the relevant Gradle validation, and report the result and any remaining trade-off.

Useful authoritative starting points include [Basics](https://klum-dsl.github.io/klum-ast/4.0/Basics/), [Validation](https://klum-dsl.github.io/klum-ast/4.0/Validation/), [Templates](https://klum-dsl.github.io/klum-ast/4.0/Templates/), [Jackson Integration](https://klum-dsl.github.io/klum-ast/4.0/Jackson-Integration/), [Layer 3](https://klum-dsl.github.io/klum-ast/4.0/Layer3/), and [Builder-first migration](https://klum-dsl.github.io/klum-ast/4.0/Builder-First-Migration/).
