# Spring configuration volumes and completed-model consumption: #481 / #528

Date: 2026-10-21. Status: **provisional design/triage assessment, not an accepted architecture or implementation plan**.

Assessment source baseline: KlumAST `b3fcf827e03787f6341d61c0a38b76653cc01234`.
Publication rebase/check baseline: `be36b45198be3fe98ad2a465c8cc21ea6e46b6e8`. The subsequently delivered
structured-input probe remains separate evidence and supplies no adopted language/API decision to this assessment.
Live issue bodies and all returned comments were read for
[#481](https://github.com/klum-dsl/klum-ast/issues/481),
[#528](https://github.com/klum-dsl/klum-ast/issues/528),
[#829](https://github.com/klum-dsl/klum-ast/issues/829),
[#529](https://github.com/klum-dsl/klum-ast/issues/529), and
[#932](https://github.com/klum-dsl/klum-ast/issues/932).
The structured-input direction was also read at its reviewed head
[`ade8b68a`](https://github.com/klum-dsl/klum-ast/tree/ade8b68a0cc341fcccd1c4c86c06d75f6a9e5758/docs/adr/0031-structured-schema-input-artifacts.md);
[PR #931](https://github.com/klum-dsl/klum-ast/pull/931) was verified merged, although issue text still called it draft.
Its ADR remains a proposed revision with unresolved language decisions. This assessment neither executes nor selects
the shared #932/#529 probe's syntax, admission rules, polymorphic aliases, or reuse seams.

This single local note is justified as a reviewable answer to the two issues' tracer gates. It changes no user guide,
release promise, milestone, issue, public API, dependency, build, or executable fixture. Examples below are design
illustrations grounded in existing APIs; no Spring or deployment acceptance run is claimed.

## Recommendation and user journey

Keep **two separately deliverable capabilities**, with an optional shared example domain:

| Capability | Smallest user outcome | Primary owner | Present triage / recommendation |
| --- | --- | --- | --- |
| #481 configuration volume | A common chart renders non-secret Spring files, and an otherwise unchanged application loads them | Chart/application projection owner; KlumAST owns the explanatory contract until ownership transfer | Open, milestone 4.2; retain provisionally, gated by deployment tracer |
| #528 completed-model consumption | Application beans use selected completed domain values, preserving helpers and identity | Application integration owner initially; any reusable KlumAST adapter needs a later explicit decision | Open, milestone 4.2; retain tracer only, defer module commitment and full product delivery unless evidence bounds them |
| #829 root recipe overlay | Reuse a compiled DelegatingScript directly with explicit per-invocation context | KlumAST factory API | Open, milestone 4.2; context only, not a prerequisite for either baseline |

The common journey is authoring a Schema/Model, completing and validating it, then adapting it to an application.
For #481 the downstream boundary is files and Spring properties. For #528 it is live domain objects in one JVM.
These are useful deployment alternatives, not mandatory consecutive stages or one combined acceptance gate.
Neither must wait for the other to provide its own accurately named capability. The existing issues reserve the broader
“Spring ready” claim for both; this report does not revise that release condition. [#481](https://github.com/klum-dsl/klum-ast/issues/481),
[#528](https://github.com/klum-dsl/klum-ast/issues/528).

Feature triage: #481's primary actor is a chart/application integrator serving related Boot applications; its recorded
workaround is viable but costly bespoke chart wiring. #528's actor is an application developer who needs domain helpers
and graph identity instead of mirrored properties. Both are identified future integration needs, not evidence of a
current production blocker. For #528, explicit consumer bean wiring is an additional viable workaround to measure;
the issue's existing YAML-or-mirroring description is not exhaustive. Annotation convenience is a secondary ergonomic
angle and must not silently determine the minimum feature.

## Deployment responsibilities and proposed topology

The following is a bounded deployment recommendation, not a general Helm or configuration framework:

```text
reviewed application recipe + trusted Schema + non-secret deployment inputs
                 -> init-container renderer -> shared output volume
                                              application.yaml
                                              application-production.yaml
                                                       |
                          Boot config-data loading <----+
                          + separate runtime secret tree
                          + operational property overrides
```

| Responsibility | Owner and boundary |
| --- | --- |
| Domain defaults, application/environment selection, composition and validation | Schema and application-owned assembly code; complete the intended graph before rendering |
| Mapping domain values into application/Spring namespaces | Application-specific projection; not a generic serializer promise or an inferred mapping from historical key names |
| ConfigMap input and generated files | Non-secret content only; the chart may mount reviewed recipe/data separately from renderer output |
| Secret material and rotation policy | Existing secret provider/operator and runtime mount or environment path; renderer receives no runtime secret mount or credentials |
| Init image, privileges, service account, mounts, egress and resources | Deployer; identify who can alter input and what a compromised renderer can reach |
| Profile, file lookup and Spring property precedence | Spring Boot and explicit operator settings |
| Schema recipe ordering, replacement, null/list/map semantics | Existing explicit factories/Templates or application assembly; future general composition belongs to #304 |

Use a writable shared volume such as `emptyDir` for the init container, mounted read-only by the application.
Keep input, output and runtime secret mounts distinct. Finish rendering and validation before successful init exit;
render/validation failure prevents application startup. Document restart/re-render behavior: an init render is a
startup snapshot, not live reconciliation. Helm rendering can verify the manifest wiring offline; actual init ordering,
permissions and mounts require a later runtime deployment check before claiming deployment proof. These are proposed
fixture requirements, not observations of a deployed chart.

Illustrative operator settings, supplied at process bootstrap:

```text
SPRING_CONFIG_ADDITIONAL_LOCATION=file:/etc/acme/generated/
SPRING_PROFILES_ACTIVE=production
SPRING_CONFIG_IMPORT=configtree:/run/secrets/acme/
```

Boot's additional location preserves default locations; `spring.config.location` replaces them. Directory locations
need a trailing slash. Required locations/imports fail when missing; `optional:` changes that contract. Profile-specific
files and higher-precedence environment/system/command-line values follow Boot's ordering. Pin and test the intended
combination rather than inventing a second precedence algorithm.
[Boot configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html).

For example, a runtime-only tree entry `orders.db.password` supplies that named secret. Neither output YAML file
contains it. Existing External Secrets tooling may populate the mount; no Spring Cloud Kubernetes client or secret
API access is needed for this pattern. Activate profiles through non-profile-specific bootstrap settings.
[Boot configuration trees](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.configtree),
[Boot profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html).

Spring profiles do not select a Klum deployment Environment or recipe automatically. For #481 the renderer explicitly
selects the required domain input and emits the agreed base/profile files; Boot subsequently selects the active profile.
Avoid using one ambiguous name for those two decisions. Generated YAML is target output and does not depend on a YAML
input interpreter or JSON Schema generation. [Jackson's asymmetric export/import contract](../../adr/0009-jackson-interoperability.md).

The September trust decisions on both issues take precedence over an unqualified “ConfigMap Groovy” route: a mount is
not a trust designation. Initial code must be reviewed application-owned code, or explicitly mapped non-secret data
through a trusted Schema. Less-trusted script authors require #261's isolated-worker boundary; transfer into host-owned
domain objects remains unproven. An init container offers a process boundary but does not by itself prove #261's
credential, network, resource or result-transfer contract.
[#481 trust decision](https://github.com/klum-dsl/klum-ast/issues/481#issuecomment-5553495255),
[#528 trust decision](https://github.com/klum-dsl/klum-ast/issues/528#issuecomment-5553496818).

## Does completed-model consumption require a KlumAST module?

**No demonstrated core or module requirement for the minimum case.** A root factory already returns a completed,
validated domain object. Explicit consumer `@Bean` methods can publish the root and selected members, with ordinary
dependency parameters, qualifiers and constructor injection. This is an inference from the existing contracts, not an
executed Spring compatibility claim. [Root factory contract](../../adr/0004-asbuilder-composition-protocol.md),
[Spring bean definitions](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html).

Illustrative Java client wiring; `OrderApplication`, `DatabaseConfig` and `OrdersClient` are application-owned types:

```java
@Configuration(proxyBeanMethods = false)
class OrderModelConfiguration {
    @Bean(destroyMethod = "")
    OrderApplication orderModel() {
        return OrderApplication.Create.FromClasspath();
    }

    @Bean(destroyMethod = "")
    DatabaseConfig ordersDatabase(OrderApplication root) {
        return root.getDatabase();
    }

    @Bean
    OrdersClient ordersClient(DatabaseConfig database) {
        return new OrdersClient(database);
    }
}
```

The same consumer operations in Groovy:

```groovy
@Bean(destroyMethod = '')
OrderApplication orderModel() { OrderApplication.Create.FromClasspath() }

@Bean(destroyMethod = '')
DatabaseConfig ordersDatabase(OrderApplication root) { root.database }

@Bean
OrdersClient ordersClient(DatabaseConfig database) { new OrdersClient(database) }
```

Only explicitly selected values become beans. A context-local singleton root bean is sufficient; no JVM-global cache,
graph scan, selector parser or registry is needed. Require eager construction before application readiness; global lazy
initialization must not defer validation into request handling. The root may instead delegate to an application-owned
context-aware factory as described below. A mounted Groovy file needs explicit loader code; it is not Boot config data.

For Layer 3, an API-only service must consume abstract Domain API types and meaningful `@Cluster` projections.
The assembly/configuration class may deliberately be Schema-specific. Do not make every Spring application adopt an
API layer when its concrete Schema is already the intended client contract. [Layer 3](../../user/Layer3.md).

Identity qualification matters: a bean post-processor can wrap returned objects. The fixture must assert identity at
the actual injection point and declare that model-value beans are not proxied or rebound. Disabling inferred destruction
on plain model beans does not disable all initialization/post-processing. Keep resource ownership on downstream service
beans; avoid initializing or destroying one shared graph object through several bean definitions. Direct
`registerSingleton` skips initialization/destruction callbacks and is not a substitute for normal resource bean lifecycle.
[Container extension points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html),
[SingletonBeanRegistry contract](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/SingletonBeanRegistry.html).

The actual #528 proposal is larger than this baseline: custom field/setter annotations, selectors, source diagnostics,
one-root orchestration and per-context thresholds. Consumer wiring does not deliver that promised adapter. Retain the
issue as a tracer and require a maintainer decision before replacing or narrowing its desired behavior. Extract a small
optional adapter only if independent consumers repeat meaningful wiring or require the custom injection semantics and
the fixture establishes an affordable supported Spring/Boot version set. Keep all Spring dependencies out of core.

### Conventional bean configuration after removing YAML

The July #528 comment is a mandatory counterexample: removing `myapp.connections.db.url` plus its placeholder mapping
does not make a conventional Spring bean understand a domain subtree. Prove an explicit application-owned `DataSource`
factory that reads the completed database value, sets the pool's JDBC URL and other chosen properties, and obtains its
password from a separate runtime secret source. Expose the pool as a normal Spring-managed bean and verify close on
context shutdown. A local embedded database is enough; no production database is needed.
[#528 conventional-bean requirement](https://github.com/klum-dsl/klum-ast/issues/528#issuecomment-5078058336).

This mapping may create a third-party bean while direct Klum injection still preserves exact domain-object identity.
Document any replaced Boot auto-configuration and which settings remain operator-overridable. A Spring property cannot
change a completed Model: apply selected, typed operational inputs before construction, or explicitly use them in the
downstream bean adapter. Do not flatten the model, infer `spring.*` namespaces, or suggest every former YAML override
continues to work. Klum-Wrap is a possible separate mapping owner, not an established dependency or automatic solution.

Source loading also needs precise wording: current `Create.From(File)` reads text and compiles a DelegatingScript at
runtime, while a compiled `Create.FromClasspath()` recipe avoids that source-compilation step. #528's “customers do
not compile Groovy during deployment” cannot mean that its mounted-file route performs no runtime compilation.
Choose the compiled route for the smallest native fixture, or explicitly qualify runtime compilation and trusted
source handling before claiming file support. [FactoryHelper source-loading paths](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/FactoryHelper.java).

## Explicit Environment context using existing lifecycle semantics

Prefer an application-owned factory taking a completed Environment and an explicitly typed Builder closure. Existing
`With(environment: selectedEnvironment, applicationInput)` forwarding and a reusable DelegatingScript Template allow
one application lifecycle without a new root-overlay API. For recipes that themselves set `environment`, the factory
must explicitly enforce the intended context/conflict policy; do not infer an unconditional context-wins rule.
[Factory forwarding](../../user/Factory-Classes.md#forwarding-builder-closures),
[Template loading](../../user/Templates.md#loading-templates-from-the-classpath),
[delivered #833](https://github.com/klum-dsl/klum-ast/issues/833).

An `ApplicationHolder` is another application-owned Schema composition when children need an Owner-based context.
It is not a KlumAST service, Spring holder singleton, or ambient active-root accessor. This schematic Groovy example
uses existing relationships and graph phases; imports/setup are abbreviated:

```groovy
@DSL class DeploymentEnvironment { String endpoint }

@DSL class ApplicationHolder {
    @Required @Field(FieldType.LINK) DeploymentEnvironment environment
    OrderApplication application
}

@DSL class OrderApplication {
    @Owner ApplicationHolder holder
    @Required @Field(FieldType.LINK)
    @LinkTo(provider = { holder }, field = 'environment')
    DeploymentEnvironment environment
    String endpoint

    @PostTree void deriveEndpoint() { endpoint ?= environment.endpoint }
}

// Complete external context before opening the holder's Construction session.
def selected = DeploymentEnvironment.Create.With(endpoint: 'https://orders.example.test')
def completed = ApplicationHolder.Create.With {
    environment selected
    application { }
}
assert completed.application.environment.is(selected)
```

During configuration the holder and its owned application are active Builders. OWNER(15) establishes the backlink,
AUTO_LINK(20) binds the external Environment, and POST_TREE(30) may read its completed values before INSTANTIATE(40)
materializes the owned graph. VALIDATE(50) and VERIFY(80) then evaluate completed results. Owner-based context is not
available in `PostCreate`, `PostApply` or phases before Owner/AutoLink; a requirement there needs explicit factory input,
not an earlier invented lifecycle. The external Environment remains aggregation and is not re-owned, mutated or
revalidated as part of the holder's composition. [Model phases](../../user/Model-Phases.md#phase-details),
[completed LINK reads and their static-access limits](../../user/Behind-the-Curtain.md#builder-backed-linkto-providers).

Inside this active holder session, `OrderApplication.Create.AsBuilder().From(CompiledOrderRecipe)` may produce an
owned child Builder for attachment instead of the empty child closure, **only for a DelegatingScript recipe**.
It runs no independent graph materialization. An ordinary Script returning a completed root cannot participate as an
owned child. Do not obtain a Builder outside the session, retain it in Spring, access internal session state, or call
a root factory to create owned composition. [AsBuilder contract](../../adr/0004-asbuilder-composition-protocol.md).

The holder changes the actual composition root: the application's structural path becomes `<root>.application`.
Returning that child does not detach its Owner or stop retention of the holder/context. If that is unwanted, use the
direct Environment-bound application factory. This pattern provides no security partition or detached-copy guarantee.
No generic context injection or reference registry is involved. #829 remains a possible ergonomic alternative for its
separately decided compiled-root case; this report proposes none of its overloads or ordering decisions.

### Validation policy gap to retain

Current [KlumValidationReporter.getFailLevel](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumValidationReporter.java)
reads `klum.validation.failOnLevel` from JVM system properties, and normal Verify uses that threshold.
[KlumObjectSupport.Validation.verify(level)](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumObjectSupport.java)
can subsequently check stored composition results at an explicit context-local threshold without rerunning validators.
It can impose a stricter policy after successful construction, but cannot recover a root rejected earlier by the
global threshold. Thus #528's general per-context construction/verification policy is not already satisfied. Do not
temporarily change a system property per Spring context; concurrent contexts would race. A future tracer must either
accept the documented existing baseline or return this precise policy gap for separate authorization, without adding
a new lifecycle mechanism here.

## Dependency and release placement

| Work | Can proceed independently | Conditional dependency / stopping point |
| --- | --- | --- |
| #481 trusted code to non-secret files | Existing Schema construction, projection, chart wiring and Boot smoke | Executable chart/Boot tracer, trust/mount owners and delivery location; less-trusted execution depends on #261 |
| #528 explicit consumer bean fixture | Existing completed root factories, factory/Template context, typed bean definitions | Identity, conventional-bean mapping, startup/diagnostic/lifecycle evidence and explicit version pins |
| Generic parsed operation input | No new grammar is needed by the two baselines | #932 probe/decisions before promising its language or dispatch |
| Generated editor JSON Schema | Optional authoring enhancement | #529 generator evidence sharing #932's admission/naming; not runtime completion validation |
| Existing managed Jackson input | Its current supported contract can be used explicitly | ADR 0009 mapping review and all four distinct modes; no assumption of the future interpreter's operation language |
| General multi-source composition | Baselines use one source or deliberate application assembly | #304 only if general ordered-layer policy becomes a real requirement |
| Root DelegatingScript overlays | Not needed by the two baselines | #829 design remains separately owned; regular Script boundary is unchanged |

Recommend #481 remain a **provisional 4.2 documentation/deployment candidate**, with no Spring module dependency.
Recommend #528 remain a **conditional 4.2 consumer tracer**, with product module/annotations later unless demonstrated
demand, bounded cost, supported versions and validation policy are settled in time. Prefer accurate individual claims
over making both capabilities a 4.2 commitment. No milestone, issue classification or release text is changed here.
#529 and #932 are currently untargeted; making either a blanket prerequisite would contradict their independent-track
boundary. [Current structured-input direction](https://github.com/klum-dsl/klum-ast/blob/ade8b68a0cc341fcccd1c4c86c06d75f6a9e5758/docs/adr/0031-structured-schema-input-artifacts.md).

## Minimum acceptance fixture, proposed and not executed

Use one tiny order domain and two independently invokable scenarios. A direct-schema first fixture is sufficient;
if claiming Layer 3, add abstract API types, one meaningful Cluster and an independently compiled API-only Java client.
Pin exact KlumAST, Spring Boot/BOM, Java, Gradle, Groovy/Spock and chart/Helm versions. Let Boot's BOM choose its matching
Framework; independently observed reference-document versions are not a compatibility matrix. Spring pages observed
here identify Boot 4.1.1 and Framework 7.0.9, not an executed supported combination.

| Scenario | Required evidence |
| --- | --- |
| Volume/bootstrap (#481) | Render semantic golden base/profile files with non-secret values; offline `helm template` assertions for init/output/secret mount separation, read-only application output mount, bootstrap environment and failure wiring |
| Actual Boot startup (#481) | Start against temporary output/secret directories; assert base value, production replacement, a separate runtime secret, environment and command-line overrides; missing required file/secret and invalid render fail before service readiness |
| Native consumption (#528) | Eagerly construct one root per context, publish only explicit selections, assert reference identity at two real injection points, domain helper behavior and absence of Builder injection; a second context constructs an independent root |
| Conventional bean (#528) | Remove old YAML namespace/placeholder mapping; explicit model-to-pool configuration succeeds, a query works, runtime secret is separate, chosen operational override is effective, pool closes once |
| Context/lifecycle | Supply two different completed Environments on repeated construction; each application's graph lifecycle runs once and preserves its selected external LINK identity; no context leakage and no intermediate unbound completed application |
| Failures/diagnostics | Missing selection, wrong source kind, malformed trusted recipe, validation failure and missing secret stop startup; source/root/explicit selection/injection site are actionable, with sentinel secret absent from output and retained diagnostics |
| Policy limits | Record default global Verify behavior, demonstrate stricter stored-result verification if used, and retain lower-than-global per-context policy as unsupported until decided; no process-global mutation workaround |

Do not retrofit existing Catwalk 4.0.1 examples with a silent version bump. Catwalk at
[`97a787ec`](https://github.com/klum-dsl/klum-catwalk/tree/97a787ecc2696569327d42f6cf5d59999c5503ac)
has independent role-leaf projects and explicit binary handoffs. Its
[Helm showcase](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/showcases/helm-target-contract/README.md)
proves Podinfo values/rendered-manifest semantics, not Spring startup, init volumes or native bean consumption. Its
[smart-home showcase](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/showcases/domain-first-smart-home/README.md)
provides the API/Schema/client handoff precedent. Neither closes these tracer gates.

Schelm is a concrete use-case owner in the public issues and
[existing completed-LINK qualification](../issue-867-lp1-evidence.md#external-schelm-qualification-completed-link-reads).
Its current chart ownership and a Spring acceptance run still need an acknowledged owner. Adopt a consumer-owned
fixture in Catwalk or a real Helm project through that repository's separate authorization; this assessment creates no
cross-repository task or changes. Promote a fixture to release evidence only by the explicit baseline process; an
ordinary showcase is not automatically a new KlumAST PR gate. [ADR 0019](../../adr/0019-published-showcase-consumer-verification.md).

## Maintainer decisions and overengineering audit

Decisions to return to the Hive, in priority order:

1. Accept a consumer-owned `@Bean` fixture as #528's first experiment, or retain custom selector/field/setter injection
   as a required initial product capability? This is a scope decision against the existing issue, not a silent rewrite.
2. Name the #481 chart/projection owner and the fixture repository; explicitly acknowledge any transfer to Schelm.
3. Select one supported Boot/JDK/Groovy/Klum set, trusted source authors and startup source route. State whether the
   initial native route uses compiled classpath code or reviewed mounted source, and what compromise can affect.
4. Decide the conventional bean's mapping/override/resource owner, whether model beans may be post-processed, and
   whether general per-context validation policy is required immediately or deferred honestly.
5. After fixture evidence, confirm independent 4.2 scope, documentation placement and the existing broader readiness
   claim; defer the optional module if its benefit and maintenance cost remain unproven.

Keep the first experiment within these limits:

- **Reuse:** root factories, typed application factories, Templates, AsBuilder, LINK/Owner, normal graph phases,
  completed validation readers, Spring bean definitions, config data and existing secret infrastructure.
- **Application-specific by design:** Environment/provider choice, Schema composition, domain-to-property or
  domain-to-third-party-bean mapping, resource ownership and profile-to-recipe mapping if one is actually needed.
- **Do not introduce:** Spring in core, a YAML parser, universal property projection, automatic graph bean publication,
  selector expression language, object-reference protocol, subtype discovery, generic injection/registry, global root
  cache, new lifecycle/session machinery, or a general Helm framework.
- **Extraction trigger:** concrete repeated consumer code or required injection behavior, with an executable fixture
  showing what a small optional adapter removes and its supported compatibility cost. Annotation symmetry alone is
  insufficient evidence.

No implementation readiness, Spring compatibility success or deployment success is inferred from this note. The bounded
assessment is complete; issue delivery and all recommendations remain pending maintainer/Hive reconciliation.
