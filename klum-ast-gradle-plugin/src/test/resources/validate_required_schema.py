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

"""Issue #1055: externally validate raw presence using jsonschema==4.23.0 (Draft 2020-12)."""
import importlib.metadata
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version('jsonschema') == '4.23.0'
root = Path(sys.argv[1])
body = {"Exact/Name": "", "a-label": "", "count": 0, "display-name": "",
        "enabled": False, "omitted": "", "text-only": ""}
member = {**body, "z-id": ""}
child = {"child-id": "worker", "child-code": ""}
owner = {"mandatory": child, "sequence": [], "children": {}}

documents = {
    "Presence": ([member], [body, {}, *[{key: value for key, value in member.items() if key != missing}
                                      for missing in body], {**member, "a-label": None},
                            {**member, "enabled": None}, {**member, "count": None},
                            {**member, "count": -1}, {**member, "displayName": "native"}]),
    "Presence.builder-body": ([body], [{}, member, {**body, "a-label": None}]),
    "PresenceOwner": (
        [owner, {**owner, "optional": child, "second": child},
         {**owner, "sequence": [child], "children": {"outer/data-key": {"child-code": ""}},
          "fixed": {"child-code": ""}, "derived": {"child-code": ""}}],
        [{}, *[{key: value for key, value in owner.items() if key != missing} for missing in owner],
         {**owner, "optional": {}}, {**owner, "optional": {"child-id": "worker"}},
         {**owner, "sequence": [{"child-code": ""}]}, {**owner, "children": {"outer": {}}},
         {**owner, "children": {"outer": child}}, {**owner, "mandatory": None}]),
    "PresenceOptional": ([{}, {"optional": child}], [{"optional": {}}, {"optional": None}]),
}
for document, (accepted, rejected) in documents.items():
    paths = [root / f"presence-{form}-{generation}" / f"{document}.schema.json"
             for form in ("direct", "domain") for generation in ("3", "4", "5")]
    assert all(path.is_file() for path in paths), f"Run direct/domain presence fixtures for Groovy 3/4/5: {document}"
    for path in paths:
        schema = json.loads(path.read_text())
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema)
        for value in accepted:
            assert validator.is_valid(value), (path, value, list(validator.iter_errors(value)))
        for value in rejected:
            assert not validator.is_valid(value), (path, value)
        if document == "Presence":
            errors = list(validator.iter_errors({key: value for key, value in member.items() if key != "a-label"}))
            assert len(errors) == 1 and errors[0].validator == "required", errors
            errors = list(validator.iter_errors({**member, "a-label": None}))
            assert len(errors) == 1 and errors[0].validator == "type", errors
        print(f"{path.parent.name}/{document}: metaschema, {len(accepted)} accepted / {len(rejected)} rejected")
    assert len({path.read_bytes() for path in paths}) == 1, "Direct/domain markers and Groovy generations must emit identical JSON"

# Presence alone accepts null; the unchanged property assertion owns null admission.
presence_only = Draft202012Validator({"type": "object", "required": ["value"]})
assert not presence_only.is_valid({})
for present in [None, "", False, 0, [], {}]:
    assert presence_only.is_valid({"value": present}), present

# Documentary counterpart: the Spock feature demonstrates import/DSL/applyStruct acceptance
# using the same two fields and exports the generated schema, without adding a validator to import.
for generation in ('3', '4', '5'):
    path = root / f'documentary-presence-{generation}' / 'AuthoringService.schema.json'
    schema = json.loads(path.read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    errors = list(validator.iter_errors({}))
    assert len(errors) == 2 and all(error.validator == 'required' for error in errors), errors
    assert validator.is_valid({'service-name': '', 'public-access': False})
    for missing in ('service-name', 'public-access'):
        value = {'service-name': '', 'public-access': False}
        del value[missing]
        errors = list(validator.iter_errors(value))
        assert len(errors) == 1 and errors[0].validator == 'required', errors
    errors = list(validator.iter_errors({'service-name': None, 'public-access': False}))
    assert len(errors) == 1 and errors[0].validator == 'type', errors
    print(f'{path.parent.name}: documentary omission rejected externally; present empty/false accepted')
