/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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

    GROOVY_24("org.codehaus.groovy:groovy-all:2.4.21", "org.spockframework:spock-core:1.3-groovy-2.4"),
    GROOVY_3("org.codehaus.groovy:groovy-all:3.0.23", "org.spockframework:spock-core:2.3-groovy-3.0"),
    GROOVY_4("org.apache.groovy:groovy-all:4.0.24", "org.spockframework:spock-core:2.3-groovy-4.0");

    private final String spockDependency;
    private final String groovyDependency;

    GroovyVersion(String groovyDependency, String spockDependency) {
        this.groovyDependency = groovyDependency;
        this.spockDependency = spockDependency;
    }

    public String getGroovyDependency() {
        return groovyDependency;
    }

    public String getSpockDependency() {
        return spockDependency;
    }
}
