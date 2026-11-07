# KlumAST 4.2 controlled release artwork

Two equal peers share one portal: an ivory square-mosaic **Struct/YAML figure on
the left**, and the approved **original 4.0 Groovy model on the right**. Their
heads align optically; the original model keeps its walking pose and shoes.
Groovy is moved left so its partly transparent upper figure sits inside the
ivory portal, with clearance from the right edge. This avoids the navy/frame
showing through the approved artwork. The palette follows the existing navy,
ivory, graphite and blue-grey language, with small muted-gold accents.

These controlled assets are activated for current documentation and the README.
The maintainer selected the existing 4.0 favicon unchanged; no replacement favicon
is included. Avatars and final release branding approval remain separate.

## Controlled sources

| Source | Purpose |
| --- | --- |
| `editable/klumast-4.2-art.svg` | Native architecture and YAML paths/optical contours, exact approved embedded Groovy PNG, native shadows |
| `editable/klumast-4.2-documentation-text-master.svg` | Full lockup: KlumAST, selected combined Groovy/YAML tagline, Season 4.2 |
| `editable/klumast-4.2-documentation-compact-text-master.svg` | Compact lockup: KlumAST, scaled combined tagline, 4.2; optical YAML contours |
| `editable/klumast-4.2-header-text-master.svg` | Header wordmark, retaining the Season 4 viewport and relationship |
| `tools/export_assets.py` | Composes masters, creates CoreText outlines, validates approved image reuse/geometry/references, renders PNGs and records hashes |
| `lettering-options/` | Native lettering studies; selected E supplies the full/compact higher-YAML treatment |

Lettering A–D are retained as editable design history only. The lettering exporter
normally regenerates E’s five review outputs; `--preview-all` puts all alternatives
in a fresh temporary directory without changing canonical inventories.

The text masters are composition inputs: the exporter fills their `artwork`
placeholder. Edit source geometry rather than generated exports.

## Approved model reuse and shadows

At the maintainer's request, the attempted native Groovy redraw has been removed.
The image node in the controlled 4.0 documentation master is reused **exactly**:

`docs/branding/season-4/editable/klumast-season-4-documentation-text-master.svg`

The original PNG payload, image position, dimensions and aspect-ratio settings
are unchanged. Its parent group translates it by `(54,0)` into the right-hand
position. There is no resampling, tracing, recolouring or replacement of its feet.
The approved 4.0 shadow path is also reused unchanged and given the same
translation, preserving the original model/shadow contact relationship and
returning the right shadow to its established diagonal. The rejected 19.3° right
shadow projection and its native shoe redraw are absent from the current master.

The YAML shadow retains its revised 34.84° direction. Its leading and rear toe
contacts are `(453,652)` and `(411,644)`. The rear-leg correction keeps the shadow
attached both at the body and at the toe. The exporter checks these native contacts
and verifies exact original model/shadow reuse, including their shared placement.
Both shadows retain the near/far fade and runway clipping.

The portal path `M340 611V167L396 110H584V611Z` and runway path
`M340 611H584L1472 945H669Z` are retained from the 4.0 controlled master. Their
perspective is unchanged by the model replacement or shadow adjustment.

## Exports, small sizes and reproduction

`exports/` contains self-contained SVG lockups and text-free square derivatives.
The approved E lettering now appears in both full and compact controlled masters.
The compact tagline scales from 64 to 70 source units, preserving its raised YAML
relationship, alongside the larger compact wordmark and 4.2 cue.

Retained previews include full artwork at 1680/640 px, compact layouts at 430/320 px,
the header at 990 px, optical header logos at 660/330 px, and the text-free optical
mark at 256/128/64/32 px. `klumast-4.2-header-optical.svg` derives from the same
header source and uses the broader YAML contours for small logo surfaces. SVG lettering is outlined;
the original approved Groovy model is their sole raster component. The optical
variant simplifies YAML into fewer, broader planes and longer contour steps.
YAML uses clean uniform contours at 1.55 source units in the detailed master and
2.25 in the optical treatment, slightly stronger than the preceding 1.15/2.0.
Fine tile seams retain their lighter treatment; no brush-style strokes are added.
The original Groovy model keeps its existing raster resolution; very large output
can reveal that limit. Square crops are illustration previews, not favicon
replacements. The existing `docs/user/img/season-4/klumast-season-4-favicon.svg`
remains the favicon; no new small-icon redraw is included.

