package com.blackbuild.groovy.configdsl.transform.model.impl

def c = Config.create {
    name "Hans"
    value("balue")
}



def a = new Config()

a.name

a.name("Hallo")
a.age 10

a.name()


a.v(10)

a.inner {


}




a.inner {
    data "Hallo"

}

