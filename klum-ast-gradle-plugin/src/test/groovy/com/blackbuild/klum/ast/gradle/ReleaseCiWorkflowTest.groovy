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

import org.yaml.snakeyaml.Yaml
import spock.lang.Issue
import spock.lang.Specification

@Issue('903')
class ReleaseCiWorkflowTest extends Specification {
    File root = new File(System.getProperty('klum.repositoryRoot'))
    Map release = readWorkflow('release.yml')

    def 'failed CI preflight makes pending documentation and protected publication ineligible'() {
        given:
        Map jobs = release.jobs
        Map outcomes = [:]
        Set protectedOperations = []

        when:
        jobs.each { name, job ->
            List needs = job.needs == null ? [] : job.needs instanceof List ? job.needs : [job.needs]
            if (needs.any { outcomes[it] != 'success' }) outcomes[name] = 'skipped'
            else if (name == 'verify-release-ci') outcomes[name] = 'failure'
            else {
                outcomes[name] = 'success'
                if (job.environment || job.uses) protectedOperations << name
            }
        }

        then:
        outcomes['verify-release-ci'] == 'failure'
        outcomes['stage-pending-documentation'] == 'skipped'
        outcomes['publish'] == 'skipped'
        protectedOperations.empty
        jobs['verify-release-ci'].permissions == [contents: 'read', actions: 'read', checks: 'read']
        !jobs['verify-release-ci'].environment
        !jobs['verify-release-ci'].env
        !jobs['verify-release-ci'].toString().contains('secrets.')
        jobs['stage-pending-documentation'].uses == './.github/workflows/publish-pending-documentation.yml'
        jobs['stage-pending-documentation'].secrets == 'inherit'
        !jobs['stage-pending-documentation'].containsKey('if')
        !jobs.publish.containsKey('if')
    }

    def 'protected recheck is the final step before publication and carries no publication credentials'() {
        given:
        Map job = release.jobs.publish
        List steps = job.steps
        Map publication = steps.last()
        Map recheck = steps[-2]

        expect:
        recheck.name == 'Recheck exact-SHA CI and SonarCloud immediately before publication'
        recheck.run.contains('node "$RUNNER_TEMP/verify-release-ci.cjs" --recheck')
        recheck.run.contains('git rev-parse HEAD')
        recheck.run.contains('git status --porcelain=v1')
        recheck.env.keySet() == ['GH_TOKEN', 'EXPECTED_COMMIT', 'SOURCE_BRANCH', 'RELEASE_VERSION',
                                'CI_RUN_ID', 'CI_RUN_ATTEMPT', 'SONAR_CHECK_ID'] as Set
        !recheck.containsKey('if')
        !recheck.containsKey('continue-on-error')
        !job.env
        job.environment.name == '${{ needs.validate-release-input.outputs.environment }}'
        publication.run.startsWith('./gradlew publishCompleteKlumAstProduct')
        publication.env.KLUM_AST_RELEASE_AUTHORIZED == '${{ inputs.stage }}'
        job.permissions == [contents: 'read', actions: 'read', checks: 'read']
        steps[0].with.ref == '${{ github.sha }}'
        steps[1].run.contains('cp .github/scripts/verify-release-ci.cjs "$RUNNER_TEMP/verify-release-ci.cjs"')
        steps[2].with.ref == '${{ inputs.commit }}'
    }

    def 'reviewed CI execution contract preserves the single job commands actions and worker limits'() {
        given:
        Map ci = readWorkflow('ci.yml')
        List steps = ci.jobs.build.steps

        expect:
        ci.jobs.keySet() == ['build'] as Set
        !ci.jobs.build.strategy
        ci.jobs.build.'runs-on' == 'ubuntu-latest'
        steps.collect { it.name ?: it.uses } == ['actions/checkout@v7', 'Set up JDK 17', 'Setup Gradle',
                'Cache SonarCloud packages', 'Build with Gradle', 'Qualify annotation propagation artifacts',
                'Publish Test Report', 'Archive compatibility-lane evidence', 'Archive test classes']
        steps[1].with.'java-version'.trim() == '17'
        steps[1].uses == 'actions/setup-java@v5'
        steps[2].uses == 'gradle/actions/setup-gradle@v6'
        steps[3].uses == 'actions/cache@v6'
        steps[4].run.trim() == './gradlew check --scan sonar --info'
        steps[5].run.trim() == './gradlew :klum-ast:annotationPropagationArtifactsGroovy3 ' +
                ':klum-ast:annotationPropagationArtifactsGroovy4 :klum-ast:annotationPropagationArtifactsGroovy5 --no-parallel --max-workers=2'
        steps[6].uses == 'mikepenz/action-junit-report@v6'
        steps[7].uses == 'actions/upload-artifact@v7'
        steps[7].with.'if-no-files-found' == 'error'
        steps[8].if == 'failure()'
        !release.toString().contains('annotationPropagationArtifacts')
        !release.toString().contains('./gradlew check')
    }

    Map readWorkflow(String name) {
        new Yaml().load(new File(root, '.github/workflows/' + name).text) as Map
    }
}
