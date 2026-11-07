# 4.2 artwork validation — 2026-11-07

Scope: controlled artwork, approved original-model reuse and local review/export
tooling. Existing release branding is preserved; the new assets are not integrated
into the production documentation renderer.

## Current implementation and checks

| Check | Evidence / result |
| --- | --- |
| SVG export and CoreText outlines | `python3 docs/branding/season-4.2/tools/export_assets.py` generates seven self-contained SVGs and thirteen PNG review renders |
| Model reuse | Exact image attributes and embedded PNG payload match the controlled 4.0 master; no image resampling or shoe redraw |
| Right shadow | Original approved vector path is identical; both model and shadow use the same `(54,0)` translation |
| YAML geometry | Retained 34.84° direction, leading/rear toe contacts and rear-leg attachment continuity pass `check_geometry` |
| Architecture | Portal/runway paths match the controlled 4.0 master |
| Favicon | Maintainer selected the existing 4.0 favicon; its asset, branding manifest and final approval record remain unchanged |
| SVG integrity | Duplicate IDs, unresolved references, external assets, runtime text/fonts and unapproved raster images are rejected; only the exact approved Groovy image is allowed |
| Inventories | Source/export SHA-256 values are recorded in `assets.json` and `lettering-options/assets.json`; the approved master and PNG hashes are also recorded |
| Python syntax | `PYTHONPYCACHEPREFIX=/tmp/klumast-4.2-pycache python3 -m py_compile docs/branding/season-4.2/tools/export_assets.py docs/branding/season-4.2/tools/export_lettering_options.py` |
| Diff and links | `git diff --check`; local Markdown link validation |
| Existing repository checks | `./gradlew license renderLocalDocumentation --console=plain` passed on the clean rewritten artwork revision. This checks the existing site, not activation of the new assets |

The selected composition keeps YAML left and Groovy right. Groovy is moved left
to place its partly transparent upper figure inside the ivory portal, clearing
the right edge instead of showing the dark frame/background through the model. YAML’s uniform
outline widths increase from 1.15 to 1.55 in the detailed treatment and from 2.0
to 2.25 in the optical version; fine tile seams are unchanged.
The Groovy redraw was rejected and replaced with the exact approved original,
including its matching shadow. The excessive 19.3° right-shadow rotation is removed;
the left shadow retains its revised direction. E keeps YAML at baseline 376 above
Groovy at 391. The full/compact controlled masters now use this accepted treatment; production
site adoption remains separate from these review assets.

## Render evidence

The current wide, header, compact, 256/128/64/32 px optical and foot-detail renders were
visually inspected. Exact output hashes are in the inventories.

- [`exports/documentation-1680.png`](exports/documentation-1680.png): 1680 × 945 full lockup.
- [`exports/documentation-640.png`](exports/documentation-640.png): approved full composition at 640 px.
- [`exports/documentation-compact-430.png`](exports/documentation-compact-430.png) and
  [`exports/documentation-compact-320.png`](exports/documentation-compact-320.png): compact lockup with the selected combined tagline.
- [`exports/header-990.png`](exports/header-990.png): 990 × 330 header.
- [`exports/header-optical-660.png`](exports/header-optical-660.png) and
  [`exports/header-optical-330.png`](exports/header-optical-330.png): small header logos using the optical YAML contours.
- [`exports/art-optical-256.png`](exports/art-optical-256.png),
  [`exports/art-optical-128.png`](exports/art-optical-128.png),
  [`exports/art-optical-64.png`](exports/art-optical-64.png),
  [`exports/art-optical-32.png`](exports/art-optical-32.png): simplified YAML contours;
  the approved Groovy model retains its original raster resolution.
- [`exports/groovy-4.0-foot-reference.png`](exports/groovy-4.0-foot-reference.png) and
  [`exports/groovy-4.2-foot-detail.png`](exports/groovy-4.2-foot-detail.png): aligned
  crops showing the exact original model, now reused in the 4.2 composition.
- [`lettering-options/exports/klumast-4.2-e-overlaid-ending-1008.png`](lettering-options/exports/klumast-4.2-e-overlaid-ending-1008.png):
  current direction with higher YAML, approved original model and matching shadow.

## Lettering inventory and history cleanup

The cleanup retains all five editable A–E masters and E’s five selected exports
(two SVGs and three PNGs). It removes the twelve PNG and eight SVG derivatives
of A–D. `lettering-options/assets.json` contains ten canonical source/output
hashes. The normal exporter generates only E; `--preview-all` renders all five
masters into a fresh temporary directory and does not update canonical assets.

All 36 protected files—the approved main artwork, original Groovy model and shadow,
YAML geometry, main exports and selected E artifacts—remain byte-identical to
pre-cleanup PR head `8569b9762c8eb9467c5ecb3b99e9c232e530c741`. The five editable
lettering masters are also preserved unchanged.

The sixteen PR-only development commits are replaced by one focused artwork
commit on the unchanged fork point `893487c19a7d8400d078dfa8e9853e054c83a0c7`.
The rewritten PR-only tree contains none of the twenty removed paths. Its
reachable object set contains none of their 68 historical blob IDs, and none of
the old sixteen PR commits remains an ancestor. The local rollback ref is outside
the rewritten branch ancestry and is not published. The base branch is unchanged.

## Remaining qualification

The main and selected-lettering exporters reproduce canonical assets without a
diff. All five retained masters rendered successfully in temporary storage; all
25 temporary outputs match their pre-cleanup bytes and leave canonical assets
and inventory unchanged. Syntax, hashes, links, geometry, SVG integrity and
`git diff --check` pass. Focused standards and spec reviews each found zero issues.
No Groovy/JVM compatibility matrix was run for this artwork-only cleanup.

Safari computer-use access was denied. Safari/Edge inspection at 390, 430,
759/760/761, 999/1000/1001 and wide desktop widths has not been completed.
`review.html` provides actual-width iframe viewports and reports the loaded source;
fixture availability is not evidence of browser execution.

The renderer recognises existing Season 4 filenames specifically. Integrating the
new assets requires an explicit adoption and branding-approval change. No JVM
output or runtime behavior changes here; no Groovy/JVM test matrix is required
for this artwork-only cleanup. Pre-cleanup PR head `8569b9762` passed hosted CI,
JUnit and SonarCloud; the rewritten head requires fresh hosted checks. The existing
4.0 favicon is unchanged; the small square illustration crops are not replacement
favicon assets.
