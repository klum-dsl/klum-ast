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
package com.blackbuild.groovy.configdsl.transform.model.impl


def c = Config.create {
    name "klaus"

    options {
        values(_a: "b", c: "d")
        condition true

    }

    skip()

    environments  {

        env("bal") {

            authorizations {

                authorization {
                }
            }
        }

        bil {
        }
    }

}

c.apply {


}


Options.create {
    value("bla", "blub")

}

println c.options.allUnderscoreOptions

println c.environments.bil

def auth = Authorization.create {}

Environment.create("Bla") {
    authorizations {
        authorization {
            url("Hallo")

        }
        authorization {

        }
        _use(auth)
    }
}

println c.name

def a = new Authorization()

a.roles "Klaus", "Dieter"
a.role "Dieter"

println a

a.partners(Name: "Klaus", senior: "Dieter")
a.partner("Klas", "Han")

a.everything(1, 2)
a.everything 2
