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

# SCHEMA-T evidence only: issues #529/#932. No production or Gradle dependency.
import importlib.metadata
import json
from pathlib import Path
from jsonschema import Draft202012Validator

assert importlib.metadata.version('jsonschema') == '4.23.0'
paths = sorted(Path('klum-ast/build/test-classes').glob('*/SchemaTracerTest/*/schema.json'))
assert paths, 'Run SchemaTracerTest first'
for path in paths:
    schema = json.loads(path.read_text())
    facts = json.loads(path.with_name('facts.json').read_text())
    cases = json.loads(path.with_name('instances.json').read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    root = 'schematracer.schema.Application_DSL$Builder'
    child = 'schematracer.schema.Component_DSL$Builder'
    reference = {'$ref': '#/$defs/schematracer.schema.Component_DSL%24Builder'}
    assert schema['$ref'] == '#/$defs/schematracer.schema.Application_DSL%24Builder'
    assert set(schema['$defs']) == {root, child}
    properties = schema['$defs'][root]['properties']
    assert set(properties) == {'region', 'service-name', 'port', 'enabled', 'ratio', 'mode',
                               'primary', 'components', 'labels', 'supplied', 'computed', 'endpoint'}
    assert properties['primary'] == reference
    assert properties['components'] == {'type': 'object', 'additionalProperties': reference}
    assert properties['endpoint'] == {'type': 'object', 'additionalProperties': {'type': 'string'}}
    assert properties['mode'] == {'type': 'string', 'enum': ['SAFE', 'FAST']}
    assert properties['ratio'] == {'type': 'number'}
    assert set(schema['$defs'][child]['properties']) == {'value'}
    for definition in schema['$defs'].values():
        assert definition['additionalProperties'] is False
        assert 'required' not in definition and 'default' not in definition
    assert any(fact['source'] == 'schematracer.api.ApplicationApi#region'
               and fact['declaringBuilder'] == 'schematracer.api.ApplicationApi_DSL$Builder'
               for fact in facts['coverage'])
    assert any(fact['source'] == 'schematracer.schema.Application#secret' for fact in facts['rejections'])
    for sample in cases:
        accepted = validator.is_valid(sample['input'])
        assert accepted == sample['accepted'] == sample['runtimeAccepted'], (
            path, sample, list(validator.iter_errors(sample['input'])))
    # Fidelity controls beyond C's overlap: structural success does not promise execution success.
    assert validator.is_valid({'port': -1})  # completed validation is deliberately not projected
    assert validator.is_valid({'endpoint': {'host': 'api', 'port': 'not-an-integer'}})
    assert validator.is_valid({'port': 1.0})  # mathematical JSON integer, not lexical spelling
    assert not validator.is_valid({'port': 2147483648})
    print(json.dumps({'lane': path.parts[-4], 'validator': 'jsonschema==4.23.0',
                      'draft': '2020-12', 'metaschema': 'passed', 'definitions': len(schema['$defs']),
                      'accepted': sum(case['accepted'] for case in cases),
                      'rejected': sum(not case['accepted'] for case in cases)}))
