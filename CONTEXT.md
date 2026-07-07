# KlumAST — Context & Domain Glossary

This document summarizes the project's domain vocabulary and a short architectural overview so agents and contributors share the same language.

## Project summary

KlumAST provides an easy way to create a complete DSL for model classes using AST transformations. Primary goals are terseness with readability and strong IDE support for model classes.

## Architectural overview (high-level)

This repository is modularised into a set of focused subprojects. The main modules are:

- `klum-ast-annotations` — annotations used by DSL model classes (`@DSL`, `@Key`, etc.)
- `klum-ast` — core compile-time helpers and entrypoints for DSL creation
- `klum-ast-runtime` — runtime library used by generated/config DSL objects
- `klum-ast-jackson` — Jackson integration for KlumAST runtime objects
- `klum-ast-gradle-plugin` — Gradle plugin(s) shipped by this project
- `klum-ast-bean-validation`, `klum-ast-bom`, `code-coverage-report` — auxiliary modules

Agents should treat the repo as a single-context project: one `CONTEXT.md` at the root and `docs/adr/` for ADRs.

## Domain glossary

These terms are sourced from the project wiki and consolidated here. Use these canonical terms when writing issues, ADRs or code comments.

- DSL-Objects

  DSL Objects are annotated with `@DSL`. These are (potentially complex) objects enhanced by the transformation. They can either be keyed or unkeyed. "Keyed" means they have a designated field of type String decorated with the `@Key` annotation, acting as a key for this class. DSL classes are automatically made `Serializable`.

- Collections

  Collections are currently either `List` or `Map`. Map keys are always `String`. List values and Map values can either be simple types or DSL-Objects. Collections of Collections are currently not supported.

  A collection field has two name properties: the collection name and the element name. The collection name defaults to the name of the field; the element name is the name of the field minus any trailing `s`. For example, a field named `roles` defaults to collection name `roles` and element name `role`. If the field name does not end with an `s`, the field name is reused as both collection and element name (e.g. `information -> information | information`). Collection name and element name can be customized via the `@Field` annotation.

  Collections must be strongly typed using generics.

- Simple Values

  Everything else — simple values as well as more complex non-DSL objects — are considered simple values.

## Where to look next

- `wiki/Terms.md` — longer form notes and examples (source material for this glossary)
- `README.md` — project goals and examples
- `settings.gradle` — subproject/module list and boundaries

