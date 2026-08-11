#!/usr/bin/env sh
# Regenerate provisional GitHub avatar review exports from approved Season 4 assets.
set -eu

candidate_dir=docs/branding/avatar-candidates
export_dir="$candidate_dir/exports"
mkdir -p "$export_dir"

rsvg-convert -w 1680 -h 945 \
  --stylesheet "$candidate_dir/provisional-klumast-season-4-repository-export.css" \
  -o "$candidate_dir/provisional-klumast-season-4-repository-approved-artwork-source.png" \
  docs/user/img/season-4/klumast-season-4-documentation.svg

sips -c 720 720 --cropOffset 20 250 \
  "$candidate_dir/provisional-klumast-season-4-repository-approved-artwork-source.png" \
  --out "$export_dir/provisional-klumast-season-4-repository-avatar-crop-source.png" >/dev/null

rsvg-convert -w 1680 -h 945 \
  -o "$candidate_dir/provisional-klumast-season-4-social-preview-approved-artwork-source.png" \
  docs/user/img/season-4/klumast-season-4-documentation.svg

sips -c 655 1310 --cropOffset 69 250 \
  "$candidate_dir/provisional-klumast-season-4-social-preview-approved-artwork-source.png" \
  --out "$export_dir/provisional-klumast-season-4-github-social-preview-crop-source.png" >/dev/null

sips -z 640 1280 \
  "$export_dir/provisional-klumast-season-4-github-social-preview-crop-source.png" \
  --out "$export_dir/provisional-klumast-season-4-github-social-preview-1280x640.png" >/dev/null

for size in 1024; do
  sips -z "$size" "$size" \
    "$export_dir/provisional-klumast-season-4-repository-avatar-crop-source.png" \
    --out "$export_dir/provisional-klumast-season-4-repository-avatar-$size.png" >/dev/null
  rsvg-convert -w "$size" -h "$size" \
    -o "$export_dir/provisional-klumdsl-suite-avatar-study-$size.png" \
    "$candidate_dir/provisional-klumdsl-suite-avatar-study-source.svg"
done
