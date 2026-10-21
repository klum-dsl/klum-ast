"""#896: independently verify retained resolution, outcomes, and archive boundaries."""
import json
from pathlib import Path
import sys


def modules(run, configuration):
    result, current = {}, None
    for line in run['proof']:
        if line.startswith('CONFIGURATION '):
            current = line.split()[1]
        elif current == configuration and line.startswith('COMPONENT '):
            coordinate = line.split()[1].split(':')
            if len(coordinate) == 3:
                result[':'.join(coordinate[:2])] = coordinate[2]
    return result


def require_modules(run, configuration, version):
    resolved = modules(run, configuration)
    for module in ('klum-ast-runtime', 'klum-ast-annotations'):
        assert resolved[f'com.blackbuild.klum.ast:{module}'] == version
    assert 'com.blackbuild.klum.ast:klum-ast' not in resolved


def require_origin(run, version):
    assert any(f'/klum-ast-runtime/{version}/' in line
               for line in run['proof'] if line.startswith('RUNTIME_ORIGIN '))


def verify(directory):
    baseline = json.loads((directory / 'matrix.json').read_text())
    assert {row['name'] for row in baseline['rows']} == {
        'AA', 'BB', 'AB', 'BA', 'AB-runtime-A', 'BB-runtime-A', 'BA-all-A'}
    assert baseline['all_rows_passed']
    for version, schema in baseline['schema'].items():
        assert schema['exit_code'] == 0
        resolved = modules(schema, 'compileClasspath')
        for module in ('klum-ast', 'klum-ast-runtime', 'klum-ast-annotations', 'klum-ast-bom'):
            assert resolved[f'com.blackbuild.klum.ast:{module}'] == version
        assert modules(schema, 'buildscriptClasspath')['com.blackbuild.klum.ast:klum-ast-gradle-plugin'] == version
    for row in baseline['rows']:
        assert row['passed']
        normal = max(row['schema_version'], row['model_plugin_version'])
        compile_version = '4.0.1' if row['selection'] == 'all' else normal
        runtime_version = normal if row['selection'] == 'normal' else '4.0.1'
        assert row['negative_compile']['exit_code'] != 0
        assert any('Cannot find matching method' in line and 'missingDslOperation' in line
                   for line in row['negative_compile']['diagnostics'])
        for part, expected_compile in (('model', compile_version), ('consumer', normal)):
            run = row[part]
            assert run['exit_code'] == 0
            require_modules(run, 'compileClasspath', expected_compile)
            require_modules(run, 'runtimeClasspath', runtime_version)
            assert modules(run, 'runtimeClasspath')['com.blackbuild.klum.ast:klum-ast-bom'] == normal
            require_origin(run, runtime_version)
        assert 'NO_AST_COMPILER' in row['consumer']['proof']
        assert modules(row['model'], 'buildscriptClasspath')['com.blackbuild.klum.ast:klum-ast-gradle-plugin'] == row['model_plugin_version']

    query = json.loads((directory / 'query-matrix.json').read_text())
    assert query['schema']['4.1.0']['exit_code'] == 0
    assert {row['name'] for row in query['rows']} == {
        'query-control', 'query-old-runtime', 'query-all-old'}
    for row in query['rows']:
        control = row['name'] == 'query-control'
        runtime = '4.1.0' if control else '4.0.1'
        assert row['compile']['exit_code'] == 0
        require_modules(row['compile'], 'compileClasspath',
                        '4.0.1' if row['selection'] == 'all' else '4.1.0')
        require_modules(row['compile'], 'runtimeClasspath', runtime)
        require_modules(row['consumer'], 'runtimeClasspath', runtime)
        for part in ('execute', 'consumer'):
            run = row[part]
            require_origin(run, runtime)
            if control:
                assert run['exit_code'] == 0
            else:
                assert run['exit_code'] != 0
                assert any('NoSuchMethodError' in line and '$klum$completedModelOrNull' in line
                           for line in run['diagnostics'])
        if control:
            assert 'NO_AST_COMPILER' in row['consumer']['proof']

    projection = json.loads((directory / 'projection.json').read_text())
    assert set(projection) == {'4.0.1', '4.1.0'}
    for version, run in projection.items():
        assert run['exit_code'] == 0 and run['mirror_count'] > 0
        assert any(archive['generated_binary_interfaces'] for archive in run['archives'].values())
        assert not any(archive['mirror_sources'] for archive in run['archives'].values())
        assert not any('generated/sources/klum-dsl-ide' in line for line in run['proof']
                       if line.startswith('SCHEMA_OR_MODEL_COMPILE_CLASSPATH '))
    print('Verified seven positive rows, seven static rejection controls, three query rows '
          '(including four expected linkage failures), and two projection/archive controls.')


if __name__ == '__main__':
    verify(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
