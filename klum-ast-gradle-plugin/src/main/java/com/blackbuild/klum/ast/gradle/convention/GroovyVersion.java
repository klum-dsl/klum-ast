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
