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

"""Issue #959 D7 design oracle: hand-authored candidates, never production output.

Run with jsonschema==4.23.0. This deliberately imports no Klum or Jackson code.
It checks proposed names and candidate schemas; it does not interpret input.
"""
import copy
import importlib.metadata
import json
from decimal import Decimal
from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
checks = 0


def check(condition, detail):
    global checks
    assert condition, detail
    checks += 1


def lower_ascii(c):
    return "a" <= c <= "z"


def upper_ascii(c):
    return "A" <= c <= "Z"


def digit_ascii(c):
    return "0" <= c <= "9"


def proposed_name(native, profile):
    if any(0xD800 <= ord(c) <= 0xDFFF for c in native):
        raise ValueError("unpaired surrogate in declaration")
    if profile == "identity-v1":
        return native
    if profile not in ("kebab-v1", "snake-v1"):
        raise ValueError("unknown profile: " + profile)
    separator = "-" if profile == "kebab-v1" else "_"
    result = []
    for i, c in enumerate(native):
        previous = native[i - 1] if i else ""
        following = native[i + 1] if i + 1 < len(native) else ""
        if i and upper_ascii(c) and (
                lower_ascii(previous) or digit_ascii(previous)
                or (upper_ascii(previous) and lower_ascii(following))):
            result.append(separator)
        result.append(chr(ord(c) + 32) if upper_ascii(c) else c)
    return "".join(result)


# Explicit expected strings, including intentionally unnormalized spelling.
vectors = [
    ("serviceName", "service-name", "service_name"),
    ("URLValue", "url-value", "url_value"),
    ("myURLValue", "my-url-value", "my_url_value"),
    ("URL", "url", "url"),
    ("XML2Parser", "xml2-parser", "xml2_parser"),
    ("URL42Value", "url42-value", "url42_value"),
    ("version2URL", "version2-url", "version2_url"),
    ("v2beta3", "v2beta3", "v2beta3"),
    ("_privateURL", "_private-url", "_private_url"),
    ("already_snake", "already_snake", "already_snake"),
    ("already-kebab", "already-kebab", "already-kebab"),
    ("foo__BAR", "foo__bar", "foo__bar"),
    ("foo/URL~Value", "foo/url~value", "foo/url~value"),
    ("naïveURL", "naïve-url", "naïve_url"),
    ("ÅngströmURL", "Ångström-url", "Ångström_url"),
    ("İDValue", "İd-value", "İd_value"),
    ("IValue", "i-value", "i_value"),
    ("e\u0301URL", "e\u0301url", "e\u0301url"),
    ("éURL", "éurl", "éurl"),
    ("𐐀Value", "𐐀value", "𐐀value"),
    ("$HTTPServer", "$http-server", "$http_server"),
]
for native, kebab, snake in vectors:
    for profile, expected in zip(("identity-v1", "kebab-v1", "snake-v1"), (native, kebab, snake)):
        check(proposed_name(native, profile) == expected, (native, profile, expected))


def resolve(declarations, profile):
    """A finite candidate name index only; never operation discovery or execution."""
    result = {}
    for source, native, rename, ignored in declarations:
        if ignored:
            continue
        external = rename if rename else proposed_name(native, profile)
        if external in result:
            raise ValueError("collision: " + external + ": " + ", ".join(sorted((result[external], source))))
        result[external] = source
    return result


