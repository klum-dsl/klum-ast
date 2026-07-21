package onboarding.helm

import com.blackbuild.klum.ast.util.KlumValidationException
import com.blackbuild.klum.ast.util.KlumObjectSupport
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.blackbuild.groovy.configdsl.transform.Validate
import spock.lang.Issue
import spock.lang.See
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

@Issue('472')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Target-Contract-Modeling.md#executable-helm-journey')
class HelmTargetContractDocumentaryTest extends Specification {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())

    def 'uses direct-schema authoring defaults and a Helm-specific convenience'() {
        when:
        ServiceRelease catalog = RepresentativeReleases.all().first()

        then:
        catalog.imageRepository == 'ghcr.io/acme/catalog'
        catalog.resources.requests.cpu == '250m'
        catalog.resources.requests.memory == '256Mi'
        catalog.resources.limits.cpu == '250m'
        catalog.resources.limits.memory == '256Mi'
        catalog.hostname == 'catalog.example.test'
        catalog.toHelmValues().ingress == [
            enabled: true,
            hosts  : [[host: 'catalog.example.test', paths: [[path: '/', pathType: 'Prefix']]]]
        ]
    }

    def 'retains a completed model while nested resources warn about a memory limit mismatch'() {
        when:
        ServiceRelease billing = RepresentativeReleases.all().last()
        def issues = KlumObjectSupport.of(billing.resources).validation.result.issues

        then:
        billing.resources.requests.memory == '256Mi'
        billing.resources.limits.memory == '512Mi'
        issues.any { issue ->
            issue.level == Validate.Level.WARNING && issue.message == 'memory limit differs from memory request'
        }
    }

    @Unroll
    def 'generates human-readable #release.name Helm values that conform to the golden contract'() {
        when:
        String generated = yaml.writeValueAsString(release.toHelmValues())
        Path generatedValues = Path.of('build', 'generated-values', "${release.name}.values.yaml")
        Files.createDirectories(generatedValues.parent)
        Files.writeString(generatedValues, generated)
        def targetExample = yaml.readTree(resourceText("helm/representative/${release.name}.values.yaml"))
        def expected = yaml.readTree(resourceText("helm/golden/${release.name}.values.yaml"))
        def actual = yaml.readTree(generated)

        then: 'the generated file is readable YAML and has the same Helm meaning as both checked-in target artifacts'
        generated.contains('image:')
        generated.contains('resources:')
        actual == targetExample
        actual == expected

        where:
        release << RepresentativeReleases.all()
    }

    def 'rejects values that cannot satisfy the Helm contract'() {
        when:
        ServiceRelease.Create.With('catalog') {
            imageTag 'latest'
            containerPort 0
        }

        then:
        KlumValidationException error = thrown()
        error.message.contains('imageTag must be a semantic version')
        error.message.contains('containerPort must be between 1 and 65535')
        error.message.contains('resources with requests and limits are required')
    }

    def 'requires CPU and memory on each resource value through its field contract'() {
        when:
        ServiceRelease.Create.With('catalog') {
            imageTag '1.4.0'
            resources {
                requests {
                    cpu '250m'
                }
            }
        }

        then:
        KlumValidationException error = thrown()
        error.message.contains('memory')
    }

    private String resourceText(String path) {
        getClass().getClassLoader().getResource(path).text
    }
}
