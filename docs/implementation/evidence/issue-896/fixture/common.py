#!/usr/bin/env python3
"""#896: isolated binary Schema/Model compatibility experiment; no product build changes."""
import argparse
import json
import os
from pathlib import Path
import shlex
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gradle', default=str(next(parent / 'gradlew' for parent in ROOT.parents if (parent / 'gradlew').exists())))
parser.add_argument('--gradle-home', default=None, help='Default: newly created disposable Gradle home')
parser.add_argument('--evidence', default=str(ROOT / 'results' / 'matrix.json'))
args = parser.parse_args()
home = Path(args.gradle_home or tempfile.mkdtemp(prefix='klum-issue896-gradle-')).resolve()
output = Path(args.evidence).resolve()
output.parent.mkdir(parents=True, exist_ok=True)
logs = ROOT / 'results'
logs.mkdir(exist_ok=True)

def normalize(value):
    return (value.replace(str(ROOT), '$FIXTURE')
            .replace(str(home), '$GRADLE_HOME')
            .replace(str(home).replace('/tmp/', '/private/tmp/'), '$GRADLE_HOME')
            .replace(args.gradle, '$GRADLE')
            .replace(os.environ.get('JAVA_HOME', '/__absent__'), '$JAVA_HOME'))

def run(project, properties, tasks, name):
    command = [args.gradle, '--gradle-user-home', str(home), '--no-daemon', '--max-workers=2',
               '--console=plain', '-p', str(ROOT / project)]
    command += [f'-P{key}={value}' for key, value in properties.items()]
    command += tasks
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (logs / f'{name}.log').write_text(result.stdout)
    lines = result.stdout.splitlines()
    evidence = {
        'command': normalize(shlex.join(command)), 'exit_code': result.returncode,
        'proof': [normalize(line) for line in lines if line.startswith((
            'GRADLE ', 'PROJECT ', 'CONFIGURATION ', 'COMPONENT ', 'ARTIFACT ',
            'PLUGIN_CLASS ', 'GROOVY_COMPILER_CLASSPATH ', 'SCHEMA_OR_MODEL_COMPILE_CLASSPATH ',
            'MODEL_OK ', 'CONSUMER_OK ', 'NO_AST_COMPILER', 'RUNTIME_ORIGIN '))],
        'diagnostics': [normalize(line) for line in lines if any(pattern in line for pattern in (
            'BUILD SUCCESSFUL', 'BUILD FAILED', 'Cannot find matching method', 'missingDslOperation',
            'Cannot classify referenced declaration', 'Exception in thread', 'Could not resolve',
            'Could not find', 'Execution failed', 'error:', 'errors', 'Error:', 'Exception:', 'Caused by:', 'NoSuchMethod', 'AbstractMethod', 'NoClassDef', 'IncompatibleClass'))],
    }
    if 'dependencyInsight' in tasks:
        start = result.stdout.find('> Task :dependencyInsight')
        end = result.stdout.find('\n> Task :', start + 1)
        evidence['dependencyInsight'] = normalize(result.stdout[start:end if end >= 0 else None]).strip()
    print(f'{name}: exit {result.returncode}', flush=True)
    return evidence