The 4.0 reference and 4.2 detail PNGs use aligned crop coordinates for comparing
the exact reused model. `klumast-4.2-foot-detail.svg` retains the full source
composition in a detail viewport. `assets.json` inventories source/export hashes
and records both the approved source-master hash and the embedded PNG hash.

On macOS with system Avenir Next, Swift/CoreText, Python 3 and `rsvg-convert`:

```sh
python3 docs/branding/season-4.2/tools/export_assets.py
PYTHONPYCACHEPREFIX=/tmp/klumast-4.2-pycache python3 docs/branding/season-4.2/tools/export_lettering_options.py
python3 -m http.server 8765 --bind 127.0.0.1
```

The existing [CoreText outline tool](../season-4/tools/outline_svg_text.swift) remains
the lettering engine. Open `/docs/branding/season-4.2/review.html` on the local
server to exercise 390, 430, 759/760/761, 999/1000/1001 and 1440 px iframe viewports.
It uses the existing `max-width: 1000px` picture boundary; the separate 760 px
navigation behavior belongs to the unchanged documentation renderer.

The exporter rejects duplicate IDs, missing internal references, external assets,
runtime lettering/fonts and all raster images **except the exact approved 4.0
model**. An altered image payload or image geometry fails validation. Repeat runs
must leave both hash inventories, generated assets and production copies unchanged.

## Licensing and provenance

- The architecture, approved model and right shadow come from the repository's
  controlled Season 4 sources under the repository [MIT license](../../../LICENSE).
  Existing 4.0 masters and published assets are preserved byte-for-byte. This
  records the owning repository source; it does not establish additional history
  for the original model beyond its existing controlled-asset provenance.
- YAML geometry and its optical treatment are native SVG authored in this change.
  The original model is the explicit exception to the initial all-vector approach,
  following the maintainer's request to reuse it. Its PNG is embedded rather than
  externally loaded. The right shadow remains the existing editable vector path.
- The generated concept reference `exec-69930abc-9a64-4b4f-9274-1e5338977930.png`
  from session `01a1b5f0-31f2-7191-864d-49bd5058d72a` supplied the selected
  composition and mosaic/organic contrast. That preview is not embedded, traced
  or distributed. No image-generation tool produced new delivered imagery.
- Fixed lettering follows the existing CoreText/Avenir Next process. Only outlines
  are exported; no font software is added. This is a provenance record rather than
  separate font or reference-image legal clearance.

Accessible description: “An ivory square-mosaic Struct and YAML figure on the left
and the original flowing Groovy model on the right stand as equal peers at one
luminous portal, with shadows extending down the diagonal ivory catwalk on navy.”

## Production boundary and tracker impact

[ACTIVATION.md](ACTIVATION.md) records integration and actual-site browser evidence.
[VALIDATION.md](VALIDATION.md) retains the source-artwork qualification history.
The exporter copies the full, compact and optical-header SVGs byte-for-byte into
`docs/user/img/season-4.2/` and records their hashes in `assets.json`. Do not edit
these production copies directly. Current `Home.md` and the repository README
select the 4.2 full lockup; the renderer selects its compact counterpart through
1000 px and uses the optical header from
[`season-4.2-klumast.json`](../season-4.2-klumast.json).

Local rendering defaults to that manifest. Pending/public documentation workflows
select it for `4.2.*` versions and retain the 4.0 manifest for older release lines.
The 4.0 production assets, manifest, favicon and final-approval record are unchanged.
The new manifest remains `candidate`: documentation activation does not approve a
final release. A future final 4.2 stage requires a separate owner-approved
`docs/branding/season-4.2-final-approval.json` bound to the exact new manifest hash;
that record is intentionally absent, so final staging remains fail-closed.

Tracker impact: no issue reference or issue-state change. No feature,
compatibility, migration or release-readiness claim is made.
