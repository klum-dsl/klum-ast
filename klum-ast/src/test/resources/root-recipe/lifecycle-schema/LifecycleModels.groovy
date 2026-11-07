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
package fixture.lifecycle

import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Default
import com.blackbuild.klum.ast.Field
import com.blackbuild.klum.ast.FieldType
import com.blackbuild.klum.ast.Key
import com.blackbuild.klum.ast.Owner
import com.blackbuild.klum.ast.PostApply
import com.blackbuild.klum.ast.PostCreate
import com.blackbuild.klum.ast.PostTree
import com.blackbuild.klum.ast.Required
import com.blackbuild.klum.ast.Validate
import com.blackbuild.klum.ast.layer3.AutoLink

@DSL class Environment {
    String region
    String endpoint
    List<String> phases = []
    static int validations

    @PostCreate void created() { phases << 'create' }
    @PostApply void applied() { phases << 'apply' }
    @AutoLink void linked() { phases << 'link' }
    @Default void defaults() { phases << 'default' }
    @PostTree void tree() { phases << 'tree' }
    @Validate void valid() {
        validations++
        assert region
        assert endpoint
        assert phases == ['create', 'apply', 'link', 'default', 'tree']
    }
}

@DSL class Application {
    @Key String name
    @Required @Field(FieldType.LINK) Environment environment
    String image
    List<Service> services
    List<String> phases = []
    static int validations

    @PostCreate void created() {
        assert environment == null
        phases << 'create'
    }
    @PostApply void applied() {
        assert environment != null : 'consumer overlay must supply the completed Environment'
        assert services.every { it.phases == ['create', 'apply'] }
        phases << 'apply'
    }
    @AutoLink void linked() { phases << 'link' }
    @Default void defaults() { phases << 'default' }
    @PostTree void tree() { phases << 'tree' }
    @Validate void valid() {
        validations++
        assert phases == ['create', 'apply', 'link', 'default', 'tree']
        assert services.every { it.environment.is(environment) && it.image == image }
    }
}

@DSL class Service {
    @Owner Application application
    @Required @Field(FieldType.LINK) Environment environment
    String name
    String image
    String endpoint
    List<String> phases = []
    static int validations

    @PostCreate void created() {
        assert environment == null
        phases << 'create'
    }
    @PostApply void applied() {
        // Immediate child callbacks precede the root overlay; this consumer needs context only in later phases.
        assert environment == null
        phases << 'apply'
    }
    @AutoLink void bindEnvironment() {
        environment = application.environment
        assert environment != null
        phases << 'link'
    }
    @Default void defaults() {
        image = application.image
        endpoint = environment.endpoint
        phases << 'default'
    }
    @PostTree void tree() {
        assert endpoint == environment.endpoint
        phases << 'tree'
    }
    @Validate void valid() {
        validations++
        assert environment.is(application.environment)
        assert endpoint == environment.endpoint
        assert image == application.image
        assert phases == ['create', 'apply', 'link', 'default', 'tree']
    }
}
