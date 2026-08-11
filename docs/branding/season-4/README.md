# Season 4 branding production masters

The SVG files in `editable/` are the controlled, text-based production masters for the
Season 4 lockups. The corresponding SVGs under `docs/user/img/season-4/` are the
published assets. They use outline paths for every fixed lettering element and must
remain font-independent.

`klumast-season-4-favicon.svg` is a square, self-contained portal-and-catwalk derivative
for browser icon surfaces. Its published asset and SHA-256 are recorded alongside the
header lockup in `docs/branding/season-4-klumast.json`.

Do not publish an editable master directly. Browser-specific Avenir Next metrics,
side bearings, and `textLength` support caused visible drift between Safari, Edge,
and librsvg during the Season 4 work.

## Required tools and settings

- macOS with the system font **Avenir Next** installed.
- The macOS Swift toolchain (`swift --version`).
- [`tools/outline_svg_text.swift`](tools/outline_svg_text.swift), which uses CoreText
  rather than Inkscape. Do not use Inkscape for this workflow; it was unstable in the
  Season 4 environment.
- Review the resulting documentation in Safari and Edge at 390 px and 430 px, then
  at both responsive boundaries: 759 px / 760 px / 761 px for navigation, and
  999 px / 1000 px / 1001 px for the compact-to-full lockup. Also inspect a wide
  desktop width.

The masters use `Avenir Next Ultra Light` for former CSS weight `300`, and
`Avenir Next` for former weight `400`.

## Recreating outlines

Run the tool from the repository root. Its arguments are:

```text
swift docs/branding/season-4/tools/outline_svg_text.swift \
  <font-face> <font-size> <tracking> <text> <baseline-y> <path-id>
```

It prints one SVG `<path>` with `data-bounds` and `data-advance` metadata. Use the
path bounds to place final artwork; remove those two diagnostic attributes from the
published SVG. The transform values below reproduce the Season 4 output exactly.

### Compact documentation lockup

| Text | Face / size / tracking / baseline | Published transform |
| --- | --- | --- |
| `KlumAST` | `Avenir Next Ultra Light` / `210` / `3` / `310` | `translate(647.26 0)` |
| `Make Your Models Groovy` | `Avenir Next Ultra Light` / `70` / `.5` / `430` | `translate(669.52 0) scale(1.02803 1) translate(-7.42 0)` |
| `4.0` | `Avenir Next Ultra Light` / `132` / `1` / `585` | `translate(1330.4 0)` |

The tagline is intentionally fitted to the wordmark path bounds. All three compact
lines share the same optical right edge.

### Full documentation lockup

| Text | Face / size / tracking / baseline | Published transform |
| --- | --- | --- |
| `KlumAST` | `Avenir Next Ultra Light` / `186.1` / `3` / `291` | `translate(742.7166 0)` |
| `Make Your Models Groovy` | `Avenir Next Ultra Light` / `64` / `.5` / `391` | `translate(754.12 0)` |
| `Season 4.0: The Makeover` | `Avenir Next Ultra Light` / `40` / `.2` / `505` | `translate(1037.88 0)` |
| `Builders first` | `Avenir Next` / `34` / `.2` / `550` | `translate(1310.146 0)` |

All full-lockup lines share a path-based optical right edge. `Builders first` remains
the only gold text (`#e5cf54`).

### Header lockup

| Text | Face / size / tracking / baseline | Published transform |
| --- | --- | --- |
| `KlumAST` | `Avenir Next` / `260` / `3` / `500` | `translate(765.4 0)` |

This transform preserves the approved portal-to-wordmark relationship.

## Publish checklist

1. Edit the appropriate text master; preserve the full and compact information
   hierarchy. The compact lockup keeps `KlumAST`, `Make Your Models Groovy`, and
   `4.0`; it omits `Builders first` and is selected through a 1000 px viewport
   width. The navigation has its separate 760 px layout breakpoint.
2. Generate and place new CoreText outlines using the settings above as the starting
   geometry. Update the meaningful asset descriptions.
3. Confirm no runtime text remains:

   ```text
   rg '<text|font-family|textLength' docs/user/img/season-4
   ```

4. If the header lockup or favicon changes, update its corresponding SHA-256 in
   `docs/branding/season-4-klumast.json`.
5. Run `./gradlew renderLocalDocumentation --console=plain`, then inspect the
   generated site in Safari and Edge at the stated widths.

The masters are source material for a later Season, not a shared runtime asset or a
technical rename/release mechanism.
