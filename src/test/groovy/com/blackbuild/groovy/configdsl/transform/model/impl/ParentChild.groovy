package com.blackbuild.groovy.configdsl.transform.model.impl

import com.blackbuild.groovy.configdsl.transform.DSL

@DSL
class ParentChildContainer {

    Parent parent
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




    parent(Child) {

    }

}
