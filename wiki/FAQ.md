# Why and AST and not just use a ModelBuilder?
 
A groovy Model Builder relies heavily on dynamic methods and properties. In comparision, a model enhanced with KlumAST
is completely static, i.e. it can easily be used with `@TypeChecked` or `@CompileStatic`. This also makes using
a Klum-model inside the IDE at lot more convenient (KlumAST makes heavy use of `@DelegateTo` annotation of Groovy, which
is interpreted by most modern IDEs)

# Why don't I get any code completion inside my IDE

Code completion for AST Transformations in the IDE can be achieved by three different ways:

- By splitting the model classes and the actual users of the model into different projects and letting the user project
  only rely on the compiled model, modern IDEs can provide full code completion by looking at the generated code. However
  this might not always be the most convenient solution. see [[Usage]] for details.
- Both Eclipse (dlsd) and Intellij IDEA (gdsl) provide mechanisms for providing hints to the IDE about the result of the
  transformation. Unfortunately, there are currently two problems:
  - Currently there only exists a gdsl for IDEA, which is outdated and suffers from a bug/change in the newer versions 
    (since 2016.2), which makes them useless. For that reason, the gdsl is currently not included in the KlumAST jar file
  - Both approaches are incompatible, effectively doubling the effort for maintaining such a solution
- A transformation specific plugin could be implemented to encapsulate the gdsl/dlsd, however, as with solution two, 
  this approach would need a lot of effort (contributions welcome!)
  
Currently, only the first approach is working, but aside of the inconvenience of maintaining two separate projects, it 
does it rather well.
  
  
# What does the name mean?

KlumAST was formerly called ConfigDSL because its main usage was config files. While configuration files are still
one major use case, the actual target of the project has much increased. Thus the renaming. 

Klum stands for Konfiguration Library for Unified Modelling - but it is also used to turn models into supermodels (_wink,
wink_).  


# What about the volatile deprecation strategy?

While evolving, KlumAST went through a lot of stages. Since the project evolution is driven by actual use cases and real
project needs, this leads to a somewhat "hit-and-run" strategy while stabilizing the API.

The goal is to reduce this as much as possible in the future. However, a couple of breaking changes are still on the
list in the goal of further reducing the clutter of the model API.

As of 1.1, the basic API is somewhat stabilized, however, there are still a couple of
open (and already implemented) changes leading to 1.2, that will unfortunately result in some
incompatible code.

# Why another library? What are the differences?

Most DSL approaches fall into two categories:

- Dynamic programming (like the Groovy NodeBuilder) framework rely strongly on dynamic features of
  Groovy (mostly methodMissing and friends). While this works well, it has a basic disadvantage in the missing
  IDE and compile-time check support, meaning usually no code completion and no syntax highlighting.
  This can be somewhat solved by using IDE specific DSL descriptors (gdsl for Intellij IDEs, dsld for Eclipse),
  however this approach does not solve compile time checks, is restricted to two specific IDEs (albeit the two
  major ones) and writing gdsl script for IDEA is really annoying for lack of documentation and support (Eclipse
  dslds are somewhat better, since they use elements of the Groovy AST itself).

  So basically, developing a DSL with dynamic features is easy and rather straightforward, using the DSL is rather cumbersome.

- Hard wired programming of the DSL, basically creating a lot of methods with `Closure` parameters. This directory
  is for example taken partially by the Gradle DSL. This approach is nice for the users of such a DSL, since they get
  all the advantages of modern IDEs, like code completion and syntax highlighting.
  However, the cost of this approach lies in the additional efforts in actually creating the DSL-classes,
  using lot's of repetative code.

Klum AST tries to focus on the second approach and creates type safe, static models, but
tries to reduce the boilerplate code to a minimum by using Annotation controled AST transformation
in order to auto generate it. The result is the best of both worlds: Faster, nicer and cleaner DSL classes on
the developer side, ease of use on the user side.

As an additional bonus, since KlumAST strongly separates RO and RW model parts, the API using an actualy model
does not even see the mutating parts of the model, making developing against a given DSL easier as well.