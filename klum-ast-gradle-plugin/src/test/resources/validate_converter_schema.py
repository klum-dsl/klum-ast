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

"""Issue #981 C1: independent JDK, ECMA plain/Unicode and Draft 2020-12 checks of actual output."""
import importlib.metadata
import json
import subprocess
import sys
import tempfile
from pathlib import Path

from jsonschema import Draft202012Validator

assert importlib.metadata.version("jsonschema") == "4.23.0"
root = Path(sys.argv[1])
java = sys.argv[2] if len(sys.argv) > 2 else "java"
# Hand-authored input/acceptance corpus, independent of the production recognizer.
rows = [("0", True), ("1234", True), ("00000000", True), ("99999999", True),
        ("", False), ("123456789", False), ("1" * 1100, False), ("-1", False),
        ("+1", False), ("1.0", False), (" 1", False), ("1 ", False),
        ("1\n", False), ("1\r", False), ("1\r\n", False), ("1\u0085", False),
        ("1\u2028", False), ("1\u2029", False), ("\u0661", False), ("\uff11", False),
        ("\U0001f600", False), ("\ud800", False), ("\udfff", False), ("1\x00", False),
        ("not digits", False), ("1Gi", False), ("\\1", False), ('"1"', False)]
java_source = r'''
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
class ConverterPatternOracle {
    public static void main(String[] args) throws Exception {
        Pattern pattern = Pattern.compile("[0-9]{1,8}");
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = input.readLine()) != null) {
            char[] text = new char[line.length() / 4];
            for (int i = 0; i < text.length; i++) text[i] = (char) Integer.parseInt(line.substring(4 * i, 4 * i + 4), 16);
            System.out.println(pattern.matcher(new String(text)).matches());
        }
    }
}
'''
with tempfile.TemporaryDirectory(prefix="converter-oracle-") as temporary:
    source = Path(temporary) / "ConverterPatternOracle.java"
    source.write_text(java_source)
    inputs = "".join(value.encode("utf-16-be", errors="surrogatepass").hex() + "\n" for value, _ in rows)
    observed = subprocess.run([java, str(source)], input=inputs, text=True, capture_output=True, check=True)
    assert observed.stdout.splitlines() == [str(expected).lower() for _, expected in rows]

node_source = r'''
const fs = require('node:fs');
const assert = require('node:assert/strict');
const input = JSON.parse(fs.readFileSync(0, 'utf8'));
for (const flags of ['', 'u']) {
    for (const [text, expected] of input.rows) {
        const accepted = input.field.type === 'string' && input.field.allOf.every(rule =>
            rule.pattern ? new RegExp(rule.pattern, flags).test(text)
                : !new RegExp(rule.not.pattern, flags).test(text));
        assert.equal(accepted, expected, JSON.stringify({text, flags}));
    }
}
console.log(process.version);
'''
count = 0
structural_count = 0
for lane in ("3", "4", "5"):
    schema = json.loads((root / ("converter-" + lane) / "converter.schema.json").read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    field = schema["$defs"]["converterjson.Service_DSL$Builder"]["properties"]["port-number"]
    for text, expected in rows:
        assert validator.is_valid({"port-number": text}) == expected, (lane, repr(text))
    for value in (None, 1234, True, [], {}):
        assert not validator.is_valid({"port-number": value}), (lane, value)
    assert validator.is_valid({})
    # #952 S3a/S3b: all new origins share structural String grammar, without C1 leakage.
    # A syntactically invalid duration remains a valid schema String; conversion owns that failure.
    properties = schema["$defs"]["converterjson.Service_DSL$Builder"]["properties"]
    for name in ("duration", "natural-duration", "sequence", "local", "adapted", "overloaded", "path", "generic", "subtype"):
        assert "allOf" not in properties[name]
        assert "x-klum-converter-constraint-source" not in properties[name]
        for value in ("PT30S", "bad", "", "1234", "unicode-✓"):
            assert validator.is_valid({name: value}), (lane, name, value)
            structural_count += 1
        for value in (None, 30, True, [], {}):
            assert not validator.is_valid({name: value}), (lane, name, value)
            structural_count += 1
    node = subprocess.run(["node", "-e", node_source], input=json.dumps({"rows": rows, "field": field}),
                          text=True, capture_output=True, check=True)
    count += len(rows) * 3 + 6
print(f"C1 actual-output oracle: {len(rows)} JDK checks and {count} ECMA/Draft checks passed across G3/G4/G5; node={node.stdout.strip()}")

print(f"S3a/S3b actual-output oracle: {structural_count} structural String checks passed across converter shapes and G3/G4/G5")
