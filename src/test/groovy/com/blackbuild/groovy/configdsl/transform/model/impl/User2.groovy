package com.blackbuild.groovy.configdsl.transform.model.impl

Config.cre

new Config().apply {
	
}


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

Config.cre

println c.environments.bal.authorizations

new Config().apply {
	
}

