/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package qualification.consumer

import groovy.transform.CompileStatic
import qualification.schema.Application
import qualification.schema.Application_DSL
import qualification.schema.Direct
import qualification.schema.Service
import qualification.schema.Service_DSL

@CompileStatic
class StaticConsumer {
    static Direct createDirect() {
        Direct.Create.With {
            name 'direct'
            assert summary() == 'DIRECT'
        }
    }

    static Application create() {
        Application.Create.With {
            name 'configured'
            assert summary() == 'CONFIGURED'
        }
    }

    static Application cluster() {
        Application.Create.With {
            name 'cluster'
            services {
                Service_DSL.Builder<Service> selected = javaRepeated { name 'cluster' }
                assert selected != null
            }
        }
    }
}
