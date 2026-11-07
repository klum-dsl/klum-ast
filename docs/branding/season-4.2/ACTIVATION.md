# Season 4.2 documentation activation

This integration adopts the controlled artwork merged in PR #1049 at
`67db08f95fe54a072432f0b665846e6f694864c9`. The source/export inventory and
licensing remain in [README.md](README.md). Production assets are byte-identical
copies of the selected full, compact and optical-header exports; no new bitmap or
artwork reconstruction is used.

The homepage and repository README use the full lockup. The renderer discovers
and copies the 4.2 compact counterpart and selects it through 1000 px. The header
uses the optical export via [the 4.2 manifest](../season-4.2-klumast.json).
Navigation retains its independent 760 px breakpoint. The image alternative text
includes the product, release, tagline and composition; surrounding prose and
navigation remain real HTML text outside the illustration.

Older assets, the Season 4.0 manifest and its final approval remain unchanged.
Release workflows use the new manifest only for `4.2.*`. Final 4.2 staging requires
its own exact-manifest owner approval at `season-4.2-final-approval.json`; no such
approval is created here. Activation is not a release, deployment or alias change.

Tracker impact: no issue reference, issue-state change or release-curation update.
Browser and check evidence will be recorded below after local verification.
