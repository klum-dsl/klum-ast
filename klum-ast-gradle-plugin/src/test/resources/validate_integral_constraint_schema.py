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

"""Issue #959: external Draft 2020-12 validation of emitted, binary-only numeric artifacts."""
import importlib.metadata
import json
import sys
from decimal import Decimal
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])
paths = sorted(root.glob("integral-*/capacity.schema.json"))
assert len(paths) == 3, "Run the integral documentary test with all three producer Groovy generations"
integral = ["byteValue", "boxedByte", "shortValue", "boxedShort", "intValue", "percentage",
            "longValue", "boxedLong", "large"]
accepted = [{}, {"enabled": False, "label": ""}, {"percentage": 0, "label": "", "enabled": False},
            {"fromText": "101"}, {"numericText": ""}, {"decimal": 0.5}, {"computed": "literal"},
            {"child": {}}, {"child": {"percentage": 0}}, {"child": {"percentage": 100}},
            {"inherited-percent": 0}, {"inherited-percent": 100},
            {"machine": -128}, {"machine": 127},
            {"exact": -9223372036854775808}, {"exact": 9223372036854775807}]
rejected = [{"decimal": -0.5}, {"child": None}, {"child": {"percentage": 101}}, {"child": {"percentage": -1}},
            {"inherited-percent": -1}, {"inherited-percent": 101}, {"inherited": 50}, {"hidden": 50},
            {"label": None}, {"enabled": None}, {"enabled": 0}, {"fromText": 50},
            {"machine": -129}, {"machine": 128},
            {"exact": -9223372036854775809}, {"exact": 9223372036854775808}]
for field in integral:
    accepted.extend({field: value} for value in [0, 100, 100.0])
    rejected.extend({field: value} for value in [-1, 101, None, False, "", "50", 0.5, 2147483648])
# Complete but unsupported contexts are text, with their original structural type admission.
for field in ["grouped", "explicitDefault", "warning", "composed", "repeated", "customRepeated"]:
    accepted.extend({field: value} for value in [-1, 101])
for path in paths:
    schema = json.loads(path.read_text(), parse_float=Decimal)
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    for value in accepted:
        assert validator.is_valid(value), (path, value, list(validator.iter_errors(value)))
    for value in rejected:
        assert not validator.is_valid(value), (path, value)
    print(f"{path.parent.name}: metaschema, {len(accepted)} accepted / {len(rejected)} rejected")
assert len({path.read_bytes() for path in paths}) == 1, "Numeric output must be byte-identical across Groovy generations"
