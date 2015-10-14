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

    Item mainItem
}

@DSL
class Item {

    @Key String name

    String value

    Deploy deploy

}

@DSL
class Deploy {
    String groupId
}

NestedConfigContainer.create {

    groups {

        group("Bla") {

            mainItem("bli") {
                value("1")
            }

            items {
                item("bli") {
                    deploy {
                        groupId "gg"

                    }
                }
            }

            items {

                item("blub") {
                    value("bli")
                }

            }
            groups {
                group("blub") {

                    groups {

                    }

                    mainItem("Bla") {

                        value "blub"
                    }

                    items {

                        item("i") {

                        }
                    }
                }
            }
        }
    }


}
