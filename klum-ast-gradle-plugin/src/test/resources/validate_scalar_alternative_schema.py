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

"""Issue #1129 U2: validate actual generated schemas and arm assertions with Draft 2020-12."""
import importlib.metadata
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])
structural = [-2147483648, 2147483647, 0, 8080, 8080.0, "8080", "http", "", 13]
invalid = [-2147483649, 2147483648, 1.5, None, True, False, {}, [], [8080]]
cases = {
    "boxed": ([1, 9000, 8080.0, "8080", "http", 13], [-2147483648, 0, 9001, 2147483647, "", "HTTP", "a" * 13, "http\n", "é"]),
    "primitive": (structural, []),
    "stringchild": ([1, 9000, "HTTP"], [0, 9001, "http", "8080", ""]),
    "mixed": ([-2147483648, 0, 2147483647, "http"], ["", "HTTP", "a" * 9]),
    "child": ([-2147483648, 0, 99, "8080", "http"], [100, 2147483647, "", "HTTP"]),
    "profile": ([7, 2147483647, "8080", ""], [6, -2147483648]),
    "text": (structural, []),
    "omit": (structural, []),
    "domain": ([1, 99, "", "HTTP"], [0, 100, 2147483647]),
    "grouped": (structural, []),
    "collected": (structural, []),
    "sequence": (structural, []),
}
accepted = rejected = schemas = 0
for generation in (3, 4, 5):
    for kind, (valid_values, invalid_values) in cases.items():
        path = root / f"scalar-alternative-{kind}-g{generation}.schema.json"
        document = json.loads(path.read_text())
        Draft202012Validator.check_schema(document)
        validator = Draft202012Validator(document)
        name = "address-value" if kind == "profile" else "address"
        properties = next(iter(document["$defs"].values()))["properties"]
        arms = properties[name]["anyOf"]
        assert [arm["type"] for arm in arms] == ["integer", "string"], path
        assert arms[0]["minimum"] == -2147483648 and arms[0]["maximum"] == 2147483647
        for instance in [{}, *[{name: value} for value in valid_values]]:
            assert validator.is_valid(instance), (path, instance)
            accepted += 1
        for instance in [*[{name: value} for value in invalid + invalid_values], {"unknown": "http"}, {"value": "http"}]:
            assert not validator.is_valid(instance), (path, instance)
            rejected += 1
        schemas += 1
    # Separated effective names are ordinary single routes: transported Min must not project here.
    path = root / f"scalar-alternative-separated-g{generation}.schema.json"
    document = json.loads(path.read_text())
    Draft202012Validator.check_schema(document)
    validator = Draft202012Validator(document)
    assert validator.is_valid({"address-value": 0, "addressValue": "http"}), path
    assert not validator.is_valid({"address-value": "0"}), path
    accepted += 1
    rejected += 1
    schemas += 1
print(f"{schemas} emitted schemas: {accepted} accepted and {rejected} rejected instances")
