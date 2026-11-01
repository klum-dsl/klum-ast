#
# The MIT License (MIT)
#
# Copyright (c) 2015-2026 Stephan Pauxberger
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

"""Issue #953 / S7: independently validate emitted body and self-contained artifacts."""
import importlib.metadata
import json
import re
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])
cases = {
    "service.builder-body": (
        [{}, {"image": "api"}, {"nested": {"service/id~": "child"}},
         {"children": {"worker": {"nested": {"service/id~": "deep"}}}}],
        [{"service/id~": "root"}, {"name": "root"}, {"nested": {}},
         {"children": {"worker": {"service/id~": "worker"}}},
         {"nested": {"service/id~": None}}, {"unknown": "value"}, None, []]),
    "service.self-contained": (
        [{"service/id~": "root"}, {"service/id~": "root", "children": {"worker": {}}}],
        [{}, {"service/id~": None}, {"service/id~": "root", "nested": {}},
         {"service/id~": "root", "children": {"worker": {"service/id~": "worker"}}}]),
    "deployment.builder-body": (
        [{}, {"service": {"service/id~": "child"}}, {"services": {"worker": {}}},
         {"fixed": {}, "derived": {}, "cluster": {"catalog": {}}}],
        [{"service": {}}, {"services": {"worker": {"service/id~": "worker"}}},
         {"fixed": {"service/id~": "fixed"}}, {"derived": {"service/id~": "derived"}},
         {"cluster": {"catalog": {"service/id~": "catalog"}}}]),
    "fallbackkey.builder-body": (
        [{}, {"value": "body"}], [{"id/~": "root"}, {"key": "root"}]),
    "fallbackkey.self-contained": ([{"id/~": "root"}], [{}, {"key": "root"}]),
    "ignored.builder-body": ([{}, {"value": "body"}], [{"name": "root"}]),
    # The colliding spelling belongs to the admitted ordinary property in a keyless body.
    "collision.builder-body": ([{}, {"value": "ordinary"}], [{"name": "root"}]),
}

def schema_without_artifact_locations(path):
    """Compare diagnostics independently of each fixture's temporary JAR location."""
    schema = json.loads(path.read_text())
    schema["x-klum-unsupported"] = {
        source: re.sub(r"artifact file:[^\]]+", "artifact <fixture>", reason)
        for source, reason in schema.get("x-klum-unsupported", {}).items()
    }
    return json.dumps(schema, sort_keys=True)


def validate_cases(cases, prefix, normalise_artifact_locations=False):
    accepted = rejected = emitted = 0
    for name, (positive, negative) in cases.items():
        paths = sorted(root.glob(f"{prefix}/{name}.schema.json"))
        assert len(paths) == 3, f"Run S7 fixture for Groovy 3/4/5: {name}"
        comparable = [schema_without_artifact_locations(path) if normalise_artifact_locations else path.read_bytes()
                      for path in paths]
        assert len(set(comparable)) == 1, name
        for path in paths:
            schema = json.loads(path.read_text())
            Draft202012Validator.check_schema(schema)
            validator = Draft202012Validator(schema)
            for value in positive:
                assert validator.is_valid(value), (path, value, list(validator.iter_errors(value)))
                accepted += 1
            for value in negative:
                assert not validator.is_valid(value), (path, value)
                rejected += 1
            emitted += 1
    return accepted, rejected, emitted


accepted, rejected, emitted = validate_cases(cases, "body-*")
for generation in ("3", "4", "5"):
    directory = root / f"body-{generation}"
    assert (directory / "deployment.self-contained.schema.json").read_bytes() == (directory / "deployment.builder-body.schema.json").read_bytes()
command_cases = {
    "command.builder-body": (
        [{}, {"resetTimeout": {}}, {"current-command": {}}, {"external-endpoint": "api"},
         {"naturalDuration": "PT30S"}],
        [{"name": "root"}, {"resetTimeout": None}, {"resetTimeout": {"value": "bad"}},
         {"resetTimeout": []}, {"current-command": False}, {"old-command": {}},
         {"FromStruct": {}}, {"Template": {}}, {"naturalDuration": 30}, {"naturalDuration": None}]),
    "command.self-contained": (
        [{"name": "root"}, {"name": "root", "resetTimeout": {}}, {"name": "root", "naturalDuration": "PT30S"}],
        [{}, {"resetTimeout": {}}, {"name": "root", "resetTimeout": {"value": "bad"}},
         {"name": "root", "naturalDuration": 30}, {"name": "root", "naturalDuration": None}]),
}
command_accepted, command_rejected, command_emitted = validate_cases(
    command_cases, "body-command-*", normalise_artifact_locations=True)
accepted += command_accepted
rejected += command_rejected
emitted += command_emitted + 3  # Unkeyed self-contained deployment artifacts equal their checked body artifacts.
print(f"S7: {emitted} emitted schemas; {accepted} accepted / {rejected} rejected instances; "
      "Groovy 3/4/5 parity (command diagnostic artifact URLs normalized)")
