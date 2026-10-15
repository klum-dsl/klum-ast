#!/usr/bin/env python3
"""Probe the real #895 lane-isolation seam from a frozen checkout with Java 17.

Use --expect-broken with the base convention restored, and --expanded for the
complete five-module / three-generation execution graph. Retain raw logs only
in the temporary output directory; no global cache settings are changed.
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
parser.add_argument('--expanded', action='store_true')
args = parser.parse_args()
logs = pathlib.Path(tempfile.mkdtemp(prefix='klum895-lanes-'))
common = ['--configuration-cache', '--configuration-cache-problems=fail',
          '--no-build-cache', '--no-parallel', '--max-workers=2', '--console=plain']
implementation = ['buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle',
                  'buildSrc/src/main/groovy/com/blackbuild/klum/ast/testing/VerifyTestLaneIsolationTask.groovy']
results = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
           'expect_broken': args.expect_broken,
           'implementation_sha256': {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest()
                                     for p in implementation if (ROOT / p).exists()}, 'runs': []}


def run(label, tasks, expected):
    command = ['./gradlew'] + tasks + common
    started = time.monotonic()
    process = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    output = process.stdout
    (logs / (label + '.log')).write_text(output)
    item = {'label': label, 'command': [s.replace(str(logs), '<probe>').replace(logs.name, '<probe-id>') for s in command],
            'exit_code': process.returncode, 'wall_seconds': round(time.monotonic() - started, 3),
            'log_sha256': hashlib.sha256(output.encode()).hexdigest(),
            'cache_status': re.findall(r'^Configuration cache .+$', output, re.MULTILINE),
            'tasks': re.findall(r'^(?:> Task )?(:[\w:-]+)(?: [A-Z-]+)?$', output, re.MULTILINE),
            'lane_verifiers': re.findall(r'^> Task :[^\n]*:verifyTestLaneIsolation(?: [A-Z-]+)?$', output, re.MULTILINE),
            'test_tasks': re.findall(r'^> Task :[^\n]*:(?:test|groovy4Tests|groovy5Tests)(?: [A-Z-]+)?$', output, re.MULTILINE)}
    report = re.search(r'See the complete report at file://([^\n]+)', output)
    if report:
        content = pathlib.Path(report.group(1)).read_text()
        data = json.loads(re.search(r'\n(\{"diagnostics".*?)\n', content).group(1))
        problems = [p for p in data['diagnostics'] if 'problem' in p]
        grouped = collections.Counter((next(t['path'] for t in p['trace'] if t['kind'] == 'Task'),
                                       ''.join(v.get('text', v.get('name', '')) for v in p['problem']))
                                      for p in problems)
        item['problem_count'] = data['totalProblemCount']
        item['unique_problems'] = len(grouped)
        item['problems'] = [{'task': task, 'message': message, 'occurrences': count}
                            for (task, message), count in sorted(grouped.items())]
        item['capture_trace'] = problems[0]['trace'] if problems else []
    results['runs'].append(item)
    print(f'{label}: exit={process.returncode}, {item["wall_seconds"]}s, {item["cache_status"]}', flush=True)
    if expected == 'broken':
        assert process.returncode != 0, label
        assert item['problem_count'] == (45 if label.startswith('full') else 9), item
        assert item['unique_problems'] == (15 if label.startswith('full') else 3), item
    else:
        assert process.returncode == 0, f'{label}: see {logs}'
        assert expected in output, f'{label}: expected {expected}, see {logs}'
    return item


def pair(label, tasks, broken=False):
    # Explicitly read a fresh property value: Gradle can reuse older entries despite a new empty init-script path.
    identity = logs / (label + '.init.gradle')
    identity.write_text("gradle.settingsEvaluated { settings -> settings.providers.gradleProperty('klumLaneCacheProbe').get() }\n")
    tasks = tasks + ['--init-script', str(identity), '-PklumLaneCacheProbe=' + logs.name + '-' + label]
    run(label + '-store', tasks, 'broken' if broken else 'Configuration cache entry stored.')
    return run(label + '-reuse', tasks, 'broken' if broken else 'Configuration cache entry reused.')


try:
    pair('help', ['help'])
    focused = [':klum-ast-bean-validation:' + task for task in
               ['cleanTest', 'cleanGroovy4Tests', 'cleanGroovy5Tests', 'verifyTestLaneIsolation']]
    focused_reuse = pair('focused', focused, args.expect_broken)
    if not args.expect_broken:
        assert len(focused_reuse['test_tasks']) == 3 and all(not t.endswith('UP-TO-DATE') for t in focused_reuse['test_tasks'])
        assert focused_reuse['lane_verifiers'] == ['> Task :klum-ast-bean-validation:verifyTestLaneIsolation']
    pair('full-inventory', ['check', '--dry-run'], args.expect_broken)
    if args.expanded:
        assert not args.expect_broken
        expanded = pair('expanded', ['verifyTestLaneIsolation'])
        assert len(expanded['lane_verifiers']) == 5 and all(not t.endswith('UP-TO-DATE') for t in expanded['lane_verifiers'])
        assert len(expanded['test_tasks']) == 15, expanded
    results['artifacts_sha256'] = {}
    for module in sorted(ROOT.glob('klum-ast*')):
        for pattern in ['build/libs/*.jar', 'build/classes/java/jpms/module-info.class', 'build/publications/*/pom-default.xml']:
            for path in sorted(module.glob(pattern)):
                results['artifacts_sha256'][path.relative_to(ROOT).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    results['junit'] = []
    for module in sorted(ROOT.glob('klum-ast*')):
        for lane in ['test', 'groovy4Tests', 'groovy5Tests']:
            suites = [ET.parse(p).getroot() for p in (module / 'build/test-results' / lane).glob('TEST-*.xml')]
            if suites:
                summary = {'module': module.name, 'lane': lane}
                summary.update({key: sum(int(s.get(key, 0)) for s in suites) for key in ['tests', 'failures', 'errors', 'skipped']})
                results['junit'].append(summary)
finally:
    (logs / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    print(f'Results: {logs / "results.json"}', flush=True)
