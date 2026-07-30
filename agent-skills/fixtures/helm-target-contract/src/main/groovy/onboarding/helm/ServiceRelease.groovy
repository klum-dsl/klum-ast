package onboarding.helm

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Default
import com.blackbuild.groovy.configdsl.transform.Key
import com.blackbuild.groovy.configdsl.transform.Required
import com.blackbuild.groovy.configdsl.transform.Validate
import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate
import static com.blackbuild.klum.ast.runtime.KlumSchemaSupport.klumValidation

@DSL
class ServiceRelease {

    @Key String name

    String imageRegistry = 'ghcr.io/acme'

    @Default(code = { "$imageRegistry/$name" })
    String imageRepository

    String imageTag
    int replicaCount = 2
    int containerPort = 8080
    boolean publiclyReachable

    @Default(code = { publiclyReachable ? "${name}.example.test" : null })
    String hostname

    ResourceRequirements resources

    /**
     * Expands the concise authoring model into the Helm values contract.
     * The target contract owns these field names and nesting.
     */
    Map<String, Object> toHelmValues() {
        Map<String, Object> values = new LinkedHashMap<>()
        values.replicaCount = replicaCount
        values.image = [repository: imageRepository, tag: imageTag]
        values.service = [type: 'ClusterIP', port: containerPort, targetPort: containerPort]
        values.resources = resources.toHelmValues()
        Map<String, Object> ingress = [enabled: publiclyReachable]
        if (publiclyReachable) {
            ingress.put('hosts', [[host: hostname, paths: [[path: '/', pathType: 'Prefix']]]])
        }
        values.ingress = ingress
        values
    }

    @Validate
    void requiresDeployableHelmValues() {
        if (!(imageTag ==~ /\d+\.\d+\.\d+/)) {
            klumValidation.error('imageTag must be a semantic version such as 1.4.0')
        }
        if (!(containerPort in 1..65535)) {
            klumValidation.error('containerPort must be between 1 and 65535')
        }
        if (publiclyReachable && !hostname) {
            klumValidation.error('publicly reachable releases need a hostname')
        }
        if (!resources) {
            klumValidation.error('resources with requests and limits are required')
        }
    }
}

@DSL
class ResourceRequirements {

    ResourceValues requests
    ResourceValues limits

    @AutoCreate
    void defaultsLimitsFromRequests() {
        if (requests && !limits) {
            def requested = requests
            limits {
                copyFrom requested
            }
        }
    }

    Map<String, Object> toHelmValues() {
        [requests: requests.toHelmValues(), limits: limits.toHelmValues()]
    }

    @Validate
    void validatesRequestsAndLimits() {
        if (!requests) {
            klumValidation.error('resource requests are required')
        }
        if (requests && limits && requests.memory != limits.memory) {
            klumValidation.issue('memory limit differs from memory request', Validate.Level.WARNING)
        }
    }
}

@DSL
class ResourceValues {

    @Required String cpu
    @Required String memory

    Map<String, String> toHelmValues() {
        [cpu: cpu, memory: memory]
    }
}
