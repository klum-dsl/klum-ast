# #488/#456 public 4.0 RC backfill matrix — 2026-08-07

This is a read-only, sanitized inventory for a separately human-reviewed backfill. It does not authorize or perform a tag, GitHub release, Pages alias, workflow dispatch, or other remote mutation.

## Evidence method

The inventory reads GitHub Actions' protected-release job state, the immutable `gh-pages` pending paths, GitHub release/tag records, and the public Maven Central `klum-ast` POM URL. A `200` Maven result and successful complete-product publication identify public artifacts; neither a local branch nor a runner checkout supplies an identity. The GitHub release API returned no `v4.0.0-rc.*` record at this snapshot.

| Version | Exact source SHA | Protected publication evidence | Public-proof status | Matching pending documentation identity | Tag/release/public docs state | Safe next action |
| --- | --- | --- | --- | --- | --- | --- |
| `4.0.0-rc.7` | `b5db1d8b237555889f6436653194353053a88af0` | Run `30812587571` succeeded; Maven Central POM was public. | Run `30907040962` succeeded, but no retained immutable proof handoff binds that result to the publication SHA. | `pending/4.0.0-rc.7/b5db1d8b237555889f6436653194353053a88af0/` | No tag, GitHub release, or promoted public exact tree/alias. | After human review, use one exact unified Verify public release re-proof; never copy this historic run identity into a second dispatch. Do not backfill automatically. |
| `4.0.0-rc.8` | `4cffbe1e79ce20332e2202e16179d33f1ed3bd68` | Run `30935687641` succeeded; Maven Central POM was public. | Run `30947750936` succeeded, but no retained immutable proof handoff binds that result to the publication SHA. | `pending/4.0.0-rc.8/4cffbe1e79ce20332e2202e16179d33f1ed3bd68/` | No tag, GitHub release, or promoted public exact tree/alias. | After human review, use one exact unified Verify public release re-proof; never copy this historic run identity into a second dispatch. Do not backfill automatically. |
| `4.0.0-rc.9` | `23158177957329098d51baf7aa334d2e5bac7fec` | Run `31026530751` succeeded; Maven Central POM was public. | No matching immutable proof identity was retained. | `pending/4.0.0-rc.9/23158177957329098d51baf7aa334d2e5bac7fec/` | No tag, GitHub release, or promoted public exact tree/alias. | Require one exact unified Verify public release re-proof and human evidence reconciliation before considering a backfill. |
| `4.0.0-rc.13` | `7967adc98253abdaff55e8261b549861141bb4c4` | Run `31148447596` succeeded; Maven Central POM was public. | No matching immutable proof identity was retained. | `pending/4.0.0-rc.13/7967adc98253abdaff55e8261b549861141bb4c4/` | No tag, GitHub release, or promoted public exact tree/alias. | Require one exact unified Verify public release re-proof and human evidence reconciliation before considering a backfill. |

## Excluded candidates and current RC.14

`rc.1` through `rc.6` and `rc.10` through `rc.12` have pending-stage history but the Maven Central POM check was `404` or their protected publication workflow failed. They are not listed as publicly published RCs. They remain immutable incident evidence, not candidates for a release-record retrofit.

`rc.14` has the pending-documentation identity `pending/4.0.0-rc.14/9bfd5100233149c83d30ef6027528f4fd60a76ec/` while its protected publication run was still in progress at the snapshot. It is not a backfill candidate; use one unified Verify public release run only after public resolvability.

## Human-reviewed backfill runbook

1. Re-read the protected publication log, registry/package evidence, public proof artifact, and pending-documentation manifest for one version. Confirm all identities independently; missing/expired/mismatched proof requires one new exact unified Verify public release re-proof, never local inference or a copied historic run identity.
2. Confirm the version remains publicly resolvable and that no conflicting tag, GitHub release, exact public documentation, alias, promotion, or rejection record exists.
3. Obtain explicit, version-specific human approval for any historical record operation. Decide and record whether the old external-consumer claim remains valid; this is not covered by the normal release authorization.
4. Only then dispatch the unified workflow once for that one exact identity. It must create its new proof internally before entering the protected documentation and release-record jobs. Stop on the first mismatch and retain the incident record. Do not batch, replace, supersede, or repair versions in place.
