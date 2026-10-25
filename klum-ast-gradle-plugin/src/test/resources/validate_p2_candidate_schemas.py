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

"""Issue #959: hand-authored P2 candidates, never emitted production mappings."""
import importlib.metadata
import json
from decimal import Decimal

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
checks = 0


def check(schema, accepted, rejected):
    global checks
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    for valid, values in ((True, accepted), (False, rejected)):
        for value in values:
            assert validator.is_valid(value) == valid, (schema, value, valid)
            checks += 1


# Parse schema numeric tokens as Decimal; no binary-float conversion on this path.
exact = json.loads('''{"type":"number", "exclusiveMinimum":9007199254740993.0000000001,
                       "maximum":9007199254740993.0000000002}''', parse_float=Decimal)
check(exact, [Decimal("9007199254740993.00000000015"), Decimal("9007199254740993.0000000002")],
      [Decimal("9007199254740993.0000000001"), Decimal("9007199254740993.0000000003"), "9007199254740993.00000000015", None])
check({"type": "number", "minimum": Decimal("-1.2300E+2"), "exclusiveMaximum": Decimal("1E+3")},
      [Decimal("-123"), Decimal("-122.999"), 0, Decimal("999.999")],
      [Decimal("-123.0001"), Decimal("1000.00"), "0", False, None])
check({"type": "number", "minimum": Decimal("-0.000"), "maximum": Decimal("0E+5")},
      [0, Decimal("0.00000")], [Decimal("-1E-99"), Decimal("1E-99")])
check({"type": "integer", "minimum": -128, "maximum": 127,
       "allOf": [{"exclusiveMinimum": Decimal("-1.2")}, {"exclusiveMaximum": Decimal("2.00")}]},
      [-1, 0, 1, 1.0], [-2, 2, 0.5, -129, 128, "1", None])
# These schemas are legal but no supplied value can satisfy their numeric intersections.
# A future generator must diagnose them rather than silently publish an unusable member.
for schema in [
        {"type": "number", "minimum": Decimal("1.00"), "exclusiveMaximum": Decimal("1E+0")},
        {"type": "integer", "minimum": Decimal("0.1"), "maximum": Decimal("0.9")},
        {"type": "integer", "maximum": 127, "exclusiveMinimum": Decimal("127.0")},
        {"type": "integer", "minimum": 2, "maximum": Decimal("1.9")}]:
    check(schema, [], [-129, -1, 0, 0.5, 1, 1.5, 2, 127, 128])
check({"type": "array", "items": {"type": "string"}, "minItems": 2, "maxItems": 3},
      [["same", "same"], ["a", "b", "c"]], [[], ["a"], ["a"] * 4, None, "aa", [1, 2]])
check({"type": "object", "additionalProperties": {"type": "string"}, "minProperties": 2, "maxProperties": 3},
      [{"a": "same", "b": "same"}, {"a": "", "b": "", "c": ""}],
      [{}, {"a": ""}, {"a": "", "b": "", "c": "", "d": ""}, None, []])
check({"type": "string", "minLength": 1, "maxLength": 1}, ["a", "😀"], ["", "e\u0301", None])
# Deprecation is annotation only, local to the old relationship reference.
child = {"type": "object", "properties": {"value": {"type": "string"}}, "additionalProperties": False}
schema = {"$defs": {"Child": child}, "type": "object", "additionalProperties": False,
          "properties": {"old-child": {"$ref": "#/$defs/Child", "deprecated": True},
                         "child": {"$ref": "#/$defs/Child"},
                         "old-operation": {"type": "string", "deprecated": True}}}
check(schema, [{}, {"old-child": {}}, {"child": {}}, {"old-child": {}, "child": {}, "old-operation": ""}],
      [{"old-child": None}, {"hidden": "x"}, {"old-operation": 1}])
assert "deprecated" not in child
assert "deprecated" not in schema["properties"]["child"]
# Ordinary optional members remain optional, explicit null remains structurally invalid.
check({"type": "object", "properties": {"amount": exact}}, [{}, {"amount": Decimal("9007199254740993.00000000015")}],
      [{"amount": None}, {"amount": "1"}])
print(f"P2 hand-authored Draft 2020-12 corpus: {checks} checks passed; jsonschema=4.23.0")
