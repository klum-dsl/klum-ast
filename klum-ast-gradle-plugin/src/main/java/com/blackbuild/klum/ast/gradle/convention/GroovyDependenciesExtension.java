package com.blackbuild.klum.ast.gradle.convention;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

public interface GroovyDependenciesExtension {

    Property<String> getGroovy();

    Property<String> getSpock();

    Property<GroovyVersion> getGroovyVersion();

    default Provider<String> getGroovyVersionDependency() {
        return getGroovyVersion().map(GroovyVersion::getGroovyDependency).orElse(getGroovy());
    }

    default Provider<String> getSpockVersionDependency() {
        return getGroovyVersion().map(GroovyVersion::getSpockDependency).orElse(getSpock());
    }

    default void copyFrom(GroovyDependenciesExtension other) {
        getGroovy().convention(other.getGroovy());
        getSpock().convention(other.getSpock());
        getGroovyVersion().convention(other.getGroovyVersion());
    }
}
