package com.blackbuild.groovy.configdsl.transform.model.impl


def c = Config.create {
    name "klaus"

    options {
        values(_a: "b", c: "d")
        condition true

    }

    environments  {

        env("bal") {

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
    value("bla", "blub")

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
        _use(auth)
    }
}

println c.name

def a = new Authorization()

a.roles "Klaus", "Dieter"
a.role "Dieter"

println a

a.partners(Name: "Klaus", senior: "Dieter")
a.partner("Klas", "Han")

a.everything(1, 2)
a.everything 2
