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

"""Issue #952 / S5: validate actual emitted concrete virtual-scope grammar."""
import importlib.metadata
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])
accepted = [
    {},
    {"virtual": {}},
    {"virtual": {"host-name": "packages.example"}},
    {"facts": {"host-name": "direct"}, "virtual": {"host-name": "scoped"},
     "workloads": {"outer": {"image": "worker"}}},
]
rejected = [
    {"virtual": None},
    {"virtual": []},
    {"virtual": "text"},
    {"virtual": 42},
    {"virtual": True},
    {"virtual": {"unknown": "value"}},
    {"virtual": {"host-name": 42}},
    {"virtual": {"owner": {}}},
    {"virtual": {"outer": {"host-name": "not-a-keyed-map"}}},
    {"virtual": {"@type": "example.Facts"}},
    {"scoped": {"host-name": "no-closure-adapter"}},
]
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
