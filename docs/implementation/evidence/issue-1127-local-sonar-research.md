# #1127 — Local Sonar feedback: refreshed primary-source assessment

Date: 2026-11-28. Research only, based on `7a74f22977a69303f41a289489a5bc9d56244c02`.
No installation, authentication, scan, source upload, hook/configuration change or hosted qualification was performed.

## Recommendation and evidence boundary

Accepted D6 in [ADR 0035](../../adr/0035-proportionate-pr-qualification.md) selects narrow advisory, initially non-gating
changed-source prechecks without Sonar-equivalence claims. Implementation is not started; evaluate Sonar's agent integration separately.
SonarQube for IDE can help with Java, but its published IntelliJ and VS Code language tables do **not** list Groovy.
Vortex explicitly lists both Java and Groovy, making it a plausible later probe for KlumAST's Groovy test findings.
Language availability does not establish detection of the actual `groovydre:S8306` braces or `groovydre:S134` nesting
findings, correct TEST scope, active profile, or STANDARD/DEEP parity. Those remain untested.
Sources: [IntelliJ languages](https://docs.sonarsource.com/sonarqube-for-intellij/using/rules),
[VS Code languages](https://docs.sonarsource.com/sonarqube-for-vs-code/using/rules),
[Vortex languages and context](https://docs.sonarsource.com/agent-centric-development-cycle/inside-your-agent-the-agentic-loop/sonar-vortex-analysis).

The prior 2026-11-27 investigation is retained at
`/private/tmp/klum-1127-perf-sonar/docs/implementation/evidence/issue-1127-local-feedback/README.md` and `scanner-hook.md`.
It established the offline Groovy braces/JaCoCo seams and inspected tagged CLI source. This refresh reads those records,
rechecks current official documentation and reads the retained tagged source; it does not repeat their runtime probe.

## SonarLint / SonarQube for IDE

SonarLint is now documented as SonarQube for IDE. IntelliJ normally analyzes the open file and also permits selected-folder,
changed-file and project scans. This is IDE-hosted feedback, not a documented general headless CLI contract.
[Scan behavior](https://docs.sonarsource.com/sonarqube-for-intellij/using/scan-my-project).

Connected mode aligns supported rules, parameters, exclusions and accepted/false-positive state with the server; it
does not push locally found issues to that server. A synchronized quality-gate notification is a server result, not a
new local quality gate. [Connected-mode contract](https://docs.sonarsource.com/sonarqube-for-intellij/connect-your-ide/connected-mode).

Results can differ because of analyzer versions, build context, source/test classification and unsupported rules.
Some project-level, architecture and expensive rules are not executed locally; connected mode may display advanced
findings imported from a prior server analysis. IntelliJ Java analysis can need freshly built class files.
[Unsupported rules](https://docs.sonarsource.com/sonarqube-for-intellij/using/rules#unsupported-rules),
[analysis differences and Java bytecode](https://docs.sonarsource.com/sonarqube-for-intellij/resources/troubleshooting).

Consequently, an IDE's empty issue list provides no complete-project duplication, coverage, final-revision or hosted
quality-gate proof. Repeated semantic-literal feedback and full-project duplication metrics are different claims.

## Two different command-line products

**SonarScanner CLI (`sonar-scanner`)** produces CI/CD project analyses; it is distinct from the agent-facing CLI.
Current scanner documentation lists **8.4.0.16527**, dated 2026-11-09. This updates the prior investigation's bounded
8.3.1.16491 source inspection. The 8.4 entry point bootstraps the scanner engine, analyzes, reports status and exits;
neither this entry point nor the current CLI documentation establishes an offline agent post-analysis-hook API.
This is a bounded inspection, not an assertion about every scanner-engine extension.
[Scanner documentation](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/scanners/sonarscanner-cli),
[8.4 entry point](https://github.com/SonarSource/sonar-scanner-cli/blob/8.4.0.16527/src/main/java/org/sonarsource/scanner/cli/Main.java).

**SonarQube CLI (`sonar`)** is the terminal/agent integration. The official release page still marks
**1.13.1.18209**, released Nov 24, as latest when checked. `sonar analyze agentic` previews changed files through
server-side Vortex; it is not an offline SonarScanner replacement. The vendor explicitly retains full CI scanner
analysis as the branch/PR source of truth.
[Release](https://github.com/SonarSource/sonarqube-cli/releases/tag/1.13.1.18209),
[product distinction](https://docs.sonarsource.com/sonarqube-cli#sonarqube-cli-vs-sonarscanner-cli),
[local-change analysis](https://docs.sonarsource.com/sonarqube-cli/analysis/analyzing-local-changes).

Vortex reuses dependencies, compiled artifacts, type and build context from a previous long-lived-branch CI analysis.
Its current documented prerequisites include a Vortex subscription: Cloud annual Team/Enterprise, or Server 2026.5+
Enterprise/Data Center. Repository entitlement and freshness/suitability of the stored context are unverified here.
[Vortex model](https://docs.sonarsource.com/agent-centric-development-cycle/inside-your-agent-the-agentic-loop/sonar-vortex-analysis),
[prerequisites](https://docs.sonarsource.com/sonarqube-cli/analysis/analyzing-local-changes#prerequisites).

The tagged request includes file paths, **complete selected contents**, optional MAIN/TEST scope, project/organization,
branch and depth. It has no test-execution or coverage-report input. Its response is issues/errors, not a complete
coverage/duplication metric certificate.
[Tagged wire contract](https://github.com/SonarSource/sonarqube-cli/blob/1.13.1.18209/src/commands/analyze/sqaa-wire-types.ts).
The CLI requires a user token; its `SONARQUBE_CLI_*` variables differ from scanner `SONAR_TOKEN`. No token was read.
[Authentication variables](https://docs.sonarsource.com/sonarqube-cli/using-sonarqube-cli/environment-variables).

## Agent hooks are feedback, not an independent qualification authority

`sonar integrate codex` installs machine-wide Codex hooks/configuration. The documented `PostToolUse` hook matches
`apply_patch` and runs STANDARD; the `Stop` hook runs DEEP and can ask for fixes three times before allowing a stop
without analysis. Hook failures allow stopping; missing project context can skip analysis. Host hook delivery/trust
and compatibility with this Codex Desktop session were not exercised.
[Current Codex integration](https://docs.sonarsource.com/sonarqube-cli/integrations/codex).

Tagged source confirms the post-edit handler analyzes the Git change set with forced STANDARD and can return without
findings output on errors/missing context. Silence cannot mean success.
[Post-edit handler](https://github.com/SonarSource/sonarqube-cli/blob/1.13.1.18209/src/commands/hook/codex-post-tool-use.ts).
Explicit JSON output identifies analyzed files, ignored files/reasons, failures, skipped files, depth and global errors;
those fields must be inspected, not only an empty issue array.
[Report schema](https://github.com/SonarSource/sonarqube-cli/blob/1.13.1.18209/src/commands/analyze/sqaa-display-json.ts).
Documented command exits distinguish 0 (success), 51 (findings), 1 (execution failure), 2 (invalid options).
[Exit codes](https://docs.sonarsource.com/sonarqube-cli/using-sonarqube-cli/exit-codes).

## Smallest useful later experiment

After maintainer decisions on source transfer, entitlement and credentials, use an explicit-file JSON invocation under
isolated CLI state; do not install global hooks for the evaluation:

```shell
sonar analyze agentic --file <retained-groovy-test> --depth STANDARD --format json
```

Compare the actual pre-fix/fixed #1124 braces pair and #1136 braces/nesting pair; retain source hashes, CLI/server/context
identity, MAIN/TEST treatment, completeness, issues, exit and timing. Try DEEP only where needed. A clean but incomplete
result, stale context or failure to reproduce a known rule is a limitation, not a successful parity test.
This is proposed acceptance work, **not executed evidence or an implementation slice started by this report**.

Coverage remains a separate JaCoCo/test concern. SonarQube Cloud imports coverage produced by third-party tools rather
than executing tests or calculating it itself; local Vortex/IDE feedback cannot fill missing coverage evidence.
[Coverage import model](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/overview).
Keep previews separate from shared-project scanner uploads and from the authentic hosted Sonar check. The separate
[#903 correlation investigation](../issue-903-sonar-feasibility.md) remains relevant: same SHA/timestamps do not prove
which CI attempt produced a Sonar analysis.

Validation: read-only primary-document refresh on 2026-11-28; tagged CLI source 1.13.1.18209 inspected locally; scanner
8.4 entry point read through GitHub's contents API. No local/hosted analyzer result or measured savings is claimed.
