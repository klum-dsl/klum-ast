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

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class GroovyDependenciesExtension {

    protected GroovyDependenciesExtension() {
        getUseSpock().convention(true);
    }

    protected abstract Property<Boolean> getUseSpock();

    public void setSkipSpock(boolean skipSpock) {
        getUseSpock().set(!skipSpock);
    }

    public void skipSpock() {
        getUseSpock().set(false);
    }

    public abstract Property<String> getGroovyVersionInternal();

    public Provider<String> getGroovyVersionDependency() {
        return getGroovyVersionInternal().map(Version::fromString).map(Version::getGroovyDependency);
    }

    public Provider<String> getGroovyBomDependency() {
        return getGroovyVersionInternal().map(Version::fromString).map(Version::getGroovyBom);
    }

    public Provider<String> getSpockVersionDependency() {
        // if skipSpock is true, return null, otherwise return the spock dependency
        return getUseSpock().flatMap(value -> value ? getGroovyVersionInternal() : null).map(Version::fromString).map(Version::getSpockDependency);
    }

    /**
     * @deprecated use {@link GroovyDependenciesExtension#setGroovyVersion(String)} or {@link GroovyDependenciesExtension#setGroovyVersion(int)} instead
     * @param groovyVersion Set the groovy version to use
     */
    @Deprecated(since = "3.0.0", forRemoval = true)
    public void setGroovyVersion(GroovyVersion groovyVersion) {
        getGroovyVersionInternal().set(groovyVersion.getVersionString());
    }

    /**
     * Set the groovy version to use. This can be major, major and minor or a full version string. (5, 5.0, 5.0.6)
     * @param version the version to use
     */
    public void setGroovyVersion(int version) {
        getGroovyVersionInternal().set(String.valueOf(version));
    }

    /**
     * Sets the major groovy version to use.
     * @param version the major groovy version to use
     */
    public void setGroovyVersion(String version) {
        getGroovyVersionInternal().set(version);
    }

    public void copyFrom(GroovyDependenciesExtension other) {
        getGroovyVersionInternal().convention(other.getGroovyVersionInternal());
    }

    private static class Version {

        private static final List<String> KNOWN_VERSIONS = List.of("3.0.25", "4.0.32", "5.0.6");
        private static final Pattern versionPattern = Pattern.compile("^(\\d+)(?:\\.(\\d+)(?:\\.(\\d+))?)?$");

        private final String groupId;
        private final String spockVersion;
        private final String groovyVersion;


        static Version fromString(String version) {
            Matcher matcher = versionPattern.matcher(version);

            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid Groovy version format: " + version);
            }

            if (matcher.group(3) != null)
                return new Version(version);

            return KNOWN_VERSIONS.stream()
                    .filter(v -> v.startsWith(version))
                    .findFirst()
                    .map(Version::new)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown Groovy version: " + version));
        }

        Version(String groovyVersion) {
            this.groupId = groovyVersion.startsWith("3.") ? "org.codehaus.groovy" : "org.apache.groovy";
            this.groovyVersion = groovyVersion;

            Matcher matcher = versionPattern.matcher(groovyVersion);

            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid Groovy version format: " + groovyVersion);
            }

            this.spockVersion = "2.4-groovy-" + matcher.group(1) + "." + matcher.group(2);
        }

        String getGroovyDependency() {
            return groupId + ":groovy:" + groovyVersion;
        }

        String getGroovyBom() {
            return groupId + ":groovy-bom:" + groovyVersion;
        }

        String getSpockDependency() {
            return "org.spockframework:spock-core:" + spockVersion;
        }

    }

}
