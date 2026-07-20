package onboarding.helm

class RepresentativeReleases {

    static List<ServiceRelease> all() {
        [
            ServiceRelease.Create.With('catalog') {
                imageTag '1.4.0'
                publiclyReachable true
                resources {
                    requests { cpu '250m'; memory '256Mi' }
                    limits { cpu '500m'; memory '256Mi' }
                }
            },
            ServiceRelease.Create.With('billing') {
                imageTag '2.1.3'
                replicaCount 1
                containerPort 8081
                resources {
                    requests { cpu '250m'; memory '256Mi' }
                    limits { cpu '500m'; memory '512Mi' }
                }
            }
        ]
    }
}
