/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
// Issue #959. Independent ECMA engine observations; no arbitrary Java regex translator.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
assert.equal(process.version, 'v26.12.0', 'Use the pinned characterization runtime');
const corpus = JSON.parse(fs.readFileSync(path.join(__dirname, 'schema-projection-p2-patterns.json'), 'utf8'));
let checks = 0;
for (const row of corpus) {
    const pattern = row.projected ?? row.pattern;
    if (row.ecma === 'error') {
        assert.throws(() => new RegExp(pattern, row.ecmaFlags), SyntaxError);
    } else {
        assert.equal(new RegExp(pattern, row.ecmaFlags).test(row.text), row.ecma, JSON.stringify(row));
        if (row.projected) {
            assert.equal(new RegExp(pattern, 'u').test(row.text), row.java, JSON.stringify(row));
            checks++;
        }
    }
    checks++;
}
assert.equal('😀'.length, 2);
assert.equal([...'😀'].length, 1);
console.log(`P2 ECMA corpus: ${checks} checks passed; node=${process.version}`);
