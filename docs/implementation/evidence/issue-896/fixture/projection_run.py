#!/usr/bin/env python3
"""#896: independent AnnoDocimal projection and packaging controls."""
from common import *
import zipfile
report = {}
for version in ['4.0.1', '4.1.0']:
    report[version] = run('schema', {'klumVersion':version}, ['clean','resolutionProof','createKlumDslSourceMirrors','javadoc','publishMavenJavaPublicationToFixtureRepository'], 'projection-'+version)
    root = ROOT / 'schema' / 'build'
    archives = {}
    for jar in sorted((root / 'libs').glob('*.jar')):
        with zipfile.ZipFile(jar) as archive:
            entries = archive.namelist()
        archives[jar.name] = {
            'generated_binary_interfaces': [name for name in entries if '_DSL' in name and name.endswith('.class')],
            'mirror_sources': [name for name in entries if '_DSL' in name and name.endswith('.java')],
        }
    report[version]['archives'] = archives
    report[version]['mirror_count'] = len(list((root / 'generated/sources/klum-dsl-ide').rglob('*.java')))
    output.write_text(json.dumps(report, indent=2)+'\n')

for version, result in report.items():
    assert result['exit_code'] == 0 and result['mirror_count'] > 0, version
    assert any(archive['generated_binary_interfaces'] for archive in result['archives'].values()), version
    assert not any(archive['mirror_sources'] for archive in result['archives'].values()), version
