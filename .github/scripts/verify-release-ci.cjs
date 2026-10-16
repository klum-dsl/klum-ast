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

// ADR 0030 / #903: reviewed against public GitHub metadata on 2026-10-16.
// Keep this small execution contract in sync with ci.yml and its workflow contract test.
const REPOSITORY = 'klum-dsl/klum-ast';
const REPOSITORY_ID = 40505913;
const WORKFLOW_ID = 23237467;
const WORKFLOW_PATH = '.github/workflows/ci.yml';
const SONAR_APP_ID = 12526;
const REQUIRED_STEPS = [
  [6, 'Build with Gradle'],
  [7, 'Qualify annotation propagation artifacts'],
  [8, 'Publish Test Report'],
  [9, 'Archive compatibility-lane evidence'],
];

function requireEvidence(condition, message) {
  if (!condition) throw new Error(message);
}
function positiveId(value) { return Number.isSafeInteger(value) && value > 0; }
function repositoryMatches(repository) {
  return repository?.id === REPOSITORY_ID && repository.full_name === REPOSITORY;
}
function successful(result) { return result?.status === 'completed' && result.conclusion === 'success'; }

function githubApi(token, fetchResponse = fetch, pause = ms => new Promise(resolve => setTimeout(resolve, ms))) {
  requireEvidence(typeof token === 'string' && token.length > 0, 'Missing GitHub read token');
  return async path => {
    requireEvidence(path.startsWith(`/repos/${REPOSITORY}`), 'Unexpected GitHub API path');
    for (let attempt = 0; attempt < 3; attempt++) {
      let response;
      try {
        response = await fetchResponse(`https://api.github.com${path}`, {
          method: 'GET', redirect: 'error', signal: AbortSignal.timeout(15000),
          headers: {Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28'},
        });
      } catch {
        if (attempt === 2) throw new Error('GitHub API unavailable after three attempts');
        await pause(1000 * (attempt + 1));
        continue;
      }
      if ([429, 500, 502, 503, 504].includes(response.status) && attempt < 2) {
        await pause(1000 * (attempt + 1));
        continue;
      }
      requireEvidence(response.status === 200, `GitHub API returned HTTP ${response.status}`);
      let body;
      try { body = await response.json(); } catch { throw new Error('Malformed GitHub JSON'); }
      requireEvidence(body && typeof body === 'object' && !Array.isArray(body), 'Malformed GitHub object');
      return {body, link: response.headers.get('link')};
    }
  };
}

async function list(api, path, key) {
  const first = new URL(`https://api.github.com${path}${path.endsWith('?') ? '' : '&'}per_page=100&page=1`);
  const records = [];
  const ids = new Set();
  let total;
  for (let page = 1; page <= 100; page++) {
    const url = new URL(first);
    url.searchParams.set('page', page);
    const {body, link} = await api(url.pathname + url.search);
    requireEvidence(Number.isSafeInteger(body.total_count) && body.total_count >= 0 &&
      body.total_count <= (key === 'workflow_runs' ? 1000 : 10000) &&
      Array.isArray(body[key]) && body[key].length <= 100 && body.incomplete_results !== true,
      'Malformed or truncated GitHub list');
    if (total === undefined) total = body.total_count;
    requireEvidence(total === body.total_count, 'GitHub pagination changed');
    for (const record of body[key]) {
      requireEvidence(positiveId(record?.id) && !ids.has(record.id), 'Missing or duplicate GitHub identity');
      ids.add(record.id);
      records.push(record);
    }
    const links = new Map();
    for (const part of link ? link.split(',') : []) {
      const match = /^\s*<([^>]+)>;\s*rel="(next|prev|first|last)"\s*$/.exec(part);
      requireEvidence(match && !links.has(match[2]), 'Malformed GitHub pagination link');
      const target = new URL(match[1]);
      const linkedPage = target.searchParams.get('page');
      requireEvidence(/^[1-9][0-9]*$/.test(linkedPage), 'Malformed GitHub page number');
      const allowed = new URL(first);
      allowed.searchParams.set('page', linkedPage);
      target.searchParams.sort();
      allowed.searchParams.sort();
      requireEvidence(target.href === allowed.href, 'Untrusted GitHub pagination target');
      links.set(match[2], Number(linkedPage));
    }
    requireEvidence(records.length <= total, 'Conflicting GitHub list count');
    if (!links.has('next')) {
      requireEvidence(records.length === total, 'Incomplete GitHub pagination');
      return records;
    }
    requireEvidence(links.get('next') === page + 1 && records.length < total && body[key].length === 100,
      'Incomplete or looping GitHub pagination');
  }
  throw new Error('GitHub pagination limit exceeded');
}

async function verifyReleaseCi(identity, api) {
  const {repository, sha, branch, version, expected} = identity;
  requireEvidence(repository === REPOSITORY, 'Wrong release repository');
  requireEvidence(typeof sha === 'string' && /^[0-9a-f]{40}$/.test(sha), 'A full source SHA is required');
  const line = /^(\d+)\.(\d+)\.\d+(?:-rc\.[1-9]\d*)?$/.exec(version);
  requireEvidence(line && (branch === 'master' || branch === `release/${line[1]}.${line[2]}.x`), 'Wrong release branch');
  const root = `/repos/${REPOSITORY}`;
  requireEvidence(repositoryMatches((await api(root)).body), 'Wrong GitHub repository identity');
  requireEvidence((await api(`${root}/commits/${sha}`)).body.sha === sha, 'Wrong source SHA');
  const live = (await api(`${root}/branches/${encodeURIComponent(branch)}`)).body;
  requireEvidence(live.name === branch && /^[0-9a-f]{40}$/.test(live.commit?.sha), 'Missing intended branch');
  const comparison = (await api(`${root}/compare/${sha}...${live.commit.sha}`)).body;
  requireEvidence(['ahead', 'identical'].includes(comparison.status) && comparison.base_commit?.sha === sha &&
    comparison.merge_base_commit?.sha === sha, 'Source is no longer on the intended branch');
  const workflow = (await api(`${root}/actions/workflows/${WORKFLOW_ID}`)).body;
  requireEvidence(workflow.id === WORKFLOW_ID && workflow.path === WORKFLOW_PATH && workflow.state === 'active',
    'Unapproved CI workflow');
  const eligible = run => repositoryMatches(run.repository) && repositoryMatches(run.head_repository) &&
    run.workflow_id === WORKFLOW_ID && run.path === WORKFLOW_PATH && run.event === 'push' &&
    run.head_sha === sha && run.head_branch === branch;
  const runs = await list(api, `${root}/actions/workflows/${WORKFLOW_ID}/runs?head_sha=${sha}&event=push&branch=${encodeURIComponent(branch)}`, 'workflow_runs');
  const candidates = runs.filter(eligible);
  requireEvidence(candidates.length > 0, 'No eligible exact-SHA CI run');
  for (const run of candidates) {
    requireEvidence(positiveId(run.id) && positiveId(run.run_attempt) &&
      typeof run.created_at === 'string' && Number.isFinite(Date.parse(run.created_at)), 'Malformed CI run');
  }
  candidates.sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || b.id - a.id);
  const selected = candidates[0];
  const current = (await api(`${root}/actions/runs/${selected.id}`)).body;
  requireEvidence(current.id === selected.id && eligible(current) && current.run_attempt === selected.run_attempt &&
    current.created_at === selected.created_at && successful(current), 'Latest CI run attempt is not successful or changed');
  const jobs = await list(api, `${root}/actions/runs/${current.id}/attempts/${current.run_attempt}/jobs?`, 'jobs');
  requireEvidence(jobs.every(job => job.run_id === current.id && job.run_attempt === current.run_attempt && job.head_sha === sha),
    'Mixed CI job attempts');
  const build = jobs.filter(job => job.name === 'build');
  requireEvidence(build.length === 1 && successful(build[0]) && Array.isArray(build[0].steps), 'Missing or unsuccessful build job');
  for (const [number, name] of REQUIRED_STEPS) {
    const matches = build[0].steps.filter(step => step.name === name || step.number === number);
    requireEvidence(matches.length === 1 && matches[0].name === name && matches[0].number === number && successful(matches[0]),
      `Missing, ambiguous or unsuccessful required step: ${name}`);
  }
  const checks = await list(api, `${root}/commits/${sha}/check-runs?filter=all`, 'check_runs');
  const sonar = checks.filter(check => check.app?.id === SONAR_APP_ID || check.name === 'SonarCloud Code Analysis');
  requireEvidence(sonar.length === 1 && sonar[0].app?.id === SONAR_APP_ID && sonar[0].head_sha === sha && successful(sonar[0]),
    'Missing, ambiguous, untrusted or unsuccessful SonarCloud check');
  requireEvidence(positiveId(sonar[0].id), 'Malformed SonarCloud check identity');
  const result = {run_id: current.id, run_attempt: current.run_attempt, check_id: sonar[0].id};
  if (expected) requireEvidence(Object.keys(result).every(key => result[key] === expected[key]),
    'CI or SonarCloud evidence changed; repeat preflight and approval');
  return result;
}

