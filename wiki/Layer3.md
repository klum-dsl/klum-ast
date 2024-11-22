Layer3 advanced structures
==========================

A Layer3 structure is a way of approaching a model from two sides:

- The API layer provides abstract base classes for the schema. These base classes usually provide access to the relevant fields of the actual classes using Maps and collections. The API layer is thus consumer specific and technical in nature.
- The Scheme layer provides the actual classes, i.e. specific subclasses of the classes defined in the API-Layer. These provide named fields and validations, making the actual modelling easier. These fields are usually a lot more domain specific.
- The model layer contains the actual configuration scripts to instantiate the schema layer classes and connect them to each other.

## Example structure

Let's consider an infrastructure model. We have an application that consists of a database, and a number of microservices. This application will be deployed in a number of environments. An environment is thus a collection of related applications that are deployed together. Actual instances of an environment represent different stages for deployment, e.g. dev, test, prod.

In this structure, the API layer will provide the following classes:

- `Environment`
- `Application`
- `Database`
- `Microservice`

These are the classes that will be consumed by our Consumer application (for example a deployment pipeline).

The schema layer contains classes modelling the actual applications, i.e. if we have two applications, each application will consist of a database class and several microservice classes.

```groovy
@DSL class CustomerServiceEnvironment extends Environment {
    Shipping shipping
    Billing billing
}

// First Application: Shipping
@DSL class Shipping extends Application {
    ShippingDatabase database
    ShippingFrontend frontend
    ShippingBackend backend
    ShippingWorker worker 
}

@DSL class ShippingDatabase extends Database {
    @Required DbUser ddl
    @Required DbUser dml
    DbUser monitoring
}

// Second Application: Billing
@DSL class Billing extends Application {
    BillingDatabase database
    BillingService service
}
```
Now without going into much detail, a dsl-model (using KlumAST) could be something like this:

```groovy
environment("dev") {
    shipping {
        database {
            ddl "admin"
            dml "shipping_user"
            monitoring "monitoring"
        }
        frontend {
            replicas 1
            ssl false
            //...
        }
        // ...
    }
    billing {
        database {
            ddl "admin"
            dml "billing_user"
        }
        service {
            //...
        }
    }
}
environment("prod") {
    shipping {
        database {
            ddl "xcvzh"
            dml "abcde"
            monitoring "mon_x"
        }
        frontend {
            replicas 3
            ssl true
            //...
        }
        // ...
    }
    //...
}
```

From the modelling perspective, this is a lot more expressive than using generic microservice or database classes. However, the API layer is still very simple, and can be used by the consumer application without having to know about the actual structure of the application.

The Environment base class contains method to access the actual applications as a Map:

```groovy
@DSL
abstract class Environment {
    @Key String name
    
    @Cluster abstract Map<String, Application> getApplications() 
}
```

That way a deployer service can simply iterate over the applications of our CustomerServiceEnvironment and deploy them.

```groovy
def deploy(Environment env) {
    env.applications.each { name, app ->
        log.info "Deploying $name"
        deployApplication(app)
    }
}
```

Validations in our ShippingApplication can also be done specifically for that application:

```groovy
@Validate void SslNeedsValidationServer() {
    if (frontend.ssl && backend.validationServer == null)
        error "Backend must define validation server if SSL is enabled"
}
```

## Implementation

Using the `@Cluster` annotation, this method will automatically be implemented using the respective methods of the ClusterModel helper class.

For example, the `getApplications` method is implemented like this:

```groovy
Map<String, Application> getApplications() {
    ClusterModel.getPropertiesOfType(this, Application)
}
```

If the annotated method return `Map<String, Collection<X>>`, `ClusterModel.getPropertyMapList` will be used instead.

Most ClusterModel methods have an additional parameter to filter the return values, which is usually one of the following:

- A `Predicate<AnnotatedElement>`
- A `Closure<Boolean>`, which accepts an AnnotatedElement as parameter
- An Annotation class (which is a shortcut for `it -> it.isAnnotationPresent(filter)`)

The most common usage is the last one, simply filtering on the presence of an annotation on the fields. This can also be implemented using the `value` field of the `@Cluster` annotation:

```groovy
@Cluster(Important) abstract Map<String, Application> getApplications()
```

will be converted to

