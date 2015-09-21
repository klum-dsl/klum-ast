package com.blackbuild.groovy.configdsl.transform.model.impl

import com.blackbuild.groovy.configdsl.transform.DSL

@DSL
class ParentChildContainer {

    List<Parent> parents
}

@DSL
class Parent {

    String parentField
}

@DSL
final class Child extends Parent {

    String childField
}


ParentChildContainer.create {



    parents {
        parent(Child) {

        }
        parent {

        }
    }

}
