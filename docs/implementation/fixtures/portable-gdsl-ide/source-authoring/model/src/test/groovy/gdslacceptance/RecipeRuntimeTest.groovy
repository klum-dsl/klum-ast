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
package gdslacceptance

import spock.lang.Issue
import spock.lang.Specification

@Issue("805")
class RecipeRuntimeTest extends Specification {
    def "mapped filename leaves root and owned-child recipe behavior unchanged"() {
        given:
        File recipe = new File('src/main/groovy/catalog.environment.groovy')
        Class recipeClass = new GroovyClassLoader(getClass().classLoader).parseClass(recipe)

        when:
        def root = Environment.Create.From(recipe)
        def deployment = Deployment.Create.With {
            environment(Environment.Create.AsBuilder().From(recipeClass))
        }
        def control = Environment.Create.With { region 'control' }

        then:
        root.region == 'eu'
        deployment.environments*.region == ['eu']
        control.region == 'control'
    }

    def "suffix mapping does not give the wrong runtime receiver an Environment operation"() {
        given:
        File recipe = new File('src/main/groovy/catalog.environment.groovy')

        when:
        Deployment.Create.From(recipe)

        then:
        MissingMethodException failure = thrown()
        failure.method == 'region'
    }
}
