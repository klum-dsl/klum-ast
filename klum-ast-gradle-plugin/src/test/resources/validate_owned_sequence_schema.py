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

"""Issue #952 / SEQ-1: validate emitted body-sequence grammar, not collection outcomes."""
import importlib.metadata
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])
accepted = [[], [{}], [{"host-name": "z"}, {"host-name": "a"}, {"host-name": "a"}]]
rejected = [None, {}, "text", 42, True, [None], ["text"], [42], [True], [[]],
            [{"host-name": 42}], [{"unknown": "x"}], [{"owner": {}}],
            [{"@type": "example.Facts"}], [{"key": "invented"}]]
members = ("factList", "factSet", "sortedFacts", "navigableFacts", "inheritedFacts")
for generation in (3, 4, 5):
    path = root / f"owned-sequences-{generation}.schema.json"
    document = json.loads(path.read_text())
    Draft202012Validator.check_schema(document)
    validator = Draft202012Validator(document)
    properties = document["$defs"]["example.Deployment_DSL$Builder"]["properties"]
    for member in members:
        grammar = properties[member]
        assert grammar["type"] == "array"
        assert grammar["items"]["$ref"] == properties["facts"]["$ref"]
        assert not {"uniqueItems", "minItems", "maxItems", "default"}.intersection(grammar)
        for items in accepted:
            assert validator.is_valid({member: items}), (path, member, items)
        for items in rejected:
            assert not validator.is_valid({member: items}), (path, member, items)
print(f"3 emitted schemas: {15 * len(accepted)} accepted and {15 * len(rejected)} rejected instances")
