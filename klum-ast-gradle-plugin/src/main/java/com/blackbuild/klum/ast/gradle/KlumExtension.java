package com.blackbuild.klum.ast.gradle;

import com.blackbuild.klum.ast.gradle.convention.GroovyVersion;
import org.gradle.api.provider.Property;

public abstract class KlumExtension {
    public abstract Property<GroovyVersion> getGroovyVersion();
}
