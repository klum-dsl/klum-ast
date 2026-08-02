# Klum visual language

This note preserves the visual direction established by KlumAST Season 4.0. It is
an internal design reference, not user documentation, a release promise, or a
technical contract. KlumAST is the source of this visual direction; a later
KlumCast or KlumDSL adoption remains a repository-local design decision.

The approved Season 4.0 production assets and their exact recreation procedure
live in [season-4/](season-4/README.md). This note records the durable grammar
and the intentionally uncommitted ideas that may guide later Seasons.

## Core grammar

- A chamfered, luminous portal opens onto a diagonal catwalk. The catwalk and
  its perspective are the common element across the Klum family.
- The portal is a sparse architectural shape: a straight right side, a cut-away
  top-left corner, and no structure below the catwalk threshold.
- Light originates behind the portal. A shadow follows the catwalk's vanishing
  direction, begins at the depicted object or figure, and becomes softer and
  lighter toward the foreground.
- The palette is restrained: deep navy ground, warm ivory light and catwalk,
  graphite line work, subdued blue-grey shadows, and small muted-gold accents.
  The portal and catwalk carry the visual weight; decorative elements stay
  secondary.
- Figures use a fashion-illustration / sumi-e-like economy: confident, slightly
  asymmetric strokes with a light, partial fill rather than detailed realism.
  At icon scale, recognisable portal and catwalk geometry take precedence over
  fine figure detail.
- Typography is airy and high-contrast. Fixed logo lettering is outlined so the
  result is stable across Safari, Edge, and documentation renderers.

## Season variation grammar

Every Season keeps the portal, perspective, catwalk, and light-to-shadow logic.
Its release cue may change the figure's pose, clothing, hair, or one accessory.
This should read as a coherent fashion collection, not a new product identity.

| Direction | Intended cue | Status |
| --- | --- | --- |
| Season 4.0: The Makeover | The neutral stylised model steps from the portal onto the catwalk. The full lockup includes `Builders first`; the compact lockup prioritises `4.0`. | Approved Season 4.0 direction |
| 4.1 Spring Break | A distinct catwalk-facing pose and a clearly readable spring dress. This can cue Spring-Boot integration without turning that integration into a logo claim. | Future candidate |
| Security-focused sandbox | A different pose with a restrained style-noire accessory, such as a hat. Avoid literal security iconography or a technical promise in the artwork. | Future candidate |
| Hair | The Season 4.0 figure deliberately has no hair treatment. One or two stylised wavy strokes remain available as a later, release-specific cue. | Future candidate |

## Product expressions

The shared catwalk is the family connection. Each product adds one legible,
product-specific element; it does not inherit KlumAST's model unchanged.

| Product / surface | Direction | Status |
| --- | --- | --- |
| KlumAST | Primary mark: a stylised model steps through the portal and onto the catwalk. | Established reference |
| KlumCast | A bouncer or doorguard in front of the portal is a candidate icon: it conveys protection of models (in practice, schemas) from unsuitable annotations. `Protect Your Models` is a candidate slogan, not approved copy. | Future candidate; no KlumCast implementation implied |
| KlumDSL organisation / suite | An open portal with two unroped stanchions can represent an opening or invitation while retaining a distinctive perspective shadow. A partially closed-door study is an alternative, but has not been selected. | Future candidate |
| KlumWrap | Use the same portal and catwalk before introducing a wrapper-specific element. The organisation/suite study above is not automatically the KlumWrap mark. | Future candidate |

## Use and handoff rules

1. Start a new Season from the controlled Season 4.0 masters, not from a rendered
   screenshot. Follow the production guidance in [season-4/](season-4/README.md).
2. Keep technical names, package coordinates, modules, artifacts, and release
   procedures unchanged. Visual language does not imply a technical rename or a
   shared release process.
3. Treat a future concept as an exploration until its owning maintainer selects
   it. Record the selected local asset, accessible description, and manifest in
   that repository's delivery work.
4. When KlumCast or another repository begins branding work, link to this note
   as source direction and make its implementation choice there. Do not copy
   this note into user-facing documentation unless the concept has become
   approved product communication.
