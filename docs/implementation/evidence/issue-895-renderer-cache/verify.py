#!/usr/bin/env python3
"""Exercise the real renderer owner, strict cache replay, and live rejection controls.

Run from an isolated, frozen checkout with Java 17. --expect-broken requires the
owning build.gradle and task source from a15148a8; no source changes are made in
that mode. Default controls temporarily perturb live files and restore their
exact bytes, including on failure. Do not run other builds concurrently here.
"""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--expect-broken', action='store_true')
args = parser.parse_args()
logs = Path(tempfile.mkdtemp(prefix='klum895-renderer-'))
common = ['--configuration-cache', '--configuration-cache-problems=fail',
          '--no-build-cache', '--no-parallel', '--max-workers=2', '--console=plain']
owned = ['build.gradle', 'buildSrc/src/main/groovy/com/blackbuild/klum/ast/docs/VerifyVersionedDocumentationRendererTask.groovy']
results = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
           'expect_broken': args.expect_broken,
           'implementation_sha256': {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in owned},
           'runs': []}
# Force a fresh configuration entry without deleting existing cache entries or execution history.
init = logs / 'probe.init.gradle'
init.write_text('''
gradle.settingsEvaluated { settings ->
    settings.providers.gradleProperty('klumRendererCacheProbe').get()
}
gradle.projectsEvaluated {
    def root = gradle.rootProject
    if (root.name == 'buildSrc') return
    def probe = root.providers.gradleProperty('rendererNegative').orElse('').get()
    if (probe == 'lateJavadocDestination') root.project(':klum-ast-annotations').tasks.named('javadoc') {
        destinationDir = new File(root.buildDir, 'renderer-probe-javadoc')
    }
    if (probe == 'generatedMirror') {
        File directory = new File(root.buildDir, 'renderer-probe-source')
        File mirror = new File(directory, 'Probe_DSL.java')
        def producer = root.tasks.register('writeRendererProbeMirror') {
            outputs.file(mirror)
            doLast { directory.mkdirs(); mirror.text = 'public class Probe_DSL {}' }
        }
        root.project(':klum-ast-annotations').tasks.named('javadoc') {
            dependsOn(producer)
            source(root.fileTree(directory))
        }
    }
}
// Mutate only unscheduled contract models after the root's projectsEvaluated publication wiring.
// This configuration callback is not an execution listener or a production build change.
gradle.taskGraph.whenReady {
    def root = gradle.rootProject
    if (root.name == 'buildSrc') return
    def probe = root.providers.gradleProperty('rendererNegative').orElse('').get()
    if (probe == 'publication') root.tasks.named('publishMavenProductToSonatype').get().setDependsOn([])
    if (probe == 'rendererDependency') {
        def inspected = root.tasks.named('renderVersionedDocumentation').get()
        inspected.setDependsOn([])
        inspected.moduleJavadocs.setFrom([])
    }
    if (probe == 'pendingDependency') root.tasks.named('preparePendingDocumentationStage').get().setDependsOn([])
    if (probe == 'lateVersion') root.tasks.named('renderLocalDocumentationFiles').get().documentationVersion.set('4.9.9')
}
''')
probe_args = ['--init-script', str(init), '-PklumRendererCacheProbe=' + logs.name]


