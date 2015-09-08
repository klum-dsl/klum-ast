package com.blackbuild.groovy.configdsl.transform.model.impl.jobs

import com.blackbuild.groovy.configdsl.transform.DSLConfig

@DSLConfig
class Config {

    Map<String, System> systems

    Map<String, Container> containers

    Map<String, MavenProject> mavenProjects

}

@DSLConfig(key = "name")
class System {
    String name

    Map<String, Environment> environments
}

@DSLConfig(key = "name", owner = "system")
class Environment {

    String name
    String description
    List<String> smokeTests

    String versionFilter

    List<String> auth

    Mail mail

    System system

    String getFullName() {
        return "${system}_${name}"
    }

    List<String> authForComponentDeploy

    boolean isAllowComponentDeploy() {
        return !authForComponentDeploy.isEmpty()
    }
}

@DSLConfig
class Mail {

    List<String> recipients

}

@DSLConfig(key = "name")
class Container {

    String name



}

@DSLConfig(key = "name")
abstract class Buildable {

    String name
    String gitRepo

    String getGitRepo() {
        return gitRepo ?: name
    }
}

@DSLConfig
class MavenProject extends Buildable {

    String parent


}

