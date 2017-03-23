/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
