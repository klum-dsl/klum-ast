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

# Disposable evidence validator for issues #529 / #932; not a build dependency.
import importlib.metadata
import json
from pathlib import Path
from jsonschema import Draft202012Validator

assert importlib.metadata.version('jsonschema') == '4.23.0'
root = Path('klum-ast-jackson/build/test-classes')
checked = []
for path in sorted(root.glob('*/StructuredInputSchemaProbeTest/*/schema-evidence.json')):
    evidence = json.loads(path.read_text())
    schema = evidence['schema']
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    valid = [
        {},
        {'enabled': False, 'port': 0, 'labels': {}, 'items': [], 'sequence': []},
        {'port': -1},  # runtime closure validation deliberately is not projected
        {'requiredValue': ''},  # raw shape says string, not lifecycle validation
        {'service-name': 'x', 'class': 'group', 'region': 'eu', 'mode': 'SAFE',
         'child': {'value': 'nested'}, 'entries': {'one': {'value': 'keyed'}},
         'items': [{'value': 'listed'}], 'labels': {'team': 'core'},
         'mapping': {'team': 'core'}, 'sequence': ['a','b'],
         'inheritedLocale': 'eu', 'endpoint': ['api',9090], 'shout': 'hello'},
        {'externalChild': {'apiValue':'separate-artifact'}},
    ]
    invalid = [
        {'unknown': 1}, {'internalSetting': 'x'}, {'serviceName':'x'},
        {'service-name':None}, {'port':None}, {'port':'8080'}, {'enabled':'false'},
        {'mode':'UNKNOWN'}, {'labels':{'team':5}}, {'child':{'missing':'x'}},
        {'items':{}}, {'entries':[]}, {'unsupportedAbstract':{}},
        {'endpoint':{'host':'api','port':9090}}, {'endpoint':['api']},
        {'endpoint':['api',9090,True]}, {'sequence':['a',5]}, {'mapping':[]},
        {'externalChild':{'value':'wrong-simple-name'}}, {'primary':{'value':'missing-key-protocol'}},
        {'copyFrom':{}}, {'apply':{}}, {'created':None},
    ]
    for instance in valid:
        assert validator.is_valid(instance), (path, instance, list(validator.iter_errors(instance)))
    for instance in invalid:
        assert not validator.is_valid(instance), (path, instance)
    checked.append({'lane':path.parts[-4], 'schema':str(path), 'valid':len(valid), 'invalid':len(invalid)})
assert checked
print(json.dumps({'validator':'jsonschema==4.23.0', 'draft':'2020-12', 'checked':checked}, indent=2))
