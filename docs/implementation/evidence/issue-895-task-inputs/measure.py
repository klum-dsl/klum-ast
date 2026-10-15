#!/usr/bin/env python3
"""Measure seven task prerequisites across outer/TestKit/artifact invocation boundaries."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--label', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--repeats', type=int, default=2)
args = parser.parse_args()
if args.repeats < 2:
    parser.error('At least two repeats are required')
root = Path(__file__).resolve().parents[4]
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
testkit = root / 'klum-ast-test-support/build/tmp/test/work/.gradle-test-kit'
if not (testkit / 'caches/modules-2').is_dir():
    parser.error('Populate the existing TemplateScopePublishedConsumerTest TestKit home first')


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def revision():
    return subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()


def implementation():
    paths = sorted((root / 'buildSrc/src/main').rglob('*'))
    paths.append(root / 'buildSrc/build.gradle')
    return {str(p.relative_to(root)): digest(p) for p in paths if p.is_file()}


sha = revision()
sources = implementation()
targets = [':klum-ast:jar', ':klum-ast-test-support:jar'] + [
    f':{module}:generatePomFileForMavenJavaPublication'
    for module in ['klum-ast-annotations', 'klum-ast-runtime', 'klum-ast-bom']]
records = []
for repeat in range(1, args.repeats + 1):
    for boundary in ['outer', 'testkit', 'artifact-prerequisites', 'unchanged-repeat']:
        name = f'{args.label}-{repeat}-{boundary}'
        command = ['./gradlew', *targets, '--init-script', str(Path(__file__).with_name('trace.init.gradle')),
                   '--info', '--no-build-cache', '--no-configuration-cache', '--no-parallel',
                   '--max-workers=2', '--console=plain']
        if boundary == 'testkit':
            command += ['--gradle-user-home', str(testkit)]
        before = time.monotonic()
        log = output / f'{name}.log'
        with log.open('w') as stream:
            result = subprocess.run(command, cwd=root, stdout=stream, stderr=subprocess.STDOUT)
        wall = time.monotonic() - before
        if result.returncode:
            raise SystemExit(f'{name} failed; see {log}')
        tasks = []
        for line in log.read_text().splitlines():
            if not line.startswith('TASK_HYGIENE '):
                continue
            task = json.loads(line[len('TASK_HYGIENE '):])
            task['modulePath'] = [dict(name=Path(p).name, sha256=digest(p)) for p in task['modulePath']]
            task['output_sha256'] = digest(task.pop('output'))
            tasks.append(task)
        if len(tasks) != 7:
            raise SystemExit(f'Expected seven task records, got {len(tasks)}')
        if revision() != sha or implementation() != sources:
            raise SystemExit('Revision or buildSrc implementation changed during measurement')
        records.append(dict(label=args.label, repeat=repeat, boundary=boundary, sha=sha,
                            wall_seconds=wall, log_sha256=digest(log), tasks=tasks))
        (output / f'{args.label}.json').write_text(json.dumps(dict(
            source_sha=sha, implementation_sha256=sources,
            java_home_name=Path(os.environ['JAVA_HOME']).parent.parent.name,
            flags=['--info', '--no-build-cache', '--no-configuration-cache', '--no-parallel', '--max-workers=2'],
            targets=targets, records=records), indent=2) + '\n')
        executed = sum(t['outcome'] == 'SUCCESS' for t in tasks)
        print(f'{name}: {wall:.3f}s, {executed}/7 executed', flush=True)
