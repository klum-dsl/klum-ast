# #456 VD-4 credential-free documentation-tracer evidence

This is a sanitized, durable local record for the VD-4 tracer. It records only
public Git identities, output paths, checksums, and assertion names. It excludes
prompts, credentials, commands, complete command output, host paths, and inferred
telemetry.

## Fixed source decision

The successful 4.x tracer input is post-VD-3 commit
`c68d2757301f94ca65964d5fc7c4e76a4e557a8a`. It contains the `docs/user/`
authoring root, the deterministic renderer, and the six-module Javadoc seam.
The tracer renders it under the neutral `4.0.0-tracer` identity, whose generated
chrome and status record expressly state that it is local verification evidence,
not a release or prerelease.

The earlier commit `963d12dbf28ebeaf9a47e52c56465f8f27b97592` is deliberately
retained as a negative fixture. It predates both `docs/user/` and
`VersionedDocumentationRenderer`; VD-4 verifies their absence and rejects it
before a render can claim success. This preserves the original pinned identity as
evidence without substituting mutable current content.

## Credential-free tracer evidence

`verifyCredentialFreeDocumentationTracer` starts from a clean local checkout,
creates an isolated local Git checkout, and runs ordinary Javadoc generation only
for the six explicit modules. It renders tagged `v3.0.1`
(`3aa97428c0420fd3d1ca70b4b5e141360d1ca5b6`) as an archived tree, renders the
fixed 4.x source as a tracer tree, and generates the labelled GitHub-wiki migration
stubs from that selected source. It has no publish, deploy, credential, registry,
GitHub, Pages, signing, or alias task dependency.

The retained build evidence is
`build/versioned-documentation/tracer/tracer-evidence.json`. Its stable schema
records only the three source identities, SHA-256 values of the rendered manifests
and stub inventory, the changed-input manifest proof, the negative-fixture result,
and named assertions. It does not retain process output or environment data.

The tracer asserts six isolated module API bases, the absent BOM API base, an
archived direct page, the local root selector, a labelled wiki stub that makes no
HTTP-redirect claim, and a changed source revision producing a different source
manifest. It neither deploys the outputs nor advances any alias.
