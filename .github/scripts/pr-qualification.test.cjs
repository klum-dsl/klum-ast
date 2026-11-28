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
// @Issue('1127'): fixtures for the qualification policy; no hosted build or GitHub writes.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {execFileSync} = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const q = require('./pr-qualification.cjs');
const repoId = {id: 40505913, full_name: 'klum-dsl/klum-ast'};
const a = 'a'.repeat(40), b = 'b'.repeat(40), c = 'c'.repeat(40);
const live = {number: 7, state: 'open', base: {repo: repoId, ref: 'master', sha: a},
  head: {repo: repoId, sha: b}, merge_commit_sha: c};
const event = {repository: repoId, action: 'opened', pull_request: live};
const api = async p => {
  if (p.includes('/git/ref/heads/')) return {body:{ref:'refs/heads/master',object:{type:'commit',sha:a}}};
  if (p.endsWith('/pulls/7')) return {body: structuredClone(live)};
  throw new Error('GitHub API returned HTTP 404');
};
function fixture(t) {
  const repo = fs.mkdtempSync(path.join(os.tmpdir(), 'prose-1127-'));
  t.after(() => fs.rmSync(repo, {recursive: true, force: true}));
  const git = (...args) => execFileSync('git', ['-C', repo, ...args], {encoding: 'utf8', stdio: ['ignore','pipe','pipe']}).trim();
  git('init', '-q', '--initial-branch=master'); git('config', 'user.name', 'Fixture'); git('config', 'user.email', 'fixture@example.invalid');
  const write = (name, text) => { fs.mkdirSync(path.dirname(path.join(repo, name)), {recursive: true}); fs.writeFileSync(path.join(repo, name), text); };
  write('CONTEXT.md', '# Context\n'); write('source.java', 'base\n'); git('add', '.'); git('commit', '-qm', 'base');
  const base = git('rev-parse', 'HEAD'); git('checkout', '-qb', 'topic');
  const commit = () => { git('add', '.'); git('commit', '-qm', 'change'); return git('rev-parse', 'HEAD'); };
  const merge = () => { const head = git('rev-parse', 'HEAD'); git('checkout', '-q', 'master'); git('merge', '--no-ff', '-qm', 'merge', head); return git('rev-parse', 'HEAD'); };
  return {repo, git, write, base, commit, merge};
}
test('flat architecture plans and evidence qualify without artificial naming', () => {
  for (const p of ['CONTEXT.md', 'docs/adr/0034-test.md', 'docs/implementation/adr-0034-test.md',
    'docs/implementation/issue-1129-struct-scalar-alternatives.md', 'docs/implementation/issue-1132-bounded-raw-data.md',
    'docs/implementation/evidence/issue-1114-yaml-qualification.md']) assert.ok(q.ALLOW.test(p));
  for (const p of ['README.md', 'CHANGES.md', 'AGENTS.md', 'docs/user/X.md', 'docs/adr/nested/0034-test.md',
    'docs/implementation/fixtures/example.md', 'docs/implementation/evidence/probe/README.md',
    'docs/implementation/evidence/check.py', '.github/workflows/ci.yml']) assert.ok(!q.ALLOW.test(p));
});
test('complete diff rejects earlier code even when final commit is prose', t => {
  const f = fixture(t); f.write('source.java', 'code\n'); f.commit(); f.write('CONTEXT.md', '# Changed\n'); const head=f.commit();
  assert.throws(() => q.classifyGit(f.repo, f.base, head, f.merge()), /path\/status/);
});
test('unique merge base with advancing base and exact integration content', t => {
  const f=fixture(t); f.write('CONTEXT.md', '# Changed\n'); const head=f.commit();
  f.git('checkout', '-q', 'master'); f.write('source.java', 'base advanced\n'); const base=f.commit(); f.git('checkout','-q','topic');
  const result=q.classifyGit(f.repo,base,head,f.merge()); assert.equal(result.mergeBase,f.base); assert.equal(result.records.length,1);
  assert.equal(result.records[0].paths[0],'CONTEXT.md');
});
for (const kind of ['delete','rename','copy','executable','symlink','binary','utf8','lfs','budget']) test(`full fallback for ${kind}`, t => {
  const f=fixture(t);
  if(kind==='delete') fs.unlinkSync(path.join(f.repo,'CONTEXT.md'));
  if(kind==='rename') f.git('mv','CONTEXT.md','docs.md');
  if(kind==='copy') f.write('docs/adr/0001-test.md','# Context\n');
  if(kind==='executable') fs.chmodSync(path.join(f.repo,'CONTEXT.md'),0o755);
  if(kind==='symlink') {fs.unlinkSync(path.join(f.repo,'CONTEXT.md'));fs.symlinkSync('source.java',path.join(f.repo,'CONTEXT.md'));}
  if(kind==='binary') f.write('CONTEXT.md',Buffer.from([0]));
  if(kind==='utf8') f.write('CONTEXT.md',Buffer.from([0xff]));
  if(kind==='lfs') f.write('CONTEXT.md','version https://git-lfs.github.com/spec/v1\n');
  if(kind==='budget') f.write('CONTEXT.md','# Context\n'+'x'.repeat(262144));
  const head=f.commit(); assert.throws(()=>q.classifyGit(f.repo,f.base,head,f.merge()));
});
test('missing or mismatched merge evidence refuses docs', t => {
  const f=fixture(t); f.write('CONTEXT.md','# New\n'); const head=f.commit();
  assert.throws(()=>q.classifyGit(f.repo,f.base,head,head),/parents/);
  assert.throws(()=>q.classifyGit(f.repo,f.base,head,a));
});
test('empty, malformed, duplicate and truncated NUL records reject', () => {
  const header=`:100644 100644 ${a} ${b} M\0CONTEXT.md\0`;
  for(const raw of ['',header.slice(0,-1),header+header,':bad\0',`:100644 100644 ${a} ${b} R100\0one\0`]) assert.throws(()=>q.parseRaw(Buffer.from(raw)));
});
test('event/current PR identities, forks and retargets are distinguished', async () => {
  assert.equal(q.pin(event,live).head,b);
  for(const changed of [{...live,head:{...live.head,sha:a}}, {...live,base:{...live.base,ref:'release/4.2.x'}}, {...live,state:'closed'}]) assert.throws(()=>q.pin(event,changed));
  const result=await q.classify({eventName:'merge_group',event,repo:'.',runSha:c},api); assert.equal(result.lane,'full');
  const retarget=await q.classify({eventName:'pull_request',event:{...event,action:'edited',changes:{base:{ref:{from:'other'}}}},repo:'.',runSha:c},api);
  assert.equal(retarget.lane,'full'); assert.match(retarget.reasons[0],/retarget/);
});
test('literal success required, stale identities fail, prose has no full check requirement', async () => {
  const result={lane:'docs',identity:q.pin(event,live),eventName:'pull_request',runSha:c};
  await q.confirm(result,event,{prose_tests:'success',policy_tests:'success',docs:'success'},api);
  for(const status of ['skipped','failure','cancelled','timed_out',undefined]) await assert.rejects(q.confirm(result,event,{prose_tests:'success',policy_tests:'success',docs:status},api));
  await assert.rejects(q.confirm(result,event,{prose_tests:'success',policy_tests:'success',docs:'success'},async p=>p.endsWith('/pulls/7')?{body:{...live,base:{...live.base,sha:b}}}:api(p)));
  await assert.rejects(q.confirm({...result,lane:'full'},event,{build:'success'},api));
  await assert.rejects(q.confirm({...result,lane:'unknown'},event,{prose_tests:'success',policy_tests:'success',docs:'success'},api));
});
test('authentic exact-head Sonar selects the fresh quality gate across repeat records', async () => {
  const start = '2026-11-28T01:00:00Z', old = '2026-11-27T01:00:00Z', newer = '2026-11-28T02:00:00Z';
  const success = {id:1,name:'SonarCloud Code Analysis',app:{id:12526},head_sha:b,
    status:'completed',conclusion:'success',started_at:start};
  const repeat = {...success,id:3,started_at:newer};
  const security = {...success,id:2,name:'SonarCloud',app:{id:57789}};
  function checksApi(checks) {
    return async p => p.includes('check-suites') ? {body:{total_count:1,check_suites:[{}]}} :
      {body:{total_count:checks.length,check_runs:checks}};
  }
  assert.equal(await q.verifySonar(checksApi([security,success]),b,start,1),1);
  assert.equal(await q.verifySonar(checksApi([success,repeat,security]),b,start,1),3);
  assert.equal(await q.verifySonar(checksApi([{...success,conclusion:'failure'},repeat]),b,start,1),3);
  for (const checks of [[],[security],[{...success,app:{id:1}}],[{...success,conclusion:'failure'}],
    [{...success,status:'in_progress'}],[{...success,head_sha:a}],[{...success,started_at:old}],
    [success,{...repeat,conclusion:'failure'}],[success,{...repeat,status:'in_progress'}],
    [success,{...repeat,status:'queued',started_at:null}],[success,{...repeat,started_at:'invalid'}],
    [success,{...success,id:4,conclusion:'failure'}]]) {
    await assert.rejects(q.verifySonar(checksApi(checks),b,start,1));
  }
  assert.equal(q.selectPrSonar([success,{...success,id:4}],b,start).id,4);
});

