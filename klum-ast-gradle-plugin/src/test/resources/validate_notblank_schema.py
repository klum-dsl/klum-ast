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

"""Issue #1074: externally validate the pragmatic NotBlank Draft 2020-12 projection."""
import importlib.metadata
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])
outputs = []
checks = 0

for generation in ("3", "4", "5"):
    path = root / f"notblank-{generation}" / "notblank.schema.json"
    outputs.append(path.read_bytes())
    schema = json.loads(path.read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)

    def check(value, valid):
        global checks
        actual = validator.is_valid(value)
        assert actual == valid, (path, value, actual, valid, list(validator.iter_errors(value)))
        checks += 1

    base = {"required-name": "release"}
    check(base, True)
    for field in ("topic-name", "display-name"):
        for value in ("", " ", "\t", "\n", " \t\n"):
            check({**base, field: value}, False)
        for value in ("release", " release ", "\trelease\n"):
            check({**base, field: value}, True)
    check({**base, "topic-name": None}, False)
    check({**base, "topic-name": 1}, False)
    check({}, False)
    check({"required-name": ""}, False)
    for field in ("text-only-name", "omitted-name", "grouped-name", "payload-name", "repeated-blank"):
        check({**base, field: " \t\n"}, True)

assert outputs[0] == outputs[1] == outputs[2], "Groovy producer schemas differ"
print(f"PASS: {checks} input controls, 3 Draft 2020-12 metaschemas, byte-identical G3/G4/G5; "
      f"jsonschema {importlib.metadata.version('jsonschema')}")
