#!/usr/bin/env bash
set -euo pipefail

pages_directory="$1"
release_version="$2"
release_stage="$3"
[[ "$release_stage" == candidate || "$release_stage" == final ]]

write_landing_page() {
  local target="$1" title="$2" version="$3" prefix="$4" badge="$5" content="$6"
  local version_path="$prefix$version"
  local manifest="$pages_directory/$version/site-manifest.json"
  local logo_asset logo_alt
  test -f "$manifest"
  logo_asset="$(jq -er '.branding.outputAsset' "$manifest")"
  logo_alt="$(jq -er '.branding.altText' "$manifest")"
  [[ "$logo_asset" =~ ^assets/branding/[A-Za-z0-9._-]+$ ]]
  test -f "$pages_directory/$version/assets/site.css"
  test -f "$pages_directory/$version/$logo_asset"

  mkdir -p "$(dirname "$target")"
  printf '%s\n' "<!doctype html>
<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
<title>$title</title><link rel=\"stylesheet\" href=\"$version_path/assets/site.css\"></head>
<body><a class=\"skip-link\" href=\"#main-content\">Skip to main content</a>
<header class=\"site-header\"><div class=\"site-header__inner\"><a class=\"brand\" href=\"${prefix:-./}\"><img src=\"$version_path/$logo_asset\" alt=\"$logo_alt\"></a><span class=\"version-badge\">$badge</span></div></header>
<main id=\"main-content\" class=\"content\" tabindex=\"-1\" style=\"width:min(70rem,calc(100% - 2rem));margin:2rem auto 5rem\"><h1>$title</h1>$content</main>
<footer class=\"site-footer\"><p>KlumAST documentation</p></footer></body></html>" > "$target"
}

read_alias_version() {
  if [[ -f "$1" ]]; then
    # The exact-target link has the same shape in legacy alias pages and selector lists.
    sed -nE 's@.*href="\.\./([0-9]+\.[0-9]+\.[0-9]+(-rc\.[1-9][0-9]*)?)/".*@\1@p' "$1"
  fi
}

is_eligible_candidate() {
  local version="$1" final_version="${1%-rc.*}" candidate_number="${1##*.}"
  [[ "$version" =~ ^([0-9]+\.){2}[0-9]+-rc\.[1-9][0-9]*$ &&
     -f "$pages_directory/$version/index.html" &&
     ! -f "$pages_directory/$final_version/site-manifest.json" ]] || return 1

  local manifest exact_directory other_number
  for manifest in "$pages_directory/$final_version"-rc.*/site-manifest.json; do
    [[ -f "$manifest" ]] || continue
    exact_directory="${manifest%/site-manifest.json}"
    other_number="${exact_directory##*.}"
    [[ "$other_number" =~ ^[1-9][0-9]*$ ]] || continue
    # Compare decimal RC numbers without Bash integer overflow.
    if (( ${#other_number} > ${#candidate_number} )) ||
       { (( ${#other_number} == ${#candidate_number} )) && [[ "$other_number" > "$candidate_number" ]]; }; then
      return 1
    fi
  done
  return 0
}

preview_index="$pages_directory/preview/index.html"
stable_index="$pages_directory/stable/index.html"
preview_version="$(read_alias_version "$preview_index")"
if [[ "$release_stage" == candidate ]] && is_eligible_candidate "$release_version"; then
  preview_version="$release_version"
elif ! is_eligible_candidate "$preview_version"; then
  preview_version=''
fi

if [[ "$release_stage" == final ]]; then
  stable_version="$release_version"
  stable_label="Open $stable_version documentation"
  line="${release_version%.*}"
  write_landing_page "$pages_directory/$line/index.html" "Current KlumAST $line documentation" "$release_version" '../' "$release_version · Current maintained line" \
    "<p>This labelled maintained-line alias selects the immutable release. <a href=\"../$release_version/\">Open $release_version documentation</a>.</p>"
else
  stable_label='Open current stable documentation'
  stable_version="$(read_alias_version "$stable_index")"
  if [[ -z "$stable_version" || "$stable_version" == *-rc.* || ! -f "$pages_directory/$stable_version/index.html" ]]; then
    stable_version=''
  fi
fi

stable_link=''
if [[ -n "$stable_version" ]]; then
  stable_link="<p><a href=\"stable/\">$stable_label</a>.</p>"
  write_landing_page "$stable_index" 'Current stable KlumAST documentation' "$stable_version" '../' "$stable_version · Current stable" \
    "<p>Choose the current stable release.</p><ul><li><a href=\"../$stable_version/\">Open $stable_version documentation</a></li></ul>"
fi

root_version="${stable_version:-$release_version}"
root_badge='Documentation'
if [[ -n "$stable_version" ]]; then
  root_badge="$stable_version · Current stable"
fi
preview_link=''
if [[ -n "$preview_version" ]]; then
  preview_badge="$preview_version · Public release candidate"
  preview_link="<p>Public release candidate. <a href=\"$preview_version/\">Open $preview_version documentation</a>.</p>"
  write_landing_page "$preview_index" 'KlumAST documentation preview' "$preview_version" '../' "$preview_badge" \
    "<p>Choose the current public release candidate.</p><ul><li><a href=\"../$preview_version/\">Open $preview_version documentation</a></li></ul>"
  if [[ "$release_stage" == candidate ]]; then
    root_version="$preview_version"
    root_badge="$preview_badge"
  fi
else
  write_landing_page "$preview_index" 'KlumAST documentation preview' "$root_version" '../' 'Preview documentation' \
    '<p>No public release candidate is currently listed.</p>'
fi

write_landing_page "$pages_directory/index.html" 'KlumAST documentation' "$root_version" '' "$root_badge" "$preview_link$stable_link"
