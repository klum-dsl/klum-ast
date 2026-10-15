#!/usr/bin/env python3
"""Advance only a disposable checkout between outer configuration and nested publication."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET


BASE = "49cc93c85e61d6ca795c790e7d81f11e23a728d6"
ADVANCED = "aa55ed9a3c2266645cff0f53f3a06ce9c69e6f90"
MODULE = "klum-ast-test-support"
TEST = "com.blackbuild.klum.ast.testsupport.TemplateScopePublishedConsumerTest"
TEST_SOURCE = f"{MODULE}/src/test/groovy/{TEST.replace('.', '/')}.groovy"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["before", "after"], required=True)
    parser.add_argument("--negative-control", choices=["live-root", "live-version"])
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.negative_control and args.mode != 'after':
        parser.error('--negative-control requires --mode after')
    source = Path(__file__).resolve().parents[4]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    checkout = output / "checkout"

    def git(*arguments):
        return subprocess.check_output(["git", *arguments], cwd=source, text=True).strip()

    subprocess.run(["git", "clone", "--no-hardlinks", str(source), str(checkout)], check=True)
    subprocess.run(["git", "checkout", "--detach", BASE], cwd=checkout, check=True)
    # Both controls have identical snapshot wiring and production source. Only the test fixture differs.
    shutil.copyfile(source / MODULE / "build.gradle", checkout / MODULE / "build.gradle")
    if args.mode == "after":
        shutil.copyfile(source / TEST_SOURCE, checkout / TEST_SOURCE)
    if args.negative_control:
        test_source = checkout / TEST_SOURCE
        text = test_source.read_text()
        if args.negative_control == 'live-root':
            text = text.replace('.withProjectDir(fixture)', '.withProjectDir(projectDirectory())')
        else:
            text = text.replace('.withArguments(arguments.toList() + ["-Prelease.version=${version()}".toString()])',
                                '.withArguments(arguments.toList())')
        assert text != test_source.read_text()
        test_source.write_text(text)

    init_script = output / "advance-head.init.gradle"
    init_script.write_text("""
gradle.projectsEvaluated {
    if (gradle.rootProject.name != 'klum-ast-root') return
    gradle.rootProject.project(':klum-ast-test-support').tasks.named('test') {
        outputs.upToDateWhen { false }
        doFirst {
            println "captured.outer.version=${rootProject.version}"
            def process = new ProcessBuilder('git', 'update-ref', 'HEAD', '%s')
                    .directory(rootProject.projectDir).redirectErrorStream(true).start()
            def output = process.inputStream.text
            assert process.waitFor() == 0 : output
        }
    }
}
""" % ADVANCED)
    command = [str(source / "gradlew"), "-p", str(checkout), f":{MODULE}:test", "--tests", TEST,
               "--init-script", str(init_script), "--no-build-cache", "--no-configuration-cache",
               "--no-parallel", "--max-workers=2", "--console=plain"]
    if args.negative_control:
        command[command.index(TEST)] = TEST + '.*nested publication*'
    started = time.monotonic()
    with (output / "build.log").open("w") as log:
        result = subprocess.run(command, cwd=source, stdout=log, stderr=subprocess.STDOUT)
    suite = ET.parse(checkout / MODULE / "build/test-results/test" / f"TEST-{TEST}.xml").getroot()
    failures = [node.attrib["message"] for node in suite.iter("failure")]
    log_text = (output / "build.log").read_text()
    version = next(line.split("=", 1)[1] for line in log_text.splitlines()
                   if line.startswith("captured.outer.version="))
    report = {
        "mode": args.mode, "negative_control": args.negative_control, "source_head": git("rev-parse", "HEAD"),
        "captured_revision": BASE, "advanced_revision": ADVANCED, "captured_version": version,
        "test_source_sha256": hashlib.sha256((checkout / TEST_SOURCE).read_bytes()).hexdigest(),
        "build_source_sha256": hashlib.sha256((checkout / MODULE / "build.gradle").read_bytes()).hexdigest(),
        "java_version": subprocess.check_output([str(Path(os.environ["JAVA_HOME"]) / "bin/java"), '-version'],
                                                stderr=subprocess.STDOUT, text=True).splitlines(),
        "wrapper_seconds": round(time.monotonic() - started, 3), "exit_code": result.returncode,
        "suite": {key: suite.attrib[key] for key in ["tests", "failures", "errors", "skipped", "time"]},
        "failures": failures,
    }
    (output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    if args.negative_control:
        assert result.returncode != 0 and len(failures) == 1
    elif args.mode == "before":
        assert result.returncode != 0 and len(failures) == 1
        assert "FileNotFoundException" in failures[0] and version in failures[0]
    else:
        assert result.returncode == 0 and not failures


if __name__ == "__main__":
    main()
