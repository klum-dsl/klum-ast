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
package fixture.recipe

import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Field
import com.blackbuild.klum.ast.FieldType
import com.blackbuild.klum.ast.Key
import com.blackbuild.klum.ast.PostApply
import com.blackbuild.klum.ast.Required
import com.blackbuild.klum.ast.Validate

@DSL class Environment { String region }
@DSL class Application {
    @Key String name
    @Required @Field(FieldType.LINK) Environment environment
    String service
    int applications
    @PostApply void completed() { applications++; assert environment != null }
    @Validate void valid() { assert applications == 1; assert service == 'orders' }
}
@DSL class LegacyApplication { String service }
