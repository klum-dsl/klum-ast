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

"""Issue #959: external Draft 2020-12 controls for source-free first-level composition.

Usage: python validate_composed_constraint_schema.py <plugin build/schema-validation directory>
Requires jsonschema; this qualification runs independently from the production generator and Groovy tests.
"""
import importlib.metadata
import json
import sys
from decimal import Decimal
from pathlib import Path

from jsonschema import Draft202012Validator

root = Path(sys.argv[1])
checks = 0
outputs = []
for lane in ("3", "4", "5"):
    path = root / lane / "composition.schema.json"
    outputs.append(path.read_bytes())
    schema = json.loads(path.read_text(), parse_float=Decimal)
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)

    def check(value, valid):
        global checks
        actual = validator.is_valid(value)
        assert actual == valid, (lane, value, actual, valid)
        checks += 1

    check({}, True)
    for value in (-1, 0, 1, 5, 10, 100, 101, 2147483648):
        check({"range": value}, 1 <= value <= 100)
        check({"multiple": value}, 10 <= value <= 100)
        check({"contradiction": value}, False)
    for value in (None, "10", True, Decimal("1.5"), [], {}):
        check({"range": value}, False)
    for value in (Decimal("1.25"), Decimal("1.2501"), Decimal("9.75"), Decimal("9.7501")):
        check({"decimal": value}, Decimal("1.25") < value <= Decimal("9.75"))
    for value in ("AB", "ab", "A", "ABC", "AB\n", "ÄB", None, 12):
        check({"code": value}, value == "AB")
    for field in ("or", "all-false", "overridden", "nested", "grouped", "payload", "repeated"):
        for value in (-100, 0, 200):
            check({field: value}, True)
    check({"intersecting-patterns": "AB"}, True)
    check({"intersecting-patterns": "BB"}, False)
    check({"direct-with-email": "AB"}, True)
    check({"direct-with-email": "email@example.com"}, False)
    check({"flagged-code": "outside"}, True)
    check({"repeated-code": "outside"}, True)
    check({"text-only": -100}, True)
    check({"omitted": -100}, True)
    check({"Inherited/Range": 0}, False)
    check({"Inherited/Range": 100}, True)
    check({"independent-pattern": "AB"}, True)
    check({"independent-pattern": "invalid"}, False)
    check({"description-only": "unvalidated prose"}, True)
    check({"size-only": ""}, True)
    properties = schema["$defs"]["example.CompositionService_DSL$Builder"]["properties"]
    assert properties["multiple"]["allOf"] == [
        {"minimum": 5}, {"minimum": 10}, {"maximum": 100}, {"minimum": 1}]
    assert properties["contradiction"]["allOf"] == [{"maximum": 5}, {"minimum": 10}]
    assert properties["omitted"]["examples"] == ["explicit field example"]
    assert "default" not in properties["omitted"]
    assert "Percentage of traffic" not in properties["omitted"]["description"]
assert outputs[0] == outputs[1] == outputs[2], "Groovy producer schemas differ"
print(f"PASS: {checks} input controls, 3 Draft 2020-12 metaschemas, byte-identical G3/G4/G5; "
      f"jsonschema {importlib.metadata.version('jsonschema')}")