def run(label, tasks, cache, diagnostic=None):
    command = ['./gradlew'] + tasks + common + probe_args
    started = time.monotonic()
    completed = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    output = completed.stdout
    (logs / (label + '.log')).write_text(output)
    item = {'label': label,
            'command': [s.replace(str(logs), '<probe>').replace(logs.name, '<probe-id>') for s in command],
            'exit_code': completed.returncode, 'wall_seconds': round(time.monotonic() - started, 3),
            'log_sha256': hashlib.sha256(output.encode()).hexdigest(),
            'cache_status': re.findall(r'^Configuration cache .+$', output, re.MULTILINE),
            'tasks': re.findall(r'^(?:> Task )?(:[\w:-]+)(?: [A-Z-]+)?$', output, re.MULTILINE),
            'renderer_outcome': re.findall(r'^> Task :verifyVersionedDocumentationRenderer[^\n]*$', output, re.MULTILINE)}
    report = re.search(r'See the complete report at file://([^\n]+)', output)
    if report:
        content = Path(report.group(1)).read_text()
        data = json.loads(re.search(r'\n(\{"diagnostics".*?)\n', content).group(1))
        problems = [p for p in data['diagnostics'] if 'problem' in p]
        grouped = collections.Counter((next(t['path'] for t in p['trace'] if t['kind'] == 'Task'),
                                       ''.join(v.get('text', v.get('name', '')) for v in p['problem'])) for p in problems)
        item['problem_count'] = data['totalProblemCount']
        item['unique_problems'] = len(grouped)
        item['problems'] = [{'task': task, 'message': message, 'occurrences': count}
                            for (task, message), count in sorted(grouped.items())]
        frames = []
        for problem in problems:
            for part in problem.get('error', {}).get('parts', []):
                frames += re.findall(r'VerifyVersionedDocumentationRendererTask\.verifyRendererContract\([^\n]+', part.get('text', part.get('internalText', '')))
        item['owning_frames'] = sorted(set(frames))
    else:
        item.update(problem_count=0, unique_problems=0, problems=[])
    results['runs'].append(item)
    print(f'{label}: exit={completed.returncode}, {item["wall_seconds"]}s, {item["cache_status"]}', flush=True)
    if cache == 'broken':
        assert completed.returncode != 0 and item['problem_count'] == 7 and item['unique_problems'] == 1, item
        assert "Task with name 'renderVersionedDocumentation' not found" in output, label
    else:
        assert cache in output, f'{label}: expected {cache}; see {logs}'
        assert item['problem_count'] == 0, item
        if diagnostic:
            assert completed.returncode != 0 and diagnostic in output, f'{label}: see {logs}'
            assert item['renderer_outcome'] == ['> Task :verifyVersionedDocumentationRenderer FAILED'], item
        else:
            assert completed.returncode == 0, f'{label}: see {logs}'
    if '--dry-run' not in tasks and not diagnostic:
        assert item['renderer_outcome'] == ['> Task :verifyVersionedDocumentationRenderer' + (' FAILED' if cache == 'broken' else '')], item
    if diagnostic:
        item['expected_diagnostic'] = diagnostic
    return item


def perturb(label, path, change, diagnostic):
    target = ROOT / path
    original = target.read_bytes()
    changed = change(original)
    assert changed != original, label
    try:
        target.write_bytes(changed)
        item = run(label, ['verifyVersionedDocumentationRenderer'], 'Configuration cache entry reused.', diagnostic)
        item['changed_file'] = path
        item['before_sha256'] = hashlib.sha256(original).hexdigest()
        item['changed_sha256'] = hashlib.sha256(changed).hexdigest()
    finally:
        target.write_bytes(original)
    run(label + '-restored', ['verifyVersionedDocumentationRenderer'], 'Configuration cache entry reused.')


# Nebula tracks Git status as a configuration input. Keep the same dirty path set
# across byte mutations, so rejection tests actually replay the healthy cache entry.
live_paths = ['docs/user/Home.md', 'README.md', '.github/workflows/publish-pending-documentation.yml',
              'docs/user/assets/migrate-3x-to-4x-builder-first.sh', 'release/plugin-consumer/settings.gradle']
