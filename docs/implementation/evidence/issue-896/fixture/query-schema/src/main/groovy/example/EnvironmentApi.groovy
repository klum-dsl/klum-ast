package example

import groovy.transform.CompileStatic
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Builder
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

@CompileStatic
@DSL
class CustomerEnvironment extends EnvironmentApi {
    String region
    @Builder.Query String regionName() { region }
    Application billing
    Application inventory
}