module.exports = {verifyReleaseCi, githubApi, REQUIRED_STEPS};

if (require.main === module) {
  (async () => {
    const env = process.env;
    const identity = {repository: env.GITHUB_REPOSITORY, sha: env.EXPECTED_COMMIT,
      branch: env.SOURCE_BRANCH, version: env.RELEASE_VERSION};
    if (process.argv[2] === '--recheck') {
      identity.expected = {run_id: Number(env.CI_RUN_ID), run_attempt: Number(env.CI_RUN_ATTEMPT), check_id: Number(env.SONAR_CHECK_ID)};
      requireEvidence(Object.values(identity.expected).every(positiveId), 'Missing successful preflight outputs');
    } else requireEvidence(process.argv.length === 2, 'Unexpected gate arguments');
    requireEvidence(!env.GITHUB_API_URL || env.GITHUB_API_URL === 'https://api.github.com', 'Unapproved GitHub API origin');
    const selected = await verifyReleaseCi(identity, githubApi(env.GH_TOKEN));
    if (env.GITHUB_OUTPUT) {
      require('node:fs').appendFileSync(env.GITHUB_OUTPUT, Object.entries(selected).map(([key, value]) => `${key}=${value}\n`).join(''));
    }
    console.log('Exact-SHA CI and SonarCloud verification passed.');
  })().catch(error => { console.error(`Release CI gate rejected: ${error.message}`); process.exitCode = 1; });
}
