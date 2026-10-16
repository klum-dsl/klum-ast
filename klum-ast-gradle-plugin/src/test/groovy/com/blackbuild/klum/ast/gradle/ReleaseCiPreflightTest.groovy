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
package com.blackbuild.klum.ast.gradle

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.TimeUnit

@Issue('903')
class ReleaseCiPreflightTest extends Specification {
    static final String SHA = 'a' * 40
    static final String TIP = 'b' * 40
    static final String REPO = '/repos/klum-dsl/klum-ast'
    File root = new File(System.getProperty('klum.repositoryRoot'))
    Map fixture

    def setup() {
        def repository = [id: 40505913, full_name: 'klum-dsl/klum-ast']
        def run = [id: 100, workflow_id: 23237467, path: '.github/workflows/ci.yml',
                   repository: repository, head_repository: repository, event: 'push', head_branch: 'master',
                   head_sha: SHA, created_at: '2026-10-16T10:00:00Z', run_attempt: 1,
                   status: 'completed', conclusion: 'success']
        def steps = ['Build with Gradle', 'Qualify annotation propagation artifacts',
                     'Publish Test Report', 'Archive compatibility-lane evidence'].withIndex().collect { name, i ->
            [name: name, number: i + 6, status: 'completed', conclusion: 'success']
        }
        fixture = [identity: [repository: 'klum-dsl/klum-ast', sha: SHA, branch: 'master', version: '4.1.0-rc.1'], responses: [
            (REPO): [body: repository],
            (REPO + '/commits/' + SHA): [body: [sha: SHA]],
            (REPO + '/branches/master'): [body: [name: 'master', commit: [sha: TIP]]],
            (REPO + '/compare/' + SHA + '...' + TIP): [body: [status: 'ahead', base_commit: [sha: SHA], merge_base_commit: [sha: SHA]]],
            (REPO + '/actions/workflows/23237467'): [body: [id: 23237467, path: '.github/workflows/ci.yml', state: 'active']],
            (runsPath()): [body: [total_count: 1, workflow_runs: [run]]],
            (REPO + '/actions/runs/100'): [body: run],
            (jobsPath()): [body: [total_count: 1, jobs: [[id: 200, run_id: 100, run_attempt: 1, head_sha: SHA,
                      name: 'build', status: 'completed', conclusion: 'success', steps: steps]]]],
            (checksPath()): [body: [total_count: 1, check_runs: [[id: 300, head_sha: SHA, name: 'SonarCloud Code Analysis',
                      app: [id: 12526, slug: 'sonarqubecloud'], status: 'completed', conclusion: 'success']]]]
        ]]
    }

    def 'admits exact source CI and trusted Sonar and accepts unchanged protected recheck'() {
        when:
        def preflight = verify()
        fixture.identity.expected = [run_id: 100, run_attempt: 1, check_id: 300]
        def recheck = verify()

        then:
        preflight.ok
        preflight.selected == [run_id: 100, run_attempt: 1, check_id: 300]
        recheck.ok
        recheck.selected == preflight.selected
    }

    def 'reads every run job and check page before admitting evidence'() {
        given:
        def run = fixture.responses[runsPath()].body.workflow_runs[0]
        paginate(runsPath(), 'workflow_runs', (101..200).collect { run + [id: it, created_at: '2025-10-16T10:00:00Z'] }, [run])
        def job = fixture.responses[jobsPath()].body.jobs[0]
        paginate(jobsPath(), 'jobs', (1000..1099).collect { job + [id: it, name: "Auxiliary $it", steps: []] }, [job])
        def check = fixture.responses[checksPath()].body.check_runs[0]
        paginate(checksPath(), 'check_runs', (2000..2099).collect { check + [id: it, app: [id: 15368], name: "Other $it"] }, [check])

        when:
        def result = verify()

        then:
        result.ok
        result.selected == [run_id: 100, run_attempt: 1, check_id: 300]
        result.calls.contains(runsPath().replace('&page=1', '&page=2'))
        result.calls.contains(jobsPath().replace('&page=1', '&page=2'))
        result.calls.contains(checksPath().replace('&page=1', '&page=2'))
    }

