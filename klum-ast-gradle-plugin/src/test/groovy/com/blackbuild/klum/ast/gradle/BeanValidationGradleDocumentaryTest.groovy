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
package com.blackbuild.klum.ast.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.See
import spock.lang.Specification
import spock.lang.Tag

@Tag("documentary")
class BeanValidationGradleDocumentaryTest extends Specification {

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#using-the-gradle-plugin")
    def "schema plugin aligns an optional Bean Validation module to its BOM"() {
        given:
        Project project = ProjectBuilder.builder().build()
        String pluginVersion = PluginHelper.determineOwnVersion()
        project.pluginManager.apply(KlumAstSchemaPlugin)

        when:
        project.dependencies.add("api", "com.blackbuild.klum.ast:klum-ast-bean-validation")

        then:
        project.configurations.api.dependencies.any {
            it.group == "com.blackbuild.klum.ast" && it.name == "klum-ast-bean-validation" && it.version == null
        }
        project.configurations.api.dependencies.any {
            it.group == "com.blackbuild.klum.ast" && it.name == "klum-ast-bom" && it.version == pluginVersion
        }
    }
}
