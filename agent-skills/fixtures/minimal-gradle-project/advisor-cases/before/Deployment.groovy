package onboarding

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Key

@DSL
class Deployment {

    @Key String name
    String environment
    Service service
}

@DSL
class Service {

    String image
    int replicas = 1
}
