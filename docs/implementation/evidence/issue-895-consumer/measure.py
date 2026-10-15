#!/usr/bin/env python3
"""Run real consumer qualification; retain local logs/XML and process timing records."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--label', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--repeats', type=int, default=2)
args = parser.parse_args()
if args.repeats < 1:
    parser.error('--repeats must be positive')
root = Path(__file__).resolve().parents[4]
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
source = root / 'klum-ast/src/test/groovy/com/blackbuild/klum/ast/LifecycleParticipantConsumerTest.groovy'
sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
source_hash = hashlib.sha256(source.read_bytes()).hexdigest()

with tempfile.TemporaryDirectory(prefix='consumer-timings-') as temporary:
    init = Path(temporary) / 'timings.init.gradle'
    init.write_text('''allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'klum.consumerTimings', 'true'
        outputs.upToDateWhen { false }
    }
}
''')
    for repeat in range(1, args.repeats + 1):
        for lane in ['test', 'groovy4Tests', 'groovy5Tests']:
            name = f'{args.label}-{repeat}-{lane}'
            command = ['./gradlew', f':klum-ast:{lane}', '--tests',
                       'com.blackbuild.klum.ast.LifecycleParticipantConsumerTest',
                       '--init-script', str(init), '--no-build-cache',
                       '--no-configuration-cache', '--no-parallel', '--max-workers=2',
                       '--console=plain']
            started = datetime.now(timezone.utc).isoformat()
            before = time.monotonic()
            with (output / f'{name}.log').open('w') as log:
                result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
            wall = time.monotonic() - before
            if result.returncode:
                raise SystemExit(f'{name} failed; inspect {output / (name + ".log")}')
            if hashlib.sha256(source.read_bytes()).hexdigest() != source_hash:
                raise SystemExit('Consumer source changed during measurement')
            xml = root / f'klum-ast/build/test-results/{lane}/TEST-com.blackbuild.klum.ast.LifecycleParticipantConsumerTest.xml'
            suite = ET.parse(xml).getroot()
            processes = []
            for line in suite.findtext('system-out', '').splitlines():
                if line.startswith('CONSUMER_PROCESS\t'):
                    _, version, kind, epoch, duration, sources = line.split('\t')
                    processes.append(dict(groovy=version, kind=kind, started_epoch_ms=int(epoch),
                                          duration_ns=int(duration), sources=sources))
            if not processes:
                raise SystemExit('Missing process instrumentation; this is not a timing run')
            record = dict(label=args.label, repeat=repeat, lane=lane, sha=sha,
                          source_sha256=source_hash, started_utc=started,
                          java_home=os.environ.get('JAVA_HOME'), command=command,
                          wall_seconds=wall,
                          suite={key: suite.get(key) for key in
                                 ['tests', 'skipped', 'failures', 'errors', 'time', 'timestamp']},
                          cases=[dict(case.attrib) for case in suite.findall('testcase')],
                          processes=processes)
            (output / f'{name}.json').write_text(json.dumps(record, indent=2) + '\n')
            (output / f'{name}.xml').write_bytes(xml.read_bytes())
            counts = dict(Counter(process['kind'] for process in processes))
            print(f'{name}: wall={wall:.3f}s suite={suite.get("time")}s {counts}', flush=True)
