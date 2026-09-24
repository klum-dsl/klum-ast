package onboarding.publicschema

import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Key
import com.blackbuild.klum.ast.Required

@DSL
class Deployment {
    @Key String name
    @Required('environment is required') String environment
    Service service
}

@DSL
class Service {
    String image
}
