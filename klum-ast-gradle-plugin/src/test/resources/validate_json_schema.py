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

"""Issue #529: validate emitted TestKit documents with external jsonschema==4.23.0 tooling."""
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

root = Path(sys.argv[1])
accepted = [
    {}, {"region": "eu"},
    {"services": {"api.v1": {"image": "nginx", "mode": "SAFE", "nested": {"mode": "FAST"}}}},
    {"configure-input": {"topics/one": ["a", "b"]}},
    {"annotations": {"app.kubernetes.io/name": "api"}, "labels": ["a", "a"]},
    {"replicas": 2147483647, "capacity": 9223372036854775807, "counter": 10**100,
     "budget": 0.001, "weight": 0.25},
]
rejected = [
    None, [], {"unknown": 1}, {"replicas": 2147483648}, {"replicas": "3"},
    {"capacity": 9223372036854775808}, {"services": []}, {"services": {"api": {"name": "invalid"}}},
    {"services": {"api": {"mode": "WRONG"}}}, {"configure-input": {"a": "wrong"}},
    {"configure": {}}, {"labels": ["a", None]}, {"annotations": {"a": 2}}, {"region": None},
]
paths = sorted(root.glob("*/deployment.schema.json"))
assert len(paths) == 3, "Run KlumJsonSchemaGenerationTest for all three Groovy generations first"
for path in paths:
    schema = json.loads(path.read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    for value in accepted:
        assert validator.is_valid(value), (path, value, list(validator.iter_errors(value)))
    for value in rejected:
        assert not validator.is_valid(value), (path, value)
    print(f"{path.parent.name}: metaschema, {len(accepted)} accepted / {len(rejected)} rejected")
assert len({path.read_bytes() for path in paths}) == 1, "Groovy generations must emit identical JSON"
