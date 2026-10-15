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
package qualification.schema

import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.layer3.AutoCreate
import qualification.javaapi.JavaPolicies.Repeated as JavaRepeated
import qualification.javaapi.JavaPolicies.Container as JavaContainer
import qualification.javaapi.JavaPolicies.RequiredValue
import qualification.javaapi.JavaPolicies.Query
import qualification.javaapi.JavaPolicies.Group
import qualification.javaapi.JavaPolicies.ForeignAlias
import qualification.groovyapi.Repeated as GroovyRepeated
import qualification.groovyapi.Container as GroovyContainer
import qualification.foreign.ForeignTrigger

@DSL class Service { String name }

@DSL @ForeignAlias('do-not-forward') class Application {
    @RequiredValue String name
    @JavaRepeated(name='java-repeat') Service javaRepeated
    @JavaContainer(name='java-container') Service javaContainer
    @GroovyRepeated(name='groovy-repeat') Service groovyRepeated
    @GroovyContainer(name='groovy-container') Service groovyContainer
    @ForeignAlias('do-not-forward') String payload
    @ForeignAlias('do-not-forward') String describe() { name }
    @Query String summary() { name.toUpperCase() }
    @Group Map<String, Service> services
}

@DSL @ForeignTrigger class Direct {
    String name
    @Builder.Query String summary() { name.toUpperCase() }
    @ForeignTrigger String payload
    @ForeignTrigger String describe() { 'direct' }
}

@ForeignAlias class OrdinaryAlias {
    @ForeignAlias String payload
    @ForeignAlias String describe() { 'ordinary' }
}

@ForeignTrigger('direct') class OrdinaryDirect {
    @ForeignTrigger('direct') String payload
    @ForeignTrigger('direct') String describe() { 'ordinary' }
}
