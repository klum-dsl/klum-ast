# Feature Triage

Use-case evidence helps prioritize feature issues and keeps a useful request from growing into a kitchen-sink feature.
Apply this policy to newly triaged enhancements. Do not revisit already-triaged issues solely to add this classification;
add it when such an issue is revisited for another reason.

## Use-case record

Record the following when the issue or discussion provides enough evidence:

- **Primary use case:** the actor, task, and desired outcome that justify the smallest coherent feature.
- **Need horizon:** choose the best-supported classification:
  - **Immediate:** needed or highly desirable for work currently being performed.
  - **Future:** tied to an identified project or planned task that is not current yet.
  - **Speculative:** a plausible need without an identified project or task.
  - **Nice-to-have:** primarily an ergonomic or appealing improvement without a concrete need.
- **Workaround:** `none`, `viable but costly`, `viable`, or `unknown`, followed by a concise description when known.
- **Secondary angle:** an optional adjacent benefit or additional use case.

A viable workaround may mean the feature is syntactic sugar. That is not a negative classification for KlumAST; record how
awkward, repetitive, or error-prone the workaround is so its ergonomic value remains visible.

Use `unknown` when the evidence is missing. An unknown or absent classification is not a hard blocker for triage,
normalization, or implementation readiness.

## Scope and priority

Derive the minimum feature scope and acceptance boundary from the primary use case. Treat the secondary angle as context,
not implicit scope; include it only when independently justified or record it as out of scope or follow-up work.

Use the need horizon and workaround as priority evidence, not as an automatic ranking. Immediate needs and cases without a
viable workaround usually deserve more weight, while reach, compatibility, risk, implementation cost, and release goals
still matter. A small broadly useful ergonomic improvement may outrank a narrow immediate request.

Mini-features may skip the full record when they are a genuine quick win: the behavior and scope are obvious, the change
adds no meaningful implementation, API, compatibility, or maintenance complexity, and it does not create a precedent that
needs design work. State the quick-win rationale in the triage result.
