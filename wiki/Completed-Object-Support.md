# Completed Object Support

Completed DSL Objects are immutable results of KlumAST construction. Client code may still need to inspect where an
object came from, navigate its ownership structure, or traverse its composed children. `KlumObjectSupport` is the stable,
Java-first entry point for those operations without exposing the object's internal Model companion.

The facade accepts either a completed root object or any completed DSL Object in its subtree:

```java
import com.blackbuild.klum.ast.util.KlumObjectSupport;
import com.blackbuild.klum.ast.validation.KlumValidationResult;
import java.util.List;
import java.util.Map;

KlumObjectSupport<Deployment> support = KlumObjectSupport.of(deployment);

Deployment object = support.getObject();
String breadcrumbPath = support.getBreadcrumbPath();
String modelPath = support.getModelPath();

KlumObjectSupport.Structure<Deployment> structure = support.getStructure();
Map<String, Service> services = structure.findAll(Service.class);

KlumObjectSupport.Validation<Deployment> validation = support.getValidation();
KlumValidationResult result = validation.getResult();
List<KlumValidationResult> subtreeResults = validation.getResults();
```

The Javadoc for `KlumObjectSupport` and its nested `Structure` and `Validation` helpers is the source of truth for complete
signatures, overloads, return types, and exceptional cases.

## Provenance and model paths

`getBreadcrumbPath()` reports construction provenance: the Builder/factory call path through which the object was
created. `getModelPath()` reports the object's structural location in the completed model. These answer different
questions and are not interchangeable.

## Ownership, paths, and traversal

`getStructure()` groups operations that inspect the completed composition graph:

- direct and single-owner lookup, owner hierarchy, and nearest ancestors by type;
- full paths from the composition root and relative paths from one object to an owned descendant; and
- typed `findAll` and `visit` traversal.

Traversal follows composed DSL values only. Owner and `LINK` edges are not followed, and identity-based cycle protection
ensures that object graphs remain safe even when DSL types override `equals`.

## Stored validation

`getValidation().getResult()` returns the result already stored for the target object. `getResults()` reads all stored
results from the target's owned composition subtree. `verify()` uses the configured failure level, while `verify(level)`
uses the supplied level. These operations only inspect lifecycle results: they do not execute `InstanceValidator`s, create
results, or mutate recorded issues.

The facade may also start at a subtree:

```java
import java.util.Optional;

KlumObjectSupport<Service> serviceSupport = KlumObjectSupport.of(service);

Optional<Object> owner = serviceSupport.getStructure().getSingleOwner();
String pathFromDeployment = support.getStructure().getRelativePath(service);
```

## Compatibility APIs

`StructureUtil` remains as a deprecated adapter for existing callers. New completed-object code should use
`KlumObjectSupport` directly. `KlumModelProxy` and its raw metadata are internal implementation details and are not a
supported client extension API.

The former `Validator.getValidationResult`, `getValidationResultsFromStructure`, and `verifyStructure` readers remain as
deprecated adapters. New completed-object code uses `KlumObjectSupport.getValidation()` as described in [[Validation]].
