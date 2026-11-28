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
// Issue #1127: routing is conservative; only a complete positive classification may omit full work.
const {spawnSync} = require('node:child_process');
const fs = require('node:fs');
const {TextDecoder} = require('node:util');
const {githubApi, list} = require('./verify-release-ci.cjs');
const REPOSITORY = 'klum-dsl/klum-ast';
const REPOSITORY_ID = 40505913;
const ACTIONS_APP = 15368;
const SONAR_APP = 12526;
const ROOT = `/repos/${REPOSITORY}`;
const SHA = /^[0-9a-f]{40}$/;
const ALLOW = /^(?:CONTEXT\.md|docs\/adr\/[0-9]{4}-[a-z0-9]+(?:-[a-z0-9]+)*\.md|docs\/implementation\/adr-[0-9]{4}-[a-z0-9]+(?:-[a-z0-9]+)*\.md)$/;
const MAX_BYTES = 1048576;
function requireEvidence(condition, message) { if (!condition) throw new Error(message); }
function decode(bytes) { return new TextDecoder('utf-8', {fatal: true}).decode(bytes); }
function git(repo, ...args) {
  const result = spawnSync('git', ['-C', repo, ...args], {
    timeout: 30000, maxBuffer: 4 * MAX_BYTES, stdio: ['ignore', 'pipe', 'pipe'],
    env: {...process.env, GIT_NO_REPLACE_OBJECTS: '1', GIT_NO_LAZY_FETCH: '1', GIT_TERMINAL_PROMPT: '0'},
  });
  requireEvidence(!result.error && result.status === 0 && result.stderr.length === 0, 'Git failed, warned or timed out');
  return result.stdout;
}
function parseRaw(bytes) {
  requireEvidence(bytes.length > 0 && bytes.length <= MAX_BYTES && bytes.at(-1) === 0, 'Empty, oversized or truncated diff');
  const fields = decode(bytes).slice(0, -1).split('\0');
  const records = [];
  const seen = new Set();
  for (let i = 0; i < fields.length;) {
    const match = /^:([0-7]{6}) ([0-7]{6}) ([0-9a-f]{40}) ([0-9a-f]{40}) ([A-Z][0-9]*)$/.exec(fields[i++]);
    requireEvidence(match, 'Malformed diff header');
    const [, oldMode, newMode, oldOid, newOid, status] = match;
    const count = /^[RC]/.test(status) ? 2 : 1;
    requireEvidence(i + count <= fields.length, 'Truncated paths');
    const paths = fields.slice(i, i += count);
    for (const path of paths) {
      requireEvidence(path && !/[\x00-\x1f\x7f]/.test(path) && !seen.has(path), 'Invalid or duplicate path');
      seen.add(path);
    }
    records.push({status, paths, oldMode, newMode, oldOid, newOid});
  }
  requireEvidence(records.length <= 500, 'Diff exceeds 500-file routing budget');
  return records;
}
function classifyGit(repo, base, head, merge, report = {}) {
  requireEvidence([base, head, merge].every(sha => SHA.test(sha)), 'Full SHA required');
  requireEvidence(decode(git(repo, 'rev-parse', '--is-shallow-repository')).trim() === 'false', 'Shallow history');
  for (const sha of [base, head, merge]) {
    requireEvidence(decode(git(repo, 'rev-parse', `${sha}^{commit}`)).trim() === sha, 'Missing commit');
  }
  const parents = decode(git(repo, 'show', '-s', '--format=%P', merge)).trim().split(' ');
  requireEvidence(parents.length === 2 && parents[0] === base && parents[1] === head, 'Merge parents differ from pinned base/head');
  const bases = decode(git(repo, 'merge-base', '--all', base, head)).trim().split('\n');
  requireEvidence(bases.length === 1 && SHA.test(bases[0]), 'No unique merge base');
  const records = parseRaw(git(repo, 'diff', '--raw', '-z', '--no-abbrev', '--no-ext-diff', '--no-textconv',
    '--ignore-submodules=none', '--find-renames=50%', '--find-copies=50%', '--find-copies-harder', bases[0], head, '--'));
  // Preserve the complete known set even when an eligibility guard below selects full.
  Object.assign(report, {mergeBase: bases[0], records});
  let total = 0;
  for (const record of records) {
    requireEvidence(['A', 'M'].includes(record.status) && record.paths.every(path => ALLOW.test(path)), 'Non-allowlisted path/status');
    requireEvidence(record.newMode === '100644' && record.oldMode === (record.status === 'A' ? '000000' : '100644'), 'Non-regular file mode');
    for (const oid of [record.oldOid, record.newOid].filter(oid => oid !== '0'.repeat(40))) {
      const size = Number(decode(git(repo, 'cat-file', '-s', oid)).trim());
      total += size;
      requireEvidence(Number.isSafeInteger(size) && size <= 262144 && total <= MAX_BYTES, 'Blob routing budget exceeded');
      const blob = git(repo, 'cat-file', 'blob', oid);
      requireEvidence(blob.length === size && !blob.includes(0), 'Binary or incomplete blob');
      requireEvidence(!decode(blob).startsWith('version https://git-lfs.github.com/spec/v1'), 'LFS pointer');
    }
    // The integration tree must retain the same admitted bytes/mode, without conflict resolutions.
    requireEvidence(decode(git(repo, 'ls-tree', merge, '--', record.paths[0])).trim() ===
      `100644 blob ${record.newOid}\t${record.paths[0]}`, 'Merge changed admitted content');
  }
  return {mergeBase: bases[0], records};
}
function repoMatches(repo) { return repo?.id === REPOSITORY_ID && repo.full_name === REPOSITORY; }
function pin(event, live) {
  const pr = event.pull_request;
  requireEvidence(Number.isSafeInteger(pr?.number) && pr.number > 0 && live?.number === pr.number && live.state === 'open', 'Missing active PR');
  requireEvidence(repoMatches(event.repository) && repoMatches(live.base?.repo) &&
    live.head?.sha === pr.head?.sha && live.base?.ref === pr.base?.ref, 'PR event identity changed');
  requireEvidence([live.head.sha, live.base.sha, live.merge_commit_sha].every(sha => typeof sha === 'string' && SHA.test(sha)), 'Missing pinned revisions');
  return {number: live.number, head: live.head.sha, base: live.base.sha, target: live.base.ref,
    merge: live.merge_commit_sha, sameRepository: repoMatches(live.head.repo)};
}
async function readIdentity(event, api) {
  const identity = pin(event, (await api(`${ROOT}/pulls/${event.pull_request?.number}`)).body);
  const {body: ref} = await api(`${ROOT}/git/ref/heads/${encodeURIComponent(identity.target)}`);
  requireEvidence(ref.ref === `refs/heads/${identity.target}` && ref.object?.type === 'commit' &&
    ref.object.sha === identity.base, 'Live base tip differs from PR merge evidence');
  return identity;
}
async function strictPolicy(api) {
  const {body: rules} = await api(`${ROOT}/rules/branches/master`);
  requireEvidence(Array.isArray(rules), 'Malformed effective rules');
  const policies = rules.filter(rule => rule.type === 'required_status_checks').map(rule => rule.parameters);
  try {
    const {body} = await api(`${ROOT}/branches/master/protection/required_status_checks`);
    requireEvidence(Array.isArray(body.checks), 'Malformed classic protection');
    policies.push({strict_required_status_checks_policy: body.strict,
      required_status_checks: body.checks.map(check => ({context: check.context, integration_id: check.app_id}))});
  } catch (error) {
    if (error.message !== 'GitHub API returned HTTP 404') throw error;
  }
  const checks = policies.flatMap(policy => policy.required_status_checks || []);
  requireEvidence(policies.some(policy => policy.strict_required_status_checks_policy === true &&
    policy.required_status_checks?.some(check => check.context === 'PR qualification' && check.integration_id === ACTIONS_APP)),
  'Strict PR qualification policy absent; full fallback');
  requireEvidence(checks.every(check => check.context === 'PR qualification' && check.integration_id === ACTIONS_APP),
    'Other required checks require full qualification');
}
async function classify({eventName, event, repo, runSha}, api) {
  const result = {version: 1, lane: 'full', started: Date.now(), runSha, eventName, eventBase: event.pull_request?.base?.sha, toolsSha: process.env.QUALIFICATION_TOOLS_SHA, reasons: [], records: []};
  try {
    requireEvidence(eventName === 'pull_request', 'Only pull_request can admit prose');
    result.identity = await readIdentity(event, api);
    requireEvidence(['opened', 'synchronize', 'reopened', 'edited'].includes(event.action), 'Unsupported PR activity');
    requireEvidence(result.identity.target === 'master' && result.identity.sameRepository &&
      !event.changes?.base && result.identity.merge === runSha, 'Fork, retarget, unsupported base or stale merge input');
    // Only committed base tooling can classify. Its checkout must be this pinned base.
    requireEvidence(process.env.QUALIFICATION_TOOLS_SHA === result.identity.base, 'Trusted base tooling unavailable or stale');
    Object.assign(result, classifyGit(repo, result.identity.base, result.identity.head, result.identity.merge, result));
    await strictPolicy(api);
    result.lane = 'docs';
    result.reasons = ['Complete merge-base allowlist; current strict gate verified'];
  } catch (error) { result.reasons = [error.message]; }
  result.classificationMs = Date.now() - result.started;
  return result;
}
async function verifySonar(api, sha, attempts = 40, pause = ms => new Promise(resolve => setTimeout(resolve, ms))) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    const checks = await list(api, `${ROOT}/commits/${sha}/check-runs?filter=all`, 'check_runs');
    const sonar = checks.filter(check => check.app?.id === SONAR_APP || check.name === 'SonarCloud Code Analysis');
    requireEvidence(sonar.length <= 1, 'Ambiguous Sonar result');
    if (sonar.length) {
      requireEvidence(sonar[0].app?.id === SONAR_APP && sonar[0].head_sha === sha, 'Untrusted Sonar result');
      if (sonar[0].status === 'completed') {
        requireEvidence(sonar[0].conclusion === 'success', 'Sonar did not succeed');
        return;
      }
    }
    if (attempt + 1 < attempts) await pause(15000);
  }
  throw new Error('Trusted exact-head Sonar unavailable after bounded wait');
}
async function confirm(result, event, outcomes, api) {
  requireEvidence(['docs', 'full'].includes(result.lane), 'Unknown qualification lane');
  const docs = result.lane === 'docs';
  const required = docs ? ['policy_tests', 'prose_tests', 'docs'] : ['policy_tests', 'prose_tests', 'build', 'annotation', 'junit', 'compatibility', 'landing', 'sonar'];
  requireEvidence(required.every(name => outcomes[name] === 'success'), 'Selected validation missing, skipped or unsuccessful');
  if (result.eventName === 'pull_request') {
    requireEvidence(result.identity && result.runSha === result.identity.merge, 'Tested merge differs from pinned PR identity');
    const current = await readIdentity(event, api);
    requireEvidence(JSON.stringify(current) === JSON.stringify(result.identity), 'PR base/head/merge changed during validation');
    if (docs) await strictPolicy(api);
  } else {
    requireEvidence(result.eventName === 'merge_group' && event.merge_group?.head_sha === result.runSha, 'Unsupported qualification event');
  }
}
module.exports = {parseRaw, classifyGit, pin, readIdentity, strictPolicy, classify, verifySonar, confirm, ALLOW};
if (require.main === module) {
  (async () => {
    const env = process.env;
    const api = githubApi(env.GH_TOKEN);
    const event = JSON.parse(fs.readFileSync(env.GITHUB_EVENT_PATH));
    const path = env.QUALIFICATION_RESULT || 'pr-qualification.json';
    if (process.argv[2] === 'classify') {
      const result = await classify({eventName: env.GITHUB_EVENT_NAME, event, repo: '.', runSha: env.GITHUB_SHA}, api);
      fs.writeFileSync(path, JSON.stringify(result, null, 2) + '\n');
      fs.appendFileSync(env.GITHUB_OUTPUT, `lane=${result.lane}\n`);
      console.log(`Selected ${result.lane}: ${result.reasons.join('; ')}`);
    } else if (process.argv[2] === 'sonar') {
      const sha = event.pull_request?.head?.sha || event.merge_group?.head_sha;
      requireEvidence(SHA.test(sha), 'Missing exact head for Sonar');
      await verifySonar(api, sha);
    } else if (process.argv[2] === 'confirm') {
      let result = {version: 1, lane: 'full', eventName: env.GITHUB_EVENT_NAME, runSha: env.GITHUB_SHA, reasons: ['Classifier infrastructure unavailable']};
      if (fs.existsSync(path)) result = JSON.parse(fs.readFileSync(path));
      // A classifier infrastructure failure still runs full work; establish identity for the final race check.
      if (!result.identity && env.GITHUB_EVENT_NAME === 'pull_request') {
        result.identity = await readIdentity(event, api);
        requireEvidence(result.identity.merge === env.GITHUB_SHA, 'Full fallback tested a stale merge');
      }
      if (env.CLASSIFICATION_OUTCOME !== 'success') {
        result.lane = 'full';
        result.reasons = ['Classifier infrastructure failure; actual full validation selected'];
      }
      const outcomes = JSON.parse(env.VALIDATION_OUTCOMES);
      try { await confirm(result, event, outcomes, api); }
      finally {
        const measurement = {...result, outcomes, elapsedSinceClassificationMs: result.started ? Date.now() - result.started : null,
          runId: env.GITHUB_RUN_ID, attempt: env.GITHUB_RUN_ATTEMPT};
        fs.writeFileSync(path, JSON.stringify(measurement, null, 2) + '\n');
        fs.appendFileSync(env.GITHUB_STEP_SUMMARY, `### PR qualification\n\nScope: ${result.lane === 'docs' ? 'prose only' : 'full product'}.\n\n` +
          '```json\n' + JSON.stringify(measurement, null, 2) + '\n```\n\nProse qualification supplies no build, JUnit, Sonar, generated-site or release proof.\n');
      }
    } else throw new Error('Unknown qualification command');
  })().catch(error => { console.error(`PR qualification rejected: ${error.message}`); process.exitCode = 1; });
}
