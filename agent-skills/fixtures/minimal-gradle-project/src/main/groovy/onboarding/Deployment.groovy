package onboarding

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Key
import com.blackbuild.groovy.configdsl.transform.Validate

@DSL
class Deployment {

    @Key String name
    String environment
    Service service

    @Validate
    void requiresAnEnvironment() {
        if (!environment) {
            throw new IllegalArgumentException('environment is required')
        }
    }
}
@DSL
class Service {

    String image
    int replicas = 1

    @Validate
    void requiresAnImage() {
        if (!image) {
            throw new IllegalArgumentException('image is required')
        }
    }
}
