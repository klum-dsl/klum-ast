package onboarding.publicschema

import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import spock.lang.Issue
import spock.lang.Specification

@Issue('469')
class DeploymentTest extends Specification {

    def 'constructs a completed deployment'() {
        when:
        Deployment deployment = Deployment.Create.With('catalog') {
            environment 'production'
            service { image 'catalog:1.0' }
        }

        then:
        deployment.name == 'catalog'
        deployment.environment == 'production'
        deployment.service.image == 'catalog:1.0'
    }

    def 'reports the missing environment rule'() {
        when:
        Deployment.Create.With('catalog') {
            service { image 'catalog:1.0' }
        }

        then:
        KlumValidationException error = thrown()
        error.message.contains('environment is required')
    }
}
