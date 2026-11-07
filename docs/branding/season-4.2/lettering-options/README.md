# Groovy/YAML lettering suggestions

These options respond to the requested combined Groovy/YAML tagline. They
are native, editable text masters exported with the same CoreText outline engine
as the controlled 4.2 lockups. No new generated imagery or font software is added.
The studies use the current artwork master: native YAML on the left and the
approved original 4.0 Groovy model on the right. The exact original raster figure
and its matching native shadow are translated together, following the maintainer's
request to reuse the original rather than continue the unsuccessful foot redraw.
This explicit raster reuse is documented in [the main source record](../README.md).

Lettering A–C was not selected. D's shared-Y idea was promising but occupied too
much space. E is the current compact direction, with Groovy on its original
baseline and higher YAML restored after comparing both vertical offsets.

All options keep the imperative **Make Your Models** and replace the single ending
with both **Groovy** and **YAML**. YAML keeps a full ivory fill; D uses the same light face for one fused word,
while A–C and E use a stronger YAML weight. Groovy remains airy. A–D remain unselected studies; E is selected for the controlled full/compact
masters. The compact master scales its wording to the established compact layout.
Production-site adoption is still separate from these review artifacts.

| Option | Treatment | Tradeoff |
| --- | --- | --- |
| A — Stepped pair | Groovy is slightly raised, YAML slightly lowered; the words meet as one staggered ending. | Closest to the original single-line phrase; YAML stays especially clear. |
| B — Crossed baselines | Ivory YAML comes first on the lower line, with gold Groovy raised to its right. | Strongest YAML emphasis and the same left/right ordering as the figures. |
| C — Shared stem | One shared “Make Your Models” feeds a compact two-line Groovy/YAML ending, divided by a quiet gold rule. | Most compact fusion; YAML is dominant, with a smaller shared prefix. |
| D — Shared Y | “GroovYAML” shares the terminal y/Y, with YAML slightly lowered. | A single merged word at tagline scale; trades the complete Groovy spelling for the common hinge. |
| E — Overlaid ending, refined | Groovy returns to the original 391-unit baseline and 64-unit size. YAML is restored to baseline 376 and layers in front of it, with an ivory foreground and navy clearance. | Compact ending with greater overlap; YAML takes priority while the gold Groovy remains partially visible. |

## Retained inventory

All five editable SVG masters remain under `editable/`. Only selected E has
canonical derivatives under `exports/`:

- `klumast-4.2-e-overlaid-ending.svg` and its 1008/430 px PNG previews.
- `klumast-4.2-e-overlaid-ending-lettering.svg` and its 840 px PNG crop.

`assets.json` therefore contains ten entries: five editable masters, two selected
SVG exports and three selected PNGs. A–D derivatives are reproducible review
material and are not committed or included in that inventory. Main approved
release artwork and its exports remain under the parent `season-4.2/` directory.

## Reproduction

The normal workflow exports E and records all five source hashes:

```sh
PYTHONPYCACHEPREFIX=/tmp/klumast-4.2-pycache python3 docs/branding/season-4.2/tools/export_lettering_options.py
```

To inspect all alternatives locally:

```sh
PYTHONPYCACHEPREFIX=/tmp/klumast-4.2-pycache python3 docs/branding/season-4.2/tools/export_lettering_options.py --preview-all
```

This prints a newly created temporary directory containing the A–E previews.
It leaves the tracked exports and inventory unchanged; these temporary derivatives
should not be committed. Both workflows use the existing CoreText outliner and
check SVG integrity and the original model/shadow placement. Browser qualification
remains pending as recorded in [the validation record](../VALIDATION.md).
