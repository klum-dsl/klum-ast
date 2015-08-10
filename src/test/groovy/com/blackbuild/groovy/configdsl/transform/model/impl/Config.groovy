package com.blackbuild.groovy.configdsl.transform.model.impl

import com.blackbuild.groovy.configdsl.transform.DSLConfig
import com.blackbuild.groovy.configdsl.transform.DSLField
import groovy.transform.Canonical

@DSLConfig
class Config {

    String name

    @DSLField(optional = true) String value
    int age

    //Environment env

    @DSLField(value = "envs", element = "e")
    Map<String, Environment> environments


}

@DSLConfig(key = "name")
class Environment {

    String name
    String url
    List<Authorization> authorizations
}

@DSLConfig
class Authorization {

    ArrayList<String> roles

    Map<String, String> partners;

    List<Integer> everything;

    @DSLField("rens")
    List<Integer> renamed;

    @DSLField(value = "others", element = "more")
    List<Integer> another;

}

class Other {

    static Other create(Closure c) {
        _create(c)
    }

}

