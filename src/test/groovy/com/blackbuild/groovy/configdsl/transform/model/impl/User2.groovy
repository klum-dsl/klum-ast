package com.blackbuild.groovy.configdsl.transform.model.impl

def auth = Authorization.create {
    partner("A", "b")
}

def c = Config.create {
    name "klaus"

    environments {

        env("bal") {
            age 10
            authorizations {

                au

                authorization {
                    partner("Hans", "Dieter")
                }

                bar(auth)

                authorization {
                    copyFrom(auth)
                }
            }
        }
    }
}

println c.environments.bal.authorizations

