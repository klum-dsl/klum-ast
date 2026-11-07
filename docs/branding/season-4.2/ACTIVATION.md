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


## Local checks

At implementation revision `8545fa7068d1845a49e7f5762da082b07ddab443`:

- `python3 docs/branding/season-4.2/tools/export_assets.py` passed twice; the
  second run reproduced all controlled exports, production copies and inventory.
  The exporter verifies geometry, exact original model/shadow reuse, internal SVG
  references, and outlined lettering without runtime fonts.
- `./gradlew license verifyVersionedDocumentationRenderer renderLocalDocumentation
  --console=plain` passed with default `4.2.0-tracer` settings, including Javadocs,
  both 4.0/4.2 responsive acceptance fixtures and the complete local site crawl.
- The actual Bash blocks from both documentation workflows passed eight
  version/stage combinations each, using a temporary stub in place of Gradle:
  `4.0.0`, `4.1.0-rc.1`, `4.2.0-rc.1`, `4.2.0` × candidate/final. Manifest and
  final-approval argument selection were checked; no workflow was dispatched.
- A separate local final-stage render with the new approval path was rejected
  because `season-4.2-final-approval.json` is absent, as intended. The existing
  4.0 approval does not authorize 4.2.
- Production hashes match the export inventory and the three source exports
  byte-for-byte. A diff against PR #1049's merge confirms no change to the 4.0
  asset/source trees, favicon, manifest or final approval. `git diff --check` passed.
- Independent Standards review: zero findings. Specification review found the
  stale local `4.0.0-tracer` default; follow-up commit `8545fa706` corrected it,
  and re-review reported zero remaining implementation findings.

An initial combined check passed an explicit 4.2 version to the acceptance task,
which deliberately verifies default settings; it rejected that override. Checks
were rerun with their default settings and passed. A preview attempt against the
then-dirty review fix was also rejected by the immutable-input guard; committing
the fix allowed the normal clean-revision preview. Neither refusal was bypassed.

Groovy 3/4/5 application suites and root `check` were omitted: this change affects
artwork and documentation build/rendering only. Renderer/buildSrc compilation,
license checks, Javadocs and renderer acceptance were exercised. Hosted CI and
SonarCloud are separate qualification of the eventual PR head.

## Actual-site browser evidence

`./gradlew previewLocalDocumentation --console=plain` served the real generated
site at `http://127.0.0.1:59767/klum-ast/4.2.0-tracer/`, under the normal Pages
base path. This was the rendered site, not the source-artwork review fixture.

The in-app browser was reloaded at each width before screenshot/DOM inspection.
[Exact measurements](evidence/viewports.json) record loaded sources, dimensions,
image readiness, navigation mode and horizontal overflow for 320, 390, 430,
759/760/761, 999/1000/1001 and 1440 px viewports. All images loaded; no horizontal
overflow was present. Navigation switches after 760 px; the compact SVG is loaded
through 1000 px and the full SVG from 1001 px. The hero retained its 16:9 ratio
without cropping and stayed separate from the title, status banner and prose.
The optical header loaded from the manifest-owned exact-tree path.

Representative actual-page screenshots:

- [1440 px desktop](evidence/browser-1440.jpg)
- [390 px mobile](evidence/browser-390.jpg)
- [430 px mobile](evidence/browser-430.jpg)

Native Safari responsive-design mode and native Microsoft Edge device emulation
were also exercised at 390, 430, 759/760/761, 999/1000/1001 and 1440 px widths,
with 900 px height. Desktop and mobile captures were visually inspected in both;
full/compact art, header, portal/figure clearance, palette and text separation
were consistent. [Edge viewport observations](evidence/edge-viewports.json)
confirm its navigation boundary and empty console. The precise loaded-source and
overflow assertions above come from the in-app browser; native screenshots and
accessibility inspection do not establish the same DOM measurements. Safari
accessibility snapshots during rapid boundary changes occasionally lagged image
loading, so those transient snapshots are not treated as source-selection proof.

Native screenshot files remain outside Git because they include browser chrome;
[capture hashes and local location](evidence/captures.json) retain their identity.
The committed screenshots contain only the rendered page. In-app browser console
inspection returned no warning/error entries; Edge DevTools reported zero messages.
The full-site crawler passed links, assets, directory navigation and fragments.

At 390 px the mobile disclosure was opened and its Structured Input link followed
successfully to `/Structured-Input/`; that page's optical header also loaded.
The alternate text was present in the browser accessibility tree and includes the
release, tagline and peer composition. Main prose, navigation, title and release
status remain separate HTML text with readable contrast and no image overlap.

Limitations: the approved thin tagline and muted release cue become small on
narrow screens; the decorative logo is not the sole source of documentation or
version information. Its wording is included in the image alternative text.
The selected raised YAML/gold Groovy lettering is preserved exactly. This was
visual/accessibility-tree inspection, not a complete WCAG or screen-reader audit.
The original embedded Groovy raster retains the resolution limit documented in
PR #1049. No external links were exhaustively probed and no published site,
release, tag, deployment or aliases were changed.
