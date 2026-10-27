# #959 emitted Pattern qualification result

Date: 2026-10-27. Source checkpoint: `ef7d13d881b1ce63fcbd0061a7c375e450fb6f22`.
Run after successful full root qualification with the [independent oracle](qualify_emitted.py).
The oracle consumes the emitted G3/G4/G5 schema documents and uses independent hand-authored expected languages.

```json
{
  "producer_schemas": 3,
  "accepted_sources": 14,
  "string_rows": 27211,
  "java_p2_and_malformed_rows": 66,
  "ecma_checks": 54422,
  "draft202012_string_checks": 27211,
  "neutrality_and_admission_checks": 75,
  "naive_trailing_lf_leak_reproduced_and_guard_closed": true,
  "python": "3.9.6",
  "jsonschema": "4.23.0",
  "node": "v26.12.0",
  "java": "openjdk version \"17.0.3\" 2022-04-19 LTS"
}
```

The three producer schema documents compare equal and pass Draft 2020-12 metaschema validation. Full-document checks
also pass for omission/null/type admission, inherited literal names, owned child placement, exclusion and final LF.
The bare-not nonstring rejection and naive final-LF leak controls reproduce their expected failures; the typed guard
closes the widening. Java malformed-source controls remain errors, while their generated properties retain valid
runtime-description-only schema output. No runtime validator/provider or Schema code is executed by generation.

The retained P2 Java corpus has 64 rows; two additional malformed expressions make the 66 oracle controls above.
This external result is local qualification, not PR CI or SonarCloud evidence. Remote delivery remains gated as recorded
in the [implementation handoff](../../issue-959-pattern-projection.md#documentary-and-delivery-record).
