#!/usr/bin/env python3
"""Recreate the selected 4.2 vector lockups and local review renders on macOS.

SPDX-License-Identifier: MIT
Copyright (c) 2026 Stephan Pauxberger
See the repository LICENSE for the full license text.
"""

import base64
import copy
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
SEASON = ROOT / 'docs/branding/season-4.2'
OUT = SEASON / 'exports'
SVG = '{http://www.w3.org/2000/svg}'
OUTLINER = ROOT / 'docs/branding/season-4/tools/outline_svg_text.swift'
APPROVED_MASTER = ROOT / 'docs/branding/season-4/editable/klumast-season-4-documentation-text-master.svg'
ET.register_namespace('', SVG[1:-1])


def read_svg(path):
    return ET.parse(path).getroot()


def write_svg(root, path):
    ET.indent(root, space='  ')
    path.write_text(ET.tostring(root, encoding='unicode') + '\n')


def outline_text(parent, inherited=None):
    """Use CoreText bounds to preserve each master's optical right edge."""
    inherited = inherited or {}
    for index, child in enumerate(list(parent)):
        attributes = {**inherited, **child.attrib}
        if child.tag != SVG + 'text':
            outline_text(child, attributes)
            continue
        face = 'Avenir Next Ultra Light' if attributes.get('font-weight') == '300' else 'Avenir Next'
        result = subprocess.check_output([
            'swift', '-module-cache-path', str(Path(tempfile.gettempdir()) / 'klumast-4.2-swift-cache'), str(OUTLINER), face, attributes['font-size'],
            attributes.get('letter-spacing', '0'), child.text,
            attributes['y'], 'lettering-' + re.sub(r'[^a-z0-9]+', '-', child.text.lower()).strip('-')
        ], text=True)
        path = ET.fromstring(result)
        path.tag = SVG + 'path'
        xmin, _, xmax, _ = map(float, path.attrib.pop('data-bounds').split(','))
        path.attrib.pop('data-advance')
        scale = float(attributes['textLength']) / (xmax - xmin) if 'textLength' in attributes else 1
        right = float(attributes['x'])
        path.set('transform', f'translate({right:g} 0) scale({scale:.8f} 1) translate({-xmax:.8f} 0)')
        path.set('fill', attributes['fill'])
        parent.remove(child)
        parent.insert(index, path)
    for key in ['font-family', 'font-weight', 'font-size', 'letter-spacing']:
        parent.attrib.pop(key, None)


def check_svg(root):
    ids = [el.get('id') for el in root.iter() if el.get('id')]
    assert len(ids) == len(set(ids)), 'Duplicate SVG ids'
    approved = next(el for el in read_svg(APPROVED_MASTER).iter() if el.tag == SVG + 'image')
    for el in root.iter():
        assert el.tag not in {SVG + 'text', SVG + 'script', SVG + 'foreignObject'}
        if el.tag == SVG + 'image':
            assert el.attrib == {**approved.attrib, 'id': 'approved-groovy-model'}, 'Unapproved or altered raster model'
        for key, value in el.attrib.items():
            assert not key.startswith('font-'), 'Runtime font dependency'
            if key == 'href' and el.tag != SVG + 'image':
                assert value.startswith('#') and value[1:] in ids, value
            for reference in re.findall(r'url\(#([^)]+)\)', value):
                assert reference in ids, reference


def matrix_values(element):
    return tuple(map(float, element.get('transform')[7:-1].split()))


def project_point(matrix, point):
    a, b, c, d, tx, ty = matrix
    x, y = point
    return a * x + c * y + tx, b * x + d * y + ty


def assert_point(actual, expected):
    assert all(math.isclose(a, e, abs_tol=.001) for a, e in zip(actual, expected)), (actual, expected)


def check_geometry(art):
    """Check retained architecture, YAML contacts and exact approved-model reuse."""
    original = read_svg(APPROVED_MASTER)
    for geometry in ['M340 611H584L1472 945H669Z', 'M340 611V167L396 110H584V611Z']:
        assert any(el.get('d') == geometry for el in original.iter())
        assert any(el.get('d') == geometry for el in art.iter()), 'Changed Season 4 architecture'
    vp_x = 340 - 244 * 329 / 559
    vp_y = 611 - 244 * 334 / 559
    shadows = next(el for el in art.iter() if el.get('id') == 'peer-shadows')
    light_x, light_y = map(float, shadows.get('data-struct-light-origin').split())
    projection = shadows[0][0]
    matrix = matrix_values(projection)
    a, b, c, d, tx, ty = matrix
    assert (a, b, d) == (1, 0, -.91)
    assert math.isclose((-c) / (-d), (425 - light_x) / (652 - light_y), abs_tol=1e-7)
    assert 0 < math.atan2(-d, -c) < math.atan2(652 - vp_y, 425 - vp_x)
    assert_point(project_point(matrix, (28, 400)), (453, 652))
    shape = next(el for el in art.iter() if el.get('id') == 'struct-shadow-shape')
    rear = next(el for el in shape if el.get('href') == '#struct-back-leg')
    correction = matrix_values(rear)
    assert_point(project_point(correction, (0, 224)), (0, 224))
    assert_point(project_point(matrix, project_point(correction, (-14, 392))), (411, 644))
    # Translate the approved model and its approved shadow together, preserving
    # the original shoe geometry and contact relationships without resampling.
    peer = next(el for el in art.iter() if el.get('id') == 'groovy-peer')
    assert peer.get('transform') == 'translate(54 0)'
    model = next(el for el in peer if el.tag == SVG + 'image')
    approved = next(el for el in original.iter() if el.tag == SVG + 'image')
    assert model.attrib == {**approved.attrib, 'id': 'approved-groovy-model'}
    original_shadow = next(el for el in original.iter() if el.get('id') == 'approved-shadow-vector')
    reused_shadow = next(el for el in art.iter() if el.get('id') == 'approved-groovy-shadow')
    comparable = lambda el: {key: value for key, value in el.attrib.items() if key not in {'id', 'fill'}}
    assert [comparable(el) for el in reused_shadow] == [comparable(el) for el in original_shadow]
    for layer in shadows:
        right = next(el for el in layer if el.get('href') == '#approved-groovy-shadow')
        assert right.get('transform') == peer.get('transform')