    def 'rejects wrong identity or missing evidence: #scenario'() {
        given:
        mutate(fixture)

        when:
        def result = verify()

        then:
        !result.ok

        where:
        scenario              | mutate
        'caller repository'   | { f -> f.identity.repository = 'fork/klum-ast' }
        'SHA prefix'          | { f -> f.identity.sha = SHA[0..7] }
        'merge ref'           | { f -> f.identity.sha = 'refs/pull/903/merge' }
        'tag ref'             | { f -> f.identity.branch = 'v4.1.0' }
        'wrong line'          | { f -> f.identity.branch = 'release/4.0.x' }
        'repository id'       | { f -> f.responses[REPO].body.id = 42 }
        'repository name'     | { f -> f.responses[REPO].body.full_name = 'fork/klum-ast' }
        'ancestor SHA'        | { f -> f.responses[REPO + '/commits/' + SHA].body.sha = TIP }
        'rewritten branch'    | { f -> f.responses[REPO + '/compare/' + SHA + '...' + TIP].body.status = 'diverged' }
        'wrong merge base'    | { f -> f.responses[REPO + '/compare/' + SHA + '...' + TIP].body.merge_base_commit.sha = TIP }
        'workflow id'         | { f -> f.responses[REPO + '/actions/workflows/23237467'].body.id = 42 }
        'workflow path'       | { f -> f.responses[REPO + '/actions/workflows/23237467'].body.path = '.github/workflows/other.yml' }
        'disabled workflow'   | { f -> f.responses[REPO + '/actions/workflows/23237467'].body.state = 'disabled_manually' }
        'fork head'           | { f -> f.responses[runsPath()].body.workflow_runs[0].head_repository = [id: 42, full_name: 'fork/klum-ast'] }
        'PR run'              | { f -> f.responses[runsPath()].body.workflow_runs[0].event = 'pull_request' }
        'dispatch run'        | { f -> f.responses[runsPath()].body.workflow_runs[0].event = 'workflow_dispatch' }
        'other workflow run'  | { f -> f.responses[runsPath()].body.workflow_runs[0].workflow_id = 42 }
        'wrong run path'      | { f -> f.responses[runsPath()].body.workflow_runs[0].path = '.github/workflows/other.yml' }
        'other branch run'    | { f -> f.responses[runsPath()].body.workflow_runs[0].head_branch = 'release/4.0.x' }
        'other SHA run'       | { f -> f.responses[runsPath()].body.workflow_runs[0].head_sha = TIP }
        'missing attempt'     | { f -> f.responses[REPO + '/actions/runs/100'].body.remove('run_attempt') }
        'mixed attempt job'   | { f -> f.responses[jobsPath()].body.jobs[0].run_attempt = 2 }
        'wrong job SHA'       | { f -> f.responses[jobsPath()].body.jobs[0].head_sha = TIP }
        'missing build'       | { f -> f.responses[jobsPath()].body.jobs[0].name = 'other' }
        'missing steps'       | { f -> f.responses[jobsPath()].body.jobs[0].steps = [] }
        'ambiguous step'      | { f -> f.responses[jobsPath()].body.jobs[0].steps << (f.responses[jobsPath()].body.jobs[0].steps[0] + [number: 99]) }
        'missing Sonar'       | { f -> f.responses[checksPath()].body = [total_count: 0, check_runs: []] }
        'spoofed Sonar name'  | { f -> f.responses[checksPath()].body.check_runs[0].app.id = 15368 }
        'wrong Sonar SHA'     | { f -> f.responses[checksPath()].body.check_runs[0].head_sha = TIP }
        'ambiguous Sonar'     | { f -> f.responses[checksPath()].body.check_runs << (f.responses[checksPath()].body.check_runs[0] + [id: 301]); f.responses[checksPath()].body.total_count = 2 }
        'run changed at read' | { f -> f.responses[REPO + '/actions/runs/100'] = [body: f.responses[runsPath()].body.workflow_runs[0] + [status: 'in_progress', conclusion: null]] }
        'malformed timestamp' | { f -> f.responses[runsPath()].body.workflow_runs[0].created_at = 'yesterday' }
    }

    def 'rejects unsuccessful required #target with #outcome'() {
        given:
        Map record = target == 'run' ? fixture.responses[REPO + '/actions/runs/100'].body :
                target == 'job' ? fixture.responses[jobsPath()].body.jobs[0] :
                target == 'Sonar' ? fixture.responses[checksPath()].body.check_runs[0] :
                fixture.responses[jobsPath()].body.jobs[0].steps.find { it.name == target }
        record.conclusion = outcome
        record.status = outcome == null ? 'in_progress' : 'completed'

        when:
        def result = verify()

        then:
        !result.ok

        where:
        [target, outcome] << (['run', 'job', 'Sonar', 'Build with Gradle', 'Qualify annotation propagation artifacts',
                              'Publish Test Report', 'Archive compatibility-lane evidence'].collectMany { target ->
            ['failure', 'skipped', 'neutral', 'cancelled', 'timed_out', null].collect { [target, it] }
        })
    }

    def 'newest run is selected before success filtering including a later page'() {
        given:
        def old = fixture.responses[runsPath()].body.workflow_runs[0]
        def newer = old + [id: 500, created_at: '2026-10-16T11:00:00Z', conclusion: 'failure']
        paginate(runsPath(), 'workflow_runs', (100..199).collect { old + [id: it] }, [newer])
        fixture.responses[REPO + '/actions/runs/500'] = [body: newer]

        when:
        def result = verify()

        then:
        !result.ok
        !result.calls.contains(jobsPath())
    }

