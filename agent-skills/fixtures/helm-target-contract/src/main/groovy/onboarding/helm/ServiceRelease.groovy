package onboarding.helm

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Default
import com.blackbuild.groovy.configdsl.transform.Key
import com.blackbuild.groovy.configdsl.transform.Validate
import com.blackbuild.klum.ast.validation.Validator

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

    String cpuRequest = '250m'
    String memoryRequest = '256Mi'

    /**
     * Expands the concise authoring model into the Helm values contract.
     * The target contract owns these field names and nesting.
     */
    Map<String, Object> toHelmValues() {
        Map<String, Object> values = new LinkedHashMap<>()
        values.replicaCount = replicaCount
        values.image = [repository: imageRepository, tag: imageTag]
        values.service = [type: 'ClusterIP', port: containerPort, targetPort: containerPort]
        values.resources = [requests: [cpu: cpuRequest, memory: memoryRequest]]
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
            Validator.addError('imageTag must be a semantic version such as 1.4.0')
        }
        if (!(containerPort in 1..65535)) {
            Validator.addError('containerPort must be between 1 and 65535')
        }
        if (publiclyReachable && !hostname) {
            Validator.addError('publicly reachable releases need a hostname')
        }
    }
}
