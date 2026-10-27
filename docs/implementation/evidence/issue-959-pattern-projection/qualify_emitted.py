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

"""Issue #959: qualify emitted schema against independent hand-authored Java cases.

Requires Python jsonschema==4.23.0, JDK 17 and Node v26.12.0. Writes helpers only
inside a temporary directory. Java is the full-match oracle; Node evaluates
ECMA expressions with and without u; jsonschema validates actual Draft 2020-12
fragments. This is an experiment, outside Gradle's production/test inputs.
"""
import argparse
import importlib.metadata
import itertools
import json
from pathlib import Path
import platform
import subprocess
import tempfile

from jsonschema import Draft202012Validator

# Each source/body/example is explicit: there is deliberately no regex parser.
ACCEPTED = [
    ('[A-Z]{2}[0-9]{4}', '[A-Z]{2}[0-9]{4}', 'AB1234'),
    ('^[A-Z]{2}[0-9]{4}$', '[A-Z]{2}[0-9]{4}', 'AB1234'),
    ('[a-z][a-z0-9-]{0,62}', '[a-z][a-z0-9-]{0,62}', 'release-1'),
    ('a?', 'a?', 'a'),
    ('a{0}', 'a{0}', ''),
    ('[0-9]{0,3}', '[0-9]{0,3}', '123'),
    ('[A-Za-z0-9_.-]{1,8}', '[A-Za-z0-9_.-]{1,8}', 'A_1.z-'),
    ('release-[0-9]{1,3}', 'release-[0-9]{1,3}', 'release-123'),
    ('[a]{2}[a]{0,2}', '[a]{2}[a]{0,2}', 'aaaa'),
    ('literal_1', 'literal_1', 'literal_1'),
    ('[0-9]{255}', '[0-9]{255}', '0' * 255),
    ('a{255}b{255}c{255}d{255}', 'a{255}b{255}c{255}d{255}',
     'a' * 255 + 'b' * 255 + 'c' * 255 + 'd' * 255),
    ('[.]', '[.]', '.'),
    ('[-]', '[-]', '-'),
]

JAVA = r'''
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// Issue #959 laboratory oracle, not a generated Schema or a production test seam.
class PatternOracle {
    static String decode(String hex) {
        char[] units = new char[hex.length() / 4];
        for (int i = 0; i < units.length; i++) {
            units[i] = (char) Integer.parseInt(hex.substring(i * 4, i * 4 + 4), 16);
        }
        return new String(units);
    }
    public static void main(String[] args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String row;
        while ((row = input.readLine()) != null) {
            String[] cells = row.split("\\t", -1);
            try {
                System.out.println(Pattern.compile(decode(cells[0]), Integer.parseInt(cells[2]))
                        .matcher(decode(cells[1])).matches());
            } catch (PatternSyntaxException failure) {
                System.out.println("error");
            }
        }
    }
}
'''

NODE = r'''
const fs = require('node:fs');
if (process.version !== 'v26.12.0') throw new Error('Use pinned Node v26.12.0');
const rows = JSON.parse(fs.readFileSync(0, 'utf8'));
const answers = rows.map(row => ['', 'u'].map(flags => {
    const text = row.text;
    return new RegExp('^' + row.body + '$', flags).test(text)
        && !new RegExp('[^ -~]', flags).test(text);
}));
process.stdout.write(JSON.stringify(answers));
'''


