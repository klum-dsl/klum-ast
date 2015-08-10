package com.blackbuild.groovy.configdsl.transform.model.impl

def c = Config.create {
    name "klaus"

    envs {

        e("bal") {

            authorizations {

                authorization {
                }
            }
        }

        bil {
        }
    }
}

println c.environments.bil

Environment.create("Bla") {
    authorizations {
        authorization {
            url("Hallo")

        }
        authorization {

        }
    }
}

println c.name

def a = new Authorization()

a.another

a.roles "Klaus", "Dieter"
a.role "Dieter"

println a

a.partners(Name: "Klaus", senior: "Dieter")
a.partner("Klas", "Han")

a.everything(1, 2)
a.everything 2

a.ren(10)
a.rens(10,15)

a.others(10, 5)
a.more(8)

a.apply {
    others(10)
    more(5)



}

Authorization.create {
    others(10)

}

Environment.create("bla") {

    url "hallo"

}

Environment.create("name") {
    url "bla"

}