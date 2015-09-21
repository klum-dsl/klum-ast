package com.blackbuild.groovy.configdsl.transform.model.impl

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Key

@DSL
class ParentChildContainer {

    List<Parent> parents
}

@DSL
class Parent {

    @Key String name
    String parentField
}

@DSL
final class Child extends Parent {

    String childField
}


ParentChildContainer.create {



    parents {
        parent(Child, "blub") {
            parentField("bla")
            childField "hallo"


        }
        parent("bli") {
            parentField "holla"
        }
    }

}
