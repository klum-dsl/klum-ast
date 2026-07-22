# KlumAST adopter skills (4.0 preview)

These are portable, task-oriented [Agent Skills](https://agentskills.io) for adopting KlumAST. They are a **4.0 preview pending field testing**. They are deliberately separate from this repository's `.agents/skills/`, which govern KlumAST maintainers and are not part of an adopter installation.

## Install selected skills

Copy only the directories you want into the skill-discovery directory used by your agent client. For example:

```shell
cp -R start-klum-project <your-agent-skill-directory>/
cp -R build-domain-first-schema <your-agent-skill-directory>/
cp -R author-klum-model <your-agent-skill-directory>/
cp -R feature-advisor <your-agent-skill-directory>/
cp -R build-target-contract-schema <your-agent-skill-directory>/
```

Each directory is a standard skill: its portable `SKILL.md` is the workflow. Agent clients choose their own discovery location and UI metadata, so keep client-specific installation instructions outside these directories. In Codex, use its configured skills directory; other clients should use their documented discovery location.

When installing, record the KlumAST release and the source tag or commit of this distribution in the project's adoption notes. `feature-advisor` uses those facts to determine whether a KlumAST or skill update is needed, recommended, or unnecessary.

Use documentation that matches the KlumAST version you are adopting. The linked `/4.0.0/` Pages tree is the immutable documentation contract for this version-matched skill distribution.

## Exercise the fixture

`fixtures/minimal-gradle-project` is a deliberately small direct-schema project. It has a representative configured model, validation, an executable test, and two intentionally improvable examples for `feature-advisor`. From this repository checkout, run:

```shell
./gradlew -p agent-skills/fixtures/minimal-gradle-project test
```

The fixture uses a composite build so it validates the checked-out KlumAST 4.0 sources. A real adopter project should use the released plugin version selected by `start-klum-project` instead.

`fixtures/domain-first-smart-home` is the companion Layer 3 journey for `build-domain-first-schema`. Its fixed floorplan Schema, separate registered Model script, and API-only `client-demo` show why Layer 3 is more expressive than a generic Schema/Model shape. Run it with:

```shell
./gradlew -p agent-skills/fixtures/domain-first-smart-home test
```

Use its field-test brief only to evaluate a real project; it does not turn the fixture into a generic scaffold or settle the Layer 3 policy questions tracked by #454.

`fixtures/helm-target-contract` is the target-contract companion: it creates readable Helm values for two services, compares their parsed meaning to representative and golden target artifacts, and records why a direct-schema authoring model is appropriate. Run its Groovy 3 baseline with:

```shell
./gradlew -p agent-skills/fixtures/helm-target-contract test
```

The portable `build-target-contract-schema` workflow points to this fixture and its later [field-test starting point](field-tests/target-contract-helm/).
