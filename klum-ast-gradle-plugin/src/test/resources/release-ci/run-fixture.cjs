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
'use strict';
const fs = require('node:fs');
const {verifyReleaseCi, githubApi} = require(process.argv[2]);
const input = JSON.parse(fs.readFileSync(0, 'utf8'));
const calls = [];
const fetchFixture = async (url) => {
  const path = url.replace('https://api.github.com', '');
  calls.push(path);
  const response = input.responses[path];
  if (!response) throw new Error('Unexpected fixture request: ' + path);
  const item = Array.isArray(response) ? (response.length > 1 ? response.shift() : response[0]) : response;
  if (item.error) throw new Error(item.error);
  return {status: item.status || 200, headers: new Headers(item.headers || {}),
    json: async () => { if (item.malformed) throw new Error('Invalid JSON'); return item.body; }};
};
(async () => {
  try {
    const selected = await verifyReleaseCi(input.identity, githubApi('fixture-token', fetchFixture, async () => {}));
    console.log(JSON.stringify({ok: true, selected, calls}));
  } catch (error) {
    console.log(JSON.stringify({ok: false, message: error.message, calls}));
  }
})();
