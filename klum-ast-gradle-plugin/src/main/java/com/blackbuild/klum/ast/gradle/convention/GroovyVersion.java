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
package com.blackbuild.klum.ast.gradle.convention;

public enum GroovyVersion {

    GROOVY_3("org.codehaus.groovy:groovy-bom:3.0.25", "org.codehaus.groovy:groovy:3.0.25", "org.spockframework:spock-core:2.4-groovy-3.0"),
    GROOVY_4("org.apache.groovy:groovy-bom:4.0.32", "org.apache.groovy:groovy:4.0.32", "org.spockframework:spock-core:2.4-groovy-4.0"),
    GROOVY_5("org.apache.groovy:groovy-bom:5.0.6", "org.apache.groovy:groovy:5.0.6", "org.spockframework:spock-core:2.4-groovy-5.0");


    private final String spockDependency;
    private final String groovyBom;
    private final String groovyDependency;

    GroovyVersion(String groovyBom, String groovyDependency, String spockDependency) {
        this.groovyBom = groovyBom;
        this.groovyDependency = groovyDependency;
        this.spockDependency = spockDependency;
    }

    public String getGroovyDependency() {
        return groovyDependency;
    }

    public String getSpockDependency() {
        return spockDependency;
    }

    public String getGroovyBom() {
        return groovyBom;
    }
}