saved_live = {path: (ROOT / path).read_bytes() for path in live_paths}
try:
    for path, original in saved_live.items():
        (ROOT / path).write_bytes(original + b'\n')
    focused = ['verifyVersionedDocumentationRenderer']
    run('focused-store', focused, 'broken' if args.expect_broken else 'Configuration cache entry stored.')
    run('focused-reuse', focused, 'broken' if args.expect_broken else 'Configuration cache entry reused.')
    inventory = ['check', '--dry-run']
    run('full-inventory-store', inventory, 'Configuration cache entry stored.')
    run('full-inventory-reuse', inventory, 'Configuration cache entry reused.')
    if not args.expect_broken:
        perturb('docs', 'docs/user/Home.md', lambda b: b + b'\n<div>invalid authored HTML</div>\n',
                'Current user documentation contains authored presentation HTML')
        perturb('readme', 'README.md', lambda b: b + b'\n[Current documentation](docs/user/Home.md)\n',
                'README must not present repository authoring files as current public documentation')
        perturb('workflow', '.github/workflows/publish-pending-documentation.yml',
                lambda b: b.replace(b'_REL-1a: Stage pending documentation (reusable)', b'Probe staging name'),
                'the pending Pages workflow must remain visibly distinct')
        perturb('migration-script', 'docs/user/assets/migrate-3x-to-4x-builder-first.sh',
                lambda b: b.replace(b'if [[ -n', b'if [ -n'), 'the migration script must retain literal double-bracket syntax')
        perturb('publication-repository', 'release/plugin-consumer/settings.gradle',
                lambda b: b.replace(b'gradlePluginPortal()', b'mavenCentral()'),
                'the Plugin Portal fixture must resolve plugin markers from the real Plugin Portal')
        controls = [
            ('version', ['-PdocumentationVersion=4.9.9'], 'local documentation version must default'),
            ('revision', ['-PdocumentationRevision=bad-revision'], 'local documentation revision must default'),
            ('publication', ['-PrendererNegative=publication'], 'the protected Maven publication aggregate must depend on every Sonatype publication task'),
            ('renderer-dependency', ['-PrendererNegative=rendererDependency'], 'Exact-version rendering must depend on every allowed module Javadoc task'),
            ('pending-dependency', ['-PrendererNegative=pendingDependency'], 'protected pending preparation must require the HTTP presentation and link crawl'),
            ('late-version', ['-PrendererNegative=lateVersion'], 'local documentation version must default'),
            ('generated-mirror', ['-PrendererNegative=generatedMirror'], 'standard klum-ast-annotations Javadocs must exclude IDE source mirrors'),
        ]
        for label, extra, diagnostic in controls:
            tasks = focused + extra
            mirror = ROOT / 'build/renderer-probe-source/Probe_DSL.java'
            if label == 'generated-mirror':
                mirror.unlink(missing_ok=True)
            run(label + '-store', tasks, 'Configuration cache entry stored.', diagnostic)
            if label == 'generated-mirror':
                mirror.unlink()
            run(label + '-reuse', tasks, 'Configuration cache entry reused.', diagnostic)
        relocated = focused + ['-PrendererNegative=lateJavadocDestination']
        default_representative = ROOT / 'klum-ast-annotations/build/docs/javadoc/com/blackbuild/klum/ast/DSL.html'
        original_default = default_representative.read_bytes()
        try:
            default_representative.unlink()
            run('late-javadoc-destination-store', relocated, 'Configuration cache entry stored.')
            run('late-javadoc-destination-reuse', relocated, 'Configuration cache entry reused.')
        finally:
            default_representative.write_bytes(original_default)
        # Invalidate representative output after Javadoc completes; it must be rejected, including on cache replay.
        representative = ROOT / 'klum-ast-annotations/build/docs/javadoc/com/blackbuild/klum/ast/DSL.html'
        assert representative.is_file(), representative
        original = representative.read_bytes()
        try:
            representative.unlink()
            # Exclude the producer only for this corruption control so Gradle cannot repair it first.
            tasks = focused + ['-x', ':klum-ast-annotations:javadoc']
            diagnostic = 'standard klum-ast-annotations Javadocs must expose a representative public type'
            run('missing-javadoc-store', tasks, 'Configuration cache entry stored.', diagnostic)
            run('missing-javadoc-reuse', tasks, 'Configuration cache entry reused.', diagnostic)
        finally:
            representative.write_bytes(original)
        run('final-restored-store', focused, 'Configuration cache entry stored.')
        run('final-restored-reuse', focused, 'Configuration cache entry reused.')
finally:
    for path, original in saved_live.items():
        (ROOT / path).write_bytes(original)
    assert results['head'] == subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(), 'HEAD moved during probe'
    (logs / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    print(f'Results: {logs / "results.json"}', flush=True)
