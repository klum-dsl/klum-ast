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

"""Issue #959 D2: hand-authored candidate assertions, NOT generated KlumAST output.

Run with Python and jsonschema==4.23.0. This deliberately does not discover
annotations, select mappings, invoke production generation, or execute Schema code.
"""
from copy import deepcopy
from importlib.metadata import version

from jsonschema import Draft202012Validator

assert version("jsonschema") == "4.23.0"

# Current structural integer shape, transcribed from JsonSchemaProjection.scalar.
structural = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "percentage": {"type": "integer", "minimum": -(2**31), "maximum": 2**31 - 1},
        "label": {"type": "string"},
        "enabled": {"type": "boolean"},
    },
}
candidate = deepcopy(structural)
candidate["properties"]["percentage"]["allOf"] = [{"minimum": 0}, {"maximum": 100}]
cases = [
    ({}, True, True),
    ({"percentage": 0}, True, True),
    ({"percentage": 100}, True, True),
    ({"percentage": 100.0}, True, True),
    ({"percentage": -1}, True, False),
    ({"percentage": 101}, True, False),
    ({"percentage": None}, False, False),
    ({"percentage": False}, False, False),
    ({"percentage": ""}, False, False),
    ({"percentage": "50"}, False, False),
    ({"percentage": 0.5}, False, False),
    ({"percentage": 2**31}, False, False),
    ({"percentage": -(2**31) - 1}, False, False),
    ({"label": "", "enabled": False}, True, True),
    ({"label": None}, False, False),
    ({"enabled": None}, False, False),
]
checks = 0
for schema, expected_index in [(structural, 1), (candidate, 2)]:
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    for case in cases:
        assert validator.is_valid(case[0]) == case[expected_index], case
        checks += 1

# Placement examples are written by hand: inheritance/metadata extraction is not tested here.
renamed = deepcopy(candidate)
renamed["properties"]["percent"] = renamed["properties"].pop("percentage")
nested = {"type": "object", "additionalProperties": False,
          "properties": {"child": {"$ref": "#/$defs/Capacity"}}, "$defs": {"Capacity": candidate}}
for schema, accepted, rejected in [
    (renamed, [{"percent": 0}, {"percent": 100}],
     [{"percent": 101}, {"percentage": 50}, {"hidden": 50}]),
    (nested, [{}, {"child": {}}, {"child": {"percentage": 100}}],
     [{"child": {"percentage": 101}}, {"child": None}]),
]:
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    for value in accepted:
        assert validator.is_valid(value), value
        checks += 1
    for value in rejected:
        assert not validator.is_valid(value), value
        checks += 1
print(f"D2 hand-authored schemas: {checks} assertions passed; no production projection claimed")
