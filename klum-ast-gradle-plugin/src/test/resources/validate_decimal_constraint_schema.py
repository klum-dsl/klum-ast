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

"""Issue #959: precision-capable validation of emitted direct decimal schemas."""
import importlib.metadata
import json
import sys
from decimal import Decimal
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
paths = sorted(Path(sys.argv[1]).glob("decimal-*/price.schema.json"))
assert len(paths) == 3, "Run the decimal documentary test with all three producer generations"
accepted = [{}, {"amount": Decimal("0.10000000000000000001")},
            {"amount": Decimal("9007199254740993.0000000001")},
            {"amount": Decimal("9007199254740993.000000000099")},
            {"equal": Decimal("1.000")}, {"zero": Decimal("-0.00")},
            {"exponent": Decimal("2.50E+3")}, {"exponent": Decimal("3E+3")},
            {"tiny": Decimal("1E-1000")}, {"units": 2}, {"units": 3},
            {"machine": 0}, {"machine": 127}, {"tight": 2}, {"tie": 3},
            {"inherited-price": Decimal("-122.99999999999999999999")},
            {"child": {}}, {"child": {"amount": Decimal("0.0")}},
            {"text": "-1"}, {"floating": -1}, {"doubleValue": -1},
            {"grouped": -1}, {"payloadBound": -1}, {"repeated": -1},
            {"composed": -1}, {"operation": -1}]
rejected = [{"amount": Decimal("0.10")}, {"amount": Decimal("0.09999999999999999999")},
            {"amount": Decimal("9007199254740993.0000000002")},
            {"equal": Decimal("1.00000000000000000001")},
            {"equal": Decimal("0.99999999999999999999")},
            {"zero": Decimal("1E-1000")}, {"zero": Decimal("-1E-1000")},
            {"exponent": Decimal("2499.99999999999999999999")},
            {"exponent": Decimal("3000.00000000000000000001")},
            {"tiny": 0}, {"units": 1}, {"units": 4}, {"units": Decimal("2.1")},
            {"machine": -1}, {"machine": 128}, {"tight": 1}, {"tie": 2},
            {"inherited-price": -123}, {"inherited": 0},
            {"child": {"amount": Decimal("-1E-1000")}}]
for field in ["amount", "equal", "zero", "exponent", "tiny", "units", "machine", "tight", "tie", "inherited-price"]:
    rejected.extend({field: value} for value in [None, True, "1", ""])
for field in ["boundedByte", "boundedShort", "boundedPrimitiveShort", "boundedInt", "boundedPrimitiveInt",
              "boundedLong", "boundedPrimitiveLong", "boundedBigInteger"]:
    accepted.extend({field: value} for value in [0, 1])
    rejected.extend({field: value} for value in [-1, 2, Decimal("0.1"), None, "1"])
for path in paths:
    # Decimal parsing retains the emitted tokens' exact value; no float parser participates.
    schema = json.loads(path.read_text(), parse_float=Decimal)
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    for value in accepted:
        assert validator.is_valid(value), (path, value, list(validator.iter_errors(value)))
    for value in rejected:
        assert not validator.is_valid(value), (path, value)
    print(f"{path.parent.name}: metaschema, {len(accepted)} accepted / {len(rejected)} rejected")
assert len({path.read_bytes() for path in paths}) == 1, "Decimal output must match across Groovy generations"

# Contradictions are valid projections in integral-only, decimal-only and combined modes.
contradictions = sorted(Path(sys.argv[1]).glob('contradictions-*/*.schema.json'))
assert len(contradictions) == 22, 'Run all three contradictory-declaration mode cases'
for path in contradictions:
    schema = json.loads(path.read_text(), parse_float=Decimal)
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    assert validator.is_valid({}), path  # Bounds never infer requiredness.
    field = 'percent' if path.stem.startswith('Impossible') else 'amount'
    if path.name == 'ExtremeBounds.schema.json':
        for field, values in [('huge', [0, Decimal('1E+1000000'), Decimal('-1E+1000000')]),
                              ('tiny', [0, Decimal('1E-2147483647'), Decimal('-1E-2147483647')])]:
            for value in values:
                assert not validator.is_valid({field: value}), (path, field, value)
        assert validator.is_valid({'ordinary': 0}), path
    else:
        for value in [-1000, -1, 0, Decimal('0.1'), Decimal('0.5'), Decimal('0.9'),
                      1, Decimal('1.00'), 2, 50, 100, 127, 128, 200, 1000, None, '1', True]:
            assert not validator.is_valid({field: value}), (path, value)
print(f'contradictions: {len(contradictions)} valid schemas, omission accepted, supplied-value corpus rejected')
