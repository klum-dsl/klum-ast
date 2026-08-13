package onboarding

import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Key
import com.blackbuild.klum.ast.Validate

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
