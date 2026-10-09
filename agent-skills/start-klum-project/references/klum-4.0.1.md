# Frozen public authority — KlumAST 4.0.1 direct-schema bootstrap

This record selects the immutable `v4.0.1` release, annotated tag object `e77057fdf65b79c2c7f92eaaec051d5e8c393950`, and target commit `4d85ec2ed7e0a737d71b421af2c0cf597f6830e4`. Keep the release and its documentation together; do not substitute RC.23 or current `master` content.

| Contract | Versioned authority |
| --- | --- |
| Schema plugin | `com.blackbuild.klum-ast-schema` version `4.0.1`; [Gradle Plugin Portal coordinate](https://plugins.gradle.org/plugin/com.blackbuild.klum-ast-schema/4.0.1), [tagged Gradle plugin guide](https://github.com/klum-dsl/klum-ast/blob/v4.0.1/docs/user/Gradle-Plugins.md) (blob `01a206e155fbda607291ced659b1de52dc66115f`) |
| Schema imports and generated factory | [tagged Basics](https://github.com/klum-dsl/klum-ast/blob/v4.0.1/docs/user/Basics.md) (blob `15922d51e4cd24bd85bf3ce588ddf417383db256`) |
| Builder lifecycle | [tagged Builder-first migration](https://github.com/klum-dsl/klum-ast/blob/v4.0.1/docs/user/Builder-First-Migration.md) (blob `51e6acec3c25d937cc616c68ccaa7ba01ed92ad0`) |
| Validation and exception | [tagged Validation](https://github.com/klum-dsl/klum-ast/blob/v4.0.1/docs/user/Validation.md) (blob `cd15be7cf2b80d92b494a2834ccaaa9ea6ff43bb`) |
| Setup and test framework | [tagged Gradle onboarding](https://github.com/klum-dsl/klum-ast/blob/v4.0.1/docs/user/Gradle-Onboarding.md) (blob `68c82a7cf3d4bb26340a66a3333cb4cc9f4a6ba6`) |

Use these imports as applicable:

```groovy
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Key
import com.blackbuild.klum.ast.Required
import com.blackbuild.klum.ast.Validate
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
```

`@Required` is the concise presence rule. Give a failed rule an explicit message. A failed completed-model verification throws `KlumValidationException`; in a test, assert the message's stable, distinctive domain fragment. The complete exception text, path, issue ordering, and formatting are not the assertion contract. The `Create.With` callback configures a Builder; its return value is the completed Model. Owned children are created through the parent Builder. Compile first and follow a specific Builder diagnostic before changing lifecycle code.

**Runtime assumptions.** The release requires Java 17 or newer ([tagged README](https://github.com/klum-dsl/klum-ast/blob/v4.0.1/README.md), blob `2f0dda36b43955774ca290eeb5d8749b68ceb122`). It supports Groovy 3, 4, and 5; Groovy 3 is the baseline and the first-mission selection. The Schema plugin supplies matching Groovy and Spock dependencies, so a new adopter does not add a second test framework. The tagged repository wrapper uses Gradle **8.14.4** ([tagged wrapper properties](https://github.com/klum-dsl/klum-ast/blob/v4.0.1/gradle/wrapper/gradle-wrapper.properties), blob `29a0dd9af1159eb1159aea67393097cfecaad84b`); that is a reproducible tested choice, not a claim about the minimum supported Gradle version. Groovy 3 uses the ordinary classpath; a named Schema module requires Groovy 4 or 5 and the documented user-owned descriptor.
