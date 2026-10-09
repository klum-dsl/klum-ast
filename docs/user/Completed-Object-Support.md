# Completed Object Support

Completed DSL Objects are immutable results of KlumAST construction. Client code may still need to inspect where an
object came from, navigate its ownership structure, or traverse its composed children. `KlumObjectSupport` is the stable,
Java-first entry point for those operations without exposing the object's internal Model companion.

The facade accepts either a completed root object or any completed DSL Object in its subtree:

```java
import com.blackbuild.klum.ast.runtime.KlumObjectSupport;
import com.blackbuild.klum.ast.runtime.validation.KlumValidationResult;
import java.util.List;
import java.util.Map;

KlumObjectSupport<Deployment> support = KlumObjectSupport.of(deployment);

Deployment object = support.getObject();
String constructionPath = support.getConstructionPath();
String modelPath = support.getModelPath();

KlumObjectSupport.Structure<Deployment> structure = support.getStructure();
Map<String, Service> services = structure.findAll(Service.class);

KlumObjectSupport.Validation<Deployment> validation = support.getValidation();
KlumValidationResult result = validation.getResult();
List<KlumValidationResult> subtreeResults = validation.getSubtreeResults();
```

The Javadoc for `KlumObjectSupport` and its nested `Structure` and `Validation` helpers is the source of truth for complete
signatures, overloads, return types, and exceptional cases.

## Construction and structural model paths

The final 4.0 facade names the Builder/factory call path `getConstructionPath()`. It is the immutable construction path
through which the object was created. `getModelPath()` reports the object's structural location in the completed model.
These answer different questions and are not interchangeable.

(See: `CompletedObjectSupportDocumentaryTest#'reports distinct construction and structural paths for a completed deployment'`.)

```groovy
given:
@DSL class Deployment {
    Service service
}

@DSL class Service {
}

when:
def deployment = Deployment.Create.With {
    service {}
}
def deploymentSupport = KlumObjectSupport.of(deployment)
def serviceSupport = KlumObjectSupport.of(deployment.service)

then:
assert deploymentSupport.constructionPath == '$/Deployment.With'
assert serviceSupport.constructionPath == '$/Deployment.With/service'
assert deploymentSupport.modelPath == '<root>'
assert serviceSupport.modelPath == '<root>.service'
```

There is no public `getBreadcrumbPath()` alias. `BreadcrumbCollector` remains an internal implementation name. The
construction path is not provenance: KlumAST does not retain a source-lineage, applied-Template, or lifecycle-event
record.

Traversal methods produce contextual traversal paths. Managed import contributes an import source, and validation records a
validation location. Neither is a substitute for the construction or structural model path.

## Ownership, paths, and traversal

`getStructure()` groups operations that inspect the completed composition graph:

- direct and single-owner lookup, owner hierarchy, and nearest ancestors by type;
- full paths from the composition root and relative paths from one object to an owned descendant; and
- typed `findAll` and `visit` traversal. The public traversal signatures are
  `visit(Class<R>, BiConsumer<String, R>)` and `findAll(Class<R>)`; the runtime traversal visitor is internal.

Traversal follows composed DSL values only. Owner and `LINK` edges are not followed, and identity-based cycle protection
ensures that object graphs remain safe even when DSL types override `equals`.

(See: `CompletedObjectSupportDocumentaryTest#'traverses a deployment composition without following linked services'`.)

```groovy
given:
@DSL class Deployment {
    Service api
    List<Service> services
    @Field(FieldType.LINK) Service catalogService
}

@DSL class Service {
    @Key String name
    @Owner Deployment deployment
}

def catalog = Service.Create.With('catalog') {}

when:
def deployment = Deployment.Create.With {
    api('api') {}
    services {
        service('worker') {}
    }
    catalogService catalog
}
def structure = KlumObjectSupport.of(deployment).structure

then:
assert structure.getRelativePath(deployment.services[0]) == 'services[0]'
assert KlumObjectSupport.of(deployment.api).structure.singleOwner.get().is(deployment)
assert structure.findAll(Service).keySet() == ['<root>.api', '<root>.services[0]']
```