def utf16_hex(value):
    raw = value.encode('utf-16-be', 'surrogatepass')
    return raw.hex()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java-home', required=True)
    parser.add_argument('--node', default='node')
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--schemas', type=Path, required=True)
    args = parser.parse_args()
    assert importlib.metadata.version('jsonschema') == '4.23.0'
    java = str(Path(args.java_home) / 'bin/java')
    javac = str(Path(args.java_home) / 'bin/javac')
    version = subprocess.check_output([java, '-version'], stderr=subprocess.STDOUT, text=True)
    assert 'version "17.' in version
    rows = []
    strings = {''.join(chars) for size in range(5)
               for chars in itertools.product('aA0-_\n', repeat=size)}
    for source, body, valid in ACCEPTED:
        samples = strings | {'', valid, valid[:-1], valid + 'x', 'x' + valid,
                            'a' * 63, 'a' * 64, '0' * 254, '0' * 256}
        # Exhaust every ASCII suffix, including all controls, plus Unicode hazards.
        for unit in [chr(i) for i in range(128)] + ['é', 'e\u0301', '😀', '\ud800',
                                                  '\udc00', '\u0085', '\u2028', '\u2029', '\r\n']:
            samples.update((unit, valid + unit, unit + valid))
        for text in sorted(samples):
            rows.append({'source': source, 'body': body, 'text': text, 'flags': 0})
    p2_path = args.repo / 'klum-ast-gradle-plugin/src/test/resources/schema-projection-p2-patterns.json'
    p2 = json.loads(p2_path.read_text())
    malformed = [{'pattern': '[', 'text': '[', 'flags': 0, 'java': 'error'},
                 {'pattern': 'a{3,2}', 'text': 'aa', 'flags': 0, 'java': 'error'}]
    oracle_rows = rows + [{'source': r['pattern'], 'text': r['text'], 'flags': r['flags']}
                          for r in p2 + malformed]
    data = ''.join(utf16_hex(r['source']) + '\t' + utf16_hex(r['text']) + '\t'
                   + str(r['flags']) + '\n' for r in oracle_rows)
    with tempfile.TemporaryDirectory(prefix='pattern-projection-959-') as temporary:
        directory = Path(temporary)
        (directory / 'PatternOracle.java').write_text(JAVA)
        (directory / 'ecma.cjs').write_text(NODE)
        subprocess.run([javac, '-d', temporary, str(directory / 'PatternOracle.java')], check=True)
        observed = subprocess.check_output([java, '-cp', temporary, 'PatternOracle'], input=data, text=True).splitlines()
        ecma = json.loads(subprocess.check_output([args.node, str(directory / 'ecma.cjs')],
                                                 input=json.dumps(rows, ensure_ascii=True), text=True))
    assert len(observed) == len(oracle_rows)
    assert len(ecma) == len(rows)
    validators = {}
    artifacts = [args.schemas / str(lane) / 'pattern.schema.json' for lane in (3, 4, 5)]
    schemas = [json.loads(path.read_text()) for path in artifacts]
    assert schemas[0] == schemas[1] == schemas[2], 'Producer lanes must emit identical schemas'
    schema = schemas[0]
    Draft202012Validator.check_schema(schema)
    properties = schema['$defs']['example.Release_DSL$Builder']['properties']
    for index, (source, body, _) in enumerate(ACCEPTED):
        emitted = properties['sample' + str(index)]
        assert emitted['type'] == 'string'
        assert emitted['allOf'] == [{'pattern': '^' + body + '$'},
                                    {'not': {'type': 'string', 'pattern': '[^ -~]'}}]
        validators[source] = Draft202012Validator({'allOf': emitted['allOf']})
    document_validator = Draft202012Validator(schema)
    for value, expected in [({}, True), ({'slug': 'release-1'}, True), ({'slug': None}, False),
                            ({'slug': 'release-1\n'}, False), ({'slug': 'é'}, False),
                            ({'slug': ''}, False), ({'optional-token': ''}, True),
                            ({'slug': 7}, False), ({'Inherited/Code': 'AB'}, True),
                            ({'Inherited/Code': 'AB\n'}, False), ({'first': {'child-code': 'AB'}}, True),
                            ({'second': {'child-code': 'AB\n'}}, False), ({'excluded': 'a'}, False)]:
        assert document_validator.is_valid(value) == expected, repr(value)
    positive = {source: valid for source, _, valid in ACCEPTED}
    for index, row in enumerate(rows):
        expected = observed[index] == 'true'
        if row['text'] == positive[row['source']]:
            assert expected, 'Each proposed accepted source needs its positive control'
        assert observed[index] in ('true', 'false')
        assert ecma[index] == [expected, expected], repr(row)
        assert validators[row['source']].is_valid(row['text']) == expected, repr(row)
    for observed_value, row in zip(observed[len(rows):], p2 + malformed):
        expected = str(row['java']).lower()
        assert observed_value == expected, repr(row)
    # Positive control: the ordinary host-engine anchor leaks a trailing LF.
    naive = Draft202012Validator({'pattern': '^[A-Z]{2}[0-9]{4}$'})
    assert naive.is_valid('AB1234\n')
    assert not validators['[A-Z]{2}[0-9]{4}'].is_valid('AB1234\n')
    # Pattern itself is neutral on null/non-string; raw String admission is separate.
    structural_checks = 0
    for validator in validators.values():
        for value in (None, 5, False, [], {}):
            assert validator.is_valid(value)
            structural_checks += 1
    assert not Draft202012Validator({'not': {'pattern': '[^ -~]'}}).is_valid(None)
    property_schema = {'type': 'object', 'properties': {'code': {'type': 'string', **validators['a?'].schema}},
                       'additionalProperties': False}
    object_validator = Draft202012Validator(property_schema)
    for value, expected in [({}, True), ({'code': ''}, True), ({'code': None}, False),
                            ({'code': 1}, False), ({'code': 'x'}, False)]:
        assert object_validator.is_valid(value) == expected
        structural_checks += 1
    # Independent Pattern contributions are conjunctions, never overwrite or union.
    combined = Draft202012Validator({'allOf': [validators['[0-9]{0,3}'].schema,
                                               validators['[A-Z]{2}[0-9]{4}'].schema]})
    assert all(not combined.is_valid(value) for value in ('', '123', 'AB1234'))
    # Description-only malformed Java syntax does not become an invalid schema regex.
    description_only = {'type': 'string', 'description': 'Runtime constraint (not enforced by this schema; '
                        'unsupported pattern syntax): @jakarta.validation.constraints.Pattern(regexp="[")'}
    Draft202012Validator.check_schema(description_only)
    assert Draft202012Validator(description_only).is_valid('[')
    print(json.dumps({'producer_schemas': len(artifacts), 'accepted_sources': len(ACCEPTED), 'string_rows': len(rows),
                      'java_p2_and_malformed_rows': len(p2) + len(malformed),
                      'ecma_checks': len(rows) * 2, 'draft202012_string_checks': len(rows),
                      'neutrality_and_admission_checks': structural_checks,
                      'naive_trailing_lf_leak_reproduced_and_guard_closed': True,
                      'python': platform.python_version(), 'jsonschema': '4.23.0',
                      'node': subprocess.check_output([args.node, '-p', 'process.version'], text=True).strip(),
                      'java': version.splitlines()[0]}, indent=2))


if __name__ == '__main__':
    main()
