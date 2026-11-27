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

"""Issue #1129 U1: validate actual emitted int32/String schemas, independently of runtime."""
import importlib.metadata
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])
accepted = [{}, *[{"address": value} for value in [
    -2147483648, 2147483647, 0, 8080, 8080.0, "8080", "http", "", 13,
]]]
rejected = [{"address": value} for value in [
    -2147483649, 2147483648, 1.5, None, True, False, {}, [], [8080],
]] + [{"unknown": "http"}, {"value": "http"}]
for kind in ("boxed", "primitive"):
    path = root / f"scalar-alternative-{kind}.schema.json"
    document = json.loads(path.read_text())
    Draft202012Validator.check_schema(document)
    validator = Draft202012Validator(document)
    for instance in accepted:
        assert validator.is_valid(instance), (path, instance)
    for instance in rejected:
        assert not validator.is_valid(instance), (path, instance)
print(f"2 emitted schemas: {2 * len(accepted)} accepted and {2 * len(rejected)} rejected instances")
