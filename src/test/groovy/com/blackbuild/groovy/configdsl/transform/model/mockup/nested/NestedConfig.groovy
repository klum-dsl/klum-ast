package com.blackbuild.groovy.configdsl.transform.model.mockup.nested

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Key

@DSL
class NestedConfigContainer {

    List<Group> groups;
}

@DSL
class Group {

    @Key String name
    List<Group> groups
    List<Item> items
}

@DSL
class Item {

    @Key String name

    String value

}

NestedConfigContainer.create {

    groups {
        group("Bla") {
            items {
                ite

            }
            groups {
                group("blub") {
                    items {

                        item("i") {

                        }
                    }
                }
            }
        }
    }


}
