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

# Explicit test evidence; not a production or Gradle parser dependency.
import importlib.metadata
import json
from decimal import Decimal
from pathlib import Path
from jsonschema import Draft202012Validator, validators

assert importlib.metadata.version('jsonschema') == '4.23.0'

# JSON integers are mathematical integers, including exact Decimal spellings such as 1.0.
# Keep large integers and decimals exact instead of converting through binary double.
def integer(checker, value):
    return ((isinstance(value, int) and not isinstance(value, bool))
            or (isinstance(value, Decimal) and value.is_finite() and value == value.to_integral_value()))

ExactValidator = validators.extend(Draft202012Validator,
                                 type_checker=Draft202012Validator.TYPE_CHECKER.redefine('integer', integer))
paths = sorted(Path('klum-ast-json-schema/build/test-classes').glob('*/JsonSchemaGeneratorTest/*/*schema.json'))
assert paths, 'Run JsonSchemaGeneratorTest first'
lanes = {}
documents_by_feature = {}
for path in paths:
    text = path.read_text()
    feature = path.parent.name
    previous = documents_by_feature.setdefault(feature, text)
    assert previous == text, ('Cross-Groovy output drift', path)
    schema = json.loads(text)
    Draft202012Validator.check_schema(schema)
    for definition in schema['$defs'].values():
        assert definition['additionalProperties'] is False
        assert 'required' not in definition and 'default' not in definition
    lane = path.parts[-4]
    counts = lanes.setdefault(lane, {'documents': 0, 'accepted': 0, 'rejected': 0})
    counts['documents'] += 1
    instances = path.with_name('instances.json')
    if instances.exists():
        cases = json.loads(instances.read_text(), parse_float=Decimal)
        validator = ExactValidator(schema)
        for case in cases:
            accepted = validator.is_valid(case['input'])
            assert accepted == case['accepted'], (path, case, list(validator.iter_errors(case['input'])))
            if 'runtimeAccepted' in case:
                assert accepted == case['runtimeAccepted'], (path, case)
            counts['accepted' if accepted else 'rejected'] += 1
    if 'binary_only_current' in path.parent.name:
        root = schema['$defs']['schematracer.schema.Application_DSL$Builder']
        assert len(schema['$defs']) == 2
        assert root['properties']['primary'] == root['properties']['components']['additionalProperties']
        assert ExactValidator(schema).is_valid({'port': -1})  # completed validation remains separate
        assert ExactValidator(schema).is_valid({'endpoint': {'port': 'not-an-integer'}})
        assert ExactValidator(schema).is_valid({'port': Decimal('1.0')})
for lane, counts in lanes.items():
    assert counts['accepted'] >= 8 and counts['rejected'] >= 24, (lane, counts)
    print(json.dumps({'lane': lane, 'validator': 'jsonschema==4.23.0', 'draft': '2020-12', **counts}))