test('all supported activity types classify fresh positive identities; unsafe input stays full', async t => {
  const f=fixture(t); f.write('CONTEXT.md','# New context\n'); const head=f.commit(); const merge=f.merge();
  const current={...live,base:{...live.base,sha:f.base},head:{...live.head,sha:head},merge_commit_sha:merge};
  const currentApi=async p=>p.endsWith('/pulls/7')?{body:current}:p.includes('/git/ref/heads/')?{body:{ref:'refs/heads/master',object:{type:'commit',sha:f.base}}}:api(p);
  const previous=process.env.QUALIFICATION_TOOLS_SHA;
  process.env.QUALIFICATION_TOOLS_SHA=f.base;
  t.after(()=>{if(previous===undefined)delete process.env.QUALIFICATION_TOOLS_SHA;else process.env.QUALIFICATION_TOOLS_SHA=previous;});
  for(const action of ['opened','synchronize','reopened','edited']) {
    const result=await q.classify({eventName:'pull_request',event:{...event,pull_request:current,action},repo:f.repo,runSha:merge},currentApi);
    assert.equal(result.lane,'docs',result.reasons.join(';'));
  }
  for(const change of [{base:{...current.base,ref:'release/4.2.x'}},{head:{...current.head,repo:{id:1,full_name:'fork/repo'}}}]) {
    const pr={...current,...change};
    const result=await q.classify({eventName:'pull_request',event:{...event,pull_request:pr},repo:f.repo,runSha:merge},async()=>({body:pr}));
    assert.equal(result.lane,'full');
  }
  delete process.env.QUALIFICATION_TOOLS_SHA;
  assert.equal((await q.classify({eventName:'pull_request',event:{...event,pull_request:current},repo:f.repo,runSha:merge},currentApi)).lane,'full');
});
test('PR triggering and final validation do not hide retargets or selected work', () => {
  const pr=fs.readFileSync(path.resolve(__dirname,'../workflows/pr-qualification.yml'),'utf8');
  assert.ok(!pr.includes('paths-ignore:')); assert.ok(!pr.includes('branches:')); assert.ok(!pr.includes('pull_request_target:'));
  assert.match(pr,/types: \[ opened, synchronize, reopened, edited \]/);
  assert.match(pr,/name: Confirm selected validation\n        if: always\(\)/);
});
test('shallow history and empty net diffs refuse prose', t => {
  const f=fixture(t); f.write('CONTEXT.md','# New\n'); f.commit(); f.write('CONTEXT.md','# Context\n'); const head=f.commit(); const merge=f.merge();
  assert.throws(()=>q.classifyGit(f.repo,f.base,head,merge),/Empty/);
  const shallow=path.join(os.tmpdir(),`shallow-1127-${process.pid}`);
  t.after(()=>fs.rmSync(shallow,{recursive:true,force:true}));
  execFileSync('git',['clone','-q','--depth=1',`file://${f.repo}`,shallow]);
  assert.throws(()=>q.classifyGit(shallow,f.base,head,merge),/Shallow/);
});
test('ambiguous merge bases and altered integration content refuse prose', t => {
  const f=fixture(t); f.write('CONTEXT.md','# New\n'); const head=f.commit(); let merge=f.merge();
  f.write('CONTEXT.md','# Integration changed\n'); f.git('add','.'); f.git('commit','--amend','-qm','altered merge'); merge=f.git('rev-parse','HEAD');
  assert.throws(()=>q.classifyGit(f.repo,f.base,head,merge),/Merge changed/);
  const tree=f.git('rev-parse',`${f.base}^{tree}`);
  const left=f.git('commit-tree',tree,'-p',f.base,'-m','left');
  const right=f.git('commit-tree',tree,'-p',f.base,'-m','right');
  const base=f.git('commit-tree',tree,'-p',left,'-p',right,'-m','base');
  const crissHead=f.git('commit-tree',tree,'-p',right,'-p',left,'-m','head');
  const crissMerge=f.git('commit-tree',tree,'-p',base,'-p',crissHead,'-m','merge');
  assert.throws(()=>q.classifyGit(f.repo,base,crissHead,crissMerge),/unique merge base/);
});
test('live ref advancement and identity API failure cannot produce success', async () => {
  const result={lane:'docs',identity:q.pin(event,live),eventName:'pull_request',runSha:c};
  const outcomes={policy_tests:'success',prose_tests:'success',docs:'success'};
  await assert.rejects(q.confirm(result,event,outcomes,async p=>p.includes('/git/ref/heads/')?{body:{ref:'refs/heads/master',object:{type:'commit',sha:b}}}:api(p)),/Live base tip/);
  await assert.rejects(q.confirm(result,event,outcomes,async p=>p.includes('/git/ref/')?Promise.reject(new Error('API unavailable')):api(p)),/API unavailable/);
});

test('already stale merge selecting full can never qualify a different pinned merge', async () => {
  const result=await q.classify({eventName:'pull_request',event,repo:'.',runSha:'d'.repeat(40)},api);
  assert.equal(result.lane,'full');
  const outcomes=Object.fromEntries(['policy_tests','prose_tests','build','annotation','junit','compatibility','landing','sonar'].map(name=>[name,'success']));
  await assert.rejects(q.confirm(result,event,outcomes,api),/Tested merge differs/);
});

test('known noneligible full changes preserve their complete enumeration for audit', t => {
  const f=fixture(t); f.write('source.java','code\n'); f.write('CONTEXT.md','# New\n'); const head=f.commit();
  const report={};
  assert.throws(()=>q.classifyGit(f.repo,f.base,head,f.merge(),report),/path\/status/);
  assert.equal(report.mergeBase,f.base);
  assert.deepEqual(report.records.flatMap(record=>record.paths),['CONTEXT.md','source.java']);
});
