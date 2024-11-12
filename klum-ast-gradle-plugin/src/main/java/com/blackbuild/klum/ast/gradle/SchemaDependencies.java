package com.blackbuild.klum.ast.gradle;

import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

public interface SchemaDependencies extends Dependencies {
    DependencyCollector getSchema();
}
