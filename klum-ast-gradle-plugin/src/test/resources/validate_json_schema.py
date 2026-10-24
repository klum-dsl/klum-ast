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

"""Issues #529/#954: validate emitted TestKit documents with external jsonschema==4.23.0 tooling."""
import importlib.metadata
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version('jsonschema') == '4.23.0'
root = Path(sys.argv[1])
accepted = [
    {}, {"region": "eu"},
    {"services": {"api.v1": {"image": "nginx", "mode": "SAFE", "nested": {"service/id~": "nested", "mode": "FAST"}}}},
    {"service": {"service/id~": "catalog", "image": "catalog:1.0"}},
    {"fixed": {"image": "fixed:1.0"}, "derived": {}, "cluster": {"catalog": {}}},
    {"services": {"api": {"children": {"worker": {"nested": {"service/id~": "deep"}}}}}},
    {"configure-input": {"topics/one": ["a", "b"]}},
    {"annotations": {"app.kubernetes.io/name": "api"}, "labels": ["a", "a"]},
    {"replicas": 2147483647, "capacity": 9223372036854775807, "counter": 10**100,
     "budget": 0.001, "weight": 0.25},
]
rejected = [
    {"service": {}}, {"service": {"service/id~": None}}, {"service": {"service/id~": 1}},
    {"fixed": {"service/id~": "fixed"}}, {"derived": {"service/id~": "derived"}},
    {"cluster": {"catalog": {"service/id~": "catalog"}}},
    {"services": {"api": {"service/id~": "api"}}},
    {"services": {"api": {"nested": {}}}},
    {"excludedChild": {}}, {"mappedServices": {"outer": {}}},
    None, [], {"unknown": 1}, {"replicas": 2147483648}, {"replicas": "3"},
    {"capacity": 9223372036854775808}, {"services": []}, {"services": {"api": {"name": "invalid"}}},
    {"services": {"api": {"mode": "WRONG"}}}, {"configure-input": {"a": "wrong"}},
    {"configure": {}}, {"labels": ["a", None]}, {"annotations": {"a": 2}}, {"region": None},
]
documents = {
    "deployment": (accepted, rejected),
    "service": (
        [{"service/id~": ""}, {"service/id~": "api", "nested": {"service/id~": "child"}},
         {"service/id~": "api", "children": {"worker": {"nested": {"service/id~": "deep"}}}}],
        [{}, {"name": "native"}, {"service/id~": None}, {"service/id~": False}, {"service/id~": []},
         {"service/id~": "api", "nested": {}},
         {"service/id~": "api", "children": {"worker": {"service/id~": "worker"}}}]),
    "keylessuses": (
        [{}, {"ignored": {"outer": {"value": "ok"}}, "fixedIgnored": {}},
         {"collisions": {"outer": {"value": "ordinary"}}, "fixedCollision": {"value": "ordinary"}}],
        [{"ignored": {"outer": {"name": "outer"}}}, {"fixedIgnored": {"name": "fixedIgnored"}},
         {"collisions": {"outer": {"name": "outer"}}}, {"fixedCollision": {"unknown": "bad"}}]),
    "fallbackkey": ([{"id/~": ""}, {"id/~": "root", "value": "ok"}],
                    [{}, {"key": "native"}, {"id/~": 1}, {"id/~": "root", "unknown": True}]),
    "emptyrename": ([{"name": ""}, {"name": "root"}], [{}, {"name": None}, {"name": 1}]),
    "ignoredoperationcollision": ([{"value": "root"}], [{}, {"name": "native"}, {"value": 1}]),
}
for document, (accepted_values, rejected_values) in documents.items():
    paths = sorted(root.glob(f"*/{document}.schema.json"))
    assert len(paths) == 3, f"Run KlumJsonSchemaGenerationTest for all three Groovy generations: {document}"
    for path in paths:
        schema = json.loads(path.read_text())
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema)
        for value in accepted_values:
            assert validator.is_valid(value), (path, value, list(validator.iter_errors(value)))
        for value in rejected_values:
            assert not validator.is_valid(value), (path, value)
        print(f"{path.parent.name}/{document}: metaschema, "
              f"{len(accepted_values)} accepted / {len(rejected_values)} rejected")
    assert len({path.read_bytes() for path in paths}) == 1, "Groovy generations must emit identical JSON"
