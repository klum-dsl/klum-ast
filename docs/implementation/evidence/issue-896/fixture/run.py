#!/usr/bin/env python3
"""#896 baseline Layer 3 binary matrix, 4.0.1/4.1.0."""
from common import *
ROWS = [
    ('AA', '4.0.1', '4.0.1', 'normal', None),
    ('BB', '4.1.0', '4.1.0', 'normal', None),
    ('AB', '4.0.1', '4.1.0', 'normal', None),
    ('BA', '4.1.0', '4.0.1', 'normal', None),
    ('AB-runtime-A', '4.0.1', '4.1.0', 'runtime', '4.0.1'),
    ('BB-runtime-A', '4.1.0', '4.1.0', 'runtime', '4.0.1'),
    ('BA-all-A', '4.1.0', '4.0.1', 'all', '4.0.1'),
]

report = {'versions': ['4.0.1', '4.1.0'], 'repositories': ['https://repo.maven.apache.org/maven2/',
          'https://plugins.gradle.org/m2/', '$FIXTURE/repository (application fixture artifacts only)'],
          'java_home_required': 'JDK 17', 'schema': {}, 'rows': []}
for version in report['versions']:
    properties = {'klumVersion': version}
    report['schema'][version] = run('schema', properties, ['clean', 'resolutionProof', 'dependencyInsight',
        '--configuration', 'compileClasspath', '--dependency', 'com.blackbuild.klum.ast',
        'compileGroovy', 'publishMavenJavaPublicationToFixtureRepository'], f'schema-{version}')
    if report['schema'][version]['exit_code']:
        output.write_text(json.dumps(report, indent=2) + '\n')
        raise SystemExit('Schema control failed; see ignored results logs')

for name, schema, model, selection, selected in ROWS:
    properties = {'klumVersion': model, 'schemaVersion': schema, 'row': name, 'selection': selection}
    if selected:
        properties['selectedVersion'] = selected
    row = {'name': name, 'schema_version': schema, 'model_plugin_version': model, 'selection': selection}
    row['model'] = run('model', properties, ['clean', 'resolutionProof', 'dependencyInsight',
        '--configuration', 'runtimeClasspath', '--dependency', 'com.blackbuild.klum.ast',
        'compileGroovy', 'executeModel', 'publishMavenJavaPublicationToFixtureRepository'], f'{name}-model')
    row['negative_compile'] = run('model', properties, ['compileNegativeGroovy'], f'{name}-negative')
    consumer_properties = {'row': name}
    if selected:
        consumer_properties['selectedVersion'] = selected
    if row['model']['exit_code'] == 0:
        row['consumer'] = run('consumer', consumer_properties, ['clean', 'resolutionProof', 'dependencyInsight',
            '--configuration', 'runtimeClasspath', '--dependency', 'com.blackbuild.klum.ast', 'run'], f'{name}-consumer')
    row['passed'] = (row['model']['exit_code'] == 0 and row.get('consumer', {}).get('exit_code') == 0
        and row['negative_compile']['exit_code'] != 0
        and any('Cannot find matching method' in line and 'missingDslOperation' in line
                for line in row['negative_compile']['diagnostics'])
        and any('NO_AST_COMPILER' in line for line in row.get('consumer', {}).get('proof', [])))
    report['rows'].append(row)
    output.write_text(json.dumps(report, indent=2) + '\n')

report['all_rows_passed'] = all(row['passed'] for row in report['rows'])
output.write_text(json.dumps(report, indent=2) + '\n')
print('Completed: ' + str(sum(row['passed'] for row in report['rows'])) + '/7 positive rows passed')

if not report['all_rows_passed']:
    raise SystemExit('Unexpected matrix outcome; inspect evidence and results logs')
