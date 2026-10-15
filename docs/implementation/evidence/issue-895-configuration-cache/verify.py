#!/usr/bin/env python3
"""Exercise #895 cache controls and the real Gradle-plugin test input seam.

Run from a frozen isolated checkout with Java 17. Raw logs stay outside the repo.
The broken control is intended for the parent implementation at the same HEAD.
"""
import argparse
import collections
import hashlib
import json
import pathlib
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--expect-broken', action='store_true')
args = parser.parse_args()
logs = pathlib.Path(tempfile.mkdtemp(prefix='klum895-config-'))
common = ['--configuration-cache', '--configuration-cache-problems=fail',
          '--no-build-cache', '--no-parallel', '--max-workers=2', '--console=plain']
results = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
           'implementation_sha256': hashlib.sha256((ROOT / 'klum-ast-gradle-plugin/build.gradle').read_bytes()).hexdigest(),
           'expect_broken': args.expect_broken, 'runs': []}


def run(label, tasks, expected):
    command = ['./gradlew'] + tasks + common
    started = time.monotonic()
    completed = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    elapsed = time.monotonic() - started
    output = completed.stdout
    (logs / (label + '.log')).write_text(output)
    item = {'label': label, 'command': [arg.replace(str(logs), '<probe>') for arg in command], 'exit_code': completed.returncode,
            'wall_seconds': round(elapsed, 3), 'log_sha256': hashlib.sha256(output.encode()).hexdigest(),
            'cache_status': re.findall(r'^Configuration cache .+$', output, re.MULTILINE),
            'task_graph': re.findall(r'^(?:> Task )?(:[\w:-]+)(?: [A-Z-]+)?$', output, re.MULTILINE),
            'plugin_test_outcome': re.findall(r'^> Task :klum-ast-gradle-plugin:test(?: [A-Z-]+)?$', output, re.MULTILINE)}
    report = re.search(r'See the complete report at file://([^\n]+)', output)
    if report:
        content = pathlib.Path(report.group(1)).read_text()
        data = json.loads(re.search(r'\n(\{"diagnostics".*?)\n', content).group(1))
        problems = [entry for entry in data['diagnostics'] if 'problem' in entry]
        item['problem_count'] = data['totalProblemCount']
        grouped = collections.Counter((next(t['path'] for t in p['trace'] if t['kind'] == 'Task'),
                                       ''.join(part.get('text', part.get('name', '')) for part in p['problem']))
                                      for p in problems)
        item['problems'] = [{'task': task, 'message': message, 'occurrences': count}
                            for (task, message), count in sorted(grouped.items())]
    results['runs'].append(item)
    print(f'{label}: exit={completed.returncode}, {elapsed:.3f}s, {item["cache_status"]}', flush=True)
    if expected == 'broken':
        assert completed.returncode != 0 and 'Configuration cache entry discarded with 1 problem.' in output, label
        assert "org.gradle.api.tasks.bundling.Jar" in output, label
    elif expected == 'inventory':
        assert completed.returncode != 0 and 'Configuration cache entry discarded' in output, label
        assert item['problem_count'] == (46 if args.expect_broken else 45), item
        assert any('Jar' in str(p) for p in item['problems']) == args.expect_broken, item
    else:
        assert completed.returncode == 0, f'{label}: see {logs}'
        assert expected in output, f'{label}: expected {expected}, see {logs}'
    return item


try:
    for label, tasks in [
        ('help', ['help']),
        ('core-focused', [':klum-ast:test', '--tests', 'com.blackbuild.klum.ast.AnnotationPropagationContractTest']),
        ('plugin-focused', [':klum-ast-gradle-plugin:cleanTest', ':klum-ast-gradle-plugin:test',
                            '--tests', 'com.blackbuild.klum.ast.gradle.KlumGdslArchiveTest'])
    ]:
        broken = args.expect_broken and label == 'plugin-focused'
        # A new, empty init script selects a fresh entry without replacing ordinary execution history.
        identity = logs / (label + '.init.gradle')
        identity.touch()
        pair = tasks + ['--init-script', str(identity)]
        run(label + '-store', pair, 'broken' if broken else 'Configuration cache entry stored.')
        repeated = run(label + '-reuse', pair, 'broken' if broken else 'Configuration cache entry reused.')
        if label == 'plugin-focused' and not broken:
            assert repeated['plugin_test_outcome'] == ['> Task :klum-ast-gradle-plugin:test'], repeated
            suites = [ET.parse(p).getroot() for p in (ROOT / 'klum-ast-gradle-plugin/build/test-results/test').glob('TEST-*.xml')]
            assert suites and all(int(s.get('failures')) == int(s.get('errors')) == 0 for s in suites)
            results['focused_cases'] = sum(int(s.get('tests')) for s in suites)
    run('full-inventory', ['check', '--dry-run'], 'inventory')
    if not args.expect_broken:
        marker = logs / 'archive-input.txt'
        marker.write_text('original archive resource\n')
        init = logs / 'archive-input.init.gradle'
        init.write_text('''
allprojects {
    if (path == ':klum-ast') {
        pluginManager.withPlugin('java') {
            tasks.named('jar') {
                from(files('%s')) { into('META-INF/issue-895') }
            }
        }
    }
}
gradle.projectsEvaluated {
    if (rootProject.findProject(':klum-ast') == null) return // Exclude buildSrc and nested builds.
    def compilerJar = rootProject.project(':klum-ast').tasks.named('jar').get()
    def pluginTest = rootProject.project(':klum-ast-gradle-plugin').tasks.named('test').get()
    // Compare the original Task file notation with the corrected archive provider.
    assert rootProject.files(compilerJar).files == rootProject.files(compilerJar.archiveFile).files
    assert pluginTest.inputs.files.files.contains(compilerJar.archiveFile.get().asFile)
}
''' % marker.as_posix())
        archive_tasks = [':klum-ast-gradle-plugin:test', '--tests', 'com.blackbuild.klum.ast.gradle.KlumGdslArchiveTest',
                         '--init-script', str(init)]
        run('archive-store', archive_tasks, 'Configuration cache entry stored.')
        warm = run('archive-unchanged', archive_tasks, 'Configuration cache entry reused.')
        assert warm['plugin_test_outcome'] == ['> Task :klum-ast-gradle-plugin:test UP-TO-DATE'], warm
        marker.write_text('changed archive resource\n')
        changed = run('archive-changed', archive_tasks, 'Configuration cache entry reused.')
        assert changed['plugin_test_outcome'] == ['> Task :klum-ast-gradle-plugin:test'], changed
finally:
    (logs / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    print(f'Results: {logs / "results.json"}', flush=True)