```groovy
Map<String, Application> getApplications() {
    return ClusterModel.getPropertiesOfType(this, Application, Important)
}
```
## Benefits of a Layer3 model

There are various major benefits of using a Layer3 model vs. a generic schema/model approach:

### Editing and code completion

With each application being a specific subclass of Application, the actual model gets more concise, and more domain specific. Consider the (partial) example above being modelled using a generic schema/model approach:

```groovy
environment("dev") {
    application("shipping") {
        database {
            user("ddl") { "admin" }
            user("dml") { "shipping_user" }
            user("monitoring") { "monitoring" }
        }
        service("frontend") {
            replicas 1
            ssl false
            //...
        }
        // ...
    }
    application("billing") { 
    // ...
```
Besides being harder to read there is neither code completion help nor any protection against typos. The developer needs to know exactly which microservices the application consists of and which database users are needed.

In contrast, by using a specific `ShippingApplication` class, there is exactly one field for each microservice, and the developer can use code completion to see which fields are available. Also, typos like using the wrong user will be detected by the compiler and the IDE immediately.

Using a specific subclass also allows to properly comment the domain specific fields (what is the use of the monitoring db user?), which is not possible with a generic schema/model approach.

### Domain consumers

Since we are building an environment model in this example, there are two distinct types of consumers:

* Generic consumers, like a deployment pipeline or a test framework only use the generic methods of the API layer (behaving exactly like in the generic schema/model approach)
* Specific consumers know the actual ShippingApplication and can access its various fields directly. Specific consumers can be, for example:
    * The application itself, for example in reading the jdbc url for the actual database directly from the model (instead of an application.yaml or such)
    * A post-deployment test that runs against a specific environment and needs to know the actual database users and passwords can obtain them directly from the model. Combined with password retrieving techniques like an Hashicorp Vault accessor, this can be a very powerful approach. Since the tests are identical for every stage, they can be effectively reused. A developer can use the same tests against a local virtual machine as against the actual approval environment. The only difference being that the developer would not have the rights to access the actual passwords of the approval environment.

### Validation

With ShipmentApplication being a class with domain knowledge, it can also contain domain specific validations. For example:

- if ssl is enabled in the frontend, the backend must have a configured validation server
- if a monitoring service is defined, the monitoring database user must be defined

Making these validation with a domain schema is trivial.

### Automatic creation and linking

Let's say that a monitoring microservice is used by multiple applications in the environment. In the generic schema/model approach, the monitoring service would be defined multiple times, once for each application. This is not only redundant, but also error-prone, since the monitoring service might be configured differently for each application.

Using the schema layer with auto create, the monitoring service could automatically be created.

```groovy
abstract class MonitoredApplication extends Application {
  @AutoCreate
  MonitoringService monitoring
}
```

Now, our monitoring service needs access to a database, but we want to reuse the database for the application. So we link
the database field of the monitoring service to the database of its owner:

```groovy
class MonitoringService extends Microservice {
  @Owner MonitoresApplication application
  @LinkTo Database database
}
```
During the instantiation of the model, the database field will bea automatically filled, but can still be overwritten 
on instance level. See JavaDoc for `LinkTo` for more details.

### Role fields

Fields can be annotated with `@Role` to indicate that they are used for a specific role as seen from their owner. 
Consider a Database class that has various users. Each user object has access to its owning database object, but it
might be necessary fo the User object to know how it is used in their database. Rather then forcing the modeller to 
set the role manually, it can simply be inferred from the field name of the Database that points to the user:

```groovy
@DSL
class MyDatabase extends Database {
  String url

  DbUser ddl
  DbUser dml
  DbUser monitoring
}

@DSL
class DbUser {
  @Owner Database database
  @Role String role
  @Key String id
}

def db = MyDatabase.Create.With {
  url "jdbc:..."
  ddl("user1")
  dml("user2")
  monitoring("user3")
}

assert db.ddl.role == "ddl"
assert db.dml.role == "dml"
assert db.monitoring.role == "monitoring"
```

That way some kind of environment checker can for example validate that all non ddl user habe the correct privileges:

```groovy
StructureUtil.deepFind(model, DbUser).each { path, user ->
    if (user.role != "ddl")
      assertNoDdlPrivileges(user, path)

}
```

Note that this check should not be standard model validation, because it requires access to the actual database.


