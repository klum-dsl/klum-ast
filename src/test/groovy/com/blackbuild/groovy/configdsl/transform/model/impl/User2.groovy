package com.blackbuild.groovy.configdsl.transform.model.impl

def auth = Authorization.create {
    partner("A", "b")
}

def c = Config.create {
    name "klaus"

    envs {

        e("bal") {
            age 10
            authorizations {

                authorization {
                    partner("Hans", "Dieter")
                }

                reuse(auth)
            }
        }
    }
}

println c.environments.bal.authorizations