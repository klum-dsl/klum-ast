package onboarding

import com.blackbuild.klum.ast.util.KlumValidationException
import spock.lang.Issue
import spock.lang.Specification

@Issue('470')
class DeploymentTest extends Specification {

    def 'builds a validated deployment model'() {
        when:
        Deployment deployment = Deployment.Create.With('catalog') {
            environment 'production'
            service {
                image 'catalog:1.0'
            }
        }

        then:
        deployment.name == 'catalog'
        deployment.environment == 'production'
        deployment.service.image == 'catalog:1.0'
        deployment.service.replicas == 1
    }

    def 'rejects a deployment without its required environment'() {
        when:
        Deployment.Create.With('catalog') {
            service {
                image 'catalog:1.0'
            }
        }

        then:
        KlumValidationException error = thrown()
        error.message.contains('environment is required')
    }

    def 'rejects a service without an image'() {
        when:
        Deployment.Create.With('catalog') {
            environment 'production'
            service { }
        }

        then:
        KlumValidationException error = thrown()
        error.message.contains('image is required')
    }

    def 'applies a service template to representative configured models'() {
        given:
        Service standardService = Service.Template.Create {
            image 'catalog:1.0'
        }
        Deployment catalog
        Deployment billing

        when:
        Service.Template.With(standardService) {
            catalog = Deployment.Create.With('catalog') {
                environment 'production'
                service { }
            }
            billing = Deployment.Create.With('billing') {
                environment 'production'
                service { }
            }
        }

        then:
        catalog.service.image == 'catalog:1.0'
        billing.service.image == 'catalog:1.0'
    }
}
