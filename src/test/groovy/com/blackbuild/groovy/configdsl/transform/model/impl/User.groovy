package com.blackbuild.groovy.configdsl.transform.model.impl


def c = Config.create {
    name "klaus"

    options {
        values(_a: "b", c: "d")
        conition true

    }

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

Options.create {
    oValue("bla", "blub")

}

println c.options.allUnderscoreOptions

println c.environments.bil

def auth = Authorization.create {}

Environment.create("Bla") {
    authorizations {
        authorization {
            url("Hallo")

        }
        authorization {

        }
        reuse()
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


    Authorization.create {
        others(10)

    }

    Environment.create("bla") {

        url "hallo"

    }

    Environment.create("name") {
        url "bla"

    }

}