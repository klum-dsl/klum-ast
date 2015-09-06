package com.blackbuild.groovy.configdsl.transform.model.impl

def c = Config.create {
    options {

        values(_a: "b", c: "d")
        condition true
    }
}