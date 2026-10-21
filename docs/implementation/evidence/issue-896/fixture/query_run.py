#!/usr/bin/env python3
"""#896 feature-specific binary witness: @Builder.Query added in 4.1."""
from common import *
report = {'schema': {}, 'rows': []}
report['schema']['4.1.0'] = run('query-schema', {'klumVersion':'4.1.0'}, ['clean','resolutionProof','compileGroovy','publishMavenJavaPublicationToFixtureRepository'], 'query-schema')
if report['schema']['4.1.0']['exit_code']:
    output.write_text(json.dumps(report, indent=2)+'\n')
    raise SystemExit('Query producer control failed')
for name, selection, selected in [('query-control','normal',None),('query-old-runtime','runtime','4.0.1'),('query-all-old','all','4.0.1')]:
    properties = {'klumVersion':'4.1.0', 'schemaVersion':'4.1.0','row':name,'selection':selection}
    if selected: properties['selectedVersion'] = selected
    row = {'name': name, 'selection': selection}
    row['compile'] = run('query-model', properties, ['clean','resolutionProof','dependencyInsight','--configuration','runtimeClasspath','--dependency','com.blackbuild.klum.ast','compileGroovy','publishMavenJavaPublicationToFixtureRepository'], name+'-compile')
    if row['compile']['exit_code'] == 0:
        row['execute'] = run('query-model', properties, ['executeModel'], name+'-execute')
        cp = {'row':name}
        if selected: cp['selectedVersion'] = selected
        row['consumer'] = run('query-consumer', cp, ['clean','resolutionProof','run'], name+'-consumer')
    report['rows'].append(row)
    output.write_text(json.dumps(report, indent=2)+'\n')

for row in report['rows']:
    assert row['compile']['exit_code'] == 0, row['name']
    for part in ('execute', 'consumer'):
        run_result = row[part]
        if row['name'] == 'query-control':
            assert run_result['exit_code'] == 0, row['name']
        else:
            assert run_result['exit_code'] != 0, row['name']
            assert any('NoSuchMethodError' in line and '$klum$completedModelOrNull' in line
                       for line in run_result['diagnostics']), row['name']
