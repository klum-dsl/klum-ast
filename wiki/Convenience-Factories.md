Since 0.16, KlumAST supports convenience factory methods that allow reading a configuration directly from a various sources:

# Script classes

`MyConfig.createFrom(Class<Script>)` runs the given Script and returns the result. The script must return the
proper type, for example:

```groovy
MyConfig.create {
  value("bla")
}
```

# Delegating Scripts

if the target script is subclass of `DelegatingScript`, the script body is considered as the part inside of the 
create closure. For keyed classes, the key value is the simple name of the script class.

In order to create a delegating script, you need to either include it explicitly via annotation: 

```groovy
@BaseScript DelegatingScript base

name 'Klaus'
...
```

which is not very convenient, or you have to modify the GroovyClassLoader / GroovyShell to set a BaseScript (see
JavaDoc of DelegatingScript for details).

To most convenient solution is for [[Usage#schema---model---consumer]] setup to modify the model project with a compiler customizer.

See the example projects for details.


# Text
`MyConfig.createFrom(text)` or `MyConfig.createFrom(key, text)` handles the given text as the content of the create 
closure.

For example

```groovy
def config = Config.createFrom(new File("bla.groovy").text)
```

and the file bla.groovy
```groovy
value("blub")
```

result in the following:

```groovy
assert config.value == "blub"
```

`createFrom()` can also take an optional classloader as last parameter:

```groovy
Config.createFrom(content, Config.class.classLoader)
```

If no classloader is given, the current context classloader is used.


# File or URL

Instead of a text, also a File or a Url can be given and in case of a keyed object, the key is derived from the filename 
(the first segment, in the example above, the key would be "bla"). By using a small dsld-snippet in your IDE, you even 
get complete code completion and syntax highlighting an specialized config files.

This allows splitting configurations into different files, which might be automatically resolved by something like:
 
```groovy
Config.create {
    environments {
        new File("envdir").eachFile { file -> 
            environment(Environment.createFrom(file)) 
        }    
    }
}
```
 
__Note__: Currently, `createFrom` does not support any polymorphic creation. This might be added later,
 see: ([#43](https://github.com/klum-dsl/klum-core/issues/43))

As with `copyFrom(text)`, `copyFrom(File|Url)` supports an additional classloader parameter as well.

# Classpath

In addition, 1.2.0 introduces a new experimental feature for instantiating a a model automatically by placing
a property file in your model library. In order for this to work, the model needs one or more single
entry points, i.e. instances that are usually present only once (like the all encompassing Config object).

By placing a marker into the classpath, this class can automatically be instantiated without the consumer needing
to know the name of the configuration class. This takes the dependency from the code into the jar-orchestration /
the buildscript.

Given the following classes:

`Model.groovy`:
```groovy
package pk
@DSL class Model {
    // ... definitions, inner elements etc.
}

```

`Configuration.groovy`:
```groovy
package impl
Model.create {
  // regular dsl code
}
```

By including a separate properties file in the jar of [[Usage#model]], this model
can automatically be instantiated. The file must be named `/META-INF/klum-model/<schema-classname>.properties`
and contain a single property: `model-class`, which contains the full classname of the Entry-Point script (which
can bea regular as well as a DelegatingScript):

`/META-INF/klum-model/pk.Model.properties`
```properties
model-class: impl.Configuration
```

This allows the code consuming the model to simply obtain it via:

```groovy
def model = Model.createFromClasspath()
```

Using this technique, the same consumer can work with different models (most of the time: different packages),
without any need to change or somehow inject the name of the Model class.

In fact, this might actually become the preferred method of consuming a model.
