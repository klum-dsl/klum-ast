package com.blackbuild.groovy.configdsl.transform.model.impl

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Field
import com.blackbuild.groovy.configdsl.transform.Key

@DSL
class Config {

    String name

    @Field(optional = true) String value
    int age

    @Field(value = "envs", element = "e")
    Map<String, Environment> environments

    Options options
}

@DSL
class Options {
    Map<String, String> oValues
    boolean condition

    def getAllUnderscoreOptions() {
        return values.findAll { key, value -> key.startsWith("_")}
    }
}

@DSL
class Environment {

    @Key String name
    String url
    List<Authorization> authorizations

    @Field
    String bla;
}

@DSL
class Authorization {

    ArrayList<String> roles

    Map<String, String> partners;

    List<Integer> everything;

    @Field("rens")
    List<Integer> renamed;

    @Field(value = "others", element = "more")
    List<Integer> another;

}

class Other {

    static Other create(Closure c) {
        _create(c)
    }

}

