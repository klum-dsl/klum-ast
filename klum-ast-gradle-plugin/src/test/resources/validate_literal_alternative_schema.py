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

"""Issues #952 / #1030: actual emitted single- and multiple-literal keyed Map grammar."""
import importlib.metadata
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])
accepted = [
    {},
    {"elements": {}},
    {"elements": {"fixed": {}}},
    {"elements": {"fixed": {"primary": {}}}},
    {"elements": {"fixed": {"primary": {"value": "configured"}}}},
    {"elements": {"fixed": {"": {}, "value": {}, "fixed": {}, "a/~": {}}}},
    {"elements": {"fixed": {"primary": {"value": "configured"}, "secondary": {}}}},
    {"elements": {"fixed": {"primary": {"value": "lifecycle-is-runtime"}}}},
]
# Alias/child payloads are objects, including empty objects; an absent instance is no invocation.
rejected = [{"elements": value} for value in [None, [], "text", 42, True]]
rejected += [{"elements": {name: {}}} for name in ["unknown", "ConcreteElement", "primary"]]
rejected += [{"elements": {"fixed": value}} for value in [None, [], "text", 42, True]]
rejected += [{"elements": {"fixed": {"primary": value}}} for value in [None, [], "text", 42, True]]
rejected += [{"elements": {"fixed": {"primary": body}}} for body in [
    {"name": "primary"}, {"name": "conflict"}, {"owner": {}}, {"@type": "ConcreteElement"},
    {"unknown": "value"}, {"value": 42}, {"value": None},
]]
accepted += [
    {"groupedElements": {}},
    {"groupedElements": {"fixed": {}, "rolling": {}}},
    {"groupedElements": {"rolling": {"b": {"window": "daily"}}, "fixed": {"a": {"value": "configured"}}}},
    {"groupedElements": {"fixed": {"a": {}}, "rolling": {"b": {}, "z": {}}}},
    {"groupedElements": {"fixed": {"same": {}}, "rolling": {"same": {}}}},  # Collision success is runtime policy.
]
rejected += [{"groupedElements": {name: {}}} for name in ["unknown", "RollingElement", "ConcreteElement"]]
rejected += [{"groupedElements": {"rolling": {"b": body}}} for body in [
    {"name": "b"}, {"window": 42}, {"window": None}, {"owner": {}}, {"@type": "RollingElement"},
]]
rejected += [{"groupedElements": {"fixed": {"a": {"window": "wrong subtype"}}}}]
rejected += [{"groupedElements": {"fixed": {}, "rolling": value}} for value in [None, [], "text", 42, True]]
for generation in (3, 4, 5):
    path = root / f"virtual-scopes-{generation}.schema.json"
    document = json.loads(path.read_text())
    Draft202012Validator.check_schema(document)
    validator = Draft202012Validator(document)
    for instance in accepted:
        assert validator.is_valid(instance), (path, instance)
    for instance in rejected:
        assert not validator.is_valid(instance), (path, instance)
print(f"3 emitted schemas: {3 * len(accepted)} accepted and {3 * len(rejected)} rejected instances")
