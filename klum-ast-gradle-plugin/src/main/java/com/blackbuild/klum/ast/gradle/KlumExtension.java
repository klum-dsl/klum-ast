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
package com.blackbuild.klum.ast.gradle;

import com.blackbuild.klum.ast.gradle.convention.GroovyDependenciesExtension;
import com.blackbuild.klum.ast.gradle.convention.GroovyVersion;
import org.gradle.api.provider.Property;

public abstract class KlumExtension {
    protected abstract Property<String> getGroovyVersionInternal();

    /**
     * @deprecated use {@link GroovyDependenciesExtension#setGroovyVersion(String)} or {@link GroovyDependenciesExtension#setGroovyVersion(int)} instead
     * @param version version to use
     */
    @Deprecated(since = "3.0.0", forRemoval = true)
    public void setGroovyVersion(GroovyVersion version) {
        getGroovyVersionInternal().set(version.getVersionString());
    }

    /**
     * Set the groovy version to use. This can be major, major and minor or a full version string. (5, 5.0, 5.0.6)
     * @param version the version to use
     */
    public void setGroovyVersion(String version) {
        getGroovyVersionInternal().set(version);
    }

    /**
     * Set the groovy version to use (major version only).
     * @param version the version to use
     */
    public void setGroovyVersion(int version) {
        getGroovyVersionInternal().set(String.valueOf(version));
    }
}
