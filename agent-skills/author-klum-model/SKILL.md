---
name: author-klum-model
description: Author a configured KlumAST model. Use when a Model Writer wants to turn a representative configuration into a KlumAST model, add validation, construct it through the generated DSL, or create an executable model test.
---

# Author a KlumAST model

Use the version-matched KlumAST documentation as the authority. Keep the configured model and its test small enough to explain one real configuration decision.

1. Read the selected project shape and identify the Model Writer's inputs, the root DSL Object, meaningful keys, owned children, and values that must be validated. Do not make an existing completed model mutable; configuration belongs in one root factory lifecycle.
2. Create or adapt `@DSL` Schema types. Use `@Key` only for stable domain identity. Declare collections with supported interfaces, and create owned children through their parent Builder callback.
3. Add validation that represents a real invariant. Explain the invalid input and expected diagnostic before encoding it. Prefer a targeted validation callback or constraint over a generic rewrite.
4. Write a representative configured model with `Root.Create.With { ... }`. It returns a completed model after Builder configuration, materialization, and validation. Do not call a child `Create.With` inside a parent callback; use the generated child method instead.
5. Add an executable test that asserts the completed model's important values and one validation behavior. Run the project's normal Gradle test task.
6. If IntelliJ completion is useful, refresh the generated source mirrors after Schema changes. Treat those mirrors as completion metadata; use the compiled generated API, not mirror files, as the build contract.

Read the matching [Basics](https://klum-dsl.github.io/klum-ast/4.0.0/Basics/), [Validation](https://klum-dsl.github.io/klum-ast/4.0.0/Validation/), [Default Values](https://klum-dsl.github.io/klum-ast/4.0.0/Default-Values/), and [Builder-first migration](https://klum-dsl.github.io/klum-ast/4.0.0/Builder-First-Migration/) guidance before changing a lifecycle or construction boundary.
