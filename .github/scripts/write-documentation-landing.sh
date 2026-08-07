#!/usr/bin/env bash
set -euo pipefail

pages_directory="$1"
release_version="$2"
release_stage="$3"

[[ "$release_stage" == candidate || "$release_stage" == final ]]
manifest="$pages_directory/$release_version/site-manifest.json"
test -f "$manifest"
logo_asset="$(jq -er '.branding.outputAsset' "$manifest")"
logo_alt="$(jq -er '.branding.altText' "$manifest")"
[[ "$logo_asset" =~ ^assets/branding/[A-Za-z0-9._-]+$ ]]
test -f "$pages_directory/$release_version/assets/site.css"
test -f "$pages_directory/$release_version/$logo_asset"

write_landing_page() {
  local target="$1"
  local title="$2"
  local css_path="$3"
  local logo_path="$4"
  local home_path="$5"
  local badge="$6"
  local detail="$7"
  local destination="$8"

  mkdir -p "$(dirname "$target")"
  printf '%s\n' "<!doctype html>
<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
<title>$title</title><link rel=\"stylesheet\" href=\"$css_path\"></head>
<body><a class=\"skip-link\" href=\"#main-content\">Skip to main content</a>
<header class=\"site-header\"><div class=\"site-header__inner\"><a class=\"brand\" href=\"$home_path\"><img src=\"$logo_path\" alt=\"$logo_alt\"></a><span class=\"version-badge\">$badge</span></div></header>
<main id=\"main-content\" class=\"content\" tabindex=\"-1\" style=\"width:min(70rem,calc(100% - 2rem));margin:2rem auto 5rem\"><h1>$title</h1><p>$detail <a href=\"$destination\">Open $release_version documentation</a>.</p></main>
<footer class=\"site-footer\"><p>KlumAST documentation</p></footer></body></html>" > "$target"
}

if [[ "$release_stage" == candidate ]]; then
  write_landing_page "$pages_directory/index.html" 'KlumAST documentation' "$release_version/assets/site.css" "$release_version/$logo_asset" './' "$release_version · Public release candidate" 'The current public documentation is the release candidate.' 'preview/'
  write_landing_page "$pages_directory/preview/index.html" 'KlumAST documentation preview' "../$release_version/assets/site.css" "../$release_version/$logo_asset" '../' "$release_version · Public release candidate" 'This labelled preview selects the immutable release candidate.' "../$release_version/"
else
  line="${release_version%.*}"
  write_landing_page "$pages_directory/index.html" 'KlumAST documentation' "$release_version/assets/site.css" "$release_version/$logo_asset" './' "$release_version · Current stable" 'The current public documentation is the stable release.' 'stable/'
  write_landing_page "$pages_directory/stable/index.html" 'Current stable KlumAST documentation' "../$release_version/assets/site.css" "../$release_version/$logo_asset" '../' "$release_version · Current stable" 'This labelled stable alias selects the immutable release.' "../$release_version/"
  write_landing_page "$pages_directory/$line/index.html" "Current KlumAST $line documentation" "../$release_version/assets/site.css" "../$release_version/$logo_asset" '../' "$release_version · Current maintained line" 'This labelled maintained-line alias selects the immutable release.' "../$release_version/"
fi
