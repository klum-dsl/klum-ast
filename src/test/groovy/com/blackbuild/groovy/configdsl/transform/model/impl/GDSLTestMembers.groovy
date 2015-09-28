package com.blackbuild.groovy.configdsl.transform.model.impl

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Field

@DSL
class Container {


    @Field(members = "m")
    List<Member> member;

}

@DSL
class Member {}

Container.create {
    member {
        m {}
    }
}
