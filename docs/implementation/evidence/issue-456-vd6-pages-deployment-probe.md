# #456 VD-6 Pages deployment probe

This narrow, manually dispatched workflow is diagnostic-only evidence for a
GitHub Pages deployment delay or failure. It is not a release operation and it
does not change the versioned-documentation or prerelease policy.

## Safety boundary

- The workflow accepts no release inputs and contains no Maven, Plugin Portal,
  signing, release, or Pages-writer credentials.
- It runs only from the current `master` tip: it rejects any other ref and
  proves that `HEAD`, `origin/master`, and the dispatch SHA agree.
- It snapshots the exact `gh-pages` commit, uploads that entire untouched
  ledger through `actions/upload-pages-artifact`, and verifies the remote
  ledger still equals the snapshot after the deployment attempt.
- The Pages deployment remains in the existing protected
  `documentation-pages` environment. Its artifact name is uniquely and
  visibly probe-only: `pages-deployment-probe-<run-id>-<attempt>`.
- It uses the same pinned official artifact and deployment actions as the
  protected pending-documentation path. It does not pass a deployment timeout,
  so `actions/deploy-pages` retains its documented 600,000 ms ceiling.
- It does not render documentation, write `gh-pages`, create a tag or GitHub
  release, publish artifacts or plugin markers, advance aliases, create
  pending/rejected release paths, or create release evidence.

## Operation and interpretation

After the workflow has reached `master`, a maintainer must manually dispatch
**Probe GitHub Pages deployment** and satisfy any `documentation-pages`
environment approval required by GitHub. The workflow summary records source
and ledger commits, the Pages action result, timestamps, elapsed seconds, and
the Pages URL if reported. Its final job proves that the remote ledger stayed
unchanged.

A successful or failed run is a bounded platform observation, not a root-cause
finding by itself. Compare the recorded status and timing with the failed and
successful release-stage runs before deciding on further remediation. The
standard protected release workflow and the immutable RC/final policy remain
unchanged.
