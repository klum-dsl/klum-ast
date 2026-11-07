#!/usr/bin/env python3
"""Export selected lettering E; render all studies only into temporary storage.

SPDX-License-Identifier: MIT
Copyright (c) 2026 Stephan Pauxberger
See the repository LICENSE for the full license text.
"""

import argparse
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

import export_assets as artwork

STUDIES = artwork.SEASON / 'lettering-options'
SVG = artwork.SVG
SELECTED_MASTER = STUDIES / 'editable/klumast-4.2-e-overlaid-ending-text-master.svg'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview-all', action='store_true',
                        help='Render A–E into a new temporary directory without changing canonical assets or inventory.')
    args = parser.parse_args()
    masters = sorted((STUDIES / 'editable').glob('*.svg'))
    output = Path(tempfile.mkdtemp(prefix='klumast-lettering-')) if args.preview_all else STUDIES / 'exports'
    output.mkdir(exist_ok=True)
    generated = []
    art = artwork.read_svg(artwork.SEASON / 'editable/klumast-4.2-art.svg')
    artwork.check_geometry(art)
    for master in (masters if args.preview_all else [SELECTED_MASTER]):
        root = artwork.read_svg(master)
        placeholder = next(el for el in root if el.get('id') == 'artwork')
        for child in art:
            if child.tag not in {SVG + 'title', SVG + 'desc'}:
                placeholder.append(copy.deepcopy(child))
        artwork.outline_text(root)
        artwork.check_svg(root)
        target = output / master.name.replace('-text-master', '')
        artwork.write_svg(root, target)
        generated.append(target)
        for width in [1008, 430]:
            png = target.with_name(f'{target.stem}-{width}.png')
            subprocess.run(['rsvg-convert', '-w', str(width), '-o', str(png), str(target)], check=True)
            generated.append(png)
        # A native, outlined crop lets reviewers compare the lettering directly.
        crop_x, crop_width = (660, 900) if 'd-shared-y' in master.name else (720, 840)
        crop = ET.Element(SVG + 'svg', {'viewBox': f'{crop_x} 315 {crop_width} 240', 'role': 'img', 'aria-labelledby': 'title desc'})
        for child in root:
            if child.tag in {SVG + 'title', SVG + 'desc'}:
                crop.append(copy.deepcopy(child))
        ET.SubElement(crop, SVG + 'rect', {'x': str(crop_x), 'y': '315', 'width': str(crop_width), 'height': '240', 'fill': '#041321'})
        lettering = copy.deepcopy(next(el for el in root if el.tag == SVG + 'g' and el.get('id') != 'artwork'))
        for child in list(lettering):
            if child.get('id') in {'lettering-klumast', 'lettering-season-4-2'}:
                lettering.remove(child)
        crop.append(lettering)
        for decoration in root:
            if decoration.tag == SVG + 'path':
                crop.append(copy.deepcopy(decoration))
        artwork.check_svg(crop)
        crop_path = target.with_name(target.stem + '-lettering.svg')
        artwork.write_svg(crop, crop_path)
        subprocess.run(['rsvg-convert', '-w', '840', '-o', str(crop_path.with_suffix('.png')), str(crop_path)], check=True)
        generated.extend([crop_path, crop_path.with_suffix('.png')])
    if args.preview_all:
        print(f'Rendered A–E for local review in {output}; canonical assets and inventory unchanged.')
        return
    inventory = {str(path.relative_to(STUDIES)): hashlib.sha256(path.read_bytes()).hexdigest()
                 for path in masters + sorted(generated)}
    (STUDIES / 'assets.json').write_text(json.dumps(inventory, indent=2) + '\n')
    print('Exported selected E: 2 SVGs and 3 PNGs; inventoried all 5 editable masters.')


if __name__ == '__main__':
    main()
