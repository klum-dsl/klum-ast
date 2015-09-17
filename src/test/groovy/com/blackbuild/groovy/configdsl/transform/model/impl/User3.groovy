package com.blackbuild.groovy.configdsl.transform.model.impl

def c = Config.create {
    options {
        oValues(_a: "b", c: "d")
        condition true
    }


}

