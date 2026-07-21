# Issue 456 VD-2 external evidence

This is a sanitized, reproducible record for ADR 0013 VD-2. It records public
release identities and artifact checksums only; it contains no credentials,
commands, host paths, prompts, or telemetry.

## Historical source identities

`renderHistoricalDocumentation` verifies every tag against the pinned commit,
renders its `wiki/` tree with archived mechanical chrome, and compares the
aggregate SHA-256 of every pre-render source file with the rendered body after
that chrome is removed. Equal digests prove that the task did not alter source
prose, examples, or assets.

| Version | Tag | Commit | `wiki/` tree | Source/rendered content digest |
| --- | --- | --- | --- | --- |
| 2.0.0 | `v2.0.0` | `b310027a735eb88a4096117976cec950b06a786e` | `c865e8e70cbd887294f17b85ca70b33639959049` | `ab1a6f00c25e9aff452117caf047ddbdc703bf833c6d7a89046604a354ac3448` |
| 2.1.0 | `v2.1.0` | `4e3cb081fecfd2ca5f19ef5c7d9a050ab16dfd25` | `0b893ec5827ce6eed0af77c27badb25a727754cd` | `c28f0605c23fa8976e711748387f3b166b3a2d4ba091432170f6e84671f35023` |
| 2.1.1 | `v2.1.1` | `f286d169ff10b38528b4d6f33e6fb861ec717153` | `0b893ec5827ce6eed0af77c27badb25a727754cd` | `c28f0605c23fa8976e711748387f3b166b3a2d4ba091432170f6e84671f35023` |
| 2.1.2 | `v2.1.2` | `af8b46ae67da4970b5a5da4f8838165ecb533bd0` | `0b893ec5827ce6eed0af77c27badb25a727754cd` | `c28f0605c23fa8976e711748387f3b166b3a2d4ba091432170f6e84671f35023` |
| 2.1.3 | `v2.1.3` | `be42da91ce2bcba7c7fd8cc01a6d02117e01a462` | `5899f36a374656d9463f312fda15ec86595eae07` | `d90c501de6530e954f4b1739994873b4e24bc7fb73c30bdba4dff316e286d9e9` |
| 2.1.4 | `v2.1.4` | `d49c14c96c32cee6ada9c5204b420fbf23dc5818` | `d07121c01ce1bbf7343782153b17eea4d82baa87` | `624899ff4b33dfe14020eef64b3d8de726fe77c60530381fa5ffb775a518f2da` |
| 2.1.5 | `v2.1.5` | `e8c545c7b73ca36e7fea180774d5822f000ab688` | `d07121c01ce1bbf7343782153b17eea4d82baa87` | `624899ff4b33dfe14020eef64b3d8de726fe77c60530381fa5ffb775a518f2da` |
| 2.2.0 | `v2.2.0` | `4e881c50467c16fdadb1fdcffe72aa72565e2f19` | `764e7ad5026c2e2b89b6bc3710b88360e649256b` | `8b1343e47b0dc2d9977cab7ac4edaa7b8284cc8c422f865438e61733a2ba5fcb` |
| 3.0.1 | `v3.0.1` | `3aa97428c0420fd3d1ca70b4b5e141360d1ca5b6` | `6388e96535005ae47e9308fba7fea99df99ba5ea` | `6a32e8ad8d918099fb91e2060547a484b1e5c075fcb9d8ef00ee567c94a17ded` |

## Historical API artifacts

The task reads only the released Javadoc JAR URL shape under Maven Central and
records each downloaded SHA-256 in its generated
`historical-content-audit.json`. It extracts only an artifact for the same
exact version below `archive/<version>/api/<module>/`.

| Versions | Imported modules | Unavailable module |
| --- | --- | --- |
| 2.0.0 through 2.1.5 | `klum-ast`, `klum-ast-runtime`, `klum-ast-annotations`, `klum-ast-jackson`, `klum-ast-gradle-plugin` | `klum-ast-bean-validation` has no released Javadoc JAR and remains explicitly unavailable. |
| 2.2.0, 3.0.1 | All six historical modules, including `klum-ast-bean-validation` | None |

The generated API landing repeats that absence rule and explicitly says that a
4.x API tree is never substituted for an old-version request.

## Historical output layout

The generated historical root contains `archive/index.md` and one exact tree
per release below `archive/<version>/`. Each exact tree exposes a generated
`index.md` landing copied from its rendered `Home.md`. The archive index uses
the target landing `<version>/index.md`; each archived page returns using
`../index.md`; and an API landing uses a module `index.html`. Every ordinary
historical page receives an explicitly labelled, mechanically converted copy
of that tag's `_Sidebar.md`; its Home entry resolves to that exact `index.md`,
other links resolve to the emitted page when present, and a missing target is
visibly labelled unavailable. This makes the
historical tree self-contained and navigable below a site subdirectory.

## GitHub-wiki migration inventory

`generateGitHubWikiMigrationStubs` inventories the last mutable wiki tree at
`c033e9b668ba53cd0a86859bc773fffc99863c09`. It emits 30 labelled Markdown
landing/deep-link stubs (including `Home.md` and the legacy `Changelog.md`) and
a SHA-256 inventory. Each page links to an exact canonical stable-path or the
canonical stable landing, and explicitly states that it is not an HTTP redirect.
`_Sidebar.md`, `_Footer.md`, and binary image paths are recorded as non-page
inputs rather than pretending they are ordinary wiki slugs.

The generated migration tree is deployment input only. It does not recreate a
repository `wiki/` authoring tree; current 4.x content remains in `docs/user/`.

## Authorized handoff boundary

VD-2 creates no GitHub-wiki mutation. After a future authorized wiki migration,
the operator must generate a fresh stub tree, compare every generated page with
`migration-stub-inventory.json`, and transfer only those labelled Markdown
stubs. The stable targets are intentionally a VD-6 alias contract, so the
transfer must wait until the corresponding stable alias is publicly proven.
Any source page without a current stable same-path counterpart maps to
`/archive/`; it is not silently redirected to a 4.x page. The old `gitPublishPush`
route remains disabled throughout this handoff.
