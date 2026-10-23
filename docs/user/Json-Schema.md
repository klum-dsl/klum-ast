# JSON Schema

The optional `klum-ast-json-schema` tool generates self-contained JSON Schema Draft 2020-12 for
[KlumAST structured input](Structured-Input.md). It reads successfully compiled Schema/API directories and JARs as data.
It does not load Schema classes, run constructors or factories, evaluate defaults, invoke operations, or execute callbacks.
Core, runtime, and Jackson do not depend on this tool.

## Standalone generation

Build and install the command from a KlumAST checkout:

```shell
./gradlew :klum-ast-json-schema:installDist
klum-ast-json-schema/build/install/klum-ast-json-schema/bin/klum-ast-json-schema \
  example.Application build/application.schema.json api.jar schema.jar
```

Arguments are one explicit concrete Model **binary name**, an output filename, then the compiled directories/JARs containing
that Model and its Schema/API dependencies. For nested classes use the binary `$` spelling and quote it in the shell.
Schema artifacts are command arguments; do not add them to the tool's own Java classpath. The installed command needs
Java 17 and carries its ASM dependency; it needs no Groovy, KlumAST runtime, Jackson or parser on its execution classpath.
The Maven module JAR can also be invoked through its main class `com.blackbuild.klum.ast.schema.JsonSchemaGenerator`
with ASM on the tool classpath.

For example, compile this Schema normally before running the command above.
(See: `JsonSchemaGeneratorTest#'generates a schema for a compiled application without running its code'`.)

```groovy
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL

@DSL class Application {
    String host
    int port
    Map<String, String> labels
    @Builder.Method void endpoint(Map<String, String> settings) {
        host = settings.host
        port = Integer.parseInt(settings.port)
    }
}
// Structured input: [endpoint: [host: 'api', port: '8080'],
//                    labels: ['app.kubernetes.io/name': 'orders']]
```

The document has a root `$ref` and reusable `$defs` keyed by fully qualified generated Builder binary names.
`endpoint` is one String-valued map payload. Inherited admitted properties and operations are flattened before closing
the domain object. Concrete recursion and repeated child types reuse local references. No registry or external `$id`
is required. Literal map and instance keys retain punctuation, case and empty strings.

Successful generation reports the root, Builder, admitted source/contract coverage, compiler rejections, and unavailable
prose on standard error. Exact retained AnnoDocimal descriptions are included when attributable to the Model declaration
or selected public operation. Descriptions never change admission. Definitions, properties and diagnostics are sorted;
output contains no timestamps, machine paths or archive-order details.

## Supported shapes and limits

| Input | Projection |
| --- | --- |
| String, boolean, enum | Exact JSON type and enum constant field names. No null or coercion from strings. |
| byte, short, int, long and wrappers | Integer with signed target bounds; mathematical `1.0` is an integer. |
| BigInteger, BigDecimal | Integer/number; use a parser and validator that retain the required numeric precision. |
| float, double and wrappers | Number, as a structural approximation. Java conversion, rounding, overflow and representability remain runtime checks. |
| Admitted collection fields | Arrays with recursively admitted items; no inferred uniqueness or sorting assertion. |
| Admitted data maps | Objects with recursive value schemas. Map keys are literal data. |
| Concrete owned child / keyed child map | Reusable child reference / object with child-reference values. Child keys remain absent from the child's input surface. Fixed child keys are construction facts. |
| Admitted one-payload Builder operation | The declared scalar, enum, List or Map payload; no spreading into positional arguments or invented Model fields. |

All raw-input properties are optional. A structurally valid document can still fail operation execution, Java binding,
default processing, lifecycle, materialization, or completed-model validation. `@Required` does not become JSON Schema
`required`; defaults and arbitrary validation code are not translated. The example's `endpoint.port: 'not-an-integer'`
is structurally valid but its operation fails when parsing that string. This is not a Jackson schema or a complete
catalogue of Groovy DSL calls.

The tool projects current compiler admission and reports its rejected members. It does not invent inputs for LINK/context,
abstract/polymorphic targets, dynamic Class/Factory selectors, unsupported containers, multi-argument or zero-argument
operations, Templates or helper/lifecycle methods. Closed domain objects reject those names. Ignored members are absent;
renames and identity fallback come directly from compiler facts. Inherited name collisions omit all colliding declarations
and report every source, matching structured admission. An unknown or unprojectable **admitted** entry, missing required
binary, enum without declared constants, incompatible duplicate identity or malformed current carrier fails the whole generation with recompile/package
guidance. Recompile older Schemas; the old eleven-token carrier is deliberately unsupported.

## Integration boundary

This first production-code slice provides the standalone generator and ordinary Gradle `run`/`installDist` application
tasks. It does not yet provide the dedicated optional Schema Gradle plugin. Explicit multi-root task configuration,
tracked Schema/dependency inputs, up-to-date/configuration-cache/build-cache qualification, stale-root cleanup and
plugin output lifecycle are the immediate next slice. The command replaces its selected file only after successful
projection. On failure it exits nonzero and leaves an existing file unchanged; callers must treat that file as the last
successful result, not current validation evidence.

Consumers own copying, embedding, publication and editor association. Real ScHelm root/output acceptance remains
consumer-owned work before the overall generator delivery is complete. No ScHelm policy or release placement follows
from this standalone capability. Related: [#529](https://github.com/klum-dsl/klum-ast/issues/529);
[#932](https://github.com/klum-dsl/klum-ast/issues/932) independently owns the admitted language and runtime semantics.

Executable examples and binary-only acceptance coverage:
[`JsonSchemaGeneratorTest`](../../klum-ast-json-schema/src/test/groovy/com/blackbuild/klum/ast/schema/JsonSchemaGeneratorTest.groovy).