    def 'latest failed attempt cannot borrow an earlier successful attempt'() {
        given:
        fixture.responses[runsPath()].body.workflow_runs[0].run_attempt = 2
        fixture.responses[REPO + '/actions/runs/100'].body.conclusion = 'failure'

        when:
        def result = verify()

        then:
        !result.ok
        !result.calls.contains(jobsPath())
    }

    def 'fails closed on API errors and malformed or incomplete lists: #scenario'() {
        given:
        mutate(fixture)

        when:
        def result = verify()

        then:
        !result.ok

        where:
        scenario               | mutate
        'API forbidden'        | { f -> f.responses[REPO] = [status: 403] }
        'API unavailable'      | { f -> f.responses[REPO] = [status: 503] }
        'timeout'              | { f -> f.responses[REPO] = [error: 'timeout'] }
        'malformed JSON'       | { f -> f.responses[REPO] = [malformed: true] }
        'null response'        | { f -> f.responses[REPO] = [body: null] }
        'missing count'        | { f -> f.responses[runsPath()].body.remove('total_count') }
        'missing next page'    | { f -> f.responses[runsPath()].body.total_count = 2 }
        'API search cap'       | { f -> f.responses[runsPath()].body.total_count = 1001 }
        'duplicate run id'     | { f -> f.responses[runsPath()].body.workflow_runs *= 2; f.responses[runsPath()].body.total_count = 2 }
        'duplicate job id'     | { f -> f.responses[jobsPath()].body.jobs *= 2; f.responses[jobsPath()].body.total_count = 2 }
        'duplicate check id'   | { f -> f.responses[checksPath()].body.check_runs *= 2; f.responses[checksPath()].body.total_count = 2 }
        'foreign next target'  | { f -> f.responses[runsPath()].headers = [link: '<https://evil.invalid/>; rel="next"'] }
        'malformed link'       | { f -> f.responses[runsPath()].headers = [link: 'page two'] }
    }

    def 'bounds transient retries and admits only a successful complete response'() {
        given:
        def success = fixture.responses[REPO]
        fixture.responses[REPO] = [[status: 503], [error: 'timeout'], success]

        when:
        def result = verify()

        then:
        result.ok
        result.calls.count(REPO) == 3

        when:
        fixture.responses[REPO] = [status: 503]
        def failed = verify()

        then:
        !failed.ok
        failed.calls == [REPO, REPO, REPO]
    }

    def 'publication recheck rejects changed evidence: #scenario'() {
        given:
        def preflight = verify()
        assert preflight.ok
        fixture.identity.expected = preflight.selected
        mutate(fixture)

        when:
        def result = verify()

        then:
        !result.ok

        where:
        scenario            | mutate
        'Sonar identity'    | { f -> f.responses[checksPath()].body.check_runs[0].id = 301 }
        'Sonar result'      | { f -> f.responses[checksPath()].body.check_runs[0].conclusion = 'failure' }
        'CI result'         | { f -> f.responses[REPO + '/actions/runs/100'].body.status = 'in_progress' }
        'branch ancestry'   | { f -> f.responses[REPO + '/compare/' + SHA + '...' + TIP].body.merge_base_commit.sha = TIP }
        'missing CI result' | { f -> f.responses[jobsPath()] = [status: 404] }
    }

    def paginate(String path, String key, List first, List last) {
        String next = path.replace('&page=1', '&page=2')
        fixture.responses[path] = [body: [total_count: first.size() + last.size(), (key): first],
                                   headers: [link: "<https://api.github.com$next>; rel=\"next\""]]
        fixture.responses[next] = [body: [total_count: first.size() + last.size(), (key): last]]
    }

    Map verify() {
        def process = new ProcessBuilder('node', new File(root, 'klum-ast-gradle-plugin/src/test/resources/release-ci/run-fixture.cjs').path,
                new File(root, '.github/scripts/verify-release-ci.cjs').path).redirectErrorStream(true).start()
        process.outputStream.withWriter('UTF-8') { it << JsonOutput.toJson(fixture) }
        String output = process.inputStream.getText('UTF-8')
        assert process.waitFor(20, TimeUnit.SECONDS)
        assert process.exitValue() == 0: output
        new JsonSlurper().parseText(output) as Map
    }

    static String runsPath() { REPO + '/actions/workflows/23237467/runs?head_sha=' + SHA + '&event=push&branch=master&per_page=100&page=1' }
    static String jobsPath() { REPO + '/actions/runs/100/attempts/1/jobs?per_page=100&page=1' }
    static String checksPath() { REPO + '/commits/' + SHA + '/check-runs?filter=all&per_page=100&page=1' }
}