## Owning Schema declarations

The [#856](https://github.com/klum-dsl/klum-ast/issues/856) capability for 4.1 adds read-only declaration queries to both
completed Object Structure and active Builder Structure. When authoritative ownership is available, the immutable
declaration identifies the accepted owning Schema field, including its original declaring class when inherited.
It works without an `@Owner` backreference; valid objects without authority return `Optional.empty()`.

```java
import java.util.Optional;
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport;
import com.blackbuild.klum.ast.runtime.KlumObjectSupport;
import com.blackbuild.klum.ast.runtime.KlumSchemaRelationship;

// receiver is a Builder in an active callback after OWNER(15).
Optional<Binding> binding = KlumBuilderSupport.of(receiver).getStructure()
    .getOwningRelationshipAnnotation(Binding.class);
// candidate is an independently completed Model.
Optional<Source> source = KlumObjectSupport.of(candidate).getStructure()
    .getOwningRelationshipAnnotation(Source.class);
Optional<KlumSchemaRelationship> declaration = KlumObjectSupport.of(candidate).getStructure()
    .getOwningRelationship();
```

`Binding`, `Source`, and `DefaultSource` in this example are consumer-defined, runtime-retained field annotations.
KlumAST supplies no provider-selection rules. A consuming Schema may use these annotations in its inherited AUTO_LINK
callback and pass the selected completed value to the existing typed relationship method.

(See: `OwningRelationshipTracerTest#'inherited AUTO_LINK selects a completed provider through consumer-owned annotations'`.)

```groovy
@DSL class Facts { String topic }
@DSL class ProviderBase {
    @Source('primary') @DefaultSource(Facts) Facts primary
    @Source('secondary') Facts secondary
}
@DSL class Provider extends ProviderBase {}
@DSL class ReceiverBase {
    @Field(FieldType.LINK) Facts facts
    @AutoLink void bindFacts() {
        if (facts != null) return
        def binding = KlumBuilderSupport.of(this).structure
            .getOwningRelationshipAnnotation(Binding).orElseThrow()
        facts Policy.choose(binding.value(), Facts)
    }
}
@DSL class Receiver extends ReceiverBase {}
@DSL class ApplicationBase { @Binding('secondary') Receiver receiver }
@DSL class Application extends ApplicationBase {}

// Policy belongs to this Schema. Its completed candidates are supplied before construction.
def provider = Provider.Create.With {
    primary { topic 'main' }
    secondary { topic 'alternative' }
}
Policy.candidates = [provider.primary, provider.secondary]
def application = Application.Create.With { receiver {} }
assert application.receiver.facts.is(provider.secondary)
assert KlumObjectSupport.of(provider.secondary).structure
    .getOwningRelationshipAnnotation(Source).orElseThrow().value() == 'secondary'
```

The executable example's `Policy.choose` reads Source/default markers through completed Object Structure. Its explicit
binding and requested-type default behavior is example consumer policy, not a framework precedence contract. An explicit
`facts` value remains selected by the callback's own early return.

Both views provide `getOwningRelationship()` and `<A extends Annotation> getOwningRelationshipAnnotation(Class<A>)`.
An eligible root, absent retained declaration, or missing annotation yields `Optional.empty()`. Absence means no known
declaration; it does not prove that an object is a root. A descriptor exposes `getDeclaringClass()`, `getName()` and
`getAnnotation(Class<A>)`. Equality uses declaring Class identity and field name, independent of receiver or path.
Annotation lookup reads only annotations directly present on the field, including private fields, without reading the
field value or expanding repeatable/meta-annotations.

A Builder Structure view can be acquired during configuration, but **each ownership query** requires that Builder to
belong to the current thread's active Construction session and the current numeric phase to be strictly after
`OWNER(15)`. Configuration and phases at or below 15 throw `KlumModelException`, including root and missing-annotation
queries. Normal sealing during materialization does not end this read lifetime: same-session queries remain eligible
through phase 40 and later phases. Completed LINK wrappers read their target's original retained declaration, not the
importing LINK field. After completion/abort, on another thread, or in another session, the live view rejects. Retained
immutable descriptors remain readable. Use completed Object support for normal work after construction returns.

Null receivers and annotation Classes throw `NullPointerException` naming the argument. An explicit retained declaration
that cannot resolve throws `KlumSchemaException` naming the Schema and field. Marked Templates retain their existing
public rejection, and this capability adds no mutation, Builder traversal, or Model extraction.

Normal direct, List, Set, and Map composition retains the containing Schema declaration, including inherited fields.
Repeated LINK/OPTIONAL_LINK aliases retain the target's original declaration. Container position and map key do not change
declaration identity.

### Templates, copies, and imports

(See: `RelationshipTemplateCopyTest#'a Template child is recaptured under the recipient field on each application'`.)

```groovy
@DSL class Node { String value }
@DSL class Definition { Node child }
@DSL class Recipient { Node actual }

def recipe = Definition.Create.Template.With {
    child { value 'configured' }
}
def result = Recipient.Create.With {
    actual { copyFrom recipe.child }
}

assert KlumObjectSupport.of(result.actual).structure.owningRelationship.orElseThrow().name == 'actual'
```

Templates retain accepted definition declarations internally, while public Object support still rejects marked Templates
and their owned nodes. Application/copy captures fresh recipient claims; it never adopts donor metadata as an owning
edge. Standalone copy roots have no owning declaration. Single-field copies from ordinary Models, Templates, Maps and
active same-session Builders, and merges into already claimed children, retain the recipient placement.

Direct copied-container insertions preserve existing alias identity but establish no authoritative owning claim, even
for a unique placement. Their metadata remains empty. A copied container alias of an already claimed child retains that
accepted declaration. Empty metadata does not identify a root and is not an instruction to infer ownership from a path,
Owner or occurrence order.

Normal OPTIONAL_LINK attachments adopt fresh same-session Builders and preserve already claimed or completed aggregation
targets. OPTIONAL_LINK copy inputs instead follow the existing recipe-rehydration route: unclaimed copied container
entries can materialize as `null`. This capability preserves that behavior; it does not turn those entries into owned
composition or repair their materialization. Normal generated relationship operations remain the route for preserving
an existing completed aggregation target's identity.

Managed Jackson root, Builder, and apply-to-Builder imports retain new owned declarations; explicit references retain the
original target's declaration and identity. Value-only Template imports retain their accepted definition edges internally
without running a lifecycle. Jackson export adds no declaration/companion wire metadata, and normal export still rejects
Templates. JSON/YAML projections do not establish a persistence round trip.

Java serialization is qualified within the same KlumAST version with compatible available Schema definitions. Ordinary
graph identity, Template recipe replay, and retained declarations survive that round trip; metadata does not retain the
old owner instance. Historical streams and Schema evolution are not promised. Empty lookup applies only to otherwise
readable absent metadata. Companion serialized forms may change; regenerate old serialized models/Templates from their
source configuration or recipes when upgrading.

Java 17 and dynamic/static Groovy 3/4/5 consumers are qualified against separately compiled Schema/annotation artifacts.
Groovy 4/5 named modules use the existing qualified construction opens; annotation lookup needs no additional opens.
The revised copied-container authority policy is qualified without a runtime change. This qualification supplies no
provider-selection policy, historical Schema/runtime ABI guarantee, or cross-version serialization promise.

### Copied providers and absent authority

Consumer code must handle absent metadata. This example policy skips candidates whose Source annotation is unavailable;
another consumer may choose a different fallback. KlumAST supplies no selection rule.

(See: `CopyOwnershipConsumerDocumentaryTest#'inherited AUTO_LINK skips providers without authoritative declarations and preserves selected identity'`.)

```groovy
@DSL class ProviderBase {
    @Source('primary') List<Facts> primary
    @Source('secondary') @Field(keyMapping = { it.topic }) Map<String, Facts> secondary
}
@DSL class ReceiverBase {
    @Field(FieldType.LINK) Facts facts
    @AutoLink void bind() {
        if (facts != null) return
        def binding = KlumBuilderSupport.of(this).structure
            .getOwningRelationshipAnnotation(Binding).map { it.value() }.orElse('secondary')
        def selected = Policy.choose(binding)
        if (selected != null) facts selected
    }
}
class Policy {
    static List<Facts> candidates
    static Facts choose(String binding) {
        candidates.find { candidate ->
            KlumObjectSupport.of(candidate).structure.getOwningRelationshipAnnotation(Source)
                .map { it.value() == binding }.orElse(false)
        }
    }
}

// provider was built with normal List/Map attachment; copied providers lack those claims.
def copied = Provider.Create.With { copyFrom provider }
Policy.candidates = copied.primary + copied.secondary.values() + provider.primary + provider.secondary.values()
def application = Application.Create.With { receiver {} }
assert application.receiver.facts.is(provider.secondary.chosen)
assert KlumObjectSupport.of(copied.secondary.chosen).structure.owningRelationship.empty
```

Source and Binding annotations in the executable example belong to its consumer Schema. It also verifies the case where
all candidates lack authority: the callback leaves `facts` absent instead of treating missing metadata as a root marker
or inventing an annotation. The selected target keeps its original declaration and identity.

## Stored validation

(See: `CompletedObjectSupportDocumentaryTest#'reads stored validation results for a completed deployment'`.)

```groovy
def deployment = Deployment.Create.With {
    service {}
}
def validation = KlumObjectSupport.of(deployment).validation

assert validation.result.issues
assert validation.subtreeResults == [
    validation.result,
    KlumObjectSupport.of(deployment.service).validation.result
]
```

`getValidation().getResult()` returns the result already stored for the target object.
`getValidation().getSubtreeResults()` reads all stored results for that target and its owned composition subtree.
`verify()` uses the configured failure level, while `verify(level)` uses the supplied level. These operations only inspect
lifecycle results: they do not execute `InstanceValidator`s, create results, or mutate recorded issues.

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

`Validator` result readers are removed in 4.0 with no compatibility adapter. Completed-object code uses
`KlumObjectSupport.getValidation()` as described in [Validation](Validation.md).

## Discovering the Model type of a Builder

`KlumBuilderSupport.of(builder).getModelType()` returns the concrete represented Model class, including a
subtype reached through a base-typed relationship or `KlumBuilder<?>`. The facade accepts only generated
Builders; null and unsupported marker implementations retain the usual targeted diagnostics.

Java consumers retain the generic Model type:

```java
Class<T> modelType = KlumBuilderSupport.of(builder).getModelType();
```

Groovy consumers can use the property spelling:

```groovy
Class<?> modelType = KlumBuilderSupport.of(builder).modelType
```

(See: `BuilderModelTypeTest#'discovers concrete Model types through base typed owned and LINK relationships'`.)

```groovy
def completed = SpecialRegistry.Create.With(host: 'packages.example.test')
def deployment = Deployment.Create.With {
    def owned = registry(SpecialRegistry.Create) { host 'owned.example.test' }
    linked completed
    assert KlumBuilderSupport.of(owned).modelType == SpecialRegistry
    assert KlumBuilderSupport.of(delegate.linked).modelType == SpecialRegistry
}
assert deployment.linked.is(completed)
```

Here both Deployment relationships are declared as Registry; linked is a LINK relationship. Type reading works
before OWNER and throughout construction, including sealed wrappers of completed LINK targets. Captured Builders
retain this immutable metadata after completion or abort, independently of owning-relationship queries, which still
require the current active session and a phase after OWNER. Reading the type does not create, adopt, unseal or
materialize anything and grants no mutation eligibility. Templates, ownership, lifecycle and materialization retain
their existing behavior. `KlumBuilder<T>` remains a zero-operation marker.
