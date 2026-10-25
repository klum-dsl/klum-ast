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

"""Issue #959: qualify advisory deprecation on production-emitted binary-consumer schemas."""
import copy
import importlib.metadata
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])


def without_deprecation(value):
    if isinstance(value, dict):
        value.pop("deprecated", None)
        for child in value.values():
            without_deprecation(child)
    elif isinstance(value, list):
        for child in value:
            without_deprecation(child)


documents = {
    "p2": (
        [{}, {"oldEndpoint": "old"}, {"endpoint": "new"}, {"oldEndpoint": "old", "endpoint": "new"},
         {"old-inherited": "old", "old-inherited-operation": "payload", "directOperation": "payload"},
         {"old-operation": "payload"}, {"old-child": {"name": "old"}, "child": {"name": "new"}},
         {"children": {"outer-key": {"value": "value"}}},
         {"oldCount": 1, "oldTags": ["one"], "oldLabels": {"one": "two"}, "oldMode": "SAFE", "oldEnabled": False},
         {"oldTagsOperation": ["one", "two"]}],
        [None, [], {"oldEndpoint": None}, {"oldEndpoint": 1}, {"endpoint": None}, {"hidden": "ignored"},
         {"hiddenOperation": "ignored"}, {"excludedLink": {"name": "old"}}, {"unknown": "value"},
         {"collision": "value"}, {"mixed-collision": "value"}, {"old-child": {}},
         {"old-child": {"name": None}}, {"children": {"outer-key": {"name": "inner-key"}}},
         {"old-operation": None}, {"oldTagsOperation": [None]}]),
    "deprecatedfallbackkey": (
        [{"legacy-id": ""}, {"legacy-id": "root", "value": "value"}],
        [{}, {"legacy-id": None}, {"key": "native"}, {"legacy-id": "root", "unknown": "value"}]),
}
checks = 0
for document, (accepted, rejected) in documents.items():
    paths = sorted(root.glob(f"deprecation-*/{document}.schema.json"))
    assert len(paths) == 3, f"Run all three deprecation documentary producer cases: {document}"
    for path in paths:
        schema = json.loads(path.read_text())
        Draft202012Validator.check_schema(schema)
        structural = copy.deepcopy(schema)
        without_deprecation(structural)
        validator = Draft202012Validator(schema)
        baseline = Draft202012Validator(structural)
        for expected, values in ((True, accepted), (False, rejected)):
            for value in values:
                assert validator.is_valid(value) == expected, (path, value, expected)
                assert baseline.is_valid(value) == expected, (path, value, "advisory parity")
                checks += 1
    assert len({path.read_bytes() for path in paths}) == 1, "Producer generations must emit identical schemas"
print(f"Deprecation: 6 metaschemas, {checks} input checks with advisory-removal parity across G3/G4/G5")
