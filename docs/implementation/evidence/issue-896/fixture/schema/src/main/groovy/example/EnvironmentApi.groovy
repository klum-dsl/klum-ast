package example

import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Key
import com.blackbuild.klum.ast.layer3.Cluster

@DSL
abstract class EnvironmentApi {
    @Cluster abstract Map<String, ApplicationApi> getApplications()
}

@DSL
abstract class ApplicationApi {
    @Key String name
    String image
}

@DSL
class Application extends ApplicationApi {}

@DSL
class CustomerEnvironment extends EnvironmentApi {
    Application billing
    Application inventory
}
