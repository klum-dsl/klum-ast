#!/usr/bin/env bash
# Regression fixture for klum-dsl/klum-ast#1072.
set -euo pipefail

writer="$(dirname "$0")/write-documentation-landing.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
pages="$fixture/pages"
root="$pages/index.html"
preview="$pages/preview/index.html"
stable="$pages/stable/index.html"

promote() {
  local version="$1" stage="$2"
  local exact="$fixture/exact/$version"
  mkdir -p "$exact/assets/branding" "$pages"
  printf '%s\n' '{"branding":{"outputAsset":"assets/branding/logo.svg","altText":"KlumAST"}}' > "$exact/site-manifest.json"
  touch "$exact/assets/site.css" "$exact/assets/branding/logo.svg" "$exact/index.html"
  cp -R "$exact" "$pages/$version"
  bash "$writer" "$pages" "$version" "$stage"
}

assert_contains() {
  if ! grep -Fq -- "$2" "$1"; then
    printf 'Expected %s in %s\n' "$2" "$1" >&2
    exit 1
  fi
}

assert_absent() {
  if grep -Fq -- "$2" "$1"; then
    printf 'Unexpected %s in %s\n' "$2" "$1" >&2
    exit 1
  fi
}

promote 4.2.0-rc.3 candidate
assert_contains "$root" 'href="4.2.0-rc.3/">Open 4.2.0-rc.3 documentation'
assert_absent "$root" 'href="stable/"'

# An unrelated final must also retain the target of a legacy preview page.
printf '%s\n' '<p><a href="../4.2.0-rc.3/">Open 4.2.0-rc.3 documentation</a>.</p>' > "$preview"
promote 4.1.1 final
assert_contains "$root" 'href="4.2.0-rc.3/">Open 4.2.0-rc.3 documentation'
assert_contains "$root" 'href="stable/">Open 4.1.1 documentation'
assert_contains "$preview" '<ul><li><a href="../4.2.0-rc.3/">Open 4.2.0-rc.3 documentation'
assert_contains "$stable" '<h1>Current stable KlumAST documentation</h1>'
assert_contains "$stable" '<ul><li><a href="../4.1.1/">Open 4.1.1 documentation'
assert_contains "$pages/4.1/index.html" 'Current maintained line'
assert_contains "$pages/4.1/index.html" 'href="../4.1.1/">Open 4.1.1 documentation'
cp "$stable" "$fixture/stable.html"
cp "$pages/4.1/index.html" "$fixture/line.html"

printf '%s\n' '<p><a href="../4.1.1/">Open 4.1.1 documentation</a>.</p>' > "$stable"
promote 4.2.0-rc.4 candidate
assert_contains "$root" 'Public release candidate'
assert_contains "$root" 'href="4.2.0-rc.4/">Open 4.2.0-rc.4 documentation'
assert_contains "$root" 'href="stable/">Open current stable documentation'
assert_absent "$root" 'href="preview/"'
assert_absent "$root" '4.2.0-rc.3/'
assert_contains "$preview" '<h1>KlumAST documentation preview</h1>'
assert_contains "$preview" '<ul><li><a href="../4.2.0-rc.4/">Open 4.2.0-rc.4 documentation'
assert_absent "$preview" '4.2.0-rc.3/'
cmp "$fixture/stable.html" "$stable"
cmp "$fixture/line.html" "$pages/4.1/index.html"

promote 4.2.0 final
assert_contains "$root" 'href="stable/">Open 4.2.0 documentation'
assert_contains "$stable" '<ul><li><a href="../4.2.0/">Open 4.2.0 documentation'
assert_contains "$preview" 'No public release candidate is currently listed.'
assert_absent "$root" '4.2.0-rc.4/'
assert_absent "$preview" '4.2.0-rc.4/'

promote 4.2.0-rc.5 candidate
assert_absent "$root" '4.2.0-rc.5/'
assert_absent "$preview" '4.2.0-rc.5/'
assert_contains "$root" 'href="stable/">Open current stable documentation'

promote 4.2.1-rc.10 candidate
assert_contains "$root" 'href="4.2.1-rc.10/">Open 4.2.1-rc.10 documentation'
promote 4.2.1-rc.9 candidate
assert_contains "$root" 'href="4.2.1-rc.10/">Open 4.2.1-rc.10 documentation'
assert_contains "$preview" 'href="../4.2.1-rc.10/">Open 4.2.1-rc.10 documentation'
assert_absent "$root" '4.2.1-rc.9/'
assert_absent "$preview" '4.2.1-rc.9/'

for exact in "$fixture/exact/"*; do
  diff -r "$exact" "$pages/$(basename "$exact")"
done