for profile in ("identity-v1", "kebab-v1", "snake-v1"):
    names = resolve([("Base#serviceName", "serviceName", "", False),
                     ("Root#hidden", "hidden", "visible", True),
                     ("Root#literal", "literal", "Keep/THIS~Name", False),
                     ("Root#empty", "URLValue", "", False)], profile)
    check(names[proposed_name("serviceName", profile)] == "Base#serviceName", profile)
    check(names["Keep/THIS~Name"] == "Root#literal", profile)
    check("visible" not in names, profile)
    check(names[proposed_name("URLValue", profile)] == "Root#empty", profile)
    for declarations in (
        [("Base#a", "URLValue", "", False), ("Root#b", "urlValue", "", False)],
        [("Root#key", "rootKey", "", False),
         ("Root#operation", "configure", proposed_name("rootKey", profile), False)],
    ):
        collision = profile != "identity-v1" or declarations[0][0] == "Root#key"
        try:
            resolve(declarations, profile)
            check(not collision, (profile, declarations))
        except ValueError as failure:
            check(collision and "collision:" in str(failure), (profile, declarations))

    key = proposed_name("rootKey", profile)
    member = proposed_name("serviceName", profile)
    operation = proposed_name("configureHTTP", profile)
    relationship = proposed_name("childService", profile)
    child_member = proposed_name("retryCount", profile)
    # Candidate-only placement: same child at two uses, no parent metadata in $defs.
    schema = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "x-klum-input-naming": {"profile": profile, "scope": "root-graph"},
        "type": "object", "required": [key], "additionalProperties": False,
        "properties": {
            key: {"type": "string"},
            member: {"type": "string", "deprecated": True},
            operation: {"type": "object", "additionalProperties": {"type": "string"}},
            "Keep/THIS~Name": {"type": "string"},
            relationship: {"$ref": "#/$defs/Child", "deprecated": True},
            "other-child": {"$ref": "#/$defs/Child"},
            "labels": {"type": "object", "additionalProperties": {"type": "string"}},
        },
        "$defs": {"Child": {"type": "object", "additionalProperties": False,
                             "properties": {child_member: {"type": "integer", "minimum": 0, "maximum": 100}}}},
    }
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    check("deprecated" not in schema["$defs"]["Child"], profile)
    good = {key: "Prod-ID", member: "api", operation: {"nativeMapKey": "unchanged"},
            relationship: {child_member: 100}, "labels": {"URL_Instance": "same"},
            "Keep/THIS~Name": "literal"}
    values = [
        (True, good), (True, {key: ""}),
        (False, {key: "id", relationship: {child_member: 101}}),
        (False, {key: "id", relationship: {child_member: None}}),
        (False, {key: "id", "hidden": "no"}), (False, {key: 1}), (False, {}),
    ]
    if profile != "identity-v1":
        values.append((False, {key: "id", "serviceName": "no alias"}))
    for expected, value in values:
        check(validator.is_valid(value) == expected, (profile, value, expected))
    stripped = copy.deepcopy(schema)
    stripped.pop("x-klum-input-naming")
    check(all(Draft202012Validator(stripped).is_valid(value) == validator.is_valid(value)
              for _, value in values), profile)

try:
    proposed_name("value", "unknown")
    raise AssertionError("unknown profile accepted")
except ValueError:
    check(True, "unknown profile rejected")
for bad in ("\\ud800", "\\udfff"):
    malformed = chr(int(bad[2:], 16))
    try:
        proposed_name(malformed, "kebab-v1")
        raise AssertionError("surrogate accepted")
    except ValueError:
        check(True, "surrogate rejected")

# Examples are annotations only; whole-array and item targets remain distinct.
examples_schema = {
    "type": "object", "additionalProperties": False,
    "properties": {
        "words": {"type": "array", "items": {"type": "string", "examples": ["one"]},
                  "examples": [["one", "two"]]},
        "old-child": {"$ref": "#/$defs/Child", "examples": [{"retry-count": 3}]},
        "new-child": {"$ref": "#/$defs/Child"},
    },
    "$defs": {"Child": {"type": "object", "additionalProperties": False,
                         "properties": {"retry-count": {"type": "integer"}}}},
}
Draft202012Validator.check_schema(examples_schema)
check(examples_schema["properties"]["words"]["examples"] == [["one", "two"]], "whole array")
check(examples_schema["properties"]["words"]["items"]["examples"] == ["one"], "array item")
check("examples" not in examples_schema["$defs"]["Child"], "no relationship leakage")
check("examples" not in examples_schema["properties"]["new-child"], "independent sibling")


def without_examples(value):
    if isinstance(value, dict):
        return {key: without_examples(item) for key, item in value.items() if key != "examples"}
    if isinstance(value, list):
        return [without_examples(item) for item in value]
    return value


with_examples = Draft202012Validator(examples_schema)
stripped_examples = Draft202012Validator(without_examples(examples_schema))
for value in ({}, {"words": []}, {"words": ["three"]}, {"words": [1]},
              {"old-child": {"retry-count": 3}}, {"old-child": {"retryCount": 3}}, {"extra": 1}):
    check(with_examples.is_valid(value) == stripped_examples.is_valid(value), value)
# Neither validation nor advisory examples populates a missing value.
missing = {}
check(with_examples.is_valid(missing) and missing == {}, "examples do not populate")


def reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate example member")
        result[key] = value
    return result


def reject_constant(value):
    raise ValueError("non-JSON constant: " + value)


for literal in ('null', 'false', '"catalog"', '["one","two"]', '{"rawKey":"catalog"}',
                '9007199254740993.0000000001'):
    example = json.loads(literal, parse_float=Decimal, object_pairs_hook=reject_duplicate_keys,
                         parse_constant=reject_constant)
    check(example is not None or literal == 'null', literal)
exact = json.loads('9007199254740993.0000000001', parse_float=Decimal)
check(exact == Decimal('9007199254740993.0000000001'), "exact example number")
for literal in ('{bad}', '{"x":1,"x":2}', 'NaN', 'Infinity', '1 2'):
    try:
        json.loads(literal, parse_float=Decimal, object_pairs_hook=reject_duplicate_keys,
                   parse_constant=reject_constant)
        raise AssertionError("unsafe example accepted: " + literal)
    except ValueError:
        check(True, literal)
print(f"D7 candidate-only checks: {checks}; no production generator/interpreter acceptance claimed")