def export_lockup(variant, art):
    root = read_svg(SEASON / f'editable/klumast-4.2-{variant}-text-master.svg')
    placeholder = next(el for el in root if el.get('id') == 'artwork')
    # Titles belong to the lockup; the inserted art supplies geometry only.
    for child in art:
        if child.tag not in {SVG + 'title', SVG + 'desc'}:
            placeholder.append(copy.deepcopy(child))
    if variant == 'header':
        next(el for el in placeholder if el.tag == SVG + 'rect').set('width', '3000')
    if variant == 'documentation-compact':
        rendering = next(el for el in root.iter() if el.get('id') == 'struct-rendering')
        rendering.set('href', '#struct-optical')
    outline_text(root)
    return root


def render(asset, width, name):
    subprocess.run(['rsvg-convert', '-w', str(width), '-o', str(OUT / name), str(asset)], check=True)


def main():
    OUT.mkdir(exist_ok=True)
    art = read_svg(SEASON / 'editable/klumast-4.2-art.svg')
    check_geometry(art)
    assets = {}
    for variant in ['documentation', 'documentation-compact', 'header']:
        assets[variant] = export_lockup(variant, art)
    optical_header = copy.deepcopy(assets['header'])
    next(el for el in optical_header.iter() if el.get('id') == 'struct-rendering').set('href', '#struct-optical')
    optical_header.find(SVG + 'title').text = 'KlumAST 4.2 optical header logo'
    assets['header-optical'] = optical_header
    for variant in ['art', 'art-optical']:
        root = copy.deepcopy(art)
        root.set('viewBox', '250 20 720 720')
        if variant == 'art-optical':
            next(el for el in root.iter() if el.get('id') == 'struct-rendering').set('href', '#struct-optical')
        assets[variant] = root
    detail = copy.deepcopy(art)
    detail.set('viewBox', '484 510 160 180')
    assets['foot-detail'] = detail
    for name, root in assets.items():
        check_svg(root)
        write_svg(root, OUT / f'klumast-4.2-{name}.svg')
    render(OUT / 'klumast-4.2-documentation.svg', 1680, 'documentation-1680.png')
    render(OUT / 'klumast-4.2-documentation.svg', 640, 'documentation-640.png')
    render(OUT / 'klumast-4.2-documentation-compact.svg', 430, 'documentation-compact-430.png')
    render(OUT / 'klumast-4.2-documentation-compact.svg', 320, 'documentation-compact-320.png')
    render(OUT / 'klumast-4.2-header.svg', 990, 'header-990.png')
    for size in [660, 330]:
        render(OUT / 'klumast-4.2-header-optical.svg', size, f'header-optical-{size}.png')
    for size in [256, 128, 64, 32]:
        render(OUT / 'klumast-4.2-art-optical.svg', size, f'art-optical-{size}.png')
    render(OUT / 'klumast-4.2-foot-detail.svg', 640, 'groovy-4.2-foot-detail.png')
    reference = read_svg(ROOT / 'docs/user/img/season-4/klumast-season-4-documentation.svg')
    reference.set('viewBox', '430 510 160 180')
    reference.set('width', '640')
    reference.set('height', '720')
    # Retain the 4.0 comparison; its approved raster model is now also reused
    # exactly in the 4.2 composition, under the explicit model-reuse exception.
    with tempfile.TemporaryDirectory(prefix='klumast-4.0-foot-') as folder:
        reference_path = Path(folder) / 'reference.svg'
        write_svg(reference, reference_path)
        render(reference_path, 640, 'groovy-4.0-foot-reference.png')
    production = ROOT / 'docs/user/img/season-4.2'
    production.mkdir(exist_ok=True)
    for name in ['documentation', 'documentation-compact', 'header-optical']:
        filename = f'klumast-4.2-{name}.svg'
        shutil.copyfile(OUT / filename, production / filename)
    manifest = {
        'status': 'selected-direction; activated for current documentation; final release approval separate',
        'production': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                       for path in sorted(production.glob('*.svg'))},
        'license': 'MIT; repository LICENSE',
        'approvedModel': {
            'source': str(APPROVED_MASTER.relative_to(ROOT)),
            'sourceSha256': hashlib.sha256(APPROVED_MASTER.read_bytes()).hexdigest(),
            'embeddedPngSha256': hashlib.sha256(base64.b64decode(next(
                el for el in read_svg(APPROVED_MASTER).iter() if el.tag == SVG + 'image'
            ).get('href').split(',', 1)[1])).hexdigest(),
            'reuse': 'Exact embedded PNG and image geometry; model and approved shadow translated together by (54,0)',
        },
        'sources': {str(path.relative_to(SEASON)): hashlib.sha256(path.read_bytes()).hexdigest()
                    for path in sorted((SEASON / 'editable').glob('*.svg'))},
        'exports': {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                    for path in sorted(OUT.iterdir()) if path.suffix in {'.svg', '.png'}}
    }
    (SEASON / 'assets.json').write_text(json.dumps(manifest, indent=2) + '\n')
    png_count = len(list(OUT.glob('*.png')))
    print(f'Verified {len(assets)} self-contained SVG exports; rendered {png_count} review PNGs; recorded SHA-256 hashes.')


if __name__ == '__main__':
    main()
