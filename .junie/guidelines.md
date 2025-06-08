# KlumAST Development Guidelines

This document provides guidelines for developing and contributing to the KlumAST project.

## Build/Configuration Instructions

### Project Structure

KlumAST is a multi-module Gradle project with the following modules:

- **klum-ast-annotations**: Contains the annotations used by KlumAST
- **klum-ast**: Contains the AST transformations that implement the DSL functionality
- **klum-ast-runtime**: Contains runtime components needed by generated code
- **klum-ast-jackson**: Contains Jackson integration for KlumAST models
- **klum-ast-gradle-plugin**: Contains a Gradle plugin for KlumAST

### Building the Project

The project uses Gradle as its build system. It requires Java 11 or higher.

```bash
# Build the entire project
./gradlew build

# Build a specific module
./gradlew :klum-ast:build

# Publish to local Maven repository
./gradlew publishToMavenLocal
```

### Multi-Groovy Testing

The project is designed to be compatible with multiple Groovy versions (2.x, 3.x, and 4.x). The build system is configured to test against all supported Groovy versions:

```bash
# Run tests for all Groovy versions
./gradlew check

# Run tests for a specific Groovy version
./gradlew :klum-ast:groovy3Tests
./gradlew :klum-ast:groovy4Tests
```

## Testing Information

### Test Structure

Tests are written using Spock Framework. The project provides several base classes for testing:

- **AbstractDSLSpec**: Base class for testing DSL transformations
- **AbstractFileBasedDSLSpec**: Base class for testing with file-based scenarios
- **AbstractFolderBasedDSLSpec**: Base class for testing with folder-based scenarios

### Writing Tests

To write a test for a DSL transformation:

1. Create a new test class that extends `AbstractDSLSpec`
2. Use the `createClass` method to define a DSL class
3. Use the `clazz` property to access the created class
4. Create instances and test their behavior

Example:

```groovy
class MyTest extends AbstractDSLSpec {
    def "test DSL class creation"() {
        given: "A simple DSL class"
        createClass('''
            package demo

            import com.blackbuild.groovy.configdsl.transform.DSL

            @DSL
            class Person {
                String name
                int age
            }
        ''')

        when: "We create an instance using the DSL"
        def person = clazz.Create.With {
            name "John Doe"
            age 30
        }

        then: "The properties are set correctly"
        person.name == "John Doe"
        person.age == 30
    }
}
```

### Running Tests

Tests can be run using Gradle:

```bash
# Run all tests
./gradlew test

# Run a specific test
./gradlew test --tests com.blackbuild.groovy.configdsl.transform.MyTest

# Run tests for a specific Groovy version
./gradlew groovy3Tests
```

### Scenario-Based Testing

The project supports scenario-based testing, where real models are compiled and then assertions are made about them:

1. Create a directory under `src/test/scenarios`
2. Add Groovy files with DSL classes
3. Add an optional `assert.groovy` file with assertions

Directories are compiled in lexical order, and then the `assert.groovy` file is evaluated.

## Additional Development Information

### Code Style

- Use 4 spaces for indentation
- Follow standard Groovy coding conventions
- Include license header in all source files
- Use descriptive method and variable names

### AST Transformation Development

When developing AST transformations:

1. Use the `AbstractASTTransformation` class as a base
2. Implement the `visit` method to transform the AST
3. Use helper methods from `DslAstHelper` for common operations
4. Test thoroughly with different scenarios

### Documentation

- Document public APIs with Javadoc
- Update the wiki when adding new features
- Include examples in documentation

### Release Process

The project uses the nebula-release plugin for releases:

```bash
# Create a candidate release
./gradlew candidate

# Create a final release
./gradlew final
```

### Compatibility

- Maintain compatibility with Groovy 2.x, 3.x, and 4.x
- Test against all supported Groovy versions
- Document all changes in CHANGES.md, breaking changes addionally in Migration.md

## Example: Creating a Simple DSL

Here's a complete example of creating and using a simple DSL:

```groovy
// Define the DSL class
@DSL
class Person {
    String name
    int age
    List<String> hobbies
}

// Create an instance using the DSL
def person = Person.Create.With {
    name "John Doe"
    age 30
    hobby "Reading"
    hobby "Coding"
}

// Access the properties
assert person.name == "John Doe"
assert person.age == 30
assert person.hobbies == ["Reading", "Coding"]
```

This example demonstrates the basic usage of KlumAST to create a DSL for a simple model class.